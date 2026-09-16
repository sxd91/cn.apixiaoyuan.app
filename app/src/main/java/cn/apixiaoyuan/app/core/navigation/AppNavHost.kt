package cn.apixiaoyuan.app.core.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import cn.apixiaoyuan.app.feature.api.ApiScreen
import cn.apixiaoyuan.app.feature.apk.ApkScreen
import cn.apixiaoyuan.app.feature.exercise.ExerciseScreen
import cn.apixiaoyuan.app.feature.home.HomeScreen
import cn.apixiaoyuan.app.feature.login.LoginScreen
import cn.apixiaoyuan.app.feature.pk.PkScreen
import cn.apixiaoyuan.app.feature.repl.ReplScreen
import cn.apixiaoyuan.app.feature.samples.SamplesScreen
import cn.apixiaoyuan.app.feature.settings.SettingsScreen
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
        composable<RouteHome> { HomeScreen(navController) }
        composable<RouteApk> { ApkScreen(navController) }
        composable<RouteApi> { ApiScreen(navController) }
        composable<RouteRepl> { ReplScreen(navController) }
        composable<RouteSamples> { SamplesScreen(navController) }
        composable<RoutePk> { PkScreen(navController) }
        composable<RouteExercise> { ExerciseScreen(navController) }
        composable<RouteLogin> { LoginScreen(navController) }
        composable<RouteSettings> { SettingsScreen(navController) }
    }
}
