package cn.apixiaoyuan.app.feature.oldsimian

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.apixiaoyuan.app.core.exercise.ExerciseRepository
import cn.apixiaoyuan.app.core.oldsimian.OldSimianPrefs
import cn.apixiaoyuan.app.core.oldsimian.ScorePump
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 「自定义分数（刷分）」页的状态机。
 *
 * ## 职责边界
 *
 * 本类**只做三件事**，算法全在 [ScorePump]：
 *  1. 读一次当前分数（[refreshScore]），供 UI 显示「当前 X → 目标 Y」；
 *  2. 起一个可取消的 [Job] 承载 [ScorePump.pumpToTarget]，把进度转成 UI 文案；
 *  3. 把扫描到的有效知识点写回 [OldSimianPrefs.customScoreKeypoint]。
 *
 * ## 为什么用 Job 而不是 `@Volatile stopped` 标志
 *
 * 参考项目 cn.nizou.sxd 是 LSPosed 模块，`ScorePump` 跑在独立 `thread {}` 里、
 * 无法从外部中断 `CountDownLatch.await()`，只能靠一个 `@Volatile stopped` 标志
 * 在每局之间检查 —— **取消粒度是「一局」，最坏要等一整局跑完**。
 *
 * 本项目是内置客户端、[ScorePump] 本身就是 `suspend`，所以直接用协程取消：
 * `job.cancel()` 会让挂起中的网络请求立即抛出 [CancellationException]，
 * 取消粒度是「一次挂起调用」，点停止即刻生效。
 *
 * ## 取消语义（**本文件最容易写错的地方**）
 *
 * [ScorePump.pumpToTarget] 内部用 `withTimeout` 给每次 IO 加了超时。
 * `TimeoutCancellationException` 是 [CancellationException] 的子类，所以
 * **不能**在这里用「捕获到 [CancellationException] 就当作已停止」的写法 ——
 * 那会把「单次操作超时」误判成「用户点了停止」。好在 [ScorePump] 内部已经用
 * `currentCoroutineContext().ensureActive()` 把这两者区分开了：单次超时被降级成
 * `null` / `false` 继续跑，真取消才会把 [CancellationException] 抛到这里。
 *
 * 捕获后仍然 `throw c` 是有意为之：协程已被取消，重抛让取消正常传播，
 * 不吞异常、不影响 [viewModelScope] 的结构化并发。
 *
 * ## 生命周期
 *
 * [Job] 挂在 [viewModelScope] 下，页面销毁（ViewModel `onCleared`）时
 * 由 `viewModelScope` 自动取消 —— 不需要手写 `onCleared`，也不会出现
 * 「退出页面后后台还在偷偷刷分」。
 */
class ScorePumpViewModel : ViewModel() {

    /** 是否正在读当前分数。 */
    var loadingScore by mutableStateOf(false)
        private set

    /** 当前分数（`curWeekScore`）。null = 还没读到。 */
    var currentScore by mutableStateOf<Int?>(null)
        private set

    /** 读分数失败的原因。null = 无错误。 */
    var scoreError by mutableStateOf<String?>(null)
        private set

    /** 是否正在刷分。 */
    var running by mutableStateOf(false)
        private set

    /** 进度文案（扫描中 / 第 N 局）。空串 = 无进度可显示。 */
    var progress by mutableStateOf("")
        private set

    /** 结果文案（成功 / 失败 / 已停止）。null = 无。 */
    var message by mutableStateOf<String?>(null)
        private set

    /** 承载 [ScorePump.pumpToTarget] 的协程。null = 未在刷分。 */
    private var job: Job? = null

    /**
     * 最近一次读到的分数。
     *
     * 单独存一份（而不是复用 [currentScore]）是为了在**取消路径**上也能给出
     * 一个数字：协程被取消后不能再调 `suspend` 函数去刷新分数，只能报出
     * 取消前最后观察到的值。
     */
    private var lastScore: Int? = null

    init {
        refreshScore()
    }

