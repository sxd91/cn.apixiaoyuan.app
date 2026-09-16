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

    data class AuthSnapshot(
        val yfdU: Long?,
        val token: String?,
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val snapshot = sessionProvider()

        var builder = request.newBuilder()

        // 1. 账号域注入 YFD_U
        val invocation = request.tag(Invocation::class.java)
        val alias = invocation?.method()
            ?.getAnnotation(BaseUrl::class.java)
            ?.value

        if (alias == BASE_YTK && snapshot?.yfdU != null) {
            val withQuery = request.url.newBuilder()
                .setQueryParameter("YFD_U", snapshot.yfdU.toString())
                .build()
            builder = builder.url(withQuery)
        }

        // 2. 登录态 header
        if (!snapshot?.token.isNullOrEmpty()) {
            builder.header("Authorization", "Bearer ${snapshot.token}")
        }

        return chain.proceed(builder.build())
    }
}