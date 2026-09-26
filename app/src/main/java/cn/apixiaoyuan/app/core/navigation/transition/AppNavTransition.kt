package cn.apixiaoyuan.app.core.navigation.transition

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cn.apixiaoyuan.app.core.design.theme.PageTransitionAnimation
import cn.apixiaoyuan.app.core.design.theme.PageTransitionPrefs
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavGesture
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavRole
import top.yukonga.miuix.kmp.nav.transition.NavSettle
import top.yukonga.miuix.kmp.nav.transition.NavSettlePhase
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavSwipeEdge
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.nav.transition.navDirectionalTransition
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// ---------------------------------------------------------------------------
//  缓动与几何工具（照 cn.nizou.sxd / WeKit 移植）
// ---------------------------------------------------------------------------

/**
 * 双段三次贝塞尔缓动（WeKit `FastOutExtraSlowIn`）。
 *
 * 在两段之间插入一个 (1/6, 0.4) 的结点再各自归一化 —— 单条 cubic-bezier 做不出
 * 这种「先快出、再极慢进」的曲线，AOSP 的 Activity 转场就是它。
 */
private val FastOutExtraSlowIn: Easing = run {
    val knotX = 0.166666f
    val knotY = 0.4f
    val first = CubicBezierEasing(0.05f / knotX, 0f, 0.133333f / knotX, 0.06f / knotY)
    val second = CubicBezierEasing(
        (0.208333f - knotX) / (1f - knotX),
        (0.82f - knotY) / (1f - knotY),
        (0.25f - knotX) / (1f - knotX),
        (1f - knotY) / (1f - knotY),
    )
    Easing { fraction ->
        if (fraction < knotX) {
            knotY * first.transform(fraction / knotX)
        } else {
            knotY + (1f - knotY) * second.transform((fraction - knotX) / (1f - knotX))
        }
    }
}

/** 手势跟手的缓动（长尾回正）。 */
private val BackGestureEasing: Easing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

private fun topProgress(depth: Float): Float = (1f + depth).coerceIn(0f, 1f)

private fun coverProgress(depth: Float): Float = depth.coerceIn(0f, 1f)

/**
 * 把缩放吸附到整像素。
 *
 * 不做吸附的话，圆角裁切的页面边缘在亚像素位置会随动画抖出一条细线
 * （`snapTranslationToPixelEdge` 同因）。1:1 安全，不引入视觉偏差。
 */
private fun snapScaleToPixelExtent(scale: Float, extent: Float): Float =
    if (extent > 0f) (scale * extent).roundToInt() / extent else scale

/** 把位移吸附到整像素边缘（先补上缩放造成的边缘偏移再取整）。 */
private fun snapTranslationToPixelEdge(
    translation: Float,
    scale: Float,
    extent: Float,
    pivotFraction: Float = 0.5f,
): Float {
    if (extent <= 0f) return translation
    val scaledEdgeOffset = extent * pivotFraction * (1f - scale)
    return (translation + scaledEdgeOffset).roundToInt() - scaledEdgeOffset
}

// ---------------------------------------------------------------------------
//  AOSP 风格转场（跨 Activity 观感：小幅漂移 + 淡入淡出 + 缩放）
// ---------------------------------------------------------------------------

private const val BOUNCE_STIFFNESS = 200f
private const val BOUNCE_DAMPING = 0.75f
private const val BOUNCE_MAX_KICK = 1000f
private const val BOUNCE_MIN_KICK = 120f
private const val OPEN_FADE_START = 0.12f
private const val OPEN_FADE_SPAN = 0.71f
private const val CLOSE_FADE_START = 0.21f
private const val CLOSE_FADE_SPAN = 0.74f
private const val CLASSIC_FADE_DURATION = 83f
private const val OPEN_FADE_OFFSET = 50f
private const val CLOSE_FADE_OFFSET = 35f
private const val CROSS_ACTIVITY_MIN_SCALE = 0.9f

/** 跨 Activity 转场的横向漂移量（AOSP 观感的关键：不是整屏滑，而是 96dp 位移）。 */
private val CrossActivityDrift = 96.dp

/** 缩放后的页面距屏幕边缘留白，避免圆角贴边。 */
private val CrossActivityEdgeMargin = 8.dp

private val ClassicActivityMotion = NavMotion(
    programmatic = NavSettleSpec.Tween(durationMillis = 450, easing = FastOutExtraSlowIn),
)

