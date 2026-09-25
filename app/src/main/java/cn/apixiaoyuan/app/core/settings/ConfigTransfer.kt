package cn.apixiaoyuan.app.core.settings

import cn.apixiaoyuan.app.core.design.theme.PageTransitionAnimation
import cn.apixiaoyuan.app.core.design.theme.PageTransitionPrefs
import cn.apixiaoyuan.app.core.design.theme.ThemePrefs
import cn.apixiaoyuan.app.core.oldsimian.OldSimianPrefs
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.Serializable

/**
 * 配置导出 / 导入的数据契约。
 *
 * ## 覆盖范围（一次全做，不留空壳）
 *
 * 导出内容 = 「老挂戏老叟」全部功能开关 + 外观五项 + 过渡动画：
 *  - [oldSimian]：18 个键（练习作答 4 + 结算时间 2 + 名字限制 1 + 分数 5 +
 *    PK/H5 3 + 结束页 3）
 *  - [theme]：主题模式 / 取色风格 / 颜色规格 / 种子色 / 底栏效果
 *  - [pageTransition]：二级页过渡动画引擎
 *
 * 用 [Serializable] + kotlinx.serialization 而不是手写 JSONObject：
 *  - 字段增减时编译期就能发现遗漏，不会静默少导一项；
 *  - 导出文件是稳定 JSON，用户可肉眼查看、可手改。
 *
 * ## 版本字段
 *
 * [version] 用于日后结构变更时的兼容判断。当前为 1；读文件时若版本更高
 * 则拒绝导入（未知字段可能带新语义，猜着填比不填更危险）。
 */
@Serializable
data class AppConfig(
    val version: Int = VERSION,
    val oldSimian: OldSimianPrefsSnapshot = OldSimianPrefsSnapshot(),
    val theme: ThemeSnapshot = ThemeSnapshot(),
    val pageTransition: String = PageTransitionAnimation.MIUIX.name,
) {
    companion object {
        /** 当前配置格式版本。 */
        const val VERSION = 1
    }
}

/** 「老挂戏老叟」全部功能开关的快照。字段名与 `OldSimianPrefs` 的 18 个键一一对应。 */
@Serializable
data class OldSimianPrefsSnapshot(
    // ---- 练习：作答内容 ----
    val autoCorrect: Boolean = false,
    val customAnswerEnabled: Boolean = false,
    val customAnswerText: String = "",
    val strokeEnabled: Boolean = false,
    // ---- 练习：结算时间 ----
    val customCostEnabled: Boolean = false,
    val customCostMs: Int = OldSimianPrefs.MIN_COST_MS,
    // ---- 账号：名字限制 ----
    val ignoreNicknameRestriction: Boolean = false,
    // ---- 分数 ----
    val customScoreEnabled: Boolean = false,
    val customScoreValue: Int = 0,
    val customScoreKeypoint: String = "",
    val customScoreLimit: Int = OldSimianPrefs.SCORE_LIMIT_DEFAULT,
    val customScoreIntervalMs: Int = OldSimianPrefs.SCORE_INTERVAL_DEFAULT,
    // ---- PK / H5 ----
    val autoNextRound: Boolean = false,
    val nextRoundIntervalMs: Int = 1500,
    val noRankingAnim: Boolean = false,
    // ---- PK 自动提交画笔 ----
    val pkStrokeEnabled: Boolean = false,
    val pkStrokeCount: Int = OldSimianPrefs.PK_STROKE_COUNT_DEFAULT,
    val pkStrokeIntervalMs: Int = OldSimianPrefs.PK_STROKE_INTERVAL_DEFAULT,
)

/** 外观五项设置的快照。 */
@Serializable
data class ThemeSnapshot(
    val mode: String = ThemePrefs.ThemeMode.FOLLOW_SYSTEM.name,
    val paletteStyle: String = com.materialkolor.PaletteStyle.TonalSpot.name,
    val colorSpec: String = com.materialkolor.dynamiccolor.ColorSpec.SpecVersion.SPEC_2025.name,
    /** 种子色的 ARGB 整数（Color 是内联类，不能直接序列化）。 */
    val seedColorArgb: Int = -0xff9a5c, // 0xFF6750A4
    val bottomBarMode: String = ThemePrefs.BottomBarMode.LIQUID_GLASS.name,
)

/**
 * 配置导出 / 导入门面。
 *
 * 只做「当前状态 ↔ [AppConfig]」的双向换算，不碰文件 IO ——
 * 文件读写由 UI 层用 SAF（Storage Access Framework）完成，
 * 这样本类可以脱离 Context 单测。
 */
object ConfigTransfer {

