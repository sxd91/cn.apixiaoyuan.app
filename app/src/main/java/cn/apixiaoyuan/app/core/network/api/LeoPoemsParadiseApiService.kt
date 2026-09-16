package cn.apixiaoyuan.app.core.network.api

import cn.apixiaoyuan.app.core.network.BASE_LEO
import cn.apixiaoyuan.app.core.network.BaseUrl
import cn.apixiaoyuan.app.core.network.CheckNothing
import cn.apixiaoyuan.app.core.network.GsonConverter
import cn.apixiaoyuan.app.core.network.NotNullAndValid
import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 诗词乐园 / 诗词 PK。
 *
 * 挂主域 [BASE_LEO]（`xyks.yuanfudao.com`）。五个方法逐行来自
 * `smali_classes6/com/yuanfudao/android/leo/poems_paradise/LeoPoemsParadiseApiService.smali`。
 *
 * 两条业务线：
 *  - `/leo-game-pk/` —— 诗词 PK 入口（`getPoemsPkEntryData`）
 *  - `/leo-poetry/`  —— 诗词背诵记录 + 诗词乐园（garden）
 *
 * ⚠️ 五个方法的**返回类型全部未从 smali 读出**（只确证了路径与参数），
 * 这里统一用 `JsonElement` 占位，保证编译通过与请求可发；
 * 真机抓到完整响应后再落具体 VO。
 *
 * 详见 `docs/API-INVENTORY.md`。
 */
interface LeoPoemsParadiseApiService {

    /**
     * 拉诗词 PK 入口数据。
     *
     * GET `/leo-game-pk/android/game/homepage`，Query `grade: Int`。
     */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @GsonConverter
    @GET("/leo-game-pk/android/game/homepage")
    suspend fun getPoemsPkEntryData(@Query("grade") grade: Int): JsonElement

    /**
     * 拉诗词背诵记录（分页）。
     *
     * GET `/leo-poetry/android/article/recite`，
     * Query `cursor: String` + `limit: Int`。
     */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @GsonConverter
    @GET("/leo-poetry/android/article/recite")
    suspend fun getPoemsReciteRecords(
        @Query("cursor") cursor: String,
        @Query("limit") limit: Int,
    ): JsonElement

    /**
     * 拉诗词乐园内容分组。
     *
     * GET `/leo-poetry/android/poetry-garden/content-set-group/{type}`，
     * Path `type: Int`。
     */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @GsonConverter
    @GET("/leo-poetry/android/poetry-garden/content-set-group/{type}")
    suspend fun getPoetryGardenContentGroup(@Path("type") type: Int): JsonElement

    /**
     * 拉诗词乐园内容集详情。
     *
     * GET `/leo-poetry/android/poetry-garden/content-set/{type}/{contentSetId}`，
     * Path `type: Int` + `contentSetId: Int`。
     */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @GsonConverter
    @GET("/leo-poetry/android/poetry-garden/content-set/{type}/{contentSetId}")
    suspend fun getPoetryGardenContentSetInfo(
        @Path("type") type: Int,
        @Path("contentSetId") contentSetId: Int,
    ): JsonElement

    /**
     * 拉诗词乐园主页信息。
     *
     * GET `/leo-poetry/android/poetry-garden`，Query `grade: Int`。
     */
    @BaseUrl(BASE_LEO)
    @NotNullAndValid
    @GsonConverter
    @GET("/leo-poetry/android/poetry-garden")
    suspend fun getPoetryGardenMainInfo(@Query("grade") grade: Int): JsonElement
}