package cn.apixiaoyuan.app.core.design.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import cn.apixiaoyuan.app.core.design.icon.AppIcons
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 悬浮底栏的**滚动限位**高度。
 *
 * 由 `MainActivity.AppShell` 提供：底栏**显示时**为「底栏高 + 12dp 呼吸 + 手势条」，
 * 二级页（底栏淡出）时为 `0.dp`。
 *
 * ## 语义纪律（2026-09-25 修正 —— 此前实现是错的）
 *
 * 这个值**只用于滚动容器的底部限位**（`LazyColumn.contentPadding.bottom` /
 * Column 底部 spacer），**绝不进内容区的 `PaddingValues(bottom =)`**：
 *
 *  - 底栏是**浮层**（不占布局空间），内容本就该能滑到它**下面** ——
 *    内容从玻璃下方穿过并产生折射，这正是液态玻璃观感成立的前提；
 *  - 若把它加进内容区 padding，等于把整个页面顶上去，底栏下方永远空着，
 *    玻璃没有东西可折射，设计意图完全落空；
 *  - 但滚动**终点**必须抬高它，否则最后一项会停在底栏后面看不见。
 *
 * 用 CompositionLocal 而不是给每个页面加参数：页面不该知道「当前是不是 Tab 根页」，
 * 那是导航层的事实。这样所有页面（现有的与以后新增的）都自动拿到正确限位。
 */
val LocalBottomBarInset = staticCompositionLocalOf { androidx.compose.ui.unit.Dp.Unspecified }

/**
 * 全应用统一页面骨架。
 *
 * 顶栏已切换为 miuix 规范：
 *  - 顶栏本体是 miuix 的 [SmallTopAppBar]（读 `MiuixTheme` 配色 / 字号 / 高度），
 *    不再用 Material3 的 CenterAlignedTopAppBar；
 *  - 顶栏背景是 miuix 的**渐变模糊**（[progressiveTextureBlur]）：贴顶一侧模糊最强、
 *    向下逐渐过渡到清晰，页面内容从模糊层下方滑过时形成柔和渐隐；
 *  - 采样源是本页内容区自己的 [LayerBackdrop]（`rememberLayerBackdrop` + 内容层
 *    `.layerBackdrop(backdrop)`），**只记录内容、不含顶栏自身**，避免自采样；
 *  - 悬浮液态玻璃底栏不参与本文件的任何改动（它是浮层，由 MainActivity 单独叠加）。
 *
 * 留白逻辑保持不变：
 *  - 顶栏自己吃 statusBars inset（[SmallTopAppBar] 的 defaultWindowInsetsPadding）；
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
@Composable
fun AppScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 24.dp,
    content: @Composable (PaddingValues) -> Unit,
) {
    // 渐变模糊的采样层：先铺一层主题 surface 打底，再记录页面内容。
    // 打底色的作用与 miuix 官方 example 的 rememberBlurBackdrop 一致 ——
    // 内容未铺满时模糊区仍有稳定底色，不会透出下层黑底。
    // 注意：surface 必须在 composable 上下文里先取出来，
    // onDraw lambda 不是 @Composable，不能直接读 MiuixTheme.colorScheme。
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            BlurredTopBar(
                backdrop = backdrop,
                title = title,
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        // innerPadding 已含顶栏高度（顶栏自带 statusBars）与 navigationBars（底部手势条）。
        //
        // **这里不再加底栏高度**（2026-09-25 修正）：底栏是浮层，内容必须能滑到它
        // 下面才能被折射。此前把 LocalBottomBarInset 加在这里，等于把整个页面
        // 顶上去，玻璃下面空无一物 —— 液态玻璃的设计意图完全落空。
        //
        // 底栏高度改由滚动容器消费：见下方 [LocalScrollBottomLimit] 的提供，
        // LazyColumn / Column 的滚动终点才抬高它（最后一项滚到不被遮挡处）。
        val barInset = LocalBottomBarInset.current
        val barExtra = if (barInset.isSpecified) barInset else 0.dp
        val bottom = innerPadding.calculateBottomPadding() + bottomInset
        CompositionLocalProvider(LocalScrollBottomLimit provides barExtra) {
            Box(
                Modifier
                    .fillMaxSize()
                    // 内容层被记录进 backdrop，供顶栏采样做渐变模糊。
                    .layerBackdrop(backdrop),
            ) {
                content(
                    PaddingValues(
                        top = innerPadding.calculateTopPadding(),
                        bottom = bottom,
                    )
                )
            }
        }
    }
}

/**
 * 滚动容器应追加的**底部限位**。
 *
 * 由 `AppScaffold` 从 [LocalBottomBarInset] 换算后提供，供 `LazyColumn` 的
 * `contentPadding.bottom` 或 `Column` 的底部 spacer 消费。
 *
 * 与 [LocalBottomBarInset] 的区别（两者数值相同，语义不同）：
 *  - [LocalBottomBarInset] 是「导航层事实」—— 当前是否在 Tab 根页、底栏多高；
 *  - 本值是「页面滚动终点该抬多少」—— 由 scaffold 换算后下发给滚动容器。
 *
 * 分成两个是为了让滚动容器不必自己判断 `isSpecified`，也避免页面直接依赖
 * 导航层的 Local。
 */
