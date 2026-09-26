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
    private const val KEY_GRADE = "grade"

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
     * 从标准 `Cookie` 请求头字符串导入 cookie（合并，同名覆盖）。
     *
     * ## 2026-09-26 更正：主域**并不需要**设备链
     *
     * 旧注释称「主域需 `sid` + `ks_sess` + `ks_deviceid` 三件套、只能靠用户从
     * 原版导入」—— **该结论已被实测推翻**。用本项目自身登录 cookie 直打主域
     * 探针 `GET /leo-star/android/exercise/rank/pre-fetch`：
     *
     * | 携带的 cookie | 结果 |
     * |---|---|
     * | 完整（登录 cookie + sid/ks_*） | 200 |
     * | **只留登录 cookie，去掉 sid 与全部 ks_*** | **200** |
     * | 不带 | 401 |
     *
     * 即：**登录本身下发的 cookie 就够拿到主域权限**。主域业务端点报 417 是
     * **只缺 `sign`（编码层）**，与认证无关（认证失败会是 401）。
     *
     * 因此账号页已移除「导入登录态」入口 —— 它是基于错误结论做的多余设计，
     * 会让用户以为必须去原版抄 cookie。本方法保留为**应急通道**（例如切换
     * 环境 / 排障时手工灌 cookie）。
     *
     * 域统一按 `yuanfudao.com` 写入（与原版 MMKV 形态一致），
     * 这样对 `ape-api` 与 `xyks` 两个子域同时生效。导入只覆盖同名项，
     * 不会清掉已登录拿到的 cookie。
     *
     * @param header `name=value; name2=value2` 形态的串
     * @return 实际解析出的条目数（0 表示格式不对）
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
     * 按名字写入一条 cookie（已存在则改值，不存在则新增）。
     *
     * ## 为什么需要它（切换子账号，待办 #6）
     *
     * 用户明确要求：「切换子账号的 cookie 应该使用登录产生的」。
     *
     * 切换成功后，服务端会通过 `Set-Cookie` 下发新的 `userid`（PersistentCookieJar
     * 通常会自动落盘），但**主域请求是否带得上，取决于这条 cookie 的 domain/path
     * 是否被正确改写**。若切换响应没带 `Set-Cookie`（或带了但 jar 未落盘），
     * 后续主域请求仍用旧身份 —— 表现就是「切换了但没生效」。
     *
     * 因此这里提供一条显式的写路径：切换成功后把新 userId 写回 `userid` cookie，
     * **沿用原条目的 domain/path/过期**（不改这些属性，避免把 cookie 挪错域），
     * 只换 value。没有同名条目时按主域新建一条。
     *
     * @param name   cookie 名（如 `userid`）
     * @param value  新值
     * @param domain 无同名条目时使用的域
     * @return 是否写入成功
     */
    fun upsertCookie(name: String, value: String, domain: String = ".yuanfudao.com"): Boolean {
        val current = loadCookies().toMutableList()
        val index = current.indexOfFirst { it.name == name }
        if (index >= 0) {
            val old = current[index]
            current[index] = old.copy(value = value)
        } else {
            current += CookieEntry(
                domain = domain,
                name = name,
                value = value,
                path = "/",
                expiresAt = 0L,
                hostOnly = false,
                httpOnly = true,
                persistent = true,
                secure = false,
            )
        }
        saveCookies(current)
        return true
    }

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
     * 当前登录用户的年级 ID。
     *
     * PK 入口数据（`/leo-game-pk/android/game/homepage`）按年级取，
     * 用错年级会拉到别的年级的入口。真机来源是 `UserVO.grade`。
     *
     * 会话里没存过（未登录 / 老会话）时返回 null，调用方自行回退。
     */
    fun grade(): Int? {
        val v = prefs().getInt(KEY_GRADE, -1)
        return if (v == -1) null else v
    }

    /** 保存年级 ID（登录 / 拉到 UserVO 后调用）。 */
    fun saveGrade(grade: Int) {
        prefs().edit().putInt(KEY_GRADE, grade).apply()
    }

    /** 是否已登录：`userid` cookie 存在即视为已登录。
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
            // 这种响应不是「下发一个空值 cookie」，是「让这个 cookie 失效」。
            //
            // ## 保护范围（2026-09-25 修正）
            //
            // **只对「用户导入的设备链」放行，不删** —— `sid` / `ks_*` 系列
            // 在本项目里只能靠用户从原版 App 导入，而服务端在风控拒绝时
            // 会连发三行清除指令（实测 HTTP 层可见）。若照单删除，用户刚
            // 导入的设备链会在第一次 401 后被服务端指令抹掉，功能当场失效
            // 且用户完全不知道为什么。
            //
            // 其余 cookie（`sess` / `g_sess` / `persistent` 等，本项目自己
            // 登录拿到的）**照常删除** —— 那是服务端登出的正常语义，
            // 拦下来会导致「服务端已登出、本地仍带旧 token」。
            //
            // 取舍理由：设备链是**只读凭据**（本项目拿不到也刷不了），
            // 留着最坏情况是请求被拒（可见错误）；删掉则是静默失效。
            // 宁可让用户看到明确失败，也不要静默把配置吃掉。
            if (c.value.isEmpty() && (c.expiresAt <= 0L || !c.persistent)) {
                if (!isImportedDeviceCredential(c.name)) merged.remove(c.name)
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

    /**
     * 是否是「只能由用户导入、不可由服务端清除」的设备凭据。
     *
     * 名单来自真机原版 MMKV `cookie_store` 的实测集合
     * （`sid` / `ks_sess` / `ks_deviceid` / `ks_persistent` / `ks_r` / `ks_u`）。
     */
    private fun isImportedDeviceCredential(name: String): Boolean =
        name == "sid" || name.startsWith("ks_")

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return SessionStore.loadCookies().mapNotNull { entry ->
            // 兜底：value 为空的条目不发出。历史版本可能已经把空值写进磁盘，
            // 这里再拦一道，避免污染请求。
            if (entry.value.isEmpty()) return@mapNotNull null
            runCatching {
                Cookie.Builder()
                    .path(entry.path)
                    .name(entry.name)
                    .value(entry.value)
                    .apply {
                        // 域口径必须与 hostOnly 一致，否则 cookie 发不出去。
                        //
                        // **这里此前写反了**：无论 hostOnly 真假都调 `hostOnlyDomain(domain)`，
                        // 而 OkHttp 的 `hostOnlyDomain()` 会把 cookie 标记为「精确主机匹配」
                        // （只匹配 `yuanfudao.com`，不匹配 `xyks.yuanfudao.com`）。
                        // 结果就是服务端下发的 `.yuanfudao.com` 域 cookie（前导点 =
                        // 含子域）被错误锁死，主域请求根本带不上 —— 与主域 401 直接相关。
                        //
                        // 正确做法：
                        //  - hostOnly = false（前导点 / 显式 domain）→ `domain()`，含子域；
                        //  - hostOnly = true → `hostOnlyDomain()`，仅精确主机。
                        if (entry.hostOnly) hostOnlyDomain(entry.domain)
                        else domain(entry.domain)
                        if (entry.expiresAt > 0L) expiresAt(entry.expiresAt)
                        if (entry.httpOnly) httpOnly()
                        if (entry.secure) secure()
                    }
                    .build()
            }.getOrNull()
        }
    }
}
