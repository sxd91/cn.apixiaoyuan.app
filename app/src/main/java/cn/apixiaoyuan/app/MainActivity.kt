package cn.apixiaoyuan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cn.apixiaoyuan.app.core.design.component.LocalBottomBarInset
import cn.apixiaoyuan.app.core.design.glass.LiquidGlassTabBar
import cn.apixiaoyuan.app.core.design.glass.TabBarMode
import cn.apixiaoyuan.app.core.design.glass.TabItem
import cn.apixiaoyuan.app.core.design.theme.ReverseOldGuyTheme
import cn.apixiaoyuan.app.core.design.theme.ThemePrefs
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

                // ==================== 悬浮底栏 ====================
                //
                // 进入二级页时的行为（用户明确要求）：底栏**不是淡出消失**，
                // 而是被二级页**盖住**，并跟随转场一起**向左平移 + 压暗** ——
                // 视觉上它仍在，只是退到了下一层，返回时随页面一起回来。
                //
                // 三件事必须同时做到，缺一就变回「两层皮」：
                //  1. **z 序下沉**：底栏原本浮在内容之上；二级页期间必须改到底下，
                //     否则它会一直盖在二级页上面（那才是真正的 bug）。
                //     用 zIndex 而不是移除/淡出 —— 移除就没有「被覆盖」的观感。
                //  2. **同步位移 + 压暗**：与二级页转场同一时长（450ms）、同向
                //     （被覆盖层向左 1/4），让底栏看起来是「被推走」的那层。
                //  3. **不可点击**：被盖住后不该还能点（否则点到看不见的 Tab）。
                //     用 `graphicsLayer` 之外再叠一个 consume 点击的拦截层不优雅，
                //     这里靠 zIndex 下沉后二级页自然吃掉触摸即可 ——
                //     二级页是不透明且 fillMaxSize 的，触摸不会穿透到底栏。
                val coveredProgress by animateFloatAsState(
                    targetValue = if (showTabBar) 0f else 1f,
                    animationSpec = tween(durationMillis = COVER_ANIM_MS),
                    label = "tab_bar_covered",
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // (1) z 序：根页时浮在上层（1f），被覆盖时沉到下层（-1f）。
                        .zIndex(if (showTabBar) 1f else -1f)
                        // (2) 与转场同步的位移 + 压暗。
                        .graphicsLayer {
                            translationX = -size.width * COVER_SHIFT_RATIO * coveredProgress
                            alpha = 1f - 0.1f * coveredProgress
                        },
                ) {
                    LiquidGlassTabBar(
                        items = tabs,
                        selectedIndex = selectedIndex,
                        onSelect = { index ->
                            // 老挂戏老叟同款：animateScrollToPage 触发平移动画。
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        backdrop = backdrop,
                        // 底栏效果三态来自设置页（液态玻璃 / 毛玻璃 / 纯色）。
                        // 默认液态玻璃；低端机可降级到毛玻璃或纯色省掉背景采样开销。
                        mode = when (ThemePrefs.bottomBarMode) {
                            ThemePrefs.BottomBarMode.LIQUID_GLASS -> TabBarMode.LiquidGlass
                            ThemePrefs.BottomBarMode.FROSTED -> TabBarMode.Blur
                            ThemePrefs.BottomBarMode.SOLID -> TabBarMode.None
                        },
                        modifier = Modifier.padding(bottom = barBottomPadding),
                    )
                    // 压暗层：只盖在底栏自身范围内（matchParentSize），
                    // 随 coveredProgress 从 0 到 COVER_DIM_MAX。
                    // 用半透明黑而不是改 alpha —— 改 alpha 会让玻璃的折射内容
                    // 一起变淡（看着像消失），压暗才是「退到暗处」的观感。
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                color = Color.Black.copy(alpha = COVER_DIM_MAX * coveredProgress),
                                shape = RoundedCornerShape(28.dp),
                            ),
                    )
                }
            }
        }
    }
}

/**
 * 二级页覆盖动画的时长。
 *
 * 与 `PageTransitions.DURATION_MS` 同值（450ms）—— 底栏的平移/压暗必须和
 * 二级页的滑入**同一节奏**，否则会看到「页面滑进来了、底栏还在自己动」的
 * 两层皮观感。
 */
private const val COVER_ANIM_MS = 450

/**
 * 被覆盖时底栏的视差位移比例（占自身宽度）。
 *
 * 取 1/4 与 `PageTransitions.miuixExit`（被覆盖页向左 1/4 屏 + alpha 0.9）
 * 同款 —— 底栏此时属于「被覆盖层」，应与被覆盖的页面同步位移。
 */
private const val COVER_SHIFT_RATIO = 0.25f

/** 被覆盖时叠加的暗色最大不透明度（progress=1 时）。 */
private const val COVER_DIM_MAX = 0.35f

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