val LocalScrollBottomLimit = staticCompositionLocalOf { 0.dp }

/**
 * miuix 渐变模糊顶栏。
 *
 * 结构：Box 内先铺一层 `matchParentSize` 的 [progressiveTextureBlur]（纯背景，无内容），
 * 再把 [SmallTopAppBar] 叠在上面并设 `color = Color.Transparent`，
 * 这样顶栏自身的实心背景不会盖住模糊层。
 *
 * 模糊参数对齐 miuix 官方 example 的 `BlurredBar`：
 *  - `ProgressiveBlur.Top.copy(curve = 2.2f)`：上强下清的渐变，曲线把过渡压向上缘；
 *  - `blurRadius = 10f`：渐进模糊的满强度半径；
 *  - blendColors 用主题 surface 以 30% 透明度提亮，避免深色内容把顶栏压得发灰。
 */
@Composable
private fun BlurredTopBar(
    backdrop: LayerBackdrop,
    title: String,
    onBack: (() -> Unit)?,
) {
    // 运行时 shader 不可用时（理论上 minSdk 33 已支持，此处仅作兜底），
    // 渐变模糊会整体跳过，若顶栏仍设透明会出现标题与内容叠字，
    // 因此降级为主题 surface 实色背景。
    val blurSupported = isRuntimeShaderSupported()
    val colors = BlurDefaults.blurColors(
        blendColors = listOf(
            BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(alpha = 0.30f)),
        ),
    )
    Box {
        if (blurSupported) {
            Box(
                Modifier
                    .matchParentSize()
                    .progressiveTextureBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        blurRadius = 10f,
                        gradient = ProgressiveBlur.Top.copy(curve = 2.2f),
                        colors = colors,
                    )
            )
        }
        SmallTopAppBar(
            title = title,
            modifier = Modifier.fillMaxWidth(),
            // 支持模糊时透明，让下层的渐变模糊透出来；不支持时退回实色。
            color = if (blurSupported) Color.Transparent else MiuixTheme.colorScheme.surface,
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = AppIcons.Back,
                            contentDescription = "返回",
                        )
                    }
                }
            },
        )
    }
}

/**
 * 列表形态骨架：LazyColumn + 统一 contentPadding。
 * 分组用 [LazyListScope] 直接给，或调用方自行包 SegmentedColumn。
 */
@Composable
fun AppListScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 24.dp,
    content: LazyListScope.() -> Unit,
) {
    AppScaffold(title = title, onBack = onBack, modifier = modifier, bottomInset = bottomInset) { pad ->
        // 滚动终点抬高底栏高度：内容仍可滑到底栏**下方**（玻璃折射成立），
        // 但最后一项能滚到「不被底栏遮挡」的位置 —— 消费的是
        // LocalScrollBottomLimit，不是内容区 padding。
        val scrollLimit = LocalScrollBottomLimit.current
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = pad.calculateTopPadding(),
                bottom = pad.calculateBottomPadding() + scrollLimit,
            ),
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