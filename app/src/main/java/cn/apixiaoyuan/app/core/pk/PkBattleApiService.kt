package cn.apixiaoyuan.app.core.pk

import cn.apixiaoyuan.app.core.network.BASE_LEO
import cn.apixiaoyuan.app.core.network.BaseUrl
import cn.apixiaoyuan.app.core.network.NeedEncode
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

/**
 * PK 秒结算/循环/并发 的 Retrofit Service。
 *
 * ## 关键设计：为什么显式带 `_productId=631`
 *
 * [cn.apixiaoyuan.app.core.network.CommonQueryInterceptor] 会给主域请求自动补
 * `_productId=611` + `sign` + `platform` + `vendor` 等全套公共参数，但 **PK 接口
 * 要求 `_productId=631`**（记忆 #36 实测：611 会 401 SolarAuthFilter）。
 * 所以本 Service 每个方法都显式带 `_productId=631&_appId=6&version=3.141.1`。
 *
 * 2026-09-26 起 [CommonQueryInterceptor] 已改为「逐参数判断缺哪个补哪个」，
 * 因此本 Service 显式带的 631/6/version 会原样保留，而它缺的
 * sign/platform/vendor/av/deviceCategory/webviewVersion/whRatio/isBackground
 * 会被自动补上 —— 正好满足 PK 提交「631 + 全套公共参数 + sign」的协议要求
 * （本地实测：提交接口缺 sign 就 401 `SolarAuthFilter`）。
 *
 * ## 编码方向（2026-09-26 修正后的权威结论）
 *
 * - 出题：走**旧版明文 `match`**（无 `@NeedDecode`）。`match/v2` 返回 arraybuffer
 *   加密，服务端 `solar-encoder` 拦（417）；旧版 `match` 返回 200 明文 JSON，
 *   本地 Python 已跑通。返回的 [ResponseBody] 是明文 JSON 字节，调用方手动 `Json.parse`。
 * - 提交 `@NeedEncode`：body 序列化 JSON 后 = gzip + `ContentBridge.encode`
 *   （= 原版 `ds/i4.c` = H5 dataEncrypt 桥），由 [NativeEncodeInstaller] 落地，
 *   且 [NeedEncodeInterceptor] 会把 Content-Type 改成 `application/octet-stream`
 *   （与 H5 提交的 header 一致）。**无需任何独立编码器。**
 */
interface PkBattleApiService {

    // ---- 出题（旧版明文 `match`，无 @NeedDecode）----
    //
    // 为什么不用 `match/v2`（2026-09-26 本地实测修正）：
    // `match/v2` / `multi/match/v2` / `final/pk/match/math/v2` 返回 arraybuffer 加密，
    // 服务端 `solar-encoder` 拦截（417）。旧版明文 `match` 返回 HTTP 200 明文 JSON，
    // 本地 Python 已跑通。因此出题统一走旧版明文接口，绕开 417 与 @NeedDecode 不确定性。

    // ---- 对局类型列表（数学知识点 pointList）----
    //
    // 真实网页首页「对局类型」就是这里拉出来的：
    // GET /leo-game-pk/{client}/math/pk/home?grade=N
    // 返回 { pointList: [{ pointId, pointName, pointGrade, winCount }], totalWinCount, weekWinCount, ... }
    // {client} = android。英语对应 english/pk/home（本刷局页不暴露，见需求）。
    @BaseUrl(BASE_LEO)
    @GET("/leo-game-pk/android/math/pk/home")
    suspend fun mathHome(
        @Query("grade") grade: Int,
        @Query("_productId") productId: String = PK_PRODUCT_ID,
        @Query("_appId") appId: String = PK_APP_ID,
        @Query("version") version: String = PK_VERSION,
    ): ResponseBody

