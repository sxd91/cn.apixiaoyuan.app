package cn.apixiaoyuan.app.core.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import cn.apixiaoyuan.app.core.design.theme.PageTransitionPrefs
import cn.apixiaoyuan.app.feature.api.ApiScreen
import cn.apixiaoyuan.app.feature.exercise.ExamScreen
import cn.apixiaoyuan.app.feature.exercise.ExerciseScreen
import cn.apixiaoyuan.app.feature.home.HomeScreen
import cn.apixiaoyuan.app.feature.login.LoginScreen
import cn.apixiaoyuan.app.feature.oldsimian.OldSimianScreen
import cn.apixiaoyuan.app.feature.oldsimian.ScorePumpScreen
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
 * 「老挂戏老叟」功能页路由。
 *
 * 从设置页的入口进入，是二级页（不带参）。
 * 页面自身用本项目 AppScaffold + miuix 组件搭建，与参考项目 cn.nizou.sxd
 * 的页面无任何复用关系。
 */
@Serializable
object RouteOldSimian

/**
 * 「自定义分数（刷分）」二级页路由。
 *
 * 从「老挂戏老叟」页的「自定义分数」行进入。单独成页而不是塞进
 * 「老挂戏老叟」页：刷分页有一组输入框 + 运行状态 + 起停按钮，塞进设置页
 * 会让那一页从「开关列表」变成「带状态机的表单」，两者节奏完全不同。
 */
@Serializable
object RouteScorePump


/**
 * 全应用导航图。
 *
 * 十一个入口中，四项占据 LiquidGlassTabBar 底栏位置：
 *  - 首页   -> RouteHome
 *  - 接口   -> RouteApi
 *  - 请求台 -> RouteRepl
 *  - 设置   -> RouteSettings
 * 其余七项（样本库、PK、练习、练习答题页、登录、老挂戏老叟、刷分页）是二级页，
 * 从首页快捷入口 / 设置页进入，不占底栏位置，通过 navController.navigate 直达。
 *
 * **底栏只在四个 Tab 根页显示**（判据在 `MainActivity.AppShell`，用
 * `NavDestination.hasRoute` 逐个比对）—— 二级页进入后底栏淡出，不再悬在
 * 页面底部遮挡内容。
 *
 * 四条转场（enter / exit / popEnter / popExit）由
 * [cn.apixiaoyuan.app.core.design.theme.PageTransitionPrefs.animation] 驱动，
 * 可选 Miuix（默认）/ AOSP，见 [pageTransitionsFor]。
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    // 过渡动画随设置实时切换：PageTransitionPrefs.animation 是 Compose 可观察状态，
    // 设置页改一下这里就重组，NavHost 用新的转场 —— 不需要重启 Activity。
    val transitions = pageTransitionsFor(PageTransitionPrefs.animation)
    NavHost(
        navController = navController,
        startDestination = RouteHome,
        modifier = modifier.fillMaxSize(),
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
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
        composable<RouteOldSimian> { OldSimianScreen(navController) }
        composable<RouteScorePump> { ScorePumpScreen(navController) }
    }
}
