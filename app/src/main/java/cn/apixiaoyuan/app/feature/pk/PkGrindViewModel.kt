package cn.apixiaoyuan.app.feature.pk

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.apixiaoyuan.app.core.account.AccountRepository
import cn.apixiaoyuan.app.core.account.SubAccountItem
import cn.apixiaoyuan.app.core.pk.PkBattleEngine
import cn.apixiaoyuan.app.core.pk.PkBattleRepository
import cn.apixiaoyuan.app.core.pk.PkMode
import cn.apixiaoyuan.app.core.pk.PkPointItem
import cn.apixiaoyuan.app.core.pk.PkStrokeMode
import cn.apixiaoyuan.app.core.session.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PkGrindViewModel : ViewModel() {

    var points by mutableStateOf<List<PkPointItem>?>(null)
        private set

    var loadError by mutableStateOf<String?>(null)
        private set

    var loading by mutableStateOf(false)
        private set

    var totalWinCount by mutableStateOf<Int?>(null)
        private set

    var weekWinCount by mutableStateOf<Int?>(null)
        private set

    var currentUserId by mutableStateOf<Long?>(null)
        private set

    var subAccounts by mutableStateOf<List<SubAccountItem>?>(null)
        private set

    var running by mutableStateOf(false)
        private set

    var progress by mutableStateOf("")
        private set

    var message by mutableStateOf<String?>(null)
        private set

    private var job: Job? = null

    init {
        refreshAll()
    }

    fun refreshAll() {
        if (loading) return
        loading = true
        loadError = null
        currentUserId = SessionStore.yfdU
        viewModelScope.launch {
            try {
                val grade = SessionStore.grade() ?: DEFAULT_GRADE
                val home = PkBattleRepository.fetchMathHome(grade)
                points = home.pointList
                totalWinCount = home.totalWinCount
                weekWinCount = home.weekWinCount
                subAccounts = runCatching { AccountRepository.fetchSubAccounts().getOrThrow() }
                    .getOrNull()
            } catch (t: Throwable) {
                if (t !is CancellationException) {
                    loadError = "拉取对局类型失败：${t.message ?: t}"
                }
            } finally {
                loading = false
            }
        }
    }

    fun switchSubAccount(item: SubAccountItem) {
        if (running) return
        viewModelScope.launch {
            try {
                message = null
                progress = "切换账号中…"
                AccountRepository.switchTo(item)
                progress = ""
                message = "已切换到 ${item.nickname}"
                refreshAll()
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                progress = ""
                message = "切换失败：${t.message ?: t}"
            }
        }
    }

    fun start(
        rounds: Int,
        pointId: Int,
        costTimeMs: Long?,
        submitDelayMs: Long,
        roundIntervalMs: Long,
        strokeMode: PkStrokeMode,
    ) {
        if (running) return
        if (rounds <= 0) {
            message = "对局数必须 ≥ 1"
            return
        }
        if (pointId <= 0) {
            message = "请先选择对局类型"
            return
        }

        running = true
        message = null
        progress = "开始：刷 $rounds 局（知识点 $pointId）"

        job = viewModelScope.launch {
            try {
                val result = PkBattleEngine.runBattle(
                    rounds = rounds,
                    modes = setOf(PkMode.MATH),
                    pointId = pointId,
                    costTimeMs = costTimeMs,
                    submitDelayMs = submitDelayMs,
                    roundIntervalMs = roundIntervalMs,
                    strokeMode = strokeMode,
                    onProgress = { _, done, total, ev ->
                        progress = "[数学] $ev（$done/$total）"
                    },
                )
                running = false
                progress = ""
                val done = result[PkMode.MATH] ?: 0
                message = "结束：完成 $done/$rounds 局"
                refreshScoreOnly()
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

    private fun refreshScoreOnly() {
        viewModelScope.launch {
            runCatching {
                val grade = SessionStore.grade() ?: DEFAULT_GRADE
                PkBattleRepository.fetchMathHome(grade)
            }.onSuccess { home ->
                totalWinCount = home.totalWinCount
                weekWinCount = home.weekWinCount
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    companion object {
        private const val DEFAULT_GRADE = 2
    }
}
