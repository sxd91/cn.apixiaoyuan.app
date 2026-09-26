package cn.apixiaoyuan.app.core.navigation

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * 全应用路由表。
 *
 * ## 为什么从 androidx `@Serializable object` 改成 miuix-nav 的 `NavKey`
 *
 * 2026-09-26 用户指出：本项目的二级页转场**不是**「老挂戏老叟的过渡动画」。
 * 逐行核对参考项目 `cn.nizou.sxd/ui/MainPagerScreen.kt` 后确认属实 ——
 * 它用的是 **miuix-nav**：
 *
 * ```kotlin
 * val backStack = rememberNavBackStack<MainRoute>(MainRoute.Main)
 * val navigator = remember(backStack) { Navigator(backStack) }
 * NavDisplay(
 *     backStack = backStack,
 *     onBack = { if (navigator.backStackSize() <= 1) backAtRoot() else navigator.pop() },
 *     transition = weKitNavTransition(ThemeSettings.pageTransitionAnimation),
 *     effects = rememberM3NavEffects(),
 * ) { entry<MainRoute.General>(swipeDismiss = NavSwipeDirection.LeftToRight) { … } }
 * ```
 *
 * 而本项目此前用的是 androidx `NavHost` + compose `slideInHorizontally` 整屏滑 ——
 * **两套完全不同的转场模型**（miuix 是「单一 float 深度 → graphicsLayer」，
 * androidx 是 `AnimatedContent` 的四条 enter/exit）。miuix 的
 * `NavTransitions.MiuixDefault` 才是「老挂戏老叟同款」的真实观感：
 * 进场页整屏从右侧滑入、被覆盖页向左视差 **1/4 宽**且 `alpha = 1 - 0.1 * progress`，
 * 另有圆角裁切（`NavDisplayEffects`）与预测性返回手势。
 *
 * ## 结构
 *
 * 一个 `sealed interface` + 若干 `@Serializable` 子类型；`NavKey` 是纯标签，
 * `@Serializable` 是 `rememberNavBackStack` 持久化返回栈的硬要求
 * （见 `miuix-nav` 的 `NavKey` KDoc）。
 */
@Serializable
sealed interface Route : NavKey

/** 首页（Tab 根页）。 */
@Serializable
data object RouteHome : Route

/** 接口（Tab 根页）。 */
@Serializable
data object RouteApi : Route

/** 请求台（Tab 根页）。 */
@Serializable
data object RouteRepl : Route

/** 设置（Tab 根页）。 */
@Serializable
data object RouteSettings : Route

/** 样本库（二级页）。 */
@Serializable
data object RouteSamples : Route

/** 口算 PK（二级页，H5 容器）。 */
@Serializable
data object RoutePk : Route

/** 练习（二级页）。 */
@Serializable
data object RouteExercise : Route

/** 登录（二级页）。 */
@Serializable
data object RouteLogin : Route

/** 「老挂戏老叟」功能页（二级页）。 */
@Serializable
data object RouteOldSimian : Route

/** 「自定义分数（刷分）」二级页。 */
@Serializable
data object RouteScorePump : Route

/** 「刷 PK 对局」二级页（纯 API 刷局：出题→弧线笔迹→提交）。 */
@Serializable
data object RoutePkGrind : Route

/** 账号页（宝贝学习账号切换 + 改密码）。 */
@Serializable
data object RouteAccount : Route

/**
 * 答题页路由（带参）。
 *
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
) : Route