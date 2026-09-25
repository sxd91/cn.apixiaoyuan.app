package cn.apixiaoyuan.app.feature.exercise

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.apixiaoyuan.app.core.exercise.ExerciseRepository
import cn.apixiaoyuan.app.core.model.ExamData
import cn.apixiaoyuan.app.core.model.ExamQuestion
import cn.apixiaoyuan.app.core.oldsimian.OldSimianPrefs
import cn.apixiaoyuan.app.core.oldsimian.OralStrokes
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 答题页状态机。
 *
 * 链路：
 * ```
 * load(keypointId, limit)
 *   → getExamInfo                    出题
 *   → answer(question, mark)         逐题记录（写进 answers 表 + 就地更新题目）
 *   → submit()                       组装 ExamData → uploadExamResult
 * ```
 *
 * **提交体的关键约束**（逐条来自原版与 cn.nizou.sxd 的实战结论）：
 *  - 每题 `costTime` **下限 5ms**（真机实测边界，见 `ExamQuestion.MIN_COST_TIME_MS`）
 *  - `status` 必须与 `userAnswer` 一致：答对 = 1，答错 = -1
 *  - 整卷 `correctCnt` = 答对题数；`costTime` = 各题之和
 *
 * 与 cn.nizou.sxd `PracticeHook.buildExamResult` 的口径一致，区别只在
 * 本工程是「用户手动作答」，那边是「自动填正确答案」。
 */
class ExamViewModel : ViewModel() {

    /** 是否正在出题。 */
    var loading by mutableStateOf(false)
        private set

    /** 是否正在提交。 */
    var submitting by mutableStateOf(false)
        private set

    /** 是否已提交成功。 */
    var submitted by mutableStateOf(false)
        private set

    /** 出题错误。null 表示无错误。 */
    var error by mutableStateOf<String?>(null)
        private set

    /** 当前练习。null 表示未拉到。 */
    var exam by mutableStateOf<ExamData?>(null)
        private set

    /**
     * 作答记录：题目 id → 作答标记（[RIGHT_MARK] / [WRONG_MARK]）。
     *
     * 独立于 [exam] 存一份，是为了让 UI 的「已作答」状态可观察 ——
     * `ExamData` 是 immutable data class，就地改字段不触发重组。
     */
    var answers by mutableStateOf<Map<Int, String>>(emptyMap())
        private set

    /** 进页面时的时间戳，用于算每题 costTime。 */
    private var startedAt: Long = 0L

    /** 上一次作答的时间戳，用于算「本题」耗时。 */
    private var lastAnswerAt: Long = 0L

    /** 每题的作答耗时（毫秒），按题目 id 存。 */
    private val costTimes = mutableMapOf<Int, Long>()

    /** 记住出题参数，供 [retry] 重新出题。 */
    private var lastKeypointId: Int = 0
    private var lastLimit: Int = 0

    /** 出题。 */
    fun load(keypointId: Int, limit: Int) {
        if (loading) return
        lastKeypointId = keypointId
        lastLimit = limit
        loading = true
        error = null
        submitted = false
        answers = emptyMap()
        costTimes.clear()
        startedAt = System.currentTimeMillis()
        lastAnswerAt = startedAt

        viewModelScope.launch {
            val data = ExerciseRepository.fetchExam(
                keypointId = keypointId,
                limit = limit,
            )
            exam = data
            loading = false
            if (data == null) {
                error = "出题失败，请重试"
            } else if (data.questions.isNullOrEmpty()) {
                error = "该知识点暂无题目"
            }
        }
    }

    /**
     * 记录一道题的作答。
     *
     * 同时记录本题耗时（当前时间 - 上次作答时间），提交时用。
     *
     * @param question 题目
     * @param mark     [RIGHT_MARK] 或 [WRONG_MARK]
     */
    fun answer(question: ExamQuestion, mark: String) {
        answers = answers + (question.id to mark)

        val now = System.currentTimeMillis()
        // 首次作答从进页面算起，之后从上次作答算起。
        val base = if (costTimes.isEmpty()) startedAt else lastAnswerAt
        costTimes[question.id] = (now - base).coerceAtLeast(0L)
        lastAnswerAt = now
    }

    /**
     * 提交练习。
     *
     * 组装提交体（填 userAnswer / status / costTime，以及整卷 correctCnt / costTime），
     * 走 `uploadExamResult`。body 带 `@NeedEncode`，编码由拦截器处理。
     */
    fun submit() {
        val current = exam ?: return
        if (submitting || submitted) return

        submitting = true
        error = null

        viewModelScope.launch {
            val body = buildSubmitBody(current)
            val result = ExerciseRepository.uploadExam(body)
            submitting = false
            if (result != null) {
                submitted = true
                exam = result
            } else {
                error = "提交失败，请重试"
            }
        }
    }

    /** 重试：清错误后按上次参数重新出题。 */
    fun retry() {
        error = null
        if (lastKeypointId > 0 && lastLimit > 0) {
            load(lastKeypointId, lastLimit)
        }
    }

