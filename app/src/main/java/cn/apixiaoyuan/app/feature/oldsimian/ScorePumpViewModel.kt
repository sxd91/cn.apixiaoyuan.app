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

    /** 进度文案。空串 = 无进度可显示。 */
    var progress by mutableStateOf("")
        private set

    /** 结果文案（成功 / 失败 / 已停止）。null = 无。 */
    var message by mutableStateOf<String?>(null)
        private set

    /** 承载 [ScorePump.pumpDelta] 的协程。null = 未在刷分。 */
    private var job: Job? = null

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
            }
        }
    }

    /**
     * 开始增量上报。
     *
     * ## 增量模式（2026-09-25 重写）
     *
     * 用户输入的是**增量**（要加多少分），不再是目标分数 ——
     * 协议已确证 `postSavedExp` 按增量记账（`obtainExp`），直接上报即可，
     * 无需取卷/整卷上传/循环逼近。
     *
     * @param delta 要增加的分数（正数）。超过单批上限会拆条分批上报。
     */
    fun start(delta: Int) {
        if (running) return
        if (!OldSimianPrefs.customScoreEnabled) {
            message = "请先在「老挂戏老叟」页打开「自定义分数（刷分）」开关"
            return
        }
        if (delta <= 0) {
            message = "增量必须大于 0"
            return
        }

        running = true
        message = null
        progress = "开始：本次 +$delta"

        job = viewModelScope.launch {
            try {
                val result = ScorePump.pumpDelta(
                    delta = delta,
                    onProgress = { reported, total ->
                        progress = "已上报 $reported / $total"
                    },
                )
                running = false
                progress = ""
                result.onSuccess { reported ->
                    message = "成功：本次 +$reported（点「刷新」读最新分数）"
                    refreshScore()
                }.onFailure { t ->
                    message = "失败：${t.message ?: t}"
                }
            } catch (c: CancellationException) {
                running = false
                progress = ""
                message = "已停止。"
                throw c
            }
        }
    }

    /** 请求停止。取消是异步生效的，UI 上的「已停止」以 [message] 为准。 */
    fun stop() {
        job?.cancel()
    }
}
