package cn.apixiaoyuan.app.core.pk

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * PK 秒结算/循环/并发 的数据模型。
 *
 * ## 来源（2026-09-26 真机 ground truth 逐字段对齐，非推测）
 *
 * 提交 body 的**权威结构**来自原版 WebView localStorage 的 `exerciseResult` 键
 * （用户实打一轮 PK 后，H5 `saveLocalResult` 落盘的真实提交体）：
 *
 * ```json
 * {
 *   "pkIdStr": "868616052223852608",
 *   "pointId": 1951,
 *   "pointName": "5以内比大小",
 *   "ruleType": -7,           // 整卷 ruleType（int）
 *   "questionCnt": 10,
 *   "correctCnt": 10,
 *   "costTime": 6357,         // 整卷耗时（毫秒）
 *   "questions": [
 *     {
 *       "id": 0,
 *       "examId": 868616052223852500,   // Long，每题相同（本卷 examId）
 *       "content": "2\\circle1",
 *       "answer": ">",
 *       "userAnswer": ">",
 *       "answers": [">"],
 *       "status": 1,
 *       "script": "[[{\"x\":188.6,\"y\":469.5},...]]",  // 笔迹 JSON 字符串
 *       "wrongScript": null,
 *       "ruleType": "COMPARE",          // 每题 ruleType（字符串）
 *       "errorState": 0,
 *       "curTrueAnswer": {
 *         "recognizeResult": ">",
 *         "pathPoints": [[{"x":188.6,"y":469.5},...]],  // 与 script 同源
 *         "answer": 1,
 *         "showReductionFraction": 0
 *       }
 *     }
 *   ]
 * }
 * ```
 *
 * ## ⚠️ 关键修正（2026-09-26，此前反复 403/400 的根因之一）
 *
 * 1. **顶层直接展开 examVO 字段**（pkIdStr/pointId/pointName/ruleType/
 *    questionCnt/correctCnt/costTime/questions），**没有** `examVO` 嵌套、
 *    **没有** `userInfos`、**没有** `updatedTime`。
 * 2. 每题保留**完整字段**（id/examId/content/answer/userAnswer/answers/
 *    status/script/wrongScript/ruleType/errorState），不是只有 userAnswer+status。
 * 3. `curTrueAnswer` 含 `recognizeResult`/`pathPoints`/`answer`/`showReductionFraction`
 *    四个字段，其中 `pathPoints` 与 `script` 内容一致。
 * 4. `examId` 是 Long 且**每题相同**（本卷 examId）。
 */

/** PK 出题响应（旧版明文 `match` 接口）。 */
@Serializable
data class PkMatchResponse(
    @SerialName("pkIdStr") val pkIdStr: String? = null,
    @SerialName("examVO") val examVO: PkExamVO? = null,
)

/** 题目卷（出题响应内层）。 */
@Serializable
data class PkExamVO(
    @SerialName("pkIdStr") val pkIdStr: String? = null,
    @SerialName("pointId") val pointId: Int = 0,
    @SerialName("pointName") val pointName: String? = null,
    @SerialName("ruleType") val ruleType: Int = 0,
    @SerialName("questionCnt") val questionCnt: Int = 0,
    @SerialName("questions") val questions: List<PkQuestion>? = null,
)

/** 单道 PK 题目（出题响应内的完整结构，提交时深拷贝后补作答字段）。 */
@Serializable
data class PkQuestion(
    @SerialName("id") val id: Int = 0,
    @SerialName("examId") val examId: Long = 0L,
    @SerialName("content") val content: String? = null,
    @SerialName("answer") val answer: String? = null,
    @SerialName("answers") val answers: List<String>? = null,
    @SerialName("ruleType") val ruleType: String? = null,
    @SerialName("errorState") val errorState: Int = 0,
) {
    /** 标准答案：优先 `answer`，否则 `answers.first()`。 */
    val rightAnswer: String?
        get() = answer ?: answers?.firstOrNull()
}

/**
 * PK 提交 body（整卷）。
 *
 * 顶层直接展开 examVO 字段（对齐真机 ground truth），无 examVO 嵌套。
 */
@Serializable
data class PkSubmitBody(
    @SerialName("pkIdStr") val pkIdStr: String,
    @SerialName("pointId") val pointId: Int = 0,
    @SerialName("pointName") val pointName: String? = null,
    @SerialName("ruleType") val ruleType: Int = 0,
    @SerialName("questionCnt") val questionCnt: Int,
    @SerialName("correctCnt") val correctCnt: Int,
    @SerialName("costTime") val costTime: Long,
    @SerialName("questions") val questions: List<PkSubmitQuestion>,
)

