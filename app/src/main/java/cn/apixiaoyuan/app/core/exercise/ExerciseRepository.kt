package cn.apixiaoyuan.app.core.exercise

import cn.apixiaoyuan.app.core.model.ExamData
import cn.apixiaoyuan.app.core.model.ExerciseEnglishSectionVO
import cn.apixiaoyuan.app.core.model.ExerciseScopeData
import cn.apixiaoyuan.app.core.model.ExerciseType
import cn.apixiaoyuan.app.core.model.LeoCurrentTaskInfo
import cn.apixiaoyuan.app.core.model.LeoUserCurrentExpData
import cn.apixiaoyuan.app.core.network.ServiceLocator

/**
 * 练习链路的数据入口。
 *
 * UI 层不直接碰 Retrofit Service —— 这是 DEV-PLAN 里定的分层纪律。
 * 本类把 [ServiceLocator] 里与练习相关的读接口包一层，做三件事：
 *
 *  1. 统一异常兜底：Service 抛的 [ApiException] 系列在这里收敛成 null 或默认值，
 *     UI 只处理「有数据 / 没数据」两态，不处理六种异常分类；
 *  2. 聚合调用：首页要同时拿任务卡与经验值，两个请求并发发出，
 *     避免串行等待；
 *  3. 隔离变化：原版接口返回结构若与推断不符，只改本类，不动 UI。
 *
 * 当前覆盖的都是**读接口**，不涉及 [cn.apixiaoyuan.app.core.network.NeedDecode]
 * 与 `@NeedEncode` —— 那两类接口在练习写链路（提交答案、上报经验）上，
 * 留待 native 解码桥（DEV-PLAN 模块 11）接入后再说。
 *
 * 数据来源（`docs/API-INVENTORY.md`）：
 *  - `GET /leo-star/android/exercise/rank/pre-fetch` → [LeoUserCurrentExpData]
 *  - `GET /leo-star/android/exercise/task/home` → [LeoCurrentTaskInfo]
 *  - `GET /leo-english/android/exercise/{type}` → [ExerciseEnglishSectionVO]
 */
object ExerciseRepository {

    /**
     * 拉首页任务卡。失败返回 null，UI 显示空态。
     *
     * 注意：`LeoCurrentTaskInfo` 的字段是从 smali 推断的（真字段未读出），
     * 若服务端返回的结构与推断不符，kotlinx.serialization 会因为
     * `ignoreUnknownKeys = true` 而不报错，只是字段落空 —— UI 表现为
     * 「拉到了但内容空」，这是可接受的中间态。
     */
    suspend fun fetchTasks(): LeoCurrentTaskInfo? = runCatching {
        ServiceLocator.exerciseLegacy.getCurrentUserTasks()
    }.getOrNull()

    /**
     * 拉当前用户经验值。失败返回 null。
     *
     * 这个接口带 `@NotNullAndValid` —— 服务端若返回空体会走 converter 报错，
     * 被 [runCatching] 兜住。
     */
    suspend fun fetchExp(): LeoUserCurrentExpData? = runCatching {
        ServiceLocator.exerciseLegacy.getCurrentUserExp()
    }.getOrNull()

    /**
     * 按类型拉英语练习章节。
     *
     * @param type     练习类型，原版是 Int 枚举（听写 / 书写等），具体取值待真机确认
     * @param grade    年级 ID
     * @param semester 学期（1 上 / 2 下）
     * @param book     教材版本
     *
     * 四个参数全部必填 —— 从 smali 的 `getEnglishExercisesSections(IIII)` 签名读出，
     * 都是原始 int 不是 nullable。若服务端对某个值有范围要求，会返回 4xx，
     * 被 [runCatching] 兜住后返回 null。
     */
    suspend fun fetchEnglishSections(
        type: Int,
        grade: Int,
        semester: Int,
        book: Int,
    ): ExerciseEnglishSectionVO? = runCatching {
        ServiceLocator.englishExercise.getEnglishExercisesSections(
            type = type,
            grade = grade,
            semester = semester,
            book = book,
        )
    }.getOrNull()

    /**
     * 按类型拉数学练习知识点树（出题链路第一步）。
     *
     * GET `/leo-math/android/exams/exercises/type/{type}`。
     *
     * 这是「10 以内加减法 + 题目数量可选」的入口：`type` 传
     * [ExerciseType.exerciseType]（口算练习 = 0），拿到 `ExerciseScopeData`
     * 后从 `sections[].keypoints[]` 里挑一个知识点，再用它的 `id` 作为
     * `keypointId` 进答题页（原版 `QuickExerciseActivity`）。
     *
     * 四个参数都是原始 int（原版签名 `(IIII...)` 无 nullable）：
     *  - type     出题类型，见 [ExerciseType]
     *  - grade    年级 ID
     *  - semester 学期（1 上 / 2 下）
     *  - book     教材版本
     *
     * 失败返回 null，UI 显示空态。
     */
    suspend fun fetchMathScope(
        type: ExerciseType,
        grade: Int,
        semester: Int,
        book: Int,
    ): ExerciseScopeData? = runCatching {
        ServiceLocator.math.getExercisesKeyPoints(
            type = type.exerciseType,
            grade = grade,
            semester = semester,
            book = book,
        )
    }.getOrNull()

    /**
     * 出题：按知识点生成一整套练习（出题链路第二步）。
     *
     * POST `/leo-math/android/exams`，FormUrlEncoded。
     *
     * @param keypointId 知识点 ID，来自 [ExerciseScopeKeypoint.id]
     * @param limit      题目数量，取自 [ExerciseType.chooseNumArray]
     *
     * 失败返回 null，UI 显示错误。
     */
    suspend fun fetchExam(
        keypointId: Int,
        limit: Int,
    ): ExamData? = runCatching {
        ServiceLocator.oral.getExamInfo(
            keypointId = keypointId.toString(),
            limit = limit.toString(),
        )
    }.getOrNull()

    /**
     * 提交练习结果（出题链路第四步）。
     *
     * PUT `/leo-math/android/exams/v2/{examId}`，body 带 `@NeedEncode`。
     *
     * 提交前调用方须把 [ExamData.questions] 里每题填好
     * `userAnswer` / `status` / `costTime`（**下限 300ms**），
     * 以及整卷的 `correctCnt` / `costTime`。
     *
     * 失败返回 null。
     */
    suspend fun uploadExam(body: ExamData): ExamData? = runCatching {
        val examId = body.idString ?: return@runCatching null
        ServiceLocator.oral.uploadExamResult(examId = examId, body = body)
    }.getOrNull()
}
