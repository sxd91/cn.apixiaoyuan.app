package cn.apixiaoyuan.app.core.design.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.apixiaoyuan.app.core.design.icon.AppIcons

/**
 * 全应用统一页面骨架。
 *
 * 留白逻辑对齐 cn.nizou.sxd（老挂戏老叟）：
 *  - 顶栏用 Scaffold 的 topBar + CenterAlignedTopAppBar，自动吃 statusBars inset，
 *    刘海区域不再挡控件；
 *  - 顶栏左侧固定返回键（二级页返回上一级，主 Tab 页返回首页）；
 *  - 内容区底部只吃 navigationBars（手势条），不给悬浮玻璃底栏留占位 ——
 *    底栏是浮层，内容可以滑到底部被它遮住，这正是玻璃透明感成立的前提。
 *
 * 两种形态：
 *  - [AppScaffold]：普通滚动内容（Column 由调用方给），适合表单/详情页；
 *  - [AppListScaffold]：LazyColumn 列表页，分组内容用 LazyListScope。
 *
 * @param title 顶栏标题
 * @param onBack 返回键动作；null 表示不显示返回键（主 Tab 根页）
 * @param bottomInset 内容区底部额外留白，默认 24dp；底栏不再需要 96dp 占位
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 24.dp,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = AppIcons.forKey("Back"),
                                contentDescription = "返回",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        // innerPadding 已含 statusBars（顶栏）与 navigationBars（底部手势条）。
        // 不额外加底栏占位 —— 悬浮玻璃底栏浮在内容之上。
        val bottom = innerPadding.calculateBottomPadding() + bottomInset
        content(
            PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = bottom,
            )
        )
    }
}

/**
 * 列表形态骨架：LazyColumn + 统一 contentPadding。
 * 分组用 [LazyListScope] 直接给，或调用方自行包 SegmentedColumn。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 24.dp,
    content: LazyListScope.() -> Unit,
) {
    AppScaffold(title = title, onBack = onBack, modifier = modifier, bottomInset = bottomInset) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = pad,
            content = content,
        )
    }
}

/**
 * 主 Tab 根页骨架：有标题但没有返回键（返回由 MainActivity 的 BackHandler 处理）。
 */
@Composable
fun AppTabScaffold(
    title: String,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 24.dp,
    content: @Composable (PaddingValues) -> Unit,
) = AppScaffold(title = title, onBack = null, modifier = modifier, bottomInset = bottomInset, content = content)
