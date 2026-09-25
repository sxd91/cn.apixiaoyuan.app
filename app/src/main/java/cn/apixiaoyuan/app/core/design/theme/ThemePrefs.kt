package cn.apixiaoyuan.app.core.design.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import cn.apixiaoyuan.app.App

/**
 * 外观设置的持久化门面（主题模式 / 取色风格 / 颜色规格 / 种子色 / 底栏效果）。
 *
 * ## 为什么单独建这个类
 *
 * 此前这五项在设置页里是**空壳**（有行无 onClick），原因是它们各自散落在不同地方、
 * 且没有落盘：
 *  - `App.paletteStyle` / `colorSpec` / `seedColor` 是 `App` companion 里的
 *    `mutableStateOf`，进程一死就回到默认值；
 *  - 「主题模式」**根本没有对应状态**（`ReverseOldGuyTheme` 只用
 *    `isSystemInDarkTheme()`）；
 *  - 「底栏效果」同样没有状态可写。
 *
 * 本类把这五项收拢到一个 SharedPreferences 里，全部带 `mutableStateOf`
 * （改动即重组），并负责在 [init] 时回填 `App` 的 companion 字段 ——
 * `ReverseOldGuyTheme` 仍从 `App.xxx` 读，改动对它透明。
 *
 * ## 主题模式三态
 *
 * [ThemeMode.FOLLOW_SYSTEM] 时 [App.themeMode] 为 null 语义由
 * `isSystemInDarkTheme()` 决定；另两态显式指定。这样 `ReverseOldGuyTheme`
 * 的 `darkTheme` 参数可以直接算出来，不需要在主题内部再分支。
 *
 * 生命周期：必须在 `App.onCreate` 里调 [init]，否则 [prefs] 会抛错
 * （与 [cn.apixiaoyuan.app.core.session.SessionStore] 同一套路数）。
 */
object ThemePrefs {

    private const val PREF_NAME = "theme_prefs"

    private const val KEY_MODE = "theme_mode"
    private const val KEY_PALETTE_STYLE = "palette_style"
    private const val KEY_COLOR_SPEC = "color_spec"
    private const val KEY_SEED_COLOR = "seed_color"
    private const val KEY_BOTTOM_BAR_MODE = "bottom_bar_mode"

    /** 主题模式：跟随系统 / 浅色 / 深色。 */
    enum class ThemeMode(val displayName: String) {
        FOLLOW_SYSTEM("跟随系统"),
        LIGHT("浅色"),
        DARK("深色"),
    }

    /**
     * 悬浮底栏的渲染模式。
     *
     * 三态是为了性能兜底：液态玻璃在低端机上可能掉帧，
     * 允许降级到毛玻璃（只模糊不折射）或纯色（完全不采样背景）。
     */
    enum class BottomBarMode(val displayName: String) {
        LIQUID_GLASS("液态玻璃"),
        FROSTED("毛玻璃"),
        SOLID("纯色"),
    }

    /** 当前主题模式。默认跟随系统。 */
    var mode by mutableStateOf(ThemeMode.FOLLOW_SYSTEM)

    /** 取色风格。默认 TonalSpot（Material You 默认）。 */
    var paletteStyle by mutableStateOf(PaletteStyle.TonalSpot)

    /** 色板规范版本。默认 2025（Expressive）。 */
    var colorSpec by mutableStateOf(ColorSpec.SpecVersion.SPEC_2025)

    /** 种子色。默认 Material You 的基准紫。 */
    var seedColor by mutableStateOf(Color(0xFF6750A4))

    /** 底栏渲染模式。默认液态玻璃。 */
    var bottomBarMode by mutableStateOf(BottomBarMode.LIQUID_GLASS)

    @Volatile
    private var appContext: Context? = null

