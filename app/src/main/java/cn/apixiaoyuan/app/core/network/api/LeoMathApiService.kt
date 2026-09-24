package cn.apixiaoyuan.app.core.network.api

import cn.apixiaoyuan.app.core.model.ExerciseScopeData
import cn.apixiaoyuan.app.core.network.BASE_LEO
import cn.apixiaoyuan.app.core.network.BaseUrl
import cn.apixiaoyuan.app.core.network.GsonConverter
import cn.apixiaoyuan.app.core.network.NotNullAndValid
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 数学练习主接口。
 *
 * 挂主域 [BASE_LEO]（`xyks.yuanfudao.com`）。方法逐行来自
 * `smali_classes3/com/fenbi/android/leo/network/api/LeoMathApiService.smali`
 * （原类 2244 行，本工程只搬出题链路必需的那一条）。
 *
 * **出题链路**：
 * ```
 * getExercisesKeyPoints(type, grade, semester, book) → ExerciseVO
 *   → sections[].keypoints[] 里挑一个 keypointId
 *   → QuickExerciseActivity(keypointId, limit) 出题
 * ```
 * `type` 取自 [cn.apixiaoyuan.app.core.model.ExerciseType.exerciseType]：
 * 口算练习 = 0，题量可选 [10, 20, 30, 60, 100]。
 *
 * 原 smali 签名：
 * ```
 * getExercisesKeyPoints(IIIILkotlin/coroutines/Continuation;)Ljava/lang/Object;
 *   p1 @Path("type")      type
 *   p2 @Query("grade")    grade
 *   p3 @Query("semester") semester
 *   p4 @Query("book")     book
 * ```
 * 注解组合 `@GsonConverter + @NotNullAndValid + @GET`，返回可空
 * `ExerciseVO`。原版还有 `getMathExercisesTabV6` / `getMathExercisesCall` /
 * `uploadExamResult` 等三十余条，属于练习首页 tab、答题详情、结果上报，
 * 不在本轮「出题」范围内，后续按需补。
 */
interface LeoMathApiService {

    /**
     * 按类型拉练习知识点树。
     *
     * GET `/leo-math/android/exams/exercises/type/{type}`，
     * Path `type: Int` + Query `grade` / `semester` / `book`（三个都是原始 int，
     * 原版签名 `(IIII...)` 无 nullable）。
     *
     * @param type     [cn.apixiaoyuan.app.core.model.ExerciseType.exerciseType]，
     *                 口算练习传 0
     * @param grade    年级 ID
     * @param semester 学期（1 上 / 2 下）
     * @param book     教材版本
     */
    @BaseUrl(BASE_LEO)
    @GsonConverter
    @NotNullAndValid
    @GET("/leo-math/android/exams/exercises/type/{type}")
    suspend fun getExercisesKeyPoints(
        @Path("type") type: Int,
        @Query("grade") grade: Int,
        @Query("semester") semester: Int,
        @Query("book") book: Int,
    ): ExerciseScopeData
}
