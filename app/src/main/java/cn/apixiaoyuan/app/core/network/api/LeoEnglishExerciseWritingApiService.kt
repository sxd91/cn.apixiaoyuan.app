package cn.apixiaoyuan.app.core.network.api

import cn.apixiaoyuan.app.core.model.EnglishListenExerciseHistoryResultVO
import cn.apixiaoyuan.app.core.model.EnglishListenExerciseResultVO
import cn.apixiaoyuan.app.core.model.ExerciseEnglishDictationContent
import cn.apixiaoyuan.app.core.model.ExerciseEnglishSectionVO
import cn.apixiaoyuan.app.core.model.OnlineExerciseEnglishDictationContent
import cn.apixiaoyuan.app.core.network.BASE_LEO
import cn.apixiaoyuan.app.core.network.BaseUrl
import cn.apixiaoyuan.app.core.network.GsonConverter
import cn.apixiaoyuan.app.core.network.NotNullAndValid
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 英语练习（听写 / 书写）。
 *
 * 挂主域 [BASE_LEO]（`xyks.yuanfudao.com`）。八个方法逐行来自
 * `smali_classes2/com/fenbi/android/leo/api/LeoEnglishExerciseWritingApiService.smali`。
 *
 * 三条子线：
 *  - `/leo-english/android/exercises/` —— 练习主体（听写、评测、错题）
 *  - `/leo-english/android/exercise/{type}` —— 按类型拉章节
 *  - `/leo-english/android/handwriting/exercises/` —— 手写听写
 *
 * **关键实现点**：
 *  - `queryEnglish` 是旧版 `Call`，参数是 `List<MultipartBody.Part>`（图片多部分）；
 *  - `reportEnglishOnlineDictationStart` 收原始 `RequestBody`，不做序列化；
 *  - `getEnglishExercisesSections` 四个 Query（`type` 是 Path，其余三个是 Query），
 *    原 smali 里 `getEnglishExercisesSections(IIII)` 的四个 int 参数对应
 *    `gradeId` / `semester` / `book` 与 Path 里的 `type`。
 *
 * 详见 `docs/LOGIN-API.md` 与 `docs/API-INVENTORY.md`。
 */
interface LeoEnglishExerciseWritingApiService {

    /**
     * 拉听写练习历史结果。
     *
     * GET `/leo-english/android/exercises/history/listen/{exerciseId}`，
     * Path `exerciseId: Long`，返回 `EnglishListenExerciseHistoryResultVO`。
     */
    @BaseUrl(BASE_LEO)
    @GsonConverter
    @NotNullAndValid
    @GET("/leo-english/android/exercises/history/listen/{exerciseId}")
    suspend fun getEnglishDictationExerciseResult(
        @Path("exerciseId") exerciseId: Long,
    ): EnglishListenExerciseHistoryResultVO

    /**
     * 拉听写内容列表（按 unitIds 过滤）。
     *
     * GET `/leo-english/android/exercises/listen/mergedx`，
     * Query `unitIds: String`，返回 `ExerciseEnglishDictationContent`。
     */
    @BaseUrl(BASE_LEO)
    @GsonConverter
    @NotNullAndValid
    @GET("/leo-english/android/exercises/listen/mergedx")
    suspend fun getEnglishExercisesDictationContentList(
        @Query("unitIds") unitIds: String,
    ): ExerciseEnglishDictationContent

    /**
     * 按类型拉练习章节。
     *
     * GET `/leo-english/android/exercise/{type}`，Path `type: Int`，
     * Query `grade` / `semester` / `book`（三个都带），
     * 返回 `ExerciseEnglishSectionVO`。
     *
     * 原 smali 签名 `getEnglishExercisesSections(IIII)` —— 四个 int，
     * 第一个是 Path 里的 `type`，后三个对应三个 Query。
     */
    @BaseUrl(BASE_LEO)
    @GsonConverter
    @NotNullAndValid
    @GET("/leo-english/android/exercise/{type}")
    suspend fun getEnglishExercisesSections(
        @Path("type") type: Int,
        @Query("grade") grade: Int,
        @Query("semester") semester: Int,
        @Query("book") book: Int,
    ): ExerciseEnglishSectionVO

    /**
     * 提交听写练习。
     *
     * POST `/leo-english/android/exercises/listen`，Body 是
     * `EnglishDictationRequestBody`（本工程暂无该类定义，用 `RequestBody` 占位）。
     *
     * @param dto 听写请求体，序列化后发送
     */
    @BaseUrl(BASE_LEO)
    @GsonConverter
    @NotNullAndValid
    @POST("/leo-english/android/exercises/listen")
    suspend fun postEnglishDictationExercise(@Body dto: RequestBody)

    /**
     * 提交听写重练（错题重做）。
     *
     * POST `/leo-english/android/exercises/listen/wrong`。
     */
    @BaseUrl(BASE_LEO)
    @GsonConverter
    @NotNullAndValid
    @POST("/leo-english/android/exercises/listen/wrong")
    suspend fun postEnglishDictationRetrainExercise(@Body dto: RequestBody)

    /**
     * 上传英语书写图片并评测（旧版 `Call`）。
     *
     * POST `/leo-english/android/exercises/evaluate/listen/{exerciseId}`，
     * Path `exerciseId: Long`，`@Part` 多部分表单字段 `images`（可多个），
     * 返回 `EnglishListenExerciseResultVO`。
     *
     * 原 smali 里该方法带 `images: List<MultipartBody.Part>` 形参。
     */
    @BaseUrl(BASE_LEO)
    @Multipart
    @POST("/leo-english/android/exercises/evaluate/listen/{exerciseId}")
    fun queryEnglish(
        @Path("exerciseId") exerciseId: Long,
        @Part("images") images: List<MultipartBody.Part>,
    ): Call<EnglishListenExerciseResultVO>

    /**
     * 重做线上听写。
     *
     * GET `/leo-english/android/handwriting/exercises/listen/repeat/{exerciseId}`，
     * 返回 `OnlineExerciseEnglishDictationContent`。
     */
    @BaseUrl(BASE_LEO)
    @GsonConverter
    @NotNullAndValid
    @GET("/leo-english/android/handwriting/exercises/listen/repeat/{exerciseId}")
    suspend fun repeatEnglishOnlineDictation(
        @Path("exerciseId") exerciseId: Long,
    ): OnlineExerciseEnglishDictationContent

    /**
     * 上报线上听写开始。
     *
     * POST `/leo-english/android/handwriting/exercises/listen/lastUnits`，
     * Body 是原始 `RequestBody`（调用方自行序列化）。
     */
    @BaseUrl(BASE_LEO)
    @POST("/leo-english/android/handwriting/exercises/listen/lastUnits")
    suspend fun reportEnglishOnlineDictationStart(@Body body: RequestBody)
}
