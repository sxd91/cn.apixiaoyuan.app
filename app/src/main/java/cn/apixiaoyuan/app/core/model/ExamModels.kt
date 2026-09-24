package cn.apixiaoyuan.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 一次练习（`ExamVO` / `AbsExamVO`）。
 *
 * 字段逐行来自 `smali_classes3/com/fenbi/android/leo/exercise/data/AbsExamVO.smali`
 * 与 `ExamVO.smali`。原版是泛型 `AbsExamVO<QuestionVO>`，这里直接拍平成
 * 非泛型的 [ExamData]，`questions` 固定为 [ExamQuestion]。
 *
 * **只保留非 transient 字段** —— 原版里 `keypointItemData` /
 * `mathThoughtItemData` 是 `transient`，Gson 不参与序列化。
 *
 * `getExp()` 是原版的计算属性（按 status==1 的题数 ×2），不是字段，
 * 这里用同名 val 复刻计算逻辑。
 */
@Serializable
data class ExamData(
    /** 考试 ID 字符串。出题响应里必有，提交时用作 Path。 */
    @SerialName("idString") val idString: String? = null,
    @SerialName("trialExamId") val trialExamId: String? = null,
    /** 知识点 ID。原版构造时默认 -1。 */
    @SerialName("keypointId") val keypointId: Int = -1,
    @SerialName("keypoint") val keypoint: String? = null,
    @SerialName("ruleType") val ruleType: Int = 0,
    /** 题目总数。 */
    @SerialName("questionCnt") val questionCnt: Int = 0,
    /** 答对题数。 */
    @SerialName("correctCnt") val correctCnt: Int = 0,
    /** 总耗时（毫秒）。 */
    @SerialName("costTime") val costTime: Long = 0L,
    @SerialName("questions") val questions: List<ExamQuestion>? = null,
    @SerialName("updatedTime") val updatedTime: Long = 0L,
    @SerialName("errorBookFlag") val errorBookFlag: Int = 0,
    @SerialName("rankFlag") val rankFlag: Int = 0,
    @SerialName("practiceCntFlag") val practiceCntFlag: Int = 0,
    @SerialName("addErrorCount") val addErrorCount: String? = null,
    @SerialName("source") val source: Int = 0,
    @SerialName("speedRank") val speedRank: Int = 0,
    @SerialName("orionKeypointId") val orionKeypointId: Int = 0,
    @SerialName("correctRate") val correctRate: Int = 0,
    @SerialName("correctRateRank") val correctRateRank: Int = 0,
) {
    /**
     * 本次练习可得的经验值。
     *
     * 复刻原版 `ExamVO.getExp()`：`questions.count { it.status == STATUS_RIGHT } * 2`。
     */
    val exp: Int
        get() = (questions?.count { it.status == ExamQuestion.STATUS_RIGHT } ?: 0) * 2

    /** 是否全部答对。复刻原版 `isAllRight`。 */
    val isAllRight: Boolean
        get() = questions?.isNotEmpty() == true &&
            questions.all { it.status == ExamQuestion.STATUS_RIGHT }
}

/**
 * 单道题目（`QuestionVO`）。
 *
 * 字段逐行来自 `smali_classes3/com/fenbi/android/leo/exercise/data/QuestionVO.smali`。
 * 原版带 `@JsonClass(generateAdapter = true)`（Moshi codegen），本工程用
 * kotlinx.serialization，语义等价。
 *
 * 状态常量逐值对齐原版：
 * ```
 * STATUS_NOT_DONE = 0
 * STATUS_RIGHT    = 1
 * STATUS_WRONG    = -1
 * ```
 */
@Serializable
data class ExamQuestion(
    @SerialName("id") val id: Int = 0,
    /** 题干，如 `78+13=\square`。 */
    @SerialName("content") val content: String? = null,
    /** 正确答案列表。原版取 `answers.first()` 作为标准答案。 */
    @SerialName("answers") val answers: List<String>? = null,
    /** 用户作答。 */
    @SerialName("userAnswer") val userAnswer: String? = null,
    /** 笔迹数据（JSON 字符串）。 */
    @SerialName("script") val script: String? = null,
    /** 作答状态：0 未做 / 1 对 / -1 错。 */
    @SerialName("status") val status: Int = STATUS_NOT_DONE,
    @SerialName("errorState") val errorState: Int = 0,
    @SerialName("wrongScript") val wrongScript: String? = null,
    /** 本题耗时（毫秒）。**服务端校验下限 300ms**（见 cn.nizou.sxd PracticeHook）。 */
    @SerialName("costTime") val costTime: Long = 0L,
    @SerialName("examId") val examId: Int = 0,
    @SerialName("keypointId") val keypointId: Int? = null,
    @SerialName("ruleType") val ruleType: Int = 0,
    @SerialName("lessonId") val lessonId: Long? = null,
    @SerialName("errorId") val errorId: Long? = null,
) {
    /** 标准答案：原版取 `answers.first()`。 */
    val rightAnswer: String?
        get() = answers?.firstOrNull()

    companion object {
        /** 未作答。 */
        const val STATUS_NOT_DONE = 0

        /** 答对。 */
        const val STATUS_RIGHT = 1

        /** 答错。 */
        const val STATUS_WRONG = -1

        /** 提交时每题耗时的服务端下限（毫秒）。 */
        const val MIN_COST_TIME_MS = 300L
    }
}
