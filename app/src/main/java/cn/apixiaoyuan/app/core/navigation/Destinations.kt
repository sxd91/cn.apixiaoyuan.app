package cn.apixiaoyuan.app.core.navigation

/**
 * 全局路由定义。
 *
 * 每个 feature 对应一个 route 常量，注册在 [AppNavHost] 的 NavHost 中。
 * 使用字符串而非 sealed class，是为了让后续深链与参数路由
 * 可以自然地嵌在同一个字符串里，不需要额外的类型映射层。
 */
object Routes {
    const val HOME = "home"
    const val APK = "apk"
    const val API = "api"
    const val PK = "pk"
    const val EXERCISE = "exercise"
    const val LOGIN = "login"
    const val REPL = "repl"
    const val SAMPLES = "samples"
    const val SETTINGS = "settings"
}

/**
 * 底部 Tab 与路由的绑定项。
 *
 * [iconKey] 对应 AppIcons 里注册的 Material Symbols 键；
 * [route] 用于 NavHost 导航；[label] 是底栏展示文案。
 */
data class BottomTab(
    val route: String,
    val label: String,
    val iconKey: String,
)

/** 底栏默认五 Tab，顺序即显示顺序。 */
val BottomTabs: List<BottomTab> = listOf(
    BottomTab(Routes.HOME, "首页", "Home"),
    BottomTab(Routes.APK, "APK", "Android"),
    BottomTab(Routes.API, "接口", "Api"),
    BottomTab(Routes.REPL, "请求台", "Terminal"),
    BottomTab(Routes.SETTINGS, "设置", "Settings"),
)
