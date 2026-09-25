package cn.apixiaoyuan.app.core.design.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

/**
 * miuix 主题根。
 *
 * 与 [ReverseOldGuyTheme] 的 Material3 主题并存，不是替换关系：
 *  - [ReverseOldGuyTheme] 负责 Material3 / MaterialExpressiveTheme 的 ColorScheme，
 *    现有页面（AppScaffold + Material3 组件）继续读 `MaterialTheme.colorScheme`；
 *  - 本函数负责 miuix 的 `MiuixTheme.colorScheme` / `MiuixTheme.textStyles`，
 *    供 miuix 组件（SmallTopAppBar / Card / Switch / ArrowPreference 等）使用。
 *
 * 两者共用同一套莫奈取色参数（种子色、风格、规范版本），因此视觉一致；
 * 生成路径不同：Material3 侧走 material-kolor 的 `rememberDynamicColorScheme`，
 * miuix 侧走 [ThemeController] 的 Monet 模式（内部同样调 material-kolor 的 HCT）。
 *
 * @param darkTheme 是否深色。
 * @param paletteStyle 取色风格，与 Material3 侧同源。
 * @param colorSpec 色板规范版本（2021 / 2025）。
 * @param seedColor 种子色。
 */
@Composable
fun AppMiuixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    colorSpec: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2025,
    seedColor: Color = Color(0xFF6750A4),
    content: @Composable () -> Unit,
) {
    val miuixPalette = remember(paletteStyle) { paletteStyle.toMiuixPaletteStyle() }
    val miuixSpec = remember(colorSpec) { colorSpec.toMiuixColorSpec() }
    // darkTheme 参与 key：ThemeController 的 isDark 是只读状态（构造后不可写），
    // 深浅色切换时必须重建 controller，否则颜色不会跟随。
    val controller = remember(seedColor, miuixPalette, miuixSpec, darkTheme) {
        ThemeController(
            // 用 Monet 模式 + keyColor：颜色由种子色现场推导，
            // 而不是读系统壁纸，保证与 Material3 侧同色。
            colorSchemeMode = ColorSchemeMode.MonetSystem,
            keyColor = seedColor,
            colorSpec = miuixSpec,
            paletteStyle = miuixPalette,
            isDark = darkTheme,
        )
    }
    MiuixTheme(controller = controller, content = content)
}

/**
 * material-kolor 的 [PaletteStyle] 与 miuix 的 [ThemePaletteStyle] 一一对应
 * （两者都是 9 项，取值集合相同，仅包名不同）。
 *
 * 可见性从 private 提升为 internal：配置导出/导入需要按名字存取这两组枚举，
 * 直接用本映射可避免两处各写一份 when 分支而漂移。
 */
internal fun PaletteStyle.toMiuixPaletteStyle(): ThemePaletteStyle = when (this) {
    PaletteStyle.TonalSpot -> ThemePaletteStyle.TonalSpot
    PaletteStyle.Neutral -> ThemePaletteStyle.Neutral
    PaletteStyle.Vibrant -> ThemePaletteStyle.Vibrant
    PaletteStyle.Expressive -> ThemePaletteStyle.Expressive
    PaletteStyle.Rainbow -> ThemePaletteStyle.Rainbow
    PaletteStyle.FruitSalad -> ThemePaletteStyle.FruitSalad
    PaletteStyle.Monochrome -> ThemePaletteStyle.Monochrome
    PaletteStyle.Fidelity -> ThemePaletteStyle.Fidelity
    PaletteStyle.Content -> ThemePaletteStyle.Content
}

/** material-kolor 的规范版本与 miuix 的 [ThemeColorSpec] 对应。 */
internal fun ColorSpec.SpecVersion.toMiuixColorSpec(): ThemeColorSpec = when (this) {
    ColorSpec.SpecVersion.SPEC_2021 -> ThemeColorSpec.Spec2021
    ColorSpec.SpecVersion.SPEC_2025 -> ThemeColorSpec.Spec2025
}
