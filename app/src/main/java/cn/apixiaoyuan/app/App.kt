package cn.apixiaoyuan.app

import android.app.Application
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec

/**
 * 全局 Application。
 *
 * 莫奈取色的产物（ColorScheme）在这里以 Compose 可观察状态持有，
 * 壁纸变更或用户手动改种子色时更新此状态，全应用重组。
 */
class App : Application() {

    companion object {
        lateinit var instance: App
            private set

        /** 当前莫奈色板，由壁纸取色或手动种子色驱动，全局 Compose 观察此状态重组。 */
        var colorScheme by mutableStateOf<ColorScheme?>(null)

        /** 当前取色风格，默认 Material You 的 TonalSpot。 */
        var paletteStyle by mutableStateOf(PaletteStyle.TonalSpot)

        /** 色板规范版本，2021 与 2025 两套 Tone 阶梯。 */
        var colorSpec by mutableStateOf(ColorSpec.SpecVersion.SPEC_2025)

        /** 种子色，取色失败或用户手动指定时使用。 */
        var seedColor by mutableStateOf(Color(0xFF6750A4))
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
