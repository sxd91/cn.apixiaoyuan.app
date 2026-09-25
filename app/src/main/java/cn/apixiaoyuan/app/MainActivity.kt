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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cn.apixiaoyuan.app.core.design.glass.LiquidGlassTabBar
import cn.apixiaoyuan.app.core.design.glass.TabItem
import cn.apixiaoyuan.app.core.design.theme.ReverseOldGuyTheme
import cn.apixiaoyuan.app.core.navigation.AppNavHost
import cn.apixiaoyuan.app.core.navigation.RouteApi
import cn.apixiaoyuan.app.core.navigation.RouteHome
import cn.apixiaoyuan.app.core.navigation.RouteRepl
import cn.apixiaoyuan.app.core.navigation.RouteSettings
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
 * 应用外壳：内容区交给 AppNavHost，底部叠液态玻璃 Tab 栏。
 *
 * 玻璃链路：
 *  - [rememberLayerBackdrop] 创建背景录制层；
 *  - 内容层 [AppNavHost] 挂 `.layerBackdrop(backdrop)` 把页面内容录成可采样纹理；
 *  - [LiquidGlassTabBar] 消费该 backdrop，在 `drawBackdrop` 里走
 *    `vibrancy() -> blur() -> lens()` 做真实折射。
 *
 * 五个主 Tab 与路由的映射固定，索引与 items 顺序一一对应；
 * 切换时先 navigate 再更新选中态，并用 launchSingleTop 避免重复压栈。
 */
@Composable
private fun AppShell() {
    val navController = rememberNavController()
    var selected by remember { mutableIntStateOf(0) }

    val tabs = remember {
        listOf(
            TabItem("首页", "Home"),
            TabItem("接口", "Api"),
            TabItem("请求台", "Terminal"),
            TabItem("设置", "Settings"),
        )
    }

    val destinations = remember {
        listOf<Any>(RouteHome, RouteApi, RouteRepl, RouteSettings)
    }

    // 背景录制层：内容层写入，底栏采样做折射。
    val backdrop = rememberLayerBackdrop()

    // 悬浮底栏底部留白：12dp 视觉呼吸 + 手势条（navigationBars）。
    // 对齐 cn.nizou.sxd 的 MainPagerScreen：
    //   barBottomPadding = 12.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // 缺这段时底栏贴屏幕最底，lens() 的圆角 SDF 在边缘被裁，折射形状不完整。
    val barBottomPadding = 12.dp +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // 当前路由：只有四个 Tab 根页才显示悬浮底栏。
    //
    // 修复的问题：此前底栏无条件叠加在内容之上，进入二级页（练习答题页 / 登录 /
    // 老挂戏老叟 / PK / 样本库）后底栏仍然悬在页面底部，既遮挡内容又暗示「还能切 Tab」，
    // 是明确的交互错误。
    //
    // 判据用 `hasRoute` 而不是字符串比较：本项目走的是类型安全路由
    // （`@Serializable object RouteHome` 等），`hasRoute(RouteHome::class)` 是
    // navigation-compose 为类型安全路由提供的官方判据，不会因路由重命名而失效。
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showTabBar = destination != null && (
        destination.hasRoute(RouteHome::class) ||
            destination.hasRoute(RouteApi::class) ||
            destination.hasRoute(RouteRepl::class) ||
            destination.hasRoute(RouteSettings::class)
        )

    Box(Modifier.fillMaxSize()) {
        AppNavHost(
            navController = navController,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
        )

        // 底栏随二级页的出现/消失淡入淡出，而不是硬切 ——
        // 硬切会让玻璃层在一帧内突然消失，视觉上像闪烁。
        AnimatedVisibility(
            visible = showTabBar,
            enter = fadeIn(tween(durationMillis = 180)),
            exit = fadeOut(tween(durationMillis = 180)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            LiquidGlassTabBar(
                items = tabs,
                selectedIndex = selected,
                onSelect = { index ->
                    if (index != selected) {
                        selected = index
                        navController.navigate(destinations[index]) {
                            popUpTo(RouteHome) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                },
                backdrop = backdrop,
                modifier = Modifier.padding(bottom = barBottomPadding),
            )
        }
    }
}
