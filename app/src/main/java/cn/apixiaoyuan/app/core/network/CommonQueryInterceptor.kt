package cn.apixiaoyuan.app.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 主域公共查询参数拦截器（`_productId` / `platform` / `version` / `vendor` / `av`
 * / `deviceCategory` / `webviewVersion` / `whRatio`）。
 *
 * ## 为什么需要它（2026-09-25 抓包实测确证）
 *
 * 通过 root + mitmproxy 解密原版真机流量后对比发现：**原版每个主域请求
 * 都带这一组公共 query 参数**，而本项目此前完全没带：
 *
 * | 对比项 | 原版（HTTP 200） | 本项目（417） |
 * |---|---|---|
 * | `_productId` | `611` | ❌ 无 |
 * | `platform` | `android37` | ❌ 无 |
 * | `version` | `3.140.1` | ❌ 无 |
 * | `vendor` | `UC` | ❌ 无 |
 * | `sign` | 32 位 MD5 | ❌ 无 |
 *
 * 实测验证（同一端点，逐个剔除）：
 *
 * | 参数组合 | 结果 |
 * |---|---|
 * | 原版完整参数（含 sign） | **200 + 真实数据** |
 * | 去掉 `sign` | 417 `x-block-by: solar-encoder` |
 * | 仅 `_productId` | 417 |
 * | 无参数 | 417 |
 *
 * ## 关于 `sign`（**尚未复刻，如实标注**）
 *
 * `sign` 是 32 位小写 MD5 hex。已确证的**性质**：
 *  - **是 `(路径, 参数集)` 的纯函数** —— 19/19 个真机样本中，
 *    同一 `(path, 参数集)` 的 sign 完全一致，零随机性，**与时间戳、
 *    cookie、会话全无关**（同一 URL 多次出现 sign 相同）；
 *  - 已尝试 2850 种候选输入形态（path/query 各种拼接顺序、10 种候选盐、
 *    md5/sha1/sha256）**全部未命中**，说明算法含未知盐值或特殊序列化。
 *
 * 本拦截器**不生成 sign**，只负责补可确定的公共参数。缺 `sign` 时主域
 * 业务端点仍会 417 —— 这是当前明确的已知阻塞，不假装解决。
 * 见 [cn.apixiaoyuan.app.core.network.api.LeoMathApiService] 的 KDoc。
 *
 * ## 与原版的一致性
 *
 * 参数值全部逐字取自真机抓包：
 *  - `_productId=611` —— 小猿口算的产品号，来自
 *    `vg/s.a()` 的 `hostProductId("611")`；
 *  - `platform=android37` —— `"android"` + `Build.VERSION.SDK_INT`；
 *  - `version` —— BuildConfig 版本名（本项目对齐原版 `3.140.1`）；
 *  - `vendor=UC`、`av=5`、`deviceCategory=phone`、`webviewVersion=150`、
 *    `whRatio=2.17` —— 真机固定值。
 *
 * 注意：**只对主域生效**。账号域（`ape-api`）实测不需要这些参数，
 * 加了反而可能干扰，因此按 host 精确判断。
 */
class CommonQueryInterceptor(
    private val appVersionName: String,
    private val sdkInt: Int = android.os.Build.VERSION.SDK_INT,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        // 只处理主域（`xyks.yuanfudao.com`）。
        // 账号域（`ape-api.yuanfudao.com`）实测不需要这组参数，加了会干扰。
        if (url.host != LEO_HOST) return chain.proceed(request)

        // 已经带过就跳过（幂等：重试 / 重定向时不重复追加）。
        if (url.queryParameter(PARAM_PRODUCT_ID) != null) return chain.proceed(request)

        val newUrl = url.newBuilder()
            .addQueryParameter(PARAM_PRODUCT_ID, PRODUCT_ID)
            .addQueryParameter(PARAM_PLATFORM, "android$sdkInt")
            .addQueryParameter(PARAM_VERSION, appVersionName)
            .addQueryParameter(PARAM_VENDOR, VENDOR)
            .addQueryParameter(PARAM_AV, AV)
            .addQueryParameter(PARAM_DEVICE_CATEGORY, DEVICE_CATEGORY)
            .addQueryParameter(PARAM_WEBVIEW_VERSION, WEBVIEW_VERSION)
            .addQueryParameter(PARAM_WH_RATIO, WH_RATIO)
            .build()

        return chain.proceed(request.newBuilder().url(newUrl).build())
    }

    companion object {
        /**
         * 主域 host。
         *
         * 与 [cn.apixiaoyuan.app.core.network.NetworkConfig.LEO_HOST_COM] 同值。
         * 这里直接写死而不引用常量：本拦截器是纯字符串判断，不依赖任何初始化，
         * 用常量引用会在单测 / 静态检查里引入不必要的耦合。
         */
        private const val LEO_HOST = "xyks.yuanfudao.com"

        /** 小猿口算产品号。真机抓包逐字：`vg/s.a()` 里 `hostProductId("611")`。 */
        const val PRODUCT_ID = "611"

        const val PARAM_PRODUCT_ID = "_productId"
        const val PARAM_PLATFORM = "platform"
        const val PARAM_VERSION = "version"
        const val PARAM_VENDOR = "vendor"
        const val PARAM_AV = "av"
        const val PARAM_DEVICE_CATEGORY = "deviceCategory"
        const val PARAM_WEBVIEW_VERSION = "webviewVersion"
        const val PARAM_WH_RATIO = "whRatio"

        /** 真机抓包固定值。 */
        const val VENDOR = "UC"
        const val AV = "5"
        const val DEVICE_CATEGORY = "phone"
        const val WEBVIEW_VERSION = "150"
        const val WH_RATIO = "2.17"
    }
}
