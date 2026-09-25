package cn.apixiaoyuan.app.core.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import cn.apixiaoyuan.app.core.design.theme.PageTransitionAnimation

/**
 * 二级页面的过渡动画。
 *
 * ## 为什么不用 miuix-nav
 *
 * 参考项目 cn.nizou.sxd 的转场是 miuix-nav 的 `NavDisplay(transition = …)` 直接给的：
 *  - `NavTransitions.MiuixDefault` —— 进场页整屏从右侧滑入（`translationX: +width -> 0`），
 *    被覆盖页向左视差 **1/4 宽度**并 `alpha = 1 - 0.1 * progress`；
 *  - 自实现的 `AospNavTransition` —— 进出只做 **96dp** 的 `translationX` 漂移 + 淡入淡出
 *    （`ClassicActivityOpen` / `ClassicActivityClose`），并配一条自定义缓动
 *    `FastOutExtraSlowIn`（450ms）。
 *
 * 本项目走的是 **androidx `NavHost`**，不是 miuix-nav 的 `NavDisplay` ——
 * 两者的转场模型完全不同（前者是 `AnimatedContent` 的 enter/exit/popEnter/popExit
 * 四条 `EnterTransition`/`ExitTransition`，后者是「单一 float 深度 → graphicsLayer」）。
 * miuix-nav 的 `NavTransition` 对象在本项目里**无法直接挂到 `NavHost` 上**，
 * 所以这里用 compose animation 的原始 API **复刻同样的观感**，而不是硬塞 miuix-nav。
 *
 * ## 两套动画的对应关系
 *
 * | 阶段 | MIUIX（默认） | AOSP |
 * |---|---|---|
 * | push 进场页 | 从右整屏滑入 | 从右 1/4 屏滑入 + 淡入 |
 * | push 被覆盖页 | 向左 1/4 屏 + 降到 0.9 透明 | 不动（保持原样） |
 * | pop 回退页 | 从 -1/4 屏滑回 + 0.9 → 1 | 不动 |
 * | pop 离开页 | 向右整屏滑出 | 向右 1/4 屏滑出 + 淡出 |
 *
 * 时长统一 450ms，与参考项目 `ClassicActivityMotion` 的 450ms 同值。
 *
 * **一处刻意的偏差**：参考项目 AOSP 的漂移是**绝对 96dp**，而这里用
 * **容器宽度的 1/4**（≈96dp @ 384dp 宽屏）—— 因为 `AnimatedContentTransitionScope`
 * 不暴露 `Density`，拿不到 dp→px 的换算。两者在常见手机宽度上量级一致，
 * 且按比例做在大屏 / 折叠屏上更自然。这一点如实记录，不假装是同一个数。
 */
private const val DURATION_MS = 450

/** miuix 风格：整屏滑入 + 被覆盖页 1/4 视差。 */
private val miuixEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(animationSpec = tween(DURATION_MS)) { fullWidth -> fullWidth }
}

private val miuixExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(animationSpec = tween(DURATION_MS)) { fullWidth -> -fullWidth / 4 } +
        fadeOut(animationSpec = tween(DURATION_MS), targetAlpha = 0.9f)
}

private val miuixPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(animationSpec = tween(DURATION_MS)) { fullWidth -> -fullWidth / 4 } +
        fadeIn(animationSpec = tween(DURATION_MS), initialAlpha = 0.9f)
}

private val miuixPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(animationSpec = tween(DURATION_MS)) { fullWidth -> fullWidth }
}

/** AOSP 风格：1/4 屏漂移 + 淡入淡出，被覆盖页不动。 */
private val aospEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(animationSpec = tween(DURATION_MS)) { fullWidth -> fullWidth / 4 } +
        fadeIn(animationSpec = tween(DURATION_MS))
}

private val aospExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    ExitTransition.None
}

private val aospPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    EnterTransition.None
}

private val aospPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(animationSpec = tween(DURATION_MS)) { fullWidth -> fullWidth / 4 } +
        fadeOut(animationSpec = tween(DURATION_MS))
}

/**
 * 按当前配置取出四条转场。
 *
 * 返回值是一个 `Quad`，因为 `NavHost` 的四个参数各自独立，无法用一个对象一次给全。
 *
 * @param animation 当前选择的动画引擎（[cn.apixiaoyuan.app.core.design.theme.PageTransitionPrefs.animation]）
 */
data class PageTransitions(
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
)

/** 按枚举取对应的一套转场。 */
fun pageTransitionsFor(animation: PageTransitionAnimation): PageTransitions = when (animation) {
    PageTransitionAnimation.MIUIX -> PageTransitions(miuixEnter, miuixExit, miuixPopEnter, miuixPopExit)
    PageTransitionAnimation.AOSP -> PageTransitions(aospEnter, aospExit, aospPopEnter, aospPopExit)
}