/**
 * 经典 Activity 打开：进场页从右侧漂移 96dp 淡入，被覆盖页保持不动。
 *
 * 与 miuix 的「整屏滑」是两种观感 —— 这才是 AOSP 的「轻推一下」。
 */
private val ClassicActivityOpen: NavTransition = navGraphicsTransition(
    motion = ClassicActivityMotion,
    scrim = { 0f },
) { scope ->
    val depth = scope.relativeDepth
    val driftPx = with(scope.density) { CrossActivityDrift.toPx() }
    if (depth <= 0f) {
        val progress = topProgress(depth)
        translationX = (1f - progress) * driftPx
        alpha = if (scope.role == NavRole.Incoming) {
            val settle = scope.settle
            if (settle != null) {
                ((settle.elapsedMillis - OPEN_FADE_OFFSET) / CLASSIC_FADE_DURATION)
                    .coerceIn(0f, 1f)
            } else {
                ((progress - OPEN_FADE_START) / OPEN_FADE_SPAN).coerceIn(0f, 1f)
            }
        } else {
            1f
        }
    } else {
        translationX = -coverProgress(depth) * driftPx
    }
}

/** 经典 Activity 关闭：离场页向右漂移 96dp 淡出。 */
private val ClassicActivityClose: NavTransition = navGraphicsTransition(
    motion = ClassicActivityMotion,
    scrim = { 0f },
) { scope ->
    val depth = scope.relativeDepth
    val driftPx = with(scope.density) { CrossActivityDrift.toPx() }
    if (depth <= 0f) {
        val progress = topProgress(depth)
        translationX = (1f - progress) * driftPx
        alpha = if (scope.role == NavRole.Outgoing) {
            val settle = scope.settle
            if (settle != null) {
                (1f - (settle.elapsedMillis - CLOSE_FADE_OFFSET) / CLASSIC_FADE_DURATION)
                    .coerceIn(0f, 1f)
            } else {
                ((progress - CLOSE_FADE_START) / CLOSE_FADE_SPAN).coerceIn(0f, 1f)
            }
        } else {
            1f
        }
    } else {
        translationX = -coverProgress(depth) * driftPx
    }
}

/**
 * 预测性返回（手势拖动）时的转场。
 *
 * 与 programmatic 的差别：页面**跟手缩放**（0.9~1.0），松手后带一点弹跳；
 * 被覆盖页在被拖出时向中心「收拢」，形成卡片式的手感。
 * 手势取消则用高刚度弹簧回弹（`NavSettleSpec.Spring(stiffness = 1500f)`）。
 */
