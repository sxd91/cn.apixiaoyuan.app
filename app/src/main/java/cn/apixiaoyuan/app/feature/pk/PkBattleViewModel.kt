package cn.apixiaoyuan.app.feature.pk

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.apixiaoyuan.app.core.pk.PkBattleEngine
import cn.apixiaoyuan.app.core.pk.PkMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * PK 秒结算/循环/并发 弹窗的状态机。
 *
 * 与 [cn.apixiaoyuan.app.feature.oldsimian.ScorePumpViewModel] 同构：
 * 起一个可取消的 [Job] 承载 [PkBattleEngine.runBattle]，把进度转成 UI 文案。
 * 取消语义：真取消重抛 [CancellationException] 让结构化并发正常传播。
 */
class PkBattleViewModel : ViewModel() {

    /** 是否正在刷。 */
    var running by mutableStateOf(false)
        private set

    /** 进度文案（多玩法合并后的单行展示）。 */
    var progress by mutableStateOf("")
        private set

    /** 结果文案（成功/失败/已停止）。 */
    var message by mutableStateOf<String?>(null)
        private set

    /** 承载战斗的协程。 */
    private var job: Job? = null

    /**
     * 开始 PK 战斗。
     *
     * @param rounds    每个玩法刷的轮数
     * @param modes     勾选的玩法集合
     * @param pointId   知识点 ID
     * @param maxRetry  失败重试上限
     */
    fun start(
        rounds: Int,
        modes: Set<PkMode>,
        pointId: Int,
        maxRetry: Int = PkBattleEngine.DEFAULT_MAX_RETRY,
    ) {
        if (running) return
        if (rounds <= 0) {
            message = "轮数必须 ≥ 1"
            return
        }
        if (modes.isEmpty()) {
            message = "至少勾选一个玩法"
            return
        }

        running = true
        message = null
        progress = "开始：${modes.joinToString("、") { it.displayName }} × $rounds 轮"

        job = viewModelScope.launch {
            try {
                val result = PkBattleEngine.runBattle(
                    rounds = rounds,
                    modes = modes,
                    pointId = pointId,
                    maxRetry = maxRetry,
                    onProgress = { mode, done, total, ev ->
                        progress = "[${mode.displayName}] $ev（$done/$total）"
                    },
                )
                running = false
                progress = ""
                val summary = result.entries.joinToString("；") { (m, c) ->
                    "${m.displayName} 完成 $c/$rounds 轮"
                }
                message = "结束：$summary"
            } catch (c: CancellationException) {
                running = false
                progress = ""
                message = "已停止。"
                throw c
            } catch (t: Throwable) {
                running = false
                progress = ""
                message = "异常：${t.message ?: t}"
            }
        }
    }

    /** 请求停止（取消是异步生效的）。 */
    fun stop() {
        job?.cancel()
    }
}