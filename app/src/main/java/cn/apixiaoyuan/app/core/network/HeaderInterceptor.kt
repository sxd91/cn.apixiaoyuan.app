package cn.apixiaoyuan.app.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 统一公共请求头。
 *
 * 原版这些头由宿主 App（`mg/h` 之类的环境类）注入，本工程自己给。
 * 头的名字与取值需真机抓包核实；这里先按小猿口算客户端的常见形态落盘，
 * 待抓包数据回来再对齐。
 *
 * 放到独立文件而不是塞进 [AuthInterceptor]：认证头与设备头生命周期不同，
 * 前者随登录态变，后者随构建变。
 */
class HeaderInterceptor(
    private val appVersionName: String,
    private val appVersionCode: Int,
    private val channel: String = "official",
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()

        builder.header("User-Agent", defaultUserAgent())
        builder.header("X-App-Version", appVersionName)
        builder.header("X-App-Version-Code", appVersionCode.toString())
        builder.header("X-Channel", channel)
        builder.header("Accept", "application/json")

        return chain.proceed(builder.build())
    }

    private fun defaultUserAgent(): String =
        "YFD-Android/$appVersionName (Android; $channel)"
}
