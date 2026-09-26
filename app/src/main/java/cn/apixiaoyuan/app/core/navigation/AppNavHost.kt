package cn.apixiaoyuan.app.core.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import cn.apixiaoyuan.app.core.design.theme.PageTransitionPrefs
import cn.apixiaoyuan.app.core.navigation.transition.appNavTransition
import cn.apixiaoyuan.app.core.navigation.transition.rememberAppNavEffects
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection

/**
 * 应用导航宿主 —— **miuix-nav 的 `NavDisplay`**（不是 androidx `NavHost`）。
 *
 * ## 这是一个「单一 NavDisplay」
 *
 * 全应用只有一个返回栈、一个 `NavDisplay`。原因见
 * [cn.apixiaoyuan.app.MainActivity] 的 KDoc：miuix 的 `NavDisplay` 内部自己注册
 * 预测性返回（`PredictiveBackHandlerWithSessions(enabled = backStack.size > 1)`），
 * 四个 Tab 各挂一个会互相抢手势。
 *
 * ## 根 entry 由调用方提供
 *
 * 四个 Tab 根路由**都必须注册 entry**（栈里出现的 key 必须有渲染者），
 * 但真正占屏幕的是 `RouteHome` 这个「根 entry」——里面放 `HorizontalPager`，
 * 由 pager 负责切 Tab（同层平移，不压栈）。`RouteApi` / `RouteRepl` /
 * `RouteSettings` 的 entry 只在「栈里恰好是它们」时被渲染（正常操作不会发生，
 * 因为切 Tab 走 pager；保留它们是为了栈状态被恢复时也安全）。
 *
 * @param navController 唯一的导航器（见 [rememberAppNavController]）。
 * @param root 根内容（四个 Tab 的 pager）。
 */
@Composable
fun AppNavHost(
    navController: AppNavController,
    root: @Composable () -> Unit,
) {
    // transition / effects 都随设置项实时切换：两者内部读的是 Compose 可观察
    // 状态（PageTransitionPrefs.animation），设置页改一下这里就重组 —— 不需要重启。
    val transition = appNavTransition(PageTransitionPrefs.animation)
    val effects = rememberAppNavEffects()
    val backdropColor = MaterialTheme.colorScheme.surfaceContainer

    CompositionLocalProvider(LocalAppNavController provides navController) {
        NavDisplay(
            backStack = navController.backStack,
            onBack = { navController.popBackStack() },
            transition = transition,
            effects = effects,
            modifier = Modifier
                .fillMaxSize()
                // 转场层底色：被覆盖页与进场页之间不能露黑（圆角裁切时会看到
                // 边角），用主题的 surfaceContainer —— 与二级页 Scaffold 同色。
                .background(backdropColor),
        ) {
            entry<RouteHome> { root() }
            entry<RouteApi> { root() }
            entry<RouteRepl> { root() }
            entry<RouteSettings> { root() }
            entry<RouteSamples>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                cn.apixiaoyuan.app.feature.samples.SamplesScreen(navController)
            }
            entry<RoutePk>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                cn.apixiaoyuan.app.feature.pk.PkScreen(navController)
            }
            entry<RouteExercise>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                cn.apixiaoyuan.app.feature.exercise.ExerciseScreen(navController)
            }
            entry<RouteExam>(swipeDismiss = NavSwipeDirection.LeftToRight) { key ->
                cn.apixiaoyuan.app.feature.exercise.ExamScreen(
                    navController = navController,
                    keypointId = key.keypointId,
                    limit = key.limit,
                    title = key.title,
                )
            }
            entry<RouteLogin>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                cn.apixiaoyuan.app.feature.login.LoginScreen(navController)
            }
            entry<RouteOldSimian>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                cn.apixiaoyuan.app.feature.oldsimian.OldSimianScreen(navController)
            }
            entry<RouteScorePump>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                cn.apixiaoyuan.app.feature.oldsimian.ScorePumpScreen(navController)
            }
            entry<RouteAccount>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                cn.apixiaoyuan.app.feature.account.AccountScreen(navController)
            }
        }
    }
}