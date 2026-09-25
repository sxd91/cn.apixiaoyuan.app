package cn.apixiaoyuan.app.core.design.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 二级页面过渡动画引擎。
 *
 * 语义蓝本是参考项目 cn.nizou.sxd（老挂戏老叟）的 `ui/theme/ThemeSettings.kt`
 * 里的同名枚举，**但默认值不同**：
 *
 * | | cn.nizou.sxd | 本项目 |
 * |---|---|---|
 * | 选项 | `AOSP` / `MIUIX` | `MIUIX` / `AOSP` |
 * | 默认 | `AOSP`（`fromName` 找不到就回落 AOSP） | **`MIUIX`**（用户指定） |
 *
 * 参考项目那边走 miuix-nav 的 `NavDisplay(transition = …)`，本项目走
 * androidx `NavHost`，两边的转场实现无法共用（见 `core/navigation/PageTransitions.kt`
 * 的 KDoc，那里逐条写了本项目如何用 compose animation 复刻这两套观感）。
 *
 * 持久化沿用本项目既有套路（`SharedPreferences` + `mutableStateOf`），
 * 不引入 DataStore / MMKV。
 */
enum class PageTransitionAnimation(val displayName: String) {
    /** miuix 默认：进场页从右侧整屏滑入，被覆盖页让位 1/4 宽度并轻微降透明度。 */
    MIUIX("Miuix"),

    /** AOSP 风格：进出只做 96dp 的轻微位移 + 淡入淡出，被覆盖页几乎不动。 */
    AOSP("AOSP"),
}

/**
 * 页面过渡动画的持久化门面。
 *
 * 生命周期与 [cn.apixiaoyuan.app.core.oldsimian.OldSimianPrefs] 一致：
 * 必须在 [cn.apixiaoyuan.app.App.onCreate] 里调 [init]，否则 [prefs] 抛错
 * （宁可早失败也不静默丢配置）。
 *
 * 单独存一个 `ui_prefs` 文件而不是塞进 `old_simian`：过渡动画是全应用级的
 * 界面设置，与「老挂戏老叟」那批功能开关不是一个域，混在一起会让
 * 「导出一份老挂戏老叟配置」这种语义变得含混。
 */
object PageTransitionPrefs {

    private const val PREF_NAME = "ui_prefs"
    private const val KEY_PAGE_TRANSITION = "page_transition"

    /** 当前过渡动画。默认 [PageTransitionAnimation.MIUIX]。 */
    var animation by mutableStateOf(PageTransitionAnimation.MIUIX)
        private set

    @Volatile
    private var appContext: Context? = null

    /** 由 `App.onCreate` 调用。 */
    fun init(context: Context) {
        appContext = context.applicationContext
        animation = fromName(prefs().getString(KEY_PAGE_TRANSITION, null))
    }

    /** 切换过渡动画并立即写盘。 */
    fun update(value: PageTransitionAnimation) {
        animation = value
        prefs().edit().putString(KEY_PAGE_TRANSITION, value.name).apply()
    }

    /**
     * 枚举名 → 枚举值。
     *
     * 落盘的是 `Enum.name`（与参考项目一致）。历史值 / 脏值一律回落
     * [PageTransitionAnimation.MIUIX] —— 界面动画宁可给默认值，也不该因为
     * 一条读不出来的配置把整页转场搞成空白。
     */
    fun fromName(value: String?): PageTransitionAnimation =
        PageTransitionAnimation.entries.find { it.name == value } ?: PageTransitionAnimation.MIUIX

    private fun prefs(): SharedPreferences {
        val ctx = appContext ?: error("PageTransitionPrefs.init() 未调用")
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
}