private val CrossActivityPredictive: NavTransition = navGraphicsTransition(
    opaqueDepth = 1f,
    motion = NavMotion(
        commit = NavSettleSpec.Tween(durationMillis = 450, easing = FastOutExtraSlowIn),
        cancel = NavSettleSpec.Spring(stiffness = 1500f),
    ),
    scrim = { scope ->
        val settle = scope.settle
        val gesture = scope.gesture
        when {
            settle?.phase == NavSettlePhase.Commit ->
                (1f - settle.elapsedMillis / 450f).coerceIn(0f, 1f)

            gesture != null ->
                (scope.relativeDepth.coerceIn(0f, 1f) /
                    (1f - gesture.progress).coerceAtLeast(0.01f)).coerceIn(0f, 1f)

            else -> scope.relativeDepth.coerceIn(0f, 1f)
        }
    },
) { scope ->
    val depth = scope.relativeDepth
    val gesture = scope.gesture
    val settle = scope.settle
    val committing = settle?.phase == NavSettlePhase.Commit
    val widthPx = scope.layoutSize.width.toFloat()
    val heightPx = scope.layoutSize.height.toFloat()
    val driftPx = with(scope.density) { CrossActivityDrift.toPx() }
    val bounce = bounceScale(settle, gesture)
    val hugMax = (
        widthPx * (1f - CROSS_ACTIVITY_MIN_SCALE) / 2f -
            with(scope.density) { CrossActivityEdgeMargin.toPx() }
        ).coerceAtLeast(0f)
    val hugs = gesture?.swipeEdge != NavSwipeEdge.Right
    if (depth <= 0f) {
        val progress = topProgress(depth)
        if (scope.role == NavRole.Outgoing && committing && gesture != null) {
            // 松手提交：从「跟手位置」继续长到原尺寸并漂走。
            val releaseProgress = (1f - gesture.progress).coerceAtLeast(0.01f)
            val post = (1f - progress / releaseProgress).coerceIn(0f, 1f)
            val releaseEasedProgress = shapedTopProgress(releaseProgress, gesture)
            val committedScale =
                CROSS_ACTIVITY_MIN_SCALE + (1f - CROSS_ACTIVITY_MIN_SCALE) * releaseEasedProgress
            val grown = committedScale + (1f - committedScale) * post
            scaleX = snapScaleToPixelExtent(grown * bounce, widthPx)
            scaleY = scaleX
            var tx = if (hugs) (1f - releaseEasedProgress) * hugMax else 0f
            tx += post * driftPx
            alpha = (1f - 5f * (settle.elapsedMillis / 450f)).coerceAtLeast(0f)
            translationX = snapTranslationToPixelEdge(tx, scaleX, widthPx)
            translationY = snapTranslationToPixelEdge(
                translation = crossActivityYShift(
                    gesture = gesture,
                    height = heightPx,
                    scale = scaleX,
                    density = scope.density,
                ),
                scale = scaleY,
                extent = heightPx,
            )
        } else {
            val easedProgress = shapedTopProgress(progress, gesture)
            scaleX = snapScaleToPixelExtent(
                scale = (
                    CROSS_ACTIVITY_MIN_SCALE + (1f - CROSS_ACTIVITY_MIN_SCALE) * easedProgress
                    ) * bounce,
                extent = widthPx,
            )
            scaleY = scaleX
            translationX = snapTranslationToPixelEdge(
                translation = if (hugs) (1f - easedProgress) * hugMax else 0f,
                scale = scaleX,
                extent = widthPx,
            )
            alpha = when {
                scope.role == NavRole.Outgoing && gesture != null -> {
                    val releaseProgress = (1f - gesture.progress).coerceAtLeast(0.01f)
                    (1f - (1f - progress / releaseProgress).coerceIn(0f, 1f) * 3.5f)
                        .coerceAtLeast(0f)
                }

                gesture != null -> 1f
                else -> (progress / 0.2f).coerceIn(0f, 1f)
            }
            translationY = snapTranslationToPixelEdge(
                translation = crossActivityYShift(
                    gesture = gesture,
                    height = heightPx,
                    scale = scaleX,
                    density = scope.density,
                ),
                scale = scaleX,
                extent = heightPx,
            )
        }
    } else {
        // 被覆盖层：手势期间跟着「收拢」，松手后回到原位。
        val cover = coverProgress(depth)
        val post = if (gesture != null) {
            val releaseProgress = gesture.progress
            if (releaseProgress >= 1f) {
                1f
            } else {
                (((1f - cover) - releaseProgress) / (1f - releaseProgress)).coerceIn(0f, 1f)
            }
        } else {
            1f - cover
        }
        val rawTranslationX = -(1f - post) * driftPx
        if (gesture != null) {
            val travel = if (committing) gesture.progress else (1f - cover)
            val eased = BackGestureEasing.transform(travel.coerceIn(0f, 1f))
            val liveScale =
                CROSS_ACTIVITY_MIN_SCALE + (1f - CROSS_ACTIVITY_MIN_SCALE) * (1f - eased)
            scaleX = snapScaleToPixelExtent(
                (liveScale + (1f - liveScale) * post) * bounce,
                widthPx,
            )
            scaleY = scaleX
        }
        translationX = snapTranslationToPixelEdge(rawTranslationX, scaleX, widthPx)
        translationY = snapTranslationToPixelEdge(
            translation = crossActivityYShift(
                gesture = gesture,
                height = heightPx,
                scale = scaleX,
                density = scope.density,
            ),
            scale = scaleX,
            extent = heightPx,
        )
    }
}

/** AOSP 风格：push / pop / 预测返回三分支。 */
private val AospNavTransition: NavTransition = navDirectionalTransition(
    push = ClassicActivityOpen,
    pop = ClassicActivityClose,
    predictivePop = CrossActivityPredictive,
)

/**
 * 松手时的弹跳增益。
 *
 * 按释放速度给一个瞬时「过冲」，再按阻尼正弦衰减 —— 快速滑出会有一下
 * 轻微的弹性，慢速滑出则几乎无感（kick 有下限，太小就归零）。
 */
