package cn.apixiaoyuan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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

    Box(Modifier.fillMaxSize()) {
        AppNavHost(
            navController = navController,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 96.dp),
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
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}