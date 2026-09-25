package cn.apixiaoyuan.app.core.session

import android.content.Context
import android.content.SharedPreferences
import cn.apixiaoyuan.app.core.network.AuthInterceptor
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 登录会话存储。
 *
 * R2 已由真机确证：登录凭据不是响应体里的字段，而是服务端通过
 * `Set-Cookie` 下发的 Cookie 集合。原版把整个 cookie 列表以 JSON 存进
 * MMKV 的 `cookie_store`，键为 `cookieJsonListKey`。
 *
 * 这里用 SharedPreferences 落地等价结构 —— 不引入 MMKV 依赖，
 * 存的是同一份 JSON（[CookieEntry] 列表），字段名与原版逐字对齐，
 * 这样真机上抓到的 cookie 字符串可以直接喂进来，也能原样导出。
 *
 * 真机实测的关键 cookie（Redmi onyx / Android 16）：
 *  - `sid`    = 7012770506069852030，expiresAt=253402300799999（永不过期）
 *  - `userid` = 1066052990，等于 UserVO.userId，就是 YFD_U 要注入的值
 *  - `sess` / `g_sess` / `ks_sess` = 会话 token
 *  - `ks_persistent` / `ks_r` / `ks_u` / `ks_deviceid` = 设备指纹与风控链
 */
object SessionStore {

