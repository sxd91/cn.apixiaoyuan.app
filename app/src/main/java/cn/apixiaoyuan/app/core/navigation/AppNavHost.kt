package cn.apixiaoyuan.app.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

// ---- 路由定义（@Serializable object，供 navigation-compose 类型安全导航）----

@Serializable
object RouteHome

@Serializable
object RouteApk

@Serializable
object RouteApi

@Serializable
object RouteRepl

@Serializable
object RouteSamples

@Serializable
object RoutePk

@Serializable
object RouteExercise

@Serializable
object RouteLogin

@Serializable
object RouteSettings

/**
 * 全应用导航图。
 *
 * 九个入口与 LiquidGlassTabBar 的五项主 Tab 对应关系：
 *  - 首页   -> RouteHome
 *  - APK    -> RouteApk
 *  - 接口   -> RouteApi
 *  - 请求台 -> RouteRepl
 *  - 设置   -> RouteSettings
 * 其余四项（样本库、PK、练习、登录）从首页快捷入口进入，
 * 不占底栏位置，通过 navController.navigate 直达。
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = RouteHome,
        modifier = modifier.fillMaxSize(),
    ) {
        composable<RouteHome> { PlaceholderPage("首页") }
        composable<RouteApk> { PlaceholderPage("APK 分析") }
        composable<RouteApi> { PlaceholderPage("接口浏览器") }
        composable<RouteRepl> { PlaceholderPage("协议请求台") }
        composable<RouteSamples> { PlaceholderPage("样本库") }
        composable<RoutePk> { PlaceholderPage("PK 模块") }
        composable<RouteExercise> { PlaceholderPage("练习模块") }
        composable<RouteLogin> { PlaceholderPage("登录注册") }
        composable<RouteSettings> { PlaceholderPage("设置") }
    }
}

/** 各功能页真实实现落盘前的统一占位，避免 NavHost 出现空 composable 分支。 */
@Composable
private fun PlaceholderPage(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
