package cn.apixiaoyuan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.apixiaoyuan.app.core.design.component.LocalBottomBarInset
import cn.apixiaoyuan.app.core.design.glass.LiquidGlassTabBar
import cn.apixiaoyuan.app.core.design.glass.TabBarMode
import cn.apixiaoyuan.app.core.design.glass.TabItem
import cn.apixiaoyuan.app.core.design.theme.ReverseOldGuyTheme
import cn.apixiaoyuan.app.core.design.theme.ThemePrefs
import cn.apixiaoyuan.app.core.navigation.AppNavHost
import cn.apixiaoyuan.app.core.navigation.RouteHome
import cn.apixiaoyuan.app.core.navigation.rememberAppNavController
import cn.apixiaoyuan.app.core.totp.TotpGate
import cn.apixiaoyuan.app.core.totp.TotpGateDialog
import cn.apixiaoyuan.app.feature.api.ApiScreen
import cn.apixiaoyuan.app.feature.home.HomeScreen
import cn.apixiaoyuan.app.feature.repl.ReplScreen
import cn.apixiaoyuan.app.feature.settings.SettingsScreen
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
 * 应用外壳 —— **单一 NavDisplay + 根 entry 放 pager + 底栏在根 entry 内**（2026-09-26 定稿）。
 *
 * ```
 * AppNavHost（唯一 NavDisplay，负责所有二级页转场）
 *   └ entry<RouteHome> → MainPager
 *        ├ Box（.layerBackdrop）→ HorizontalPager〔首页/接口/请求台/设置〕
 *        └ LiquidGlassTabBar（悬浮底栏，**在 entry 内**）
 * ```
 *
 * ## 为什么底栏必须放在「根 entry 内」（用户 2026-09-26 指出）
 *
 * 参考项目 `cn.nizou.sxd/ui/MainPagerScreen.kt` 的 `FloatingBottomBar` 是
 * `MainPager` 的**兄弟节点**，而 `MainPager` 本身就是 `entry<MainRoute.Main>`
 * 的内容 —— 也即**底栏是根 entry 内容的一部分**。
 *
 * 这样做的后果正是用户要的转场观感：底栏属于**被覆盖层**，二级页压栈时
 * `NavDisplay` 的转场会**统一带动它**（与 pager 内容同一套 translationX / alpha），
 * 而不是"底栏自己在动"。
 *
 * 此前那版把底栏放在 `NavDisplay` **外面**、用 `zIndex` + 手动 `coveredProgress`
 * 模拟视差 —— 那是错的：时序与转场不同步、视差基准也按自身宽度算而非整层。
 * 已按参考项目结构改正（底栏进入 entry，手动模拟全部删除）。
 *
 * 底栏「不消失，被盖住」的语义因此**免费获得**：二级页不透明，压栈后自然盖住它。
 *
 * ## 为什么不是「四个 Tab 各挂一个导航容器」
 *
 * miuix 的 `NavDisplay` 内部**自己注册预测性返回**
 * （`PredictiveBackHandlerWithSessions(enabled = backStack.size > 1)`）——
 * 四个常驻 NavDisplay 会四个都注册、互相抢返回手势。所以只用一个。
 *
 * 代价（如实记录）：各 Tab 不再各持独立返回栈；在「设置」里进了二级页，
 * 切到「首页」再切回来，那一层仍在栈里 —— 这正是参考项目的行为。
 *
 * ## 玻璃链路
 *
 * [rememberLayerBackdrop] 建背景录制层 → pager 内容层挂 `.layerBackdrop()` 写入 →
 * [LiquidGlassTabBar] 在 `drawBackdrop` 里 `vibrancy() -> blur() -> lens()` 折射。
 * 底栏**不做内容底部留白限位**（内容可从玻璃下方穿过，才有东西可折射）。
 */
