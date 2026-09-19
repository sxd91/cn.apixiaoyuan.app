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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import cn.apixiaoyuan.app.core.design.glass.LiquidGlassTabBar
import cn.apixiaoyuan.app.core.design.glass.TabItem
import cn.apixiaoyuan.app.core.design.theme.ReverseOldGuyTheme
import cn.apixiaoyuan.app.core.navigation.AppNavHost
import cn.apixiaoyuan.app.core.navigation.RouteApi
import cn.apixiaoyuan.app.core.navigation.RouteApk
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
            TabItem("APK", "Android"),
            TabItem("接口", "Api"),
            TabItem("请求台", "Terminal"),
            TabItem("设置", "Settings"),
        )
    }

    val destinations = remember {
        listOf<Any>(RouteHome, RouteApk, RouteApi, RouteRepl, RouteSettings)
    }

    // 背景录制层：内容层写入，底栏采样做折射。
    val backdrop = rememberLayerBackdrop()

    // 悬浮底栏底部留白：12dp 视觉呼吸 + 手势条（navigationBars）。
    // 对齐 cn.nizou.sxd 的 MainPagerScreen：
    //   barBottomPadding = 12.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // 缺这段时底栏贴屏幕最底，lens() 的圆角 SDF 在边缘被裁，折射形状不完整。
    val barBottomPadding = 12.dp +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        AppNavHost(
            navController = navController,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
        )

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
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = barBottomPadding),
        )
    }
}
