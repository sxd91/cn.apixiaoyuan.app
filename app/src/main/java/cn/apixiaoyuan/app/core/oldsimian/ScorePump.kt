package cn.apixiaoyuan.app.core.oldsimian

import cn.apixiaoyuan.app.core.exercise.ExerciseRepository
import cn.apixiaoyuan.app.core.model.LeoTodayExerciseData
import cn.apixiaoyuan.app.core.model.LeoTodayExerciseListData
import kotlinx.coroutines.CancellationException
import kotlin.random.Random

/**
 * 练习经验增量上报（「自定义分数」· 增量模式）。
 *
 * ## 语义（用户拍板：增量模式 B —— 独立增量上报）
 *
 * 逐行取证后确证，原版 `postSavedExp` 就是**增量上报接口**：
 *
 * ```
 * POST /leo-star/android/exercise/rank/login/attend
 * body: {"todayExercises": [{"finishTime": ms, "obtainExp": 增量, "ruleType": 类型}]}
 * ```
 *
 * 每个 `LeoTodayExerciseData` 是一条**增量记录**（`obtainExp` = 本次获得经验，
 * 非总分），服务端累计到周分数。这正对应「给一个增量值直接上报」——
 * **不需要取卷、不需要整卷上传、不需要循环逼近**。
 *
 * 与整卷上传（`uploadExamResult`）的关系：那条是原版真实练习的提交链路
 * （服务端按卷算分），本条是原版「练习完成后的经验记账」链路 ——
 * 两者并存，增量的表达只在后者。
 *
 * ## 实现依据（smali 逐行）
 *
 *  - body 结构：`LeoTodayExerciseListData.smali`（唯一字段 `todayExercises`）
 *    + `LeoTodayExerciseData.smali`（构造 `(JII)V`：finishTime/obtainExp/ruleType）；
 *  - 「当天」过滤：`LeoExerciseCommonDataStore.k()` 用 `ds/b1.K(finishTime)`
 *    判是否今日 —— 上报时 finishTime 必须落在今天，否则会被服务端/原版侧逻辑忽略；
 *  - 单条上限：参考项目 `Score.perItem` 默认 200（服务端单条上限），
 *    大增量拆成多条（每条 ≤200）分批上报。
 *
 * ## ⚠️ 已知阻塞
 *
 * 1. `sign` 参数未破 —— attend 是主域端点，会撞 417 solar-encoder；
 * 2. `@NeedEncode` 的 native 编码口径未完整复刻（同 417 根因链）。
 *
 * 代码按已确证协议写好，等 sign 后实测；不假装可用。
 */
object ScorePump {

    /** 单条增量的上限（对齐参考项目 `Score.perItem` 默认 200：服务端单条上限）。 */
    const val PER_ITEM_MAX = 200

    /** 单批最多多少条（防一次性超大 payload）。UI 与拆条逻辑共用。 */
    const val MAX_ITEMS_PER_BATCH = 50

    /**
     * 把「目标增量」拆成增量记录并上报。
     *
     * @param delta 本周要增加的经验值（正数）。超过 [PER_ITEM_MAX] 会拆成多条。
     * @param ruleType 规则类型。原版按练习类型给；本项目缺真机取值样本，
     *   先用 0（原版 `ruleType` 出现在 `n(context, ruleType, ...)` 签名里，
     *   具体枚举未确证，**如实标注待实测**）。
     * @param onProgress (已上报增量, 总增量) —— UI 显示进度。
     * @return 成功上报的增量总和；失败抛异常（取消透传）。
     */
    suspend fun pumpDelta(
        delta: Int,
        ruleType: Int = 0,
        onProgress: (reported: Int, total: Int) -> Unit = { _, _ -> },
    ): Result<Int> {
        require(delta > 0) { "增量必须为正数，收到 $delta" }
        val total = delta.coerceAtMost(MAX_ITEMS_PER_BATCH * PER_ITEM_MAX)
        if (total < delta) {
            return Result.failure(IllegalArgumentException("单次最多 ${MAX_ITEMS_PER_BATCH * PER_ITEM_MAX} 分"))
        }

        // 拆条：每条 ≤ PER_ITEM_MAX，finishTime 用当前时间（落在"今天"过滤窗口内）。
        val now = System.currentTimeMillis()
        val items = buildList {
            var remaining = total
            while (remaining > 0) {
                val piece = remaining.coerceAtMost(PER_ITEM_MAX)
                add(LeoTodayExerciseData(finishTime = now, obtainExp = piece, ruleType = ruleType))
                remaining -= piece
            }
        }

        return runCatching {
            var reported = 0
            for (chunk in items.chunked(MAX_ITEMS_PER_BATCH)) {
                ExerciseRepository.postSavedExp(
                    LeoTodayExerciseListData(todayExercises = chunk),
                )
                reported += chunk.sumOf { it.obtainExp }
                onProgress(reported, total)
            }
            reported
        }
    }
}
