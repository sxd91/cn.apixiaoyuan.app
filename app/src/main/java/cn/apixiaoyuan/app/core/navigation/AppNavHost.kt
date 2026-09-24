package cn.apixiaoyuan.app.core.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import cn.apixiaoyuan.app.feature.api.ApiScreen
import cn.apixiaoyuan.app.feature.exercise.ExamScreen
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
object RouteApi

@Serializable
object RouteRepl

@Serializable
object RouteSamples

@Serializable
object RoutePk

@Serializable
object RouteExercise

/**
 * 答题页路由。
 *
 * 类型安全路由带参（navigation-compose 2.8+ 的 `@Serializable data class`），
 * 三个参数全部来自练习页当前选择：
 *  - [keypointId] 知识点 ID
 *  - [limit]      题目数量，取自 `ExerciseType.chooseNumArray`
 *  - [title]      知识点名，仅用于顶栏展示
 */
@Serializable
data class RouteExam(
    val keypointId: Int,
    val limit: Int,
    val title: String,
)

@Serializable
object RouteLogin

@Serializable
object RouteSettings

/**
 * 全应用导航图。
 *
 * 九个入口中，四项占据 LiquidGlassTabBar 底栏位置：
 *  - 首页   -> RouteHome
 *  - 接口   -> RouteApi
 *  - 请求台 -> RouteRepl
 *  - 设置   -> RouteSettings
 * 其余五项（样本库、PK、练习、练习答题页、登录）从首页快捷入口进入，
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
        composable<RouteApi> { ApiScreen(navController) }
        composable<RouteRepl> { ReplScreen(navController) }
        composable<RouteSamples> { SamplesScreen(navController) }
        composable<RoutePk> { PkScreen(navController) }
        composable<RouteExercise> { ExerciseScreen(navController) }
        composable<RouteExam> { entry ->
            val route = entry.toRoute<RouteExam>()
            ExamScreen(
                navController = navController,
                keypointId = route.keypointId,
                limit = route.limit,
                title = route.title,
            )
        }
        composable<RouteLogin> { LoginScreen(navController) }
        composable<RouteSettings> { SettingsScreen(navController) }
    }
}
