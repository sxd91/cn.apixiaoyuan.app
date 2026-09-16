package cn.apixiaoyuan.app.feature.pk

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController

/**
 * 口算 PK 入口页。
 *
 * 口算 PK 是 H5 应用，原生侧只提供容器与登录态同步，实际交互全在
 * [PkH5Screen] 里的 WebView 中。本页做两件事：
 *
 *  1. 触发 [PkViewModel.loadEntry] 拉 PK 入口数据（可选，失败不阻塞）
 *  2. 直接渲染 [PkH5Screen]，把 H5 容器全屏铺开
 *
 * 原注释写的「包对比」是错的 —— PK 在这个工程里指「口算 PK」，
 * 与 APK 差分无关。这里一并修正。
 */
@Composable
fun PkScreen(
    navController: NavHostController,
    viewModel: PkViewModel = viewModel(),
) {
    // 进页面拉一次入口数据；H5 不依赖它，拉失败也照常加载。
    LaunchedEffect(Unit) {
        viewModel.loadEntry()
    }

    PkH5Screen(
        viewModel = viewModel,
        onFinish = { navController.popBackStack() },
    )
}