    /** 从当前各处 Prefs 抓一份快照。 */
    fun export(): AppConfig = AppConfig(
        version = AppConfig.VERSION,
        oldSimian = OldSimianPrefsSnapshot(
            autoCorrect = OldSimianPrefs.autoCorrect,
            customAnswerEnabled = OldSimianPrefs.customAnswerEnabled,
            customAnswerText = OldSimianPrefs.customAnswerText,
            strokeEnabled = OldSimianPrefs.strokeEnabled,
            customCostEnabled = OldSimianPrefs.customCostEnabled,
            customCostMs = OldSimianPrefs.customCostMs,
            ignoreNicknameRestriction = OldSimianPrefs.ignoreNicknameRestriction,
            customScoreEnabled = OldSimianPrefs.customScoreEnabled,
            customScoreValue = OldSimianPrefs.customScoreValue,
            customScoreKeypoint = OldSimianPrefs.customScoreKeypoint,
            customScoreLimit = OldSimianPrefs.customScoreLimit,
            customScoreIntervalMs = OldSimianPrefs.customScoreIntervalMs,
            autoNextRound = OldSimianPrefs.autoNextRound,
            nextRoundIntervalMs = OldSimianPrefs.nextRoundIntervalMs,
            noRankingAnim = OldSimianPrefs.noRankingAnim,
            pkStrokeEnabled = OldSimianPrefs.pkStrokeEnabled,
            pkStrokeCount = OldSimianPrefs.pkStrokeCount,
            pkStrokeIntervalMs = OldSimianPrefs.pkStrokeIntervalMs,
        ),
        theme = ThemeSnapshot(
            mode = ThemePrefs.mode.name,
            paletteStyle = ThemePrefs.paletteStyle.name,
            colorSpec = ThemePrefs.colorSpec.name,
            seedColorArgb = ThemePrefs.seedColor.toArgb(),
            bottomBarMode = ThemePrefs.bottomBarMode.name,
        ),
        pageTransition = PageTransitionPrefs.animation.name,
    )

    /**
     * 把快照写回各处 Prefs。
     *
     * 每个枚举都走 `runCatching` + 默认值兜底：导入的文件可能来自旧版本、
     * 或被用户手改过，写错一个值不该让整个导入失败并留下半套配置。
     *
     * @return 导入成功返回 null；失败返回错误说明。
     */
    fun import(config: AppConfig): String? {
        if (config.version > AppConfig.VERSION) {
            return "配置文件版本 ${config.version} 高于当前支持的 ${AppConfig.VERSION}，无法导入"
        }

        val s = config.oldSimian
        OldSimianPrefs.autoCorrect = s.autoCorrect
        OldSimianPrefs.customAnswerEnabled = s.customAnswerEnabled
        OldSimianPrefs.customAnswerText = s.customAnswerText
        OldSimianPrefs.strokeEnabled = s.strokeEnabled
        OldSimianPrefs.customCostEnabled = s.customCostEnabled
        OldSimianPrefs.customCostMs = s.customCostMs
            .coerceIn(OldSimianPrefs.COST_RANGE_MIN, OldSimianPrefs.COST_RANGE_MAX)
        OldSimianPrefs.ignoreNicknameRestriction = s.ignoreNicknameRestriction
        OldSimianPrefs.customScoreEnabled = s.customScoreEnabled
        OldSimianPrefs.customScoreValue = s.customScoreValue.coerceAtLeast(0)
        OldSimianPrefs.customScoreKeypoint = s.customScoreKeypoint
        OldSimianPrefs.customScoreLimit = s.customScoreLimit
            .coerceIn(OldSimianPrefs.SCORE_LIMIT_MIN, OldSimianPrefs.SCORE_LIMIT_MAX)
        OldSimianPrefs.customScoreIntervalMs = s.customScoreIntervalMs
            .coerceIn(OldSimianPrefs.SCORE_INTERVAL_MIN, OldSimianPrefs.SCORE_INTERVAL_MAX)
        OldSimianPrefs.autoNextRound = s.autoNextRound
        OldSimianPrefs.nextRoundIntervalMs = s.nextRoundIntervalMs
            .coerceIn(OldSimianPrefs.NEXT_ROUND_INTERVAL_MIN, OldSimianPrefs.NEXT_ROUND_INTERVAL_MAX)
        OldSimianPrefs.noRankingAnim = s.noRankingAnim
        OldSimianPrefs.pkStrokeEnabled = s.pkStrokeEnabled
        OldSimianPrefs.pkStrokeCount = s.pkStrokeCount
            .coerceIn(OldSimianPrefs.PK_STROKE_COUNT_MIN, OldSimianPrefs.PK_STROKE_COUNT_MAX)
        OldSimianPrefs.pkStrokeIntervalMs = s.pkStrokeIntervalMs
            .coerceIn(OldSimianPrefs.PK_STROKE_INTERVAL_MIN, OldSimianPrefs.PK_STROKE_INTERVAL_MAX)
        OldSimianPrefs.persist()

        val t = config.theme
        ThemePrefs.mode = runCatching { ThemePrefs.ThemeMode.valueOf(t.mode) }
            .getOrDefault(ThemePrefs.ThemeMode.FOLLOW_SYSTEM)
        ThemePrefs.paletteStyle = runCatching { com.materialkolor.PaletteStyle.valueOf(t.paletteStyle) }
            .getOrDefault(com.materialkolor.PaletteStyle.TonalSpot)
        ThemePrefs.colorSpec = runCatching { com.materialkolor.dynamiccolor.ColorSpec.SpecVersion.valueOf(t.colorSpec) }
            .getOrDefault(com.materialkolor.dynamiccolor.ColorSpec.SpecVersion.SPEC_2025)
        ThemePrefs.seedColor = androidx.compose.ui.graphics.Color(t.seedColorArgb)
        ThemePrefs.bottomBarMode = runCatching { ThemePrefs.BottomBarMode.valueOf(t.bottomBarMode) }
            .getOrDefault(ThemePrefs.BottomBarMode.LIQUID_GLASS)
        ThemePrefs.persist()

        PageTransitionPrefs.update(
            runCatching { PageTransitionAnimation.valueOf(config.pageTransition) }
                .getOrDefault(PageTransitionAnimation.MIUIX),
        )
        return null
    }
}