package cn.apixiaoyuan.app.feature.apk

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
 * APK 分析页。
 *
 * 后续接入 dex/jar 解析、Manifest 查看、资源提取；
 * 当前先占位，保证导航链路完整。
 *
 * 顶栏与返回键由 [AppScaffold] 统一提供；
 * 悬浮底栏是浮层，内容不再为它预留 96dp。
 */
@Composable
fun ApkScreen(navController: NavHostController) {
    AppScaffold(title = "APK 分析", onBack = { navController.popBackStack() }) { pad: PaddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "APK 分析",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
