package cn.apixiaoyuan.app.core.network.api

import cn.apixiaoyuan.app.core.model.ExamData
import cn.apixiaoyuan.app.core.network.BASE_LEO
import cn.apixiaoyuan.app.core.network.BaseUrl
import cn.apixiaoyuan.app.core.network.CheckNothing
import cn.apixiaoyuan.app.core.network.GsonConverter
import cn.apixiaoyuan.app.core.network.NeedEncode
import cn.apixiaoyuan.app.core.network.NotNullAndValid
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * 口算练习出题与提交接口。
 *
 * 挂主域 [BASE_LEO]（`xyks.yuanfudao.com`）。方法逐行来自
 * `smali_classes6/com/yuanfudao/android/leo/exercise/oral/ApiService.smali`
 * （原类 6 个方法，本工程搬出题链路必需的 3 条；`preSubmitArrangedHomeworkExercise`
 * 与 `getArrangedHomeworkStudentDetail` 属于布置作业链路，不在本范围）。
 *
 * **完整出题链路**：
 * ```
 * 1. getExercisesKeyPoints(type, grade, semester, book)   → 知识点树
 * 2. getExamInfo(keypointId, limit)                       → 生成一整套题（ExamData）
 * 3. 本地作答 → 填 userAnswer / script / status / costTime
 * 4. uploadExamResult(examId, body)                       → 提交（body 需 native 编码）
 * 5. getExamResult(examId)                                → 拉回带批改的结果
 * ```
 *
 * 关键实现点（均逐行确证）：
 *  - `getExamInfo` 是 **FormUrlEncoded + POST**，两个 `@Field`：
 *    `keypointId` / `limit`（都是 String，不是 Int）。返回 `ExamVO`。
 *  - `uploadExamResult` 的 body 带 **`@NeedEncode`** —— 请求体需
 *    `libRequestEncoder.so` 编码后发出。本工程 [NeedEncodeInterceptor]
 *    已挂恒等实现挡着，native 接入后自动生效。
 *  - `uploadExamResubmitResult` 比 `uploadExamResult` 多一个
 *    Query `syncData: Int`，是「重做错题」链路，两者同路径同方法。
 */
interface LeoOralApiService {

    /**
     * 出题：按知识点生成一整套练习。
     *
     * POST `/leo-math/android/exams`，FormUrlEncoded。
     *
     * @param keypointId 知识点 ID（String 形态，来自
     *                   [cn.apixiaoyuan.app.core.model.ExerciseScopeKeypoint.id]）
     * @param limit      题目数量（String 形态）。取值见
     *                   [cn.apixiaoyuan.app.core.model.ExerciseType.chooseNumArray]，
     *                   口算练习为 `[10, 20, 30, 60, 100]`
     */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @GsonConverter
    @FormUrlEncoded
    @POST("/leo-math/android/exams")
    suspend fun getExamInfo(
        @Field("keypointId") keypointId: String,
        @Field("limit") limit: String,
    ): ExamData

    /**
     * 提交练习结果。
     *
     * PUT `/leo-math/android/exams/v2/{examId}`。
     *
     * **body 带 `@NeedEncode`** —— 请求体在发出前需 native 编码。
     * 提交前必须本地填好每题的：
     *  - `userAnswer` —— 用户作答
     *  - `script` —— 笔迹 JSON
     *  - `status` —— 1 对 / -1 错
     *  - `costTime` —— 每题耗时，**服务端下限 300ms**
     *
     * 以及整卷的 `correctCnt` / `costTime`。
     *
     * @param examId 来自出题响应的 `idString`
     */
    @BaseUrl(BASE_LEO)
    @GsonConverter
    @NotNullAndValid
    @NeedEncode
    @PUT("/leo-math/android/exams/v2/{examId}")
    suspend fun uploadExamResult(
        @Path("examId") examId: String,
        @Body body: ExamData,
    ): ExamData

    /**
     * 拉取练习结果（含批改）。
     *
     * GET `/leo-math/android/exams/{examId}`。
     */
    @BaseUrl(BASE_LEO)
    @GsonConverter
    @NotNullAndValid
    @GET("/leo-math/android/exams/{examId}")
    suspend fun getExamResult(
        @Path("examId") examId: String,
    ): ExamData
}
