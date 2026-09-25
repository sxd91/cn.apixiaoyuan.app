package cn.apixiaoyuan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cn.apixiaoyuan.app.core.design.component.LocalBottomBarInset
import cn.apixiaoyuan.app.core.design.glass.LiquidGlassTabBar
import cn.apixiaoyuan.app.core.design.glass.TabItem
import cn.apixiaoyuan.app.core.design.theme.ReverseOldGuyTheme
import cn.apixiaoyuan.app.core.navigation.AppNavHost
import cn.apixiaoyuan.app.core.navigation.RouteApi
import cn.apixiaoyuan.app.core.navigation.RouteHome
import cn.apixiaoyuan.app.core.navigation.RouteRepl
import cn.apixiaoyuan.app.core.navigation.RouteSettings
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ReverseOldGuyTheme {
                AppShell()
            }
        }
    }
}

/**
 * 应用外壳：四个 Tab 根页放进 [HorizontalPager]，底部叠液态玻璃 Tab 栏。
 *
 * ## 为什么是 HorizontalPager 而不是 NavHost 转场（2026-09-25 修正）
 *
 * 此前四个 Tab 是 `NavHost` 的 composable destination，切 Tab 走的是
 * `NavHost` 的全局 enter/exit 转场 —— 那是**二级页的平移动画**
 * （整屏从右滑入 + 被覆盖页视差），用在同层 Tab 切换上，观感就成了
 * 「进入了下一级页面」，是明确的错误。
 *
 * 老挂戏老叟（cn.nizou.sxd）的 Tab 切换**根本不走转场**，是
 * `HorizontalPager` + `animateScrollToPage`：整屏水平平移、带惯性吸附。
 * 这才是「同款平移动画」的真身 —— 不是给 NavHost 换个 enter/exit 能模拟的。
 * 逐行取证见 `cn.nizou.sxd/ui/MainPagerScreen.kt`。
 *
 * ## 结构 A：每个 Tab 独立 NavHost（用户拍板）
 *
 * 四个 Tab 各自持有 `rememberNavController()`，**返回栈互相独立**：
 *  - 在「首页」进练习页再返回，回到首页；切到「设置」再切回来，
 *    首页**不会**被重置到根（各 tab 状态保持）；
 *  - 不会出现在「设置」里按返回却回到「首页」的返回栈串台问题。
 * 这与老挂戏老叟（`NavDisplay` + 每 tab 独立 backStack）语义一致。
 *
 * ## 玻璃链路
 *
 *  - [rememberLayerBackdrop] 创建背景录制层；
 *  - 内容层（pager）挂 `.layerBackdrop(backdrop)` 把页面内容录成可采样纹理；
 *  - [LiquidGlassTabBar] 消费该 backdrop，在 `drawBackdrop` 里走
 *    `vibrancy() -> blur() -> lens()` 做真实折射。
 */
@Composable
private fun AppShell() {
    val tabs = remember {
        listOf(
            TabItem("首页", "Home"),
            TabItem("接口", "Api"),
            TabItem("请求台", "Terminal"),
            TabItem("设置", "Settings"),
        )
    }

    // 四个 Tab 各自独立的返回栈。key 用 TabItem 的名字 —— 每个 page 一个实例，
    // 切 Tab 不共享，返回栈因此互不干扰（结构 A）。
    val navControllers = tabs.map { rememberNavController() }
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    // 底栏选中态跟随 pager：用 targetPage 而不是 currentPage ——
    // 滑动过程中 targetPage 已经指向目标页，指示器立刻跟上，不迟滞。
    val selectedIndex = pagerState.targetPage

    // 背景录制层：内容层写入，底栏采样做折射。
    val backdrop = rememberLayerBackdrop()

    // 悬浮底栏底部留白：12dp 视觉呼吸 + 手势条（navigationBars）。
    val barBottomPadding = 12.dp +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // 当前是否停在某个 Tab 根页：四个 NavController 里，当前那个若停在根路由
    // 则显示底栏；进了二级页（栈深 > 1）就隐藏。
    val currentNav = navControllers.getOrNull(pagerState.currentPage)
    val backStackEntry by (currentNav?.currentBackStackEntryAsState() ?: remember { androidx.compose.runtime.mutableStateOf(null) })
    val destination = backStackEntry?.destination
    val showTabBar = destination == null || (
        destination.hasRoute(RouteHome::class) ||
            destination.hasRoute(RouteApi::class) ||
            destination.hasRoute(RouteRepl::class) ||
            destination.hasRoute(RouteSettings::class)
        )

    Box(Modifier.fillMaxSize()) {
        // 滚动限位：只在底栏真的显示时给滚动容器追加高度（二级页时为 0）。
        CompositionLocalProvider(
            LocalBottomBarInset provides if (showTabBar) {
                TAB_BAR_HEIGHT + barBottomPadding
            } else {
                0.dp
            },
        ) {
            Box(Modifier.fillMaxSize()) {
                // 四个 Tab 根页放进 pager：切 Tab 是整屏水平平移，不走 NavHost 转场。
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(backdrop),
                ) { page ->
                    // 每个 page 用自己的 NavController（结构 A：独立返回栈）。
                    AppNavHost(
                        navController = navControllers[page],
                        startDestination = when (page) {
                            0 -> RouteHome
                            1 -> RouteApi
                            2 -> RouteRepl
                            else -> RouteSettings
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                AnimatedVisibility(
                    visible = showTabBar,
                    enter = fadeIn(tween(durationMillis = 180)),
                    exit = fadeOut(tween(durationMillis = 180)),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    LiquidGlassTabBar(
                        items = tabs,
                        selectedIndex = selectedIndex,
                        onSelect = { index ->
                            // 老挂戏老叟同款：animateScrollToPage 触发平移动画。
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        backdrop = backdrop,
                        modifier = Modifier.padding(bottom = barBottomPadding),
                    )
                }
            }
        }
    }
}

/**
 * 悬浮底栏的**滚动限位**高度（与 `LiquidGlassTabBar` 内部胶囊的 `.height(56.dp)` 一致）。
 *
 * 单独提出来是因为它现在有两个消费方：底栏自己，以及 [LocalBottomBarInset]
 * 的计算。硬编码两处迟早会漂移。
 *
 * ## 语义纪律（2026-09-25 修正）
 *
 * 这个值**不是**内容区的 `padding`，而是**滚动容器的底部限位**：
 *
 *  - 内容区 `PaddingValues(bottom =)` **不抬高**（只吃 navigationBars 手势条），
 *    让内容能一直滑到底栏**下面** —— 底栏是浮层，内容从其下方穿过时
 *    玻璃才有东西可折射，这是液态玻璃观感成立的前提；
 *  - 只把**滚动范围终点**（`LazyColumn.contentPadding.bottom` / Column 底部
 *    spacer）抬高这个值，保证最后一项能滚到「视觉上不被底栏遮挡」的位置。
 *
 * 此前把它加进内容区 padding，等于把整个页面顶上去，底栏下面永远空着，
 * 折射无从谈起 —— 那是明确的错误。
 */
private val TAB_BAR_HEIGHT = 56.dp