/** PK 提交 body 内的单题作答（完整 question 字段 + 作答字段）。 */
@Serializable
data class PkSubmitQuestion(
    @SerialName("id") val id: Int = 0,
    @SerialName("examId") val examId: Long = 0L,
    @SerialName("content") val content: String? = null,
    @SerialName("answer") val answer: String? = null,
    /** 用户作答（判对错的唯一依据）。秒结算时 = 正确答案。 */
    @SerialName("userAnswer") val userAnswer: String,
    @SerialName("answers") val answers: List<String>? = null,
    /** 作答状态：1 = 对，0 = 错。 */
    @SerialName("status") val status: Int,
    /** 笔迹 JSON 字符串（`[[{x,y},...],...]`），与 [curTrueAnswer] 的 pathPoints 同源。 */
    @SerialName("script") val script: String,
    @SerialName("wrongScript") val wrongScript: String? = null,
    @SerialName("ruleType") val ruleType: String? = null,
    @SerialName("errorState") val errorState: Int = 0,
    @SerialName("curTrueAnswer") val curTrueAnswer: PkCurTrueAnswer,
) {
    companion object {
        /** 答对。 */
        const val STATUS_RIGHT = 1

        /** 答错。 */
        const val STATUS_WRONG = 0
    }
}

/**
 * `curTrueAnswer`：完整结构（真机 ground truth 四字段）。
 *
 * - [recognizeResult]：识别结果（答案字符串）
 * - [pathPoints]：笔迹点集（与 script 同源）
 * - [answer]：1=对 0=错（与 status 同值）
 * - [showReductionFraction]：0（真机恒 0）
 */
@Serializable
data class PkCurTrueAnswer(
    @SerialName("recognizeResult") val recognizeResult: String,
    @SerialName("pathPoints") val pathPoints: List<List<PkPoint>>,
    @SerialName("answer") val answer: Int,
    @SerialName("showReductionFraction") val showReductionFraction: Int = 0,
)

/** 单个笔迹点。 */
@Serializable
data class PkPoint(
    @SerialName("x") val x: Float,
    @SerialName("y") val y: Float,
)

/**
 * PK 数学首页（对局类型列表）响应。
 *
 * `GET /leo-game-pk/android/math/pk/home?grade=N` 的真实响应（2026-09-26 实测）：
 * ```json
 * {
 *   "baseUserInfoVO": {...},
 *   "pointList": [{"pointId":1956,"pointName":"1-6表内乘法","pointGrade":2,"winCount":12}],
 *   "totalWinCount":22,
 *   "weekWinCount":22,
 *   ...
 * }
 * ```
 *
 * [pointList] 就是「对局类型」—— 每个元素是一个知识点（比大小/乘法/除法…），
 * 出题时用 [PkPointItem.pointId]。分数实时显示用 [totalWinCount] / [weekWinCount]。
 */
@Serializable
data class PkMathHome(
    @SerialName("pointList") val pointList: List<PkPointItem> = emptyList(),
    @SerialName("totalWinCount") val totalWinCount: Int = 0,
    @SerialName("weekWinCount") val weekWinCount: Int = 0,
)

/** 单个对局类型（知识点）。 */
@Serializable
data class PkPointItem(
    @SerialName("pointId") val pointId: Int,
    @SerialName("pointName") val pointName: String? = null,
    @SerialName("pointGrade") val pointGrade: Int = 0,
    @SerialName("winCount") val winCount: Int = 0,
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

/**
 * PK 提交画笔算法。
 *
 * 2026-09-26 用户拍板：服务端拦「画点」（稀疏折线），放「画弧线」（密集点）。
 * 所以 PK 比较题（`>` / `<`）默认用 [ARC]（弧线），[SEVEN_SEGMENT] 保留给
 * 其它符号回退或用户主动选择。
 */
enum class PkStrokeMode(val displayName: String) {
    /** 密集弧线（20+ 点，像真人手写）—— 服务端接受，PK 默认。 */
    ARC("弧线（推荐）"),

    /** 七段码折线（稀疏点）—— 可能被服务端判作弊 403，仅做对照。 */
    SEVEN_SEGMENT("七段码"),
}