    /**
     * 由 `App.onCreate` 调用。
     *
     * 读完本地配置后**同步回填 `App` 的 companion 字段** ——
     * `ReverseOldGuyTheme` 的默认值取自那里，回填后首帧就是用户上次的设置，
     * 不会先闪一下默认紫再变。
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        val p = prefs()
        mode = runCatching { ThemeMode.valueOf(p.getString(KEY_MODE, null) ?: ThemeMode.FOLLOW_SYSTEM.name) }
            .getOrDefault(ThemeMode.FOLLOW_SYSTEM)
        paletteStyle = runCatching { PaletteStyle.valueOf(p.getString(KEY_PALETTE_STYLE, null) ?: PaletteStyle.TonalSpot.name) }
            .getOrDefault(PaletteStyle.TonalSpot)
        colorSpec = runCatching { ColorSpec.SpecVersion.valueOf(p.getString(KEY_COLOR_SPEC, null) ?: ColorSpec.SpecVersion.SPEC_2025.name) }
            .getOrDefault(ColorSpec.SpecVersion.SPEC_2025)
        // 种子色以 ARGB 整数存（Color 是内联类，不能直接序列化）。
        val seedArgb = p.getInt(KEY_SEED_COLOR, -1)
        seedColor = if (seedArgb == -1) Color(0xFF6750A4) else Color(seedArgb)
        bottomBarMode = runCatching { BottomBarMode.valueOf(p.getString(KEY_BOTTOM_BAR_MODE, null) ?: BottomBarMode.LIQUID_GLASS.name) }
            .getOrDefault(BottomBarMode.LIQUID_GLASS)

        syncToApp()
    }

    /** 把当前值写回 `App` companion（主题根从那里读）。 */
    private fun syncToApp() {
        App.paletteStyle = paletteStyle
        App.colorSpec = colorSpec
        App.seedColor = seedColor
        App.themeMode = mode
        App.bottomBarMode = bottomBarMode
    }

    /** 写盘。设置页每次改动调用一次。 */
    fun persist() {
        prefs().edit()
            .putString(KEY_MODE, mode.name)
            .putString(KEY_PALETTE_STYLE, paletteStyle.name)
            .putString(KEY_COLOR_SPEC, colorSpec.name)
            .putInt(KEY_SEED_COLOR, seedColor.toArgb())
            .putString(KEY_BOTTOM_BAR_MODE, bottomBarMode.name)
            .apply()
        syncToApp()
    }

    /**
     * 供 `ReverseOldGuyTheme` 计算 darkTheme。
     *
     * @param systemDark 系统的深色模式状态（`isSystemInDarkTheme()`）
     */
    fun resolveDark(systemDark: Boolean): Boolean = when (mode) {
        ThemeMode.FOLLOW_SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    /** 取色风格的全部可选项（设置页单选列表用）。 */
    val paletteStyleOptions: List<PaletteStyle> = PaletteStyle.entries

    /** 颜色规格的全部可选项。 */
    val colorSpecOptions: List<ColorSpec.SpecVersion> = ColorSpec.SpecVersion.entries

    /**
     * 预设种子色板。
     *
     * 给用户一组可直接选的色，而不是只提供自定义取色 ——
     * 大多数场景用户只想换个主色调，不想调色。
     */
    val presetSeedColors: List<Color> = listOf(
        Color(0xFF6750A4), // Material You 基准紫（默认）
        Color(0xFFB3261E), // 红
        Color(0xFFF2B8B5), // 粉
        Color(0xFF7D5260), // 玫红
        Color(0xFF625B71), // 紫灰
        Color(0xFF48416A), // 深紫
        Color(0xFF3B82F6), // 蓝
        Color(0xFF00639B), // 深蓝
        Color(0xFF00A9A5), // 青
        Color(0xFF006D3B), // 绿
        Color(0xFF84CC16), // 黄绿
        Color(0xFFF5A524), // 橙
        Color(0xFF8B5A2B), // 棕
        Color(0xFF607D8B), // 蓝灰
        Color(0xFF1F2937), // 深灰
        Color(0xFF000000), // 黑（纯灰阶取色）
    )

    private fun prefs(): SharedPreferences {
        val ctx = appContext ?: error("ThemePrefs.init() 未调用")
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
}
