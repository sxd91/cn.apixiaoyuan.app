package cn.apixiaoyuan.app.core.pk

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * PK 秒结算/循环/并发 的数据模型。
 *
 * ## 来源（逐字取证，非推测）
 *
 * 出题响应结构来自 `leo-web-oral-pk-pages/useOral-legacy.D8vdCCUa.js`：
 * ```
 * d = e => e.map(q => ({ ...q, answers: q.answers ? [...q.answers] : q.answers }))
 * // 出题后 d(f.examVO.questions)，y.value = f，h() 读 y.value.pkIdStr / y.value.examVO.pointId
 * ```
 * ⇒ 出题响应 = `{ pkIdStr, examVO: { pointId, questions: [{ content, answers, ... }] } }`。
 *
 * 提交 body 结构来自 AOC `WebViewHook.hookDataEncrypt` 逐行 + H5
 * `exercise-legacy.C5DFMay0.js` 的 `postPkExerciseResult`：
 * ```
 * { pkIdStr, questionCnt, costTime, questions: [{ userAnswer, script, curTrueAnswer, status }] }
 * ```
 * 判对错只看 `userAnswer`（H5 内 `expectedResult.includes(recognizeResult)`），
 * 笔迹只用于回放展示，不参与判分。
 */

/** PK 出题响应。 */
@Serializable
data class PkMatchResponse(
    /** 本局 PK 的 ID（提交时必填）。 */
    @SerialName("pkIdStr") val pkIdStr: String? = null,
    /** 题目卷。 */
    @SerialName("examVO") val examVO: PkExamVO? = null,
)

/** 题目卷（出题响应内层）。 */
@Serializable
data class PkExamVO(
    @SerialName("pointId") val pointId: Int = 0,
    @SerialName("questions") val questions: List<PkQuestion>? = null,
)

/** 单道 PK 题目。 */
@Serializable
data class PkQuestion(
    /** 题干，如 `78+13=`。 */
    @SerialName("content") val content: String? = null,
    /** 正确答案列表。原版取 `answers.first()` 作标准答案。 */
    @SerialName("answers") val answers: List<String>? = null,
) {
    /** 标准答案：`answers.first()`。 */
    val rightAnswer: String?
        get() = answers?.firstOrNull()
}

/** PK 提交 body（整卷）。 */
@Serializable
data class PkSubmitBody(
    @SerialName("pkIdStr") val pkIdStr: String,
    @SerialName("questionCnt") val questionCnt: Int,
    @SerialName("costTime") val costTime: Long,
    @SerialName("questions") val questions: List<PkSubmitQuestion>,
)

/** PK 提交 body 内的单题作答。 */
@Serializable
data class PkSubmitQuestion(
    /** 用户作答（判对错的唯一依据）。秒结算时 = 正确答案。 */
    @SerialName("userAnswer") val userAnswer: String,
    /** 笔迹 JSON 字符串（`[[{x,y},...],...]`），只回放不判分。 */
    @SerialName("script") val script: String,
    @SerialName("curTrueAnswer") val curTrueAnswer: PkCurTrueAnswer,
    /** 作答状态：1 = 对。 */
    @SerialName("status") val status: Int,
) {
    companion object {
        /** 答对（与练习 ExamQuestion.STATUS_RIGHT 同值）。 */
        const val STATUS_RIGHT = 1
    }
}

/** `curTrueAnswer`：笔迹回放用的 pathPoints。 */
@Serializable
data class PkCurTrueAnswer(
    /** `[[{x,y},...],...]`，与 [PkSubmitQuestion.script] 同源。 */
    @SerialName("pathPoints") val pathPoints: List<List<PkPoint>>,
)

/** 单个笔迹点。 */
@Serializable
data class PkPoint(
    @SerialName("x") val x: Float,
    @SerialName("y") val y: Float,
)

/**
 * PK 玩法。
 *
 * 并发语义（用户拍板）：「并发 = 多玩法并发」，不是同一玩法并发多局。
 * 勾选几个玩法就同时起几个协程循环刷轮数。
 */
enum class PkMode(val displayName: String) {
    /** 常规口算 PK。 */
    MATH("数学 PK"),

    /** 多人 PK。 */
    MULTI("多人 PK"),

    /** 巅峰/决赛 PK。 */
    FINAL("巅峰 PK"),

    /** 语文（英语）PK —— 题目结构待真机验证，接口已声明。 */
    ENGLISH("语文 PK"),
}