    @BaseUrl(BASE_LEO)
    @POST("/leo-game-pk/android/math/pk/match")
    suspend fun mathMatch(
        @Query("pointId") pointId: Int,
        @Query("_productId") productId: String = PK_PRODUCT_ID,
        @Query("_appId") appId: String = PK_APP_ID,
        @Query("version") version: String = PK_VERSION,
    ): ResponseBody

    @BaseUrl(BASE_LEO)
    @POST("/leo-game-pk/android/math/pk/multi/match")
    suspend fun multiMatch(
        @Query("pointId") pointId: Int,
        @Query("_productId") productId: String = PK_PRODUCT_ID,
        @Query("_appId") appId: String = PK_APP_ID,
        @Query("version") version: String = PK_VERSION,
    ): ResponseBody

    @BaseUrl(BASE_LEO)
    @POST("/leo-game-pk/android/final/pk/match/math")
    suspend fun finalMatch(
        @Query("pointId") pointId: Int,
        @Query("_productId") productId: String = PK_PRODUCT_ID,
        @Query("_appId") appId: String = PK_APP_ID,
        @Query("version") version: String = PK_VERSION,
    ): ResponseBody

    /** 语文（英语）PK 出题。题目结构待真机验证，接口已声明。 */
    @BaseUrl(BASE_LEO)
    @POST("/leo-game-pk/android/english/pk/match")
    suspend fun englishMatch(
        @Query("pointId") pointId: Int,
        @Query("_productId") productId: String = PK_PRODUCT_ID,
        @Query("_appId") appId: String = PK_APP_ID,
        @Query("version") version: String = PK_VERSION,
    ): ResponseBody

    // ---- 提交（@NeedEncode）----

    @BaseUrl(BASE_LEO)
    @NeedEncode
    @PUT("/leo-game-pk/android/math/pk/submit")
    suspend fun submitMath(
        @Body body: PkSubmitBody,
        @Query("_productId") productId: String = PK_PRODUCT_ID,
        @Query("_appId") appId: String = PK_APP_ID,
        @Query("version") version: String = PK_VERSION,
    ): ResponseBody

    @BaseUrl(BASE_LEO)
    @NeedEncode
    @POST("/leo-game-pk/android/math/pk/multi/submit")
    suspend fun submitMulti(
        @Body body: PkSubmitBody,
        @Query("_productId") productId: String = PK_PRODUCT_ID,
        @Query("_appId") appId: String = PK_APP_ID,
        @Query("version") version: String = PK_VERSION,
    ): ResponseBody

    @BaseUrl(BASE_LEO)
    @NeedEncode
    @POST("/leo-game-pk/android/final/pk/submit/math")
    suspend fun submitFinal(
        @Body body: PkSubmitBody,
        @Query("_productId") productId: String = PK_PRODUCT_ID,
        @Query("_appId") appId: String = PK_APP_ID,
        @Query("version") version: String = PK_VERSION,
    ): ResponseBody

    /** 语文（英语）PK 提交。 */
    @BaseUrl(BASE_LEO)
    @NeedEncode
    @POST("/leo-game-pk/android/english/pk/submit")
    suspend fun submitEnglish(
        @Body body: PkSubmitBody,
        @Query("_productId") productId: String = PK_PRODUCT_ID,
        @Query("_appId") appId: String = PK_APP_ID,
        @Query("version") version: String = PK_VERSION,
    ): ResponseBody

    companion object {
        /** PK 专属产品号。区别于练习的 611。 */
        const val PK_PRODUCT_ID = "631"

        /** PK 的 `_appId`。 */
        const val PK_APP_ID = "6"

        /**
         * 冒充的小猿口算客户端版本。
         *
         * H5 从 UA `YuanSouTiKouSuan/3.141.1` 正则解析出 version，原生直连没有 UA，
         * 这里显式给同值。**不能**用本项目自己的 BuildConfig.VERSION_NAME（0.1.0）——
         * PK 接口实测缺 version 或 version 不符会 400。
         */
        const val PK_VERSION = "3.141.1"
    }
}