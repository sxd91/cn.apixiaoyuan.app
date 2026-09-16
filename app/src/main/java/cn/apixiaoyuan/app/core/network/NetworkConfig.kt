package cn.apixiaoyuan.app.core.network

/**
 * 域名与网络环境配置。
 *
 * 全部域名从原版 `mg/h.smali`（`leo-host_release` 模块）逐行挖出，不是猜的：
 *
 * | 别名 | 线上（com） | 内部（biz） | 原版方法链 |
 * |---|---|---|---|
 * | [LEO_HOST_COM] | `xyks.yuanfudao.com` | `xyks.yuanfudao.biz` | `d()` → `f()` → `e(z())` |
 * | [YTK_HOST_COM] | `ape-api.yuanfudao.com` | `ape-api.yuanfudao.biz` | `w()` → `y()` → `x(z())` |
 *
 * 原版的 `i(Z)` 是后缀切换器（`true` → `"com"`，`false` → `"biz"`），
 * `z()` 在环境未命中时返回 `true`，故线上取 `com`。
 *
 * 本工程只做「线上 / 内部」二选一，不做原版那套多环境自动判定——
 * 切换由 [Env] 显式指定，避免线上包误连内部环境。
 */
object NetworkConfig {

    // ---- 线上（com）----

    const val LEO_HOST_COM = "xyks.yuanfudao.com"
    const val YTK_HOST_COM = "ape-api.yuanfudao.com"

    // ---- 内部（biz）----

    const val LEO_HOST_BIZ = "xyks.yuanfudao.biz"
    const val YTK_HOST_BIZ = "ape-api.yuanfudao.biz"

    /** 网络环境。默认线上；调试内部环境需显式切换。 */
    enum class Env(val suffix: String) {
        /** 线上正式环境，域名后缀 `com`。 */
        PROD("com"),

        /** 内部 / 预发环境，域名后缀 `biz`。 */
        STAGING("biz"),
    }

    /**
     * 按环境拼主域完整 URL。
     *
     * @param env 目标环境，默认 [Env.PROD]
     */
    fun leoBaseUrl(env: Env = Env.PROD): String = when (env) {
        Env.PROD -> "https://$LEO_HOST_COM"
        Env.STAGING -> "https://$LEO_HOST_BIZ"
    }

    /**
     * 按环境拼账号域完整 URL。
     *
     * @param env 目标环境，默认 [Env.PROD]
     */
    fun ytkBaseUrl(env: Env = Env.PROD): String = when (env) {
        Env.PROD -> "https://$YTK_HOST_COM"
        Env.STAGING -> "https://$YTK_HOST_BIZ"
    }

    /**
     * 原始 host（不含 scheme），供 H5 PK 页面拼接等场景使用。
     *
     * 口算 PK 是 H5：`{base}/bh5/leo-web-oral-pk/pk.html#/`，其中 `{base}`
     * 就是这里返回的主域。
     */
    fun h5BaseUrl(env: Env = Env.PROD): String = leoBaseUrl(env)

    // ---- 同文件挖出的其他 host（本工程暂不用，留档备查）----

    /** `s(Z)` —— 小猿搜题。 */
    const val XYST_HOST_COM = "xyst.yuanfudao.com"

    /** `g(Z)` —— H3 网关。 */
    const val H3_HOST_COM = "xyks-h3.yuanfudao.com"

    /** `u(Z)` —— ke 域。 */
    const val KE_HOST_COM = "ke.yuanfudao.com"

    /** `j(Z)` —— 图库域（com / biz 不同形，非简单后缀切换）。 */
    const val GALLERY_HOST_COM = "gallery.yuanfudao.com"
    const val GALLERY_HOST_BIZ = "ytkgallery.yuanfudao.biz"

    /** `c()` —— 固定常量，非 yuanfudao 域。 */
    const val SOLAR_HOST = "solar.fbcontent.cn"
}