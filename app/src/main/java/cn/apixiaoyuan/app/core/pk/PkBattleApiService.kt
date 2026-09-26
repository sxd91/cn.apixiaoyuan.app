package cn.apixiaoyuan.app.core.pk

import cn.apixiaoyuan.app.core.network.BASE_LEO
import cn.apixiaoyuan.app.core.network.BaseUrl
import cn.apixiaoyuan.app.core.network.NeedDecode
import cn.apixiaoyuan.app.core.network.NeedEncode
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

/**
 * PK 秒结算/循环/并发 的 Retrofit Service。
 *
 * ## 关键设计：为什么显式带 `_productId=631`
 *
 * [cn.apixiaoyuan.app.core.network.CommonQueryInterceptor] 会给主域请求自动补
 * `_productId=611` + `sign`，但 **PK 接口要求 `_productId=631`**（记忆 #36 实测：
 * 611 会 401 SolarAuthFilter，631+_appId+version 才 200）。该拦截器有
 * 「URL 已带 `_productId` 就整段跳过」的保护 —— 所以本 Service 每个方法都
 * 显式带 `_productId=631`，从而**同时绕过 611 与 sign 追加**。
 *
 * 这是符合 H5 行为的：PK 的 axios（`request-legacy`）只有 XSRF、**没有 sign**，
 * 公共参数是 `_productId/_appId/version`（version 来自 UA 正则 `YuanSouTiKouSuan/3.141.1`）。
 * 原生直连等效：version 显式给「冒充的小猿口算客户端版本」`3.141.1`。
 *
 * ## 编码方向（2026-09-26 修正后的权威结论）
 *
 * - 出题 `@NeedDecode`：响应是 arraybuffer 加密，解码 = `ContentBridge.encode` + gunzip
 *   （= 原版 `ds/i4.a` = H5 dataDecrypt 桥），由 [NativeDecodeInstaller] 落地。
 *   拦截后返回的 [ResponseBody] 是明文 JSON 字节，调用方手动 `Json.parse`。
 * - 提交 `@NeedEncode`：body 序列化 JSON 后 = gzip + `ContentBridge.encode`
 *   （= 原版 `ds/i4.c` = H5 dataEncrypt 桥），由 [NativeEncodeInstaller] 落地，
 *   且 [NeedEncodeInterceptor] 会把 Content-Type 改成 `application/octet-stream`
 *   （与 H5 提交的 header 一致）。**无需任何独立编码器。**
 */
interface PkBattleApiService {

    // ---- 出题（@NeedDecode）----

    @BaseUrl(BASE_LEO)
    @NeedDecode
    @POST("/leo-game-pk/android/math/pk/match/v2")
    suspend fun mathMatchV2(
        @Query("pointId") pointId: Int,
        @Query("triggerPeakMatch") triggerPeakMatch: Int = 0,
        @Query("_productId") productId: String = PK_PRODUCT_ID,
        @Query("_appId") appId: String = PK_APP_ID,
        @Query("version") version: String = PK_VERSION,
    ): ResponseBody

    @BaseUrl(BASE_LEO)
    @NeedDecode
    @POST("/leo-game-pk/android/math/pk/multi/match/v2")
    suspend fun multiMatchV2(
        @Query("pointId") pointId: Int,
        @Query("_productId") productId: String = PK_PRODUCT_ID,
        @Query("_appId") appId: String = PK_APP_ID,
        @Query("version") version: String = PK_VERSION,
    ): ResponseBody

    @BaseUrl(BASE_LEO)
    @NeedDecode
    @POST("/leo-game-pk/android/final/pk/match/math/v2")
    suspend fun finalMatchV2(
        @Query("pointId") pointId: Int,
        @Query("_productId") productId: String = PK_PRODUCT_ID,
        @Query("_appId") appId: String = PK_APP_ID,
        @Query("version") version: String = PK_VERSION,
    ): ResponseBody

    /** 语文（英语）PK 出题。题目结构待真机验证，接口已声明。 */
    @BaseUrl(BASE_LEO)
    @NeedDecode
    @POST("/leo-game-pk/android/english/pk/match/v2")
    suspend fun englishMatchV2(
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