    /**
     * 组装提交体。
     *
     * 逐题：填 `userAnswer`、`status`（1 / -1）、`costTime`
     * （**下限 5ms**，不足补到 5 —— 真机实测边界）。
     * 整卷：`correctCnt` = 答对题数，`costTime` = 各题之和。
     *
     * **「老挂戏老叟」开关接入点**（本项目是内置客户端，这里改的是自己
     * 组装的请求体，不是 hook 宿主内存对象）：
     *  - [OldSimianPrefs.autoCorrect]：每题 `userAnswer` 取服务端下发的
     *    正确答案（`ExamQuestion.rightAnswer`，即 `answers.first()`），
     *    `status = STATUS_RIGHT`，整卷自然全对；
     *  - [OldSimianPrefs.customAnswerEnabled] + `customAnswerText`：
     *    `userAnswer` 固定为自定义答案，`status` 按它是否等于正确答案判定；
     *  - [OldSimianPrefs.strokeEnabled]：每题 `script` 由 [OralStrokes] 按
     *    该题实际提交的 `userAnswer` 生成（「按题目数量提交等量画笔」）；
     *  - [OldSimianPrefs.customCostEnabled]：每题 `costTime` 固定为
     *    [OldSimianPrefs.customCostMs]（存储层已保证 ≥ 5ms）。
     */
    private fun buildSubmitBody(current: ExamData): ExamData {
        val autoCorrect = OldSimianPrefs.autoCorrect
        val customAnswer = OldSimianPrefs.customAnswerText.trim()
        val useCustomAnswer = OldSimianPrefs.customAnswerEnabled && customAnswer.isNotEmpty()
        val useStrokes = OldSimianPrefs.strokeEnabled
        var totalCost = 0L
        var correctCount = 0

        val newQuestions = current.questions.orEmpty().map { q ->
            val mark = answers[q.id]
            // 是否答对（作答标记为「对」）。
            val answeredRight = mark == RIGHT_MARK
            // 提交的作答文本，优先级：自动全对 > 自定义答案 > 用户答对时回填正确答案。
            //
            // 注意：本项目 UI 是「对 / 错」按钮，拿不到用户真实手写文本，因此
            // **绝不能把内部标记 `"right"` / `"wrong"` 当 `userAnswer` 提交**
            // （这是 B1 遗留缺陷）。用户答对时用服务端下发的正确答案回填 ——
            // 答对即意味着他写的就是它，这不是伪造，只是补齐原版必然携带的字段。
            val userAnswer = when {
                autoCorrect || useCustomAnswer -> OldSimianPrefs.answerFor(q.rightAnswer, null)
                answeredRight -> q.rightAnswer ?: ""
                else -> ""
            }
            // `status` 与 `userAnswer` 必须自洽：
            //  - 自动全对   → 恒判对；
            //  - 自定义答案 → 看它是否恰好等于正确答案（不伪造判分）；
            //  - 否则       → 按用户实际作答。
            val correct = when {
                autoCorrect -> true
                useCustomAnswer -> userAnswer == q.rightAnswer
                else -> answeredRight
            }
            val status = if (correct) ExamQuestion.STATUS_RIGHT else ExamQuestion.STATUS_WRONG

            // costTime 下限 5ms；未作答的题给一个随机值（下限..下限+150），
            // 避免整卷耗时完全相同被风控识别（cn.nizou.sxd 同款做法）。
            // 「未作答」= 用户没点过，且没有开启任何代答（自动全对 / 自定义答案）。
            val raw = costTimes[q.id] ?: 0L
            val answered = mark != null
            val base = if (raw < ExamQuestion.MIN_COST_TIME_MS) {
                if (!answered && !autoCorrect && !useCustomAnswer) Random.nextLong(
                    ExamQuestion.MIN_COST_TIME_MS,
                    ExamQuestion.MIN_COST_TIME_MS + 150,
                ) else ExamQuestion.MIN_COST_TIME_MS
            } else raw
            // 自定义结算时间：开启后每题耗时统一为配置值，否则用实测值。
            val cost = OldSimianPrefs.costTimeFor(base)

            // 提交画笔：按本题实际提交的作答文本生成笔迹 JSON。
            // 「按题目数量提交等量画笔」—— 有 N 道题就生成 N 条 script。
            // 生成失败（答案为空 / 全是字形表里没有的字符）时保留原 script，
            // 不写空数组（空笔迹比无笔迹更异常）。
            val script = if (useStrokes) {
                OralStrokes.scriptJson(userAnswer) ?: q.script
            } else {
                q.script
            }

            totalCost += cost
            if (status == ExamQuestion.STATUS_RIGHT) correctCount++

            q.copy(
                userAnswer = userAnswer,
                status = status,
                costTime = cost,
                script = script,
            )
        }

        return current.copy(
            questions = newQuestions,
            correctCnt = correctCount,
            costTime = totalCost,
        )
    }
}

/** 作答标记：答对。与 [ExamScreen] 的 `RIGHT_MARK` 同值。 */
private const val RIGHT_MARK = "right"

/** 作答标记：答错。与 [ExamScreen] 的 `WRONG_MARK` 同值。 */
private const val WRONG_MARK = "wrong"
