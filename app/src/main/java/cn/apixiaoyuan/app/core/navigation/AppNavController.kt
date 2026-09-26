package cn.apixiaoyuan.app.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack

/**
 * 全应用导航器 —— **方法与 androidx `NavHostController` 同名**，故各页面调用点无需改写。
 *
 * ## 为什么自己封一层
 *
 * 参考项目 `cn.nizou.sxd` 的 `Navigator` 只有 `push` / `pop` / `replace` / `popUntil`，
 * 而本项目各二级页写的是 `navController.navigate(route)` 与
 * `navController.popBackStack()`。这里**刻意沿用那两个方法名**，
 * 让「换导航引擎」对页面透明：页面只改类型与 import，逻辑一行不动。
 *
 * ## 为什么去掉 `rememberCoroutineScope`
 *
 * miuix-nav 的 `NavBackStack` 是 `SnapshotStateList<NavKey>` ——
 * `add` / `removeAt` 立即改状态并触发 `NavDisplay` 重组，**不需要协程**。
 * （上一版封了一个 scope 却没用上，已删。）
 *
 * `popBackStack()` 的 100ms 去重来自参考项目 `Navigator.pop`（防手势/返回键重入
 * 把栈一次弹穿），是**必要**的 —— 保留。
 */
class AppNavController internal constructor(
    val backStack: NavBackStack,
) {
    /** 当前栈深（根页为 1）。 */
    fun backStackSize(): Int = backStack.size

    /**
     * 压入一个路由（对应 androidx 的 `navController.navigate(route)`）。
     *
     * 防重：与栈顶相同则忽略 —— 参考项目 `Navigator.push` 同款保护，
     * 避免双击入口压出两层同样的页面。
     */
    fun navigate(key: NavKey) {
        if (backStack.lastOrNull() == key) return
        backStack.add(key)
    }

    /**
     * 回退一层（对应 androidx 的 `navController.popBackStack()`）。
     *
     * 栈深 <= 1 时**不弹**（根页不能被弹掉），与参考项目
     * `Navigator.pop` 的 `if (backStackSize() <= 1) return` 同语义；
     * 根页上的返回语义由调用方决定（`NavDisplay.onBack` 会落到这里，
     * 栈深为 1 时什么都不做 = 交给系统退出 App）。
     *
     * 100ms 去重：防止手势与返回键在同一瞬间各触发一次，把栈一次弹穿两层。
     */
    fun popBackStack() {
        val now = System.currentTimeMillis()
        if (now - lastPopTime < POP_DEBOUNCE_MS) return
        if (backStack.size <= 1) return
        lastPopTime = now
        backStack.removeAt(backStack.lastIndex)
    }

    /**
     * 回到指定路由（含泛型参数，对应 androidx 的
     * `popBackStack<RouteHome>(inclusive = false)`）。
     *
     * `inclusive = false` 语义：**目标那层保留**，只弹掉它上面的。
     * 返回 `false` 表示「栈里没有该路由」或「已经在它上面」，调用方可据此兜底
     * （如 PkScreen 会退化成普通 `popBackStack()`）。
     */
    inline fun <reified T : NavKey> popBackStack(inclusive: Boolean = false): Boolean {
        val index = backStack.indexOfLast { it is T }
        val last = backStack.lastIndex
        if (index < 0) return false
        if (!inclusive && index == last) return false
        val keep = if (inclusive) index - 1 else index
        if (keep < 0) return false
        while (backStack.size > keep + 1) backStack.removeAt(backStack.lastIndex)
        return true
    }

    private var lastPopTime = 0L

    private companion object {
        const val POP_DEBOUNCE_MS = 100L
    }
}

/**
 * 供 `CompositionLocal` 下发的导航器实例（由 `AppNavHost` 提供）。
 *
 * 页面当前仍走**显式传参**（改动最小、依赖明确）；保留此 Local 是为了
 * 后续新增页面可以少传一个参数。
 */
val LocalAppNavController = staticCompositionLocalOf<AppNavController> {
    error("LocalAppNavController 未提供：请在 AppNavHost 内使用")
}

/**
 * 建一个绑定到 composition 的导航器（持久化返回栈，`rememberSaveable` 支撑）。
 *
 * @param startDestination 栈底（根页）。本项目只有一个栈，固定传 `RouteHome`。
 */
@Composable
fun rememberAppNavController(startDestination: Route): AppNavController {
    // 显式给父类型 Route：整个 sealed 层级的序列化器才会被用上（miuix 的硬要求）。
    val backStack = rememberNavBackStack<Route>(startDestination)
    return remember(backStack) { AppNavController(backStack) }
}