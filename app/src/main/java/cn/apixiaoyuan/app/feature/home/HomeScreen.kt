package cn.apixiaoyuan.app.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

/**
 * 首页。
 *
 * 当前仅承载导航外壳的内容占位；后续接入莫奈色板展示卡片、
 * 最近任务、功能入口。底栏由外层 AppShell 统一挂载，
 * 本屏不重复渲染底栏，只负责自身内容区域。
 */
@Composable
fun HomeScreen(navController: NavHostController) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 96.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "逆向系老挂 · 首页",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}