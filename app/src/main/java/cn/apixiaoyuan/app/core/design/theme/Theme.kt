package cn.apixiaoyuan.app.core.design.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import cn.apixiaoyuan.app.App

/**
 * 全局主题。
 *
 * 莫奈取色链路：
 *  - 种子色优先级：用户手动指定 > 壁纸取色产物 > 固定兜底 [App.seedColor]。
 *  - 由 material-kolor 的 [rememberDynamicColorScheme] 生成完整 ColorScheme，
 *    内部走 HCT 色彩空间 + Tone 阶梯，与 AOSP material-color-utilities 对齐。
 *  - 风格 [PaletteStyle] 与规范版本 [ColorSpec] 可在设置页切换，
 *    当前默认 TonalSpot + SPEC_2025（Material You 2025 规范）。
 *
 * 生成后的 ColorScheme 通过 [SideEffect] 写回 [App.colorScheme]，
 * 供壁纸变更等外部场景复用；用 SideEffect 是为了避免在 composition
 * 期间直接写全局可变状态触发重组循环。
 */
@Composable
fun ReverseOldGuyTheme(
    darkTheme: Boolean = ThemePrefs.resolveDark(isSystemInDarkTheme()),
    paletteStyle: PaletteStyle = App.paletteStyle,
    colorSpec: ColorSpec.SpecVersion = App.colorSpec,
    seedColor: Color = App.seedColor,
    content: @Composable () -> Unit,
) {
    val scheme: ColorScheme = rememberDynamicColorScheme(
        seedColor = seedColor,
        isDark = darkTheme,
        style = paletteStyle,
        specVersion = colorSpec,
    )

    SideEffect { App.colorScheme = scheme }
    // Material3 主题（现有页面）与 miuix 主题（miuix 组件）并存：
    // 外层 MaterialExpressiveTheme 提供 MaterialTheme.colorScheme，
    // 内层 AppMiuixTheme 提供 MiuixTheme.colorScheme，两者共用同一套
    // 种子色 / 风格 / 规范版本，因此配色一致。
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
    ) {
        AppMiuixTheme(
            darkTheme = darkTheme,
            paletteStyle = paletteStyle,
            colorSpec = colorSpec,
            seedColor = seedColor,
            content = content,
        )
    }
}
