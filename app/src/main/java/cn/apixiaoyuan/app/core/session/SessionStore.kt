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

    /** 是否已登录：`sid` 与 `userid` 同时存在。 */
    val isLoggedIn: Boolean
        get() = cookie("sid") != null && cookie("userid") != null

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
        SessionStore.saveCookies(merged.values.toList())
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return SessionStore.loadCookies().mapNotNull { entry ->
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
