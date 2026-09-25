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
    private const val KEY_DYNAMIC_WALLPAPER = "dynamic_wallpaper"

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

    /**
     * 动态壁纸取色：用系统壁纸的强调色作种子，而不是自定义种子色。
     *
     * 对齐老挂戏老叟 `ThemeSettings.dynamicWallpaper`：
     *  - 开启时种子取 `WallpaperColors` 的强调色（SDK 31+ 才有 API）；
     *  - 设置页里开启本开关后**隐藏**「种子颜色」行（种子由系统给，自定义无意义）；
     *  - 取不到（SDK < 31 / 用户没设壁纸 / 无权限）时回退到 [seedColor]。
     *
     * 默认关 —— 与老挂戏老叟一致（默认用固定种子，行为可预测）。
     */
    var dynamicWallpaper by mutableStateOf(false)

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
        dynamicWallpaper = p.getBoolean(KEY_DYNAMIC_WALLPAPER, false)

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
            .putBoolean(KEY_DYNAMIC_WALLPAPER, dynamicWallpaper)
            .apply()
        syncToApp()
    }

    /**
     * 解析出真正用于取色的种子色。
     *
     * 优先级（对齐老挂戏老叟 `SeedResolver`）：
     *  1. [dynamicWallpaper] 开启 且 能取到壁纸强调色 → 用壁纸色；
     *  2. 否则 → [seedColor]（用户自定义）。
     *
     * 取不到壁纸色的三种常见情况：SDK < 31、用户没设壁纸、系统没给权限。
     * 这些都不是错误，静默回退即可 —— 取色失败不该让主题崩掉。
     */
    fun resolveSeed(): Color {
        if (!dynamicWallpaper) return seedColor
        return wallpaperAccent() ?: seedColor
    }

    /**
     * 取系统壁纸的强调色（Android 12 / SDK 31+）。
     *
     * 用 `WallpaperManager.getWallpaperColors(FLAG_SYSTEM)` 拿系统壁纸的
     * `WallpaperColors`，再按优先级取 `HINT_SUPPORTS_DARK_TEXT` 对应的色
     * （primary > secondary > tertiary），与 AOSP 的 `DynamicColors` 口径一致。
     *
     * @return 强调色；取不到返回 null。
     */
    private fun wallpaperAccent(): Color? = runCatching {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return null
        val ctx = appContext ?: return null
        val wm = android.app.WallpaperManager.getInstance(ctx)
        val colors = wm.getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM)
            ?: return null
        // AOSP DynamicColors 的取色优先级：primary > secondary > tertiary。
        val accent = colors.primaryColor
            ?: colors.secondaryColor
            ?: colors.tertiaryColor
            ?: return null
        Color(accent.toArgb())
    }.getOrNull()

    /**
     * 种子色的 HEX 文本（`#RRGGBB`），给设置页的输入框/展示用。
     *
     * 与老挂戏老叟 `ThemeSettings.seedColorHex()` 同格式 ——
     * 去掉 alpha 通道，因为种子色的 alpha 没有意义（取色只用 RGB）。
     */
    fun seedColorHex(): String =
        "#%06X".format(0xFFFFFF and seedColor.toArgb())

    /**
     * 从 HEX 文本设置种子色。
     *
     * 接受 `#RRGGBB` / `RRGGBB` / `#AARRGGBB` / `AARRGGBB` 四种写法，
     * 解析失败时**不改动**当前值（用户可能还在输入中途）。
     *
     * @return 是否解析并应用成功
     */
    fun setSeedColorHex(hex: String): Boolean {
        val parsed = runCatching {
            android.graphics.Color.parseColor(
                if (hex.startsWith("#")) hex else "#$hex",
            )
        }.getOrNull() ?: return false
        seedColor = Color(parsed)
        return true
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