    private const val PREF_NAME = "leo_session"
    private const val KEY_COOKIES = "cookieJsonListKey"
    private const val KEY_YFD_U = "yfd_u"
    private const val KEY_SUB_USER_IDS = "sub_user_ids"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun prefs(): SharedPreferences {
        val ctx = appContext ?: error("SessionStore.init() 未调用")
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 单个 cookie。字段名对齐原版 MMKV 里的 JSON。
     */
    @Serializable
    data class CookieEntry(
        @SerialName("domain") val domain: String,
        @SerialName("name") val name: String,
        @SerialName("value") val value: String,
        @SerialName("path") val path: String = "/",
        @SerialName("expiresAt") val expiresAt: Long = 0L,
        @SerialName("hostOnly") val hostOnly: Boolean = false,
        @SerialName("httpOnly") val httpOnly: Boolean = true,
        @SerialName("persistent") val persistent: Boolean = true,
        @SerialName("secure") val secure: Boolean = false,
    )

    /** 写入完整 cookie 列表（登录成功后调用）。 */
    fun saveCookies(cookies: List<CookieEntry>) {
        prefs().edit().putString(KEY_COOKIES, json.encodeToString(cookies)).apply()
        // sid 或 userid 存在即视为有登录态
        cookies.firstOrNull { it.name == "userid" }?.value?.toLongOrNull()?.let {
            prefs().edit().putLong(KEY_YFD_U, it).apply()
        }
    }

    /**
     * 单独写入 `YFD_U`（登录态兜底）。
     *
     * 正常路径下 `userid` cookie 由 [saveCookies] 一并写入；本方法供
     * 直连版登录在「响应体解析成功、但响应未带 Set-Cookie」时兜底，
     * 避免解析成功却被判为未登录。
     */
    fun saveYfdU(value: Long) {
        prefs().edit().putLong(KEY_YFD_U, value).apply()
    }

    /**
     * 保存子账号（宝贝学习账号）ID 列表。
     *
     * 数据源是登录响应 `UserAccount.subUserInfos.project2SubUserInfo["6"].subUserIds` ——
     * 服务端**没有**单独的「我的子账号列表」接口，只有 `batchGet` 批量换资料，
     * 所以 ID 列表必须在登录时截下来。
     *
     * **列表第一个元素是主账号自己**，调用方负责区分（见
     * [cn.apixiaoyuan.app.core.account.AccountRepository.fetchSubAccounts]）。
     */
    fun saveSubUserIds(ids: List<Int>) {
        prefs().edit().putString(KEY_SUB_USER_IDS, ids.joinToString(",")).apply()
    }

    /** 读子账号 ID 列表。未登录 / 单账号用户返回空表。 */
    fun subUserIds(): List<Int> {
        val raw = prefs().getString(KEY_SUB_USER_IDS, null) ?: return emptyList()
        return raw.split(',').mapNotNull { it.trim().toIntOrNull() }
    }

    /**
     * 从标准 `Cookie` 请求头字符串导入 cookie（合并，同名覆盖）。
     *
     * ## 为什么需要这个入口
     *
     * 2026-09-25 实测确证：**主域（`xyks.yuanfudao.com`）的认证需要
     * `sid` + `ks_sess` + `ks_deviceid` 三者同时存在** ——
     *
     * | 携带的 cookie | 结果 |
     * |---|---|
     * | 无 / 仅 `sid` / `sid`+`ks_sess` / `sid`+`ks_deviceid` | 401 `unauthorized` |
     * | **`sid` + `ks_sess` + `ks_deviceid`** | 不再 401（授权通过） |
     *
     * 而这三者**只有原版的其它登录通道会下发**：本项目走的直连版
     * `POST /accounts/android/safe/login`（账号域）实测只回
     * `sess` / `userid` / `g_sess` / `__sub_user_infos__` / `g_loc` / `persistent`
     * —— **从不含 `sid` 与 `ks_*` 系列**。它们也不在 APK 的 smali / assets /
     * 任何 so 里（已全盘查过），是服务端在特定风控流程中下发的。
     *
     * 所以本项目**无法自己拿到**这三个 cookie。能给用户的诚实方案是：
     * 从已登录的原版 App 里导出 cookie，粘进来导入。
     *
     * ## 导入格式
     *
     * 标准 `Cookie` 头形态（`name=value; name2=value2`），或浏览器
     * DevTools / 抓包工具里直接复制的形态。**域名统一按域根 `yuanfudao.com`
     * 写入**（实测原版就是 `"domain":"yuanfudao.com"`，不带前导点），
     * 这样 cookie 对 `ape-api` 与 `xyks` 两个子域同时生效。
     *
     * @param header `name=value; name2=value2` 形态的串
     * @return 实际导入的条目数
     */
    fun importCookieHeader(header: String): Int {
        val parsed = header.split(';')
            .mapNotNull { part ->
                val kv = part.trim()
                if (kv.isEmpty()) return@mapNotNull null
                val eq = kv.indexOf('=')
                if (eq <= 0) return@mapNotNull null
                val name = kv.substring(0, eq).trim()
                val value = kv.substring(eq + 1).trim()
                if (name.isEmpty() || value.isEmpty()) return@mapNotNull null
                name to value
            }
        if (parsed.isEmpty()) return 0

        val merged = linkedMapOf<String, CookieEntry>()
        loadCookies().forEach { merged[it.name] = it }
        parsed.forEach { (name, value) ->
            merged[name] = CookieEntry(
                domain = "yuanfudao.com",
                name = name,
                value = value,
                path = "/",
                // 导入的 cookie 不设过期时刻：原版这些字段的 expiresAt 是远未来
                // （253402300799999），但导入时无从得知，留 0 让 CookieJar
                // 按 session cookie 处理 —— 至少不会因过期立刻失效。
                expiresAt = 0L,
                hostOnly = false,
                httpOnly = true,
                persistent = true,
                secure = false,
            )
        }
        saveCookies(merged.values.toList())
        return parsed.size
    }

    /** 读出全部 cookie；无会话时返回空表。 */
    fun loadCookies(): List<CookieEntry> {
        val raw = prefs().getString(KEY_COOKIES, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<CookieEntry>>(raw) }.getOrDefault(emptyList())
    }

    /** 取某个 cookie 的值，没有返回 null。 */
    fun cookie(name: String): String? = loadCookies().firstOrNull { it.name == name }?.value

    /**
     * 当前登录用户 ID（`YFD_U` 的取值）。
     *
     * 真机确证：`userid` cookie 与 `UserVO.userId` 同值。
     */
    val yfdU: Long?
        get() {
            val v = prefs().getLong(KEY_YFD_U, -1L)
            return if (v == -1L) cookie("userid")?.toLongOrNull() else v
        }

    /**
     * 是否已登录：`userid` cookie 存在即视为已登录。
     *
     * ## 判定口径为什么不是 `sid`
     *
     * 2026-09-25 实测：直连版 `POST /accounts/android/safe/login`
     * 成功时下发的是 `sess` / `userid` / `g_sess` / `__sub_user_infos__`
     * / `g_loc` / `persistent`，**从不含 `sid`**。
     *
     * `sid` 出现在真机原版（走运营商一键登录等其它通道）的 cookie 表里，
     * 与本项目走的短信直连通道不是同一条。早先按真机 cookie 表把 `sid`
     * 写成必要条件，会让「直连版登录成功」被判为未登录 —— 登录页跳转后
     * 首页登录态卡仍显示未登录。
     *
     * 取 `userid` 作唯一判据的原因：它同时是 [yfdU] 的取值来源，
     * 也就是本项目后续所有鉴权请求真正依赖的那个值。它存在即有登录态。
     */
    val isLoggedIn: Boolean
        get() = cookie("userid") != null

    /** 清空登录态（登出）。 */
    fun clear() {
        prefs().edit().clear().apply()
    }

    /**
     * 组装成 `Cookie` 请求头（`name=value; name2=value2`）。
     *
     * 供 [AuthInterceptor] 注入用。
     */
    fun cookieHeader(): String? {
        val list = loadCookies()
        if (list.isEmpty()) return null
        return list.joinToString("; ") { "${it.name}=${it.value}" }
    }

    /** 生成给 [AuthInterceptor] 用的鉴权快照。 */
    fun snapshot(): AuthInterceptor.AuthSnapshot? {
        val cookie = cookieHeader() ?: return null
        return AuthInterceptor.AuthSnapshot(yfdU = yfdU, cookie = cookie)
    }
}

/**
 * OkHttp 持久化 CookieJar。
 *
 * R2 落地收口：登录响应的 `Set-Cookie` 由 OkHttp 在响应返回时交给这里，
 * 写入 [SessionStore]；之后每个请求的 `Cookie` 头也由这里从 [SessionStore] 组装。
 * 这比在 [AuthInterceptor] 里手动拼 cookie 更贴近原版行为 —— 原版就是
 * 靠一个 CookieJar 把整份 cookie 列表持久化到 MMKV 的 `cookie_store`。
 *
 * 与 [AuthInterceptor] 的分工：
 *  - [AuthInterceptor] 负责 `YFD_U` 查询参数注入（那是业务参数，不是 cookie）；
 *  - 本类负责 cookie 的读写。两者都读 [SessionStore]，数据源单一。
 *
 * 注意：httpOnly 的 cookie（sid / sess / g_sess 都是）在真机 MMKV 里
 * 是以明文 JSON 存的，本工程同样明文落 SharedPreferences —— 与原版一致。
 */
object PersistentCookieJar : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val merged = linkedMapOf<String, SessionStore.CookieEntry>()
        // 先放入已有的，同名新 cookie 覆盖
        SessionStore.loadCookies().forEach { merged[it.name] = it }
        cookies.forEach { c ->
            // 服务端删除指令：value 为空且已过期（典型形态 Set-Cookie: ks_sess=;Max-Age=0）。
            //
            // 这种响应不是「下发一个空值 cookie」，是「让这个 cookie 失效」。原版
            // 小猿口算的风控在 401 时会连发三行 ks_sess / ks_persistent / ks_deviceid
            // 的清除指令（实测 HTTP 层可见）。若照单写入空值，本地就留下一条
            // 「被服务端明确标记失效」的 cookie 记录，之后每次请求都带着空指纹走，
            // 风控看到的就是一台被拒过的设备，陷入死循环。
            //
            // 正确处理：把同名旧条目从表里移除，而不是写入空值。
            if (c.value.isEmpty() && (c.expiresAt <= 0L || !c.persistent)) {
                merged.remove(c.name)
            } else {
                merged[c.name] = SessionStore.CookieEntry(
                    domain = c.domain,
                    name = c.name,
                    value = c.value,
                    path = c.path,
                    expiresAt = c.expiresAt,
                    hostOnly = c.hostOnly,
                    httpOnly = c.httpOnly,
                    persistent = c.persistent,
                    secure = c.secure,
                )
            }
        }
        SessionStore.saveCookies(merged.values.toList())
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return SessionStore.loadCookies().mapNotNull { entry ->
            // 兜底：value 为空的条目不发出。历史版本可能已经把空值写进磁盘，
            // 这里再拦一道，避免污染请求。
            if (entry.value.isEmpty()) return@mapNotNull null
            runCatching {
                Cookie.Builder()
                    .domain(entry.domain)
                    .path(entry.path)
                    .name(entry.name)
                    .value(entry.value)
                    .apply {
                        if (entry.expiresAt > 0L) expiresAt(entry.expiresAt)
                        if (entry.httpOnly) httpOnly()
                        if (entry.secure) secure()
                        if (!entry.hostOnly) hostOnlyDomain(entry.domain)
                    }
                    .build()
            }.getOrNull()
        }
    }
}