private fun bounceScale(settle: NavSettle?, gesture: NavGesture?): Float {
    if (settle == null || settle.phase != NavSettlePhase.Commit || gesture == null) return 1f
    val factor = if (gesture.swipeEdge != NavSwipeEdge.None) 2f else 1f
    val floorKick = if (gesture.progress < 0.1f) BOUNCE_MIN_KICK else 0f
    val kick = (abs(settle.releaseVelocity) * 100f * (1f - CROSS_ACTIVITY_MIN_SCALE) * factor)
        .coerceIn(floorKick, BOUNCE_MAX_KICK)
    if (kick <= 0f) return 1f
    val omega = sqrt(BOUNCE_STIFFNESS)
    val omegaD = omega * sqrt(1f - BOUNCE_DAMPING * BOUNCE_DAMPING)
    val t = settle.elapsedMillis / 1000f
    val overlay =
        -(kick / omegaD) * exp(-BOUNCE_DAMPING * omega * t) * sin(omegaD * t)
    return ((100f + overlay) / 100f).coerceAtMost(1f)
}

/** 手势期间把「顶部进度」映射到跟手位置。 */
private fun shapedTopProgress(progress: Float, gesture: NavGesture?): Float =
    if (gesture == null) progress else 1f - BackGestureEasing.transform((1f - progress).coerceIn(0f, 1f))

/**
 * 手势期间的纵向跟随：手指上下移动时，页面按阻尼比例上下偏移。
 *
 * 只在手势中存在，且位移上限受「缩放后剩余空间」约束（不会露出后面的层）。
 */
private fun crossActivityYShift(
    gesture: NavGesture?,
    height: Float,
    scale: Float,
    density: Density,
): Float {
    if (gesture == null || height <= 0f) return 0f
    val rawDelta = gesture.touchY - gesture.initialTouchY
    val half = height / 2f
    val ratio = min(half, abs(rawDelta)) / half
    val damped = 1f - (1f - ratio) * (1f - ratio)
    val marginPx = with(density) { CrossActivityEdgeMargin.toPx() }
    val maxShift = ((height - height * scale) / 2f - marginPx).coerceAtLeast(0f)
    return maxShift * damped * (if (rawDelta < 0f) -1f else 1f)
}

// ---------------------------------------------------------------------------
//  对外入口
// ---------------------------------------------------------------------------

/**
 * 按设置项取转场（对应参考项目 `weKitNavTransition`）。
 *
 * - [PageTransitionAnimation.MIUIX] → miuix 官方的 [NavTransitions.MiuixDefault]：
 *   整屏从右滑入 + 被覆盖页向左 1/4 宽视差 + `alpha = 1 - 0.1 * progress`；
 * - [PageTransitionAnimation.AOSP] → 本项目移植的 [AospNavTransition]：
 *   96dp 漂移 + 淡入淡出 + 预测返回跟手缩放。
 *
 * 两个对象都是顶层 `val`（只构造一次），设置页切换只是换引用，不重建。
 */
fun appNavTransition(animation: PageTransitionAnimation): NavTransition = when (animation) {
    PageTransitionAnimation.MIUIX -> NavTransitions.MiuixDefault
    PageTransitionAnimation.AOSP -> AospNavTransition
}

/**
 * `NavDisplay` 的正交视觉效果（对应参考项目 `rememberM3NavEffects`）。
 *
 * - **圆角裁切**：转场中的页面按设备屏幕圆角裁切，滑动时才像一张「卡片」；
 *   半径优先取系统圆角（`rememberNavSystemCornerRadius`），无圆角屏回落 32dp；
 * - **裁切模式**：MIUIX 只裁前缘（`Leading`），AOSP 前后都裁（`All`）
 *   —— AOSP 的页面在动画中**双面都圆角**；
 * - **压暗**：被覆盖页叠加 scrim，`dimAmount = 0.5f`；
 * - `blockInputDuringTransition = false`：转场期间不禁输入（与参考项目一致，
 *   本项目底栏需要全程可点，见 MainActivity 的悬浮底栏说明）。
 */
@Composable
fun rememberAppNavEffects(): NavDisplayEffects {
    val cornerRadius = rememberNavSystemCornerRadius()
    val clipRadius = if (cornerRadius > 0.dp) cornerRadius else 32.dp
    val backdropColor = MaterialTheme.colorScheme.surfaceContainer
    // 读设置项（可观察状态）：切换动画引擎时 NacDisplayEffects 跟着重建。
    val roundAllCorners = PageTransitionPrefs.animation == PageTransitionAnimation.AOSP
    return remember(clipRadius, backdropColor, roundAllCorners) {
        NavDisplayEffects(
            enableCornerClip = true,
            cornerClipRadius = clipRadius,
            cornerClipMode = if (roundAllCorners) NavCornerClipMode.All else NavCornerClipMode.Leading,
            dimAmount = 0.5f,
            backdropColor = backdropColor,
            blockInputDuringTransition = false,
        )
    }
}