@Composable
private fun AppShell() {
    // TOTP 门禁：未通过验证时不渲染主界面（组合级拦截，不是遮罩 ——
    // 遮罩能被返回键/手势绕过）。
    var totpPassed by remember { mutableStateOf(TotpGate.isVerified()) }

    if (!totpPassed) {
        TotpGateDialog(onPassed = { totpPassed = true })
        return
    }

    val tabs = remember {
        listOf(
            TabItem("首页", "Home"),
            TabItem("接口", "Api"),
            TabItem("请求台", "Terminal"),
            TabItem("设置", "Settings"),
        )
    }

    // 唯一导航器（单一 NavDisplay 的返回栈）。
    val navController = rememberAppNavController(RouteHome)

    // 滚动限位常驻：底栏在根 entry 内，「被盖住」而非消失；
    // 二级页同样是全屏滚动页，内容同样该滚到玻璃上方为止。
    val barBottomPadding = 12.dp +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    CompositionLocalProvider(
        LocalBottomBarInset provides TAB_BAR_HEIGHT + barBottomPadding,
    ) {
        AppNavHost(
            navController = navController,
            root = {
                MainPager(
                    tabs = tabs,
                    barBottomPadding = barBottomPadding,
                    navController = navController,
                )
            },
        )
    }
}

/**
 * 根页内容：四个 Tab 的 [HorizontalPager] + 悬浮液态玻璃底栏。
 *
 * 两者是**兄弟节点**（照参考项目 `MainPager` 结构）：pager 负责内容与 backdrop 录制，
 * 底栏浮在它之上做折射。切 Tab 走 `animateScrollToPage`（整屏水平平移、带惯性吸附），
 * **不走任何转场** —— 这是「老挂戏老叟同款平移动画」的真身。
 */
@Composable
private fun MainPager(
    tabs: List<TabItem>,
    barBottomPadding: Dp,
    navController: cn.apixiaoyuan.app.core.navigation.AppNavController,
) {
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()

    Box(Modifier.fillMaxSize()) {
        // 内容层写入 backdrop，供底栏采样做 LiquidGlass 折射。
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
        ) { page ->
            when (page) {
                0 -> HomeScreen(navController)
                1 -> ApiScreen(navController)
                2 -> ReplScreen(navController)
                else -> SettingsScreen(navController)
            }
        }

        LiquidGlassTabBar(
            items = tabs,
            // 选中态用 targetPage：滑动过程中指示器立刻指向目标页，不迟滞。
            selectedIndex = pagerState.targetPage,
            onSelect = { index ->
                // 老挂戏老叟同款：animateScrollToPage 触发平移动画。
                scope.launch { pagerState.animateScrollToPage(index) }
            },
            backdrop = backdrop,
            // 底栏三态来自设置页（液态玻璃 / 毛玻璃 / 纯色）。
            mode = when (ThemePrefs.bottomBarMode) {
                ThemePrefs.BottomBarMode.LIQUID_GLASS -> TabBarMode.LiquidGlass
                ThemePrefs.BottomBarMode.FROSTED -> TabBarMode.Blur
                ThemePrefs.BottomBarMode.SOLID -> TabBarMode.None
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = barBottomPadding),
        )
    }
}

/**
 * 悬浮底栏的**滚动限位**高度（与 `LiquidGlassTabBar` 胶囊的 56dp 一致）。
 *
 * ## 语义纪律（2026-09-25 修正，务必保持）
 *
 * 这个值**不是**内容区的 `padding`，而是**滚动容器的底部限位**：
 *
 *  - 内容区 `PaddingValues(bottom =)` **不抬高**（只吃 navigationBars 手势条），
 *    让内容能一直滑到底栏**下面** —— 底栏是浮层，内容从其下方穿过时
 *    玻璃才有东西可折射，这是液态玻璃观感成立的前提；
 *  - 只把**滚动范围终点**（`LazyColumn.contentPadding.bottom` / Column 底部
 *    spacer）抬高这个值，保证最后一项能滚到「视觉上不被底栏遮挡」的位置。
 *
 * 此前把它加进内容区 padding，等于把整个页面顶上去、底栏下面永远空着 ——
 * 那是明确的错误。
 */
private val TAB_BAR_HEIGHT: Dp = 56.dp