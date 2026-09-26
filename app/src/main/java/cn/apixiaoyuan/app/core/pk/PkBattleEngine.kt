package cn.apixiaoyuan.app.core.pk

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * PK 秒结算 + 循环 + 多玩法并发的引擎。
 *
 * ## 语义（用户拍板，2026-09-26）
 *
 * - 「秒结算」：出题 → 解码 → 每题答案填对 → 直接提交（不等真实作答）。
 * - 「循环 PK」：每个玩法连续刷 N 轮（N = 用户设的轮数）。
 * - 「多玩法并发」：勾选 math / multi / final / 语文 几个玩法，就**同时**起
 *   几个协程各自循环 —— **不是同一玩法并发多局**。
 * - 「失败重试」：每轮出题/提交失败按退避策略重试（上限可配）。
 *
 * ## 为什么 Engine 与 ViewModel 分开
 *
 * 与练习线 [ScorePump] 的分层一致：算法（循环/并发/退避）在 Engine，
 * ViewModel 只做「起/停一个 Job + 把进度转文案」。这样 Engine 可单测、
 * 不依赖 Compose 状态。
 */
object PkBattleEngine {

    /** 默认失败重试上限。 */
    const val DEFAULT_MAX_RETRY = 3

    /** 默认重试基础退避（毫秒）。 */
    const val DEFAULT_RETRY_BASE_MS = 800L

    /** 默认每局间隔（毫秒），0 = 不等待。 */
    const val DEFAULT_ROUND_INTERVAL_MS = 0L

    /**
     * 跑一轮 PK 战斗（多玩法并发，各自循环）。
     *
     * @param rounds           每个玩法要刷的轮数（≥1）
     * @param modes            要并发的玩法集合（勾选哪些跑哪些）
     * @param pointId          知识点 ID（PK 首页 pointList 提供，默认 1）
     * @param maxRetry         每轮出题/提交失败的重试上限（≥1）
     * @param retryBaseMs      重试基础退避毫秒（每次失败按 2^n 倍退避，加随机抖动）
     * @param roundIntervalMs  每轮之间的固定间隔（毫秒）
     * @param costTimeMs       每局提交的整卷耗时；null = 由题数 × 下限推导
     * @param onProgress       (玩法, 已完成轮数, 总轮数, 事件文本) —— UI 显示进度
     * @return 各玩法最终完成轮数（含失败导致的不足 rounds 的情况）
     */
    suspend fun runBattle(
        rounds: Int,
        modes: Set<PkMode>,
        pointId: Int,
        maxRetry: Int = DEFAULT_MAX_RETRY,
        retryBaseMs: Long = DEFAULT_RETRY_BASE_MS,
        roundIntervalMs: Long = DEFAULT_ROUND_INTERVAL_MS,
        costTimeMs: Long? = null,
        submitDelayMs: Long = 0L,
        strokeMode: PkStrokeMode = PkStrokeMode.ARC,
        onProgress: (PkMode, Int, Int, String) -> Unit = { _, _, _, _ -> },
    ): Map<PkMode, Int> = coroutineScope {
        require(rounds >= 1) { "轮数必须 ≥1" }
        require(modes.isNotEmpty()) { "至少勾选一个玩法" }

        modes.associateWith { mode ->
            async {
                var done = 0
                for (round in 1..rounds) {
                    val ok = runOneRound(
                        mode = mode,
                        pointId = pointId,
                        maxRetry = maxRetry,
                        retryBaseMs = retryBaseMs,
                        costTimeMs = costTimeMs,
                        submitDelayMs = submitDelayMs,
                        strokeMode = strokeMode,
                        onEvent = { ev -> onProgress(mode, done, rounds, ev) },
                    )
                    if (ok) {
                        done++
                        onProgress(mode, done, rounds, "第 $round/$rounds 轮完成")
                    } else {
                        onProgress(mode, done, rounds, "第 $round/$rounds 轮失败（已达重试上限）")
                        // 失败不中断整个玩法循环，继续下一轮 —— 用户要的是「失败重试」
                        // 不是「失败即停」。
                    }
                    if (round < rounds && roundIntervalMs > 0) {
                        delay(roundIntervalMs)
                    }
                }
                done
            }
        }.mapValues { (_, d) -> d.await() }
    }

    /**
     * 跑一局：出题 → 组装全对 body → 提交，带失败重试。
     *
     * @return true = 本局成功（出题+提交都成功）；false = 重试耗尽仍失败。
     */
    private suspend fun runOneRound(
        mode: PkMode,
        pointId: Int,
        maxRetry: Int,
        retryBaseMs: Long,
        costTimeMs: Long?,
        submitDelayMs: Long,
        strokeMode: PkStrokeMode,
        onEvent: (String) -> Unit,
    ): Boolean {
        var attempt = 0
        while (true) {
            try {
                onEvent("出题中…")
                val match = PkBattleRepository.fetchMatch(mode, pointId)
                onEvent("出题成功（${match.examVO?.questions?.size ?: 0} 题），组装提交…")
                if (submitDelayMs > 0) {
                    onEvent("等待 ${submitDelayMs}ms 后提交…")
                    delay(submitDelayMs)
                }
                val body = PkBattleRepository.buildSubmitBody(match, costTimeMs, strokeMode)
                onEvent("提交中…")
                PkBattleRepository.submit(mode, body)
                onEvent("提交成功")
                return true
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                attempt++
                if (attempt >= maxRetry) {
                    onEvent("失败：${t.message ?: t}（已重试 $attempt 次）")
                    return false
                }
                // 指数退避 + 随机抖动，避免多玩法并发时同频重试。
                val backoff = retryBaseMs * (1L shl (attempt - 1))
                val jitter = Random.nextLong(0, backoff.coerceAtLeast(1) + 1)
                onEvent("失败：${t.message ?: t}，第 $attempt 次重试（${backoff + jitter}ms 后）")
                delay(backoff + jitter)
            }
        }
    }
}