package cn.apixiaoyuan.app.core.design.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward

/**
 * 图标库统一出口。
 *
 * 底座有两套：
 *  1. androidx.compose.material.icons 的 material-icons-core（底栏与首页快捷入口在用）；
 *  2. **miuix-icons**（`top.yukonga.miuix.kmp:miuix-icons-android`）——
 *     miuix 官方矢量图标库，笔重分 Light/Normal/Regular/Medium/Demibold 五档。
 *
 * ## 为什么引入 miuix-icons
 *
 * 顶栏返回键此前用 material-icons 的 `ArrowBack`，是一条细描边箭头，与 miuix 的
 * 顶栏（[top.yukonga.miuix.kmp.basic.SmallTopAppBar]）放在一起质感明显不搭 ——
 * 用户明确要求换成 miuix 的。`MiuixIcons.Back` 是 miuix 规范的实心笔画箭头，
 * 与 miuix 顶栏、`IconButton` 的默认尺寸/内边距是同源设计。
 *
 * 历史记录（勿重蹈）：曾引 `com.composables:icons-material-symbols-outlined-cmp:2.2.1`，
 * 解包 classes.jar 后 `materialsymbols/outlined` 包里只有 `AndroidKt.class`，
 * 不是整套图标库，`Home`/`Api`/`Terminal`/`Settings` 必然 Unresolved，CI 已实际报错。
 * miuix-icons 的 aar 已解包核对：`icon/extended/BackKt` 等类与 `MiuixIcons$Regular`
 * 扩展属性都在，是完整库。
 *
 * 键名与导航路由一一对应，调用侧只认 [forKey] / [forKeySelected]；
 * 换图标库只需改这两张 map，调用侧不动。
 */
object AppIcons {

    /**
     * 顶栏返回键。
     *
     * 单独开一个口而不是塞进 [outlined] 的 `"Back"` 键：`AppScaffold` 是唯一消费方，
     * 直接引用语义更清楚，也避免与 material-icons 的 `ArrowBack` 混淆。
     * 用 `Regular` 档 —— miuix 顶栏图标的默认字重。
     */
    val Back: ImageVector get() = MiuixIcons.Regular.Back

    /** 右向箭头（列表项「可进入」提示）。miuix `Regular` 档。 */
    val ChevronForward: ImageVector get() = MiuixIcons.Regular.ChevronForward

    /** 左向箭头（返回/上一级）。miuix `Regular` 档。 */
    val ChevronBackward: ImageVector get() = MiuixIcons.Regular.ChevronBackward

    private val outlined = mapOf(
        "Home" to Icons.Outlined.Home,
        "Android" to Icons.Outlined.Person,
        "Api" to Icons.AutoMirrored.Outlined.List,
        "Terminal" to Icons.Outlined.Build,
        "Settings" to Icons.Outlined.Settings,
        // 首页快捷入口四键（不进底栏）
        "Login" to Icons.Outlined.AccountCircle,
        "Exercise" to Icons.Outlined.Edit,
        "Pk" to Icons.Outlined.Star,
        "Samples" to Icons.Outlined.Search,
    )

    /** 按 iconKey 取未选中态图标；未知 key 回退 Home，避免空图标崩溃。 */
    fun forKey(key: String): ImageVector = outlined[key] ?: Icons.Outlined.Home

    /** 按 iconKey 取选中态图标；filled 未接入前与未选中态同源。 */
    fun forKeySelected(key: String): ImageVector = outlined[key] ?: Icons.Outlined.Home
}