    /**
     * 刷新当前分数。
     *
     * 失败时把原因写进 [scoreError]（UI 展示），**不清空** [currentScore] ——
     * 已经读到过分数的话，一次刷新失败不该让界面退回「—」。
     */
    fun refreshScore() {
        if (loadingScore) return
        loadingScore = true
        scoreError = null
        viewModelScope.launch {
            val exp = runCatching { ExerciseRepository.fetchExp() }.getOrNull()
            loadingScore = false
            val score = exp?.curWeekScore
            if (score == null) {
                scoreError = "读取当前分数失败（登录态失效或网络异常）"
            } else {
                currentScore = score
                lastScore = score
            }
        }
    }

    /**
     * 开始刷分。
     *
     * @param target     目标分数，必须**严格大于** [currentScore]
     * @param keypointId 知识点 ID；空串 = 自动扫描 1..[OldSimianPrefs.SCORE_KEYPOINT_MAX]
     * @param limit      每局题目数（调用方已 `coerceIn`）
     * @param intervalMs 每局间隔（毫秒，调用方已 `coerceIn`）
     *
     * 参数在开始时**统一写盘**（而不是每敲一个字就写一次）：刷分参数是
     * 「开始那一刻的快照」，中途改输入框不该影响正在跑的这一轮。
     */
    fun start(
        target: Int,
        keypointId: String,
        limit: Int,
        intervalMs: Long,
    ) {
        if (running) return
        if (!OldSimianPrefs.customScoreEnabled) {
            message = "请先在「老挂戏老叟」页打开「自定义分数（刷分）」开关"
            return
        }
        val cur = currentScore
        if (cur == null) {
            message = "尚未读到当前分数，请先点「刷新」"
            return
        }
        if (target <= cur) {
            message = "目标分数必须大于当前分数（$cur）"
            return
        }

        val kp = keypointId.trim()
        OldSimianPrefs.customScoreKeypoint = kp
        OldSimianPrefs.customScoreLimit = limit
        OldSimianPrefs.customScoreIntervalMs = intervalMs.toInt()
        OldSimianPrefs.persist()

        running = true
        message = null
        progress = "开始：当前 $cur → 目标 $target" +
            if (kp.isEmpty()) "（知识点留空，取题失败时自动扫描）" else "（知识点 $kp）"

        job = viewModelScope.launch {
            try {
                val result = ScorePump.pumpToTarget(
                    keypointId = kp,
                    limit = limit,
                    intervalMs = intervalMs,
                    target = target,
                    onProgress = { current, rounds ->
                        // current < 0 是 ScorePump 约定的「正在扫描知识点」哨兵值。
                        if (current >= 0) lastScore = current
                        progress = if (current < 0) {
                            "扫描知识点中（1~${OldSimianPrefs.SCORE_KEYPOINT_MAX} 自动尝试，可随时停止）…"
                        } else {
                            "第 $rounds 局 · 当前 $current / 目标 $target"
                        }
                    },
                    onKeypointFound = { found ->
                        OldSimianPrefs.customScoreKeypoint = found
                        OldSimianPrefs.persist()
                    },
                )
                running = false
                progress = ""
                result.onSuccess { finalScore ->
                    currentScore = finalScore
                    lastScore = finalScore
                    message = "成功：已刷到 $finalScore 分"
                }.onFailure { t ->
                    message = "失败：${t.message ?: t}"
                    refreshScore()
                }
            } catch (c: CancellationException) {
                // 走到这里 = 用户点了「停止」（ScorePump 已把单次超时排除掉）。
                running = false
                progress = ""
                message = "已停止。停止前最后读数 ${lastScore ?: "未知"}，可点「刷新」确认。"
                // 协程已取消，不能在这里调 suspend 刷新分数 —— 交给用户手动刷新。
                throw c
            }
        }
    }

    /** 请求停止。取消是异步生效的，UI 上的「已停止」以 [message] 为准。 */
    fun stop() {
        job?.cancel()
    }
}
