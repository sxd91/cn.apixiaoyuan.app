package cn.apixiaoyuan.app.core.network

import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Invocation

/**
 * 登录态与账号域参数注入。
 *
 * 两条规则：
 *  1. 账号域（[BASE_YTK]）的请求带 Query 参数 `YFD_U`（Long，猿辅导用户 ID）；
 *  2. 所有已登录请求带鉴权 header（名字待抓包确认，先用 `Authorization`）。
 *
 * [sessionProvider] 由 `core/session/SessionManager` 实现；未登录时返回 null，
 * 本 Interceptor 跳过注入。
 *
 * 顺序约束：必须位于 [BaseUrlInterceptor] 之后——[BaseUrl] 注解决定最终 host，
 * 鉴权要读同一个注解才能判断是不是账号域。
 */
class AuthInterceptor(
    private val sessionProvider: () -> AuthSnapshot?,
) : Interceptor {

    /**
     * 鉴权快照。
     *
     * R2 已由真机确证（Redmi onyx / Android 16 / KernelSU）：登录态由
     * 服务端 `Set-Cookie` 下发，原版把整份 cookie 列表存进 MMKV 的
     * `cookie_store`（键 `cookieJsonListKey`）。因此这里的登录凭据是
     * [cookie]（完整的 `Cookie:` 请求头），不再是 `Bearer token`。
     *
     * `token` 字段保留但标为废弃 —— 若某些接口确实走 `Authorization`
     * 头（尚未在真机流量里观察到），仍可从此注入。
     */
    data class AuthSnapshot(
        val yfdU: Long?,
        val cookie: String? = null,
        @Deprecated("R2 已确证登录态走 Cookie，Authorization 头未在真机流量中观察到")
        val token: String? = null,
    )

    @Suppress("DEPRECATION")   // 兼容出口主动读废弃的 token 字段，警告是预期内的
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val snapshot = sessionProvider()

        var builder = request.newBuilder()

        // 1. 账号域注入 YFD_U
        val invocation = request.tag(Invocation::class.java)
        val alias = invocation?.method()
            ?.getAnnotation(BaseUrl::class.java)
            ?.value

        // 账号域必注入；主域有值也注入 —— 真机 userid cookie 挂在
        // `yuanfudao.com` 全域，主域请求同样带 YFD_U。
        //
        // 关键：只在请求**未显式携带** YFD_U 时才注入。
        // 登录/发码接口自己传的是设备指纹（DeviceFingerprint，设备级频控键），
        // 与这里的 userid（用户级）语义不同；setQueryParameter 会覆盖已存在的
        // 同名参数，无条件注入会把设备指纹冲掉。
        val hasExplicitYfdU = request.url.queryParameter("YFD_U") != null
        if (!hasExplicitYfdU && snapshot?.yfdU != null && snapshot.yfdU > 0L) {
            val withQuery = request.url.newBuilder()
                .setQueryParameter("YFD_U", snapshot.yfdU.toString())
                .build()
            builder = builder.url(withQuery)
        }

        // 2. 登录态：完整 Cookie 头（sid / userid / sess / g_sess / ks_* 等）
        if (!snapshot?.cookie.isNullOrEmpty()) {
            builder.header("Cookie", snapshot.cookie)
        }

        // 3. 兼容路径：Authorization 头（当前未观察到，保留出口）
        if (!snapshot?.token.isNullOrEmpty()) {
            builder.header("Authorization", "Bearer ${snapshot.token}")
        }

        return chain.proceed(builder.build())
    }
}
