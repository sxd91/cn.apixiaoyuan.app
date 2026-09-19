package cn.apixiaoyuan.app.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import cn.apixiaoyuan.app.core.design.component.AppScaffold

/**
 * 设置页。
 *
 * 后续接入取色风格（PaletteStyle）、ColorSpec 版本、种子色、
 * 深色模式跟随等开关；当前先占位，保证导航链路完整。
 *
 * 顶栏与返回键由 [AppScaffold] 统一提供；
 * 悬浮底栏是浮层，内容不再为它预留 96dp。
 */
@Composable
fun SettingsScreen(navController: NavHostController) {
    AppScaffold(title = "设置", onBack = null) { pad: PaddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}