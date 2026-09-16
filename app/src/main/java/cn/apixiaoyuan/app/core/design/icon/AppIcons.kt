package cn.apixiaoyuan.app.core.design.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 图标库统一出口。
 *
 * 底座是 androidx.compose.material.icons 的 material-icons-core
 * （Compose 自带，由 material3 传递带入）。
 *
 * 曾经引过 com.composables:icons-material-symbols-outlined-cmp:2.2.1，
 * 解包该 artifact 的 classes.jar 后，materialsymbols/outlined 包里
 * 只有 AndroidKt.class 一个文件——它不是整套图标库，
 * Home/Api/Terminal/Settings 必然 Unresolved，CI 已实际报错。
 * 整套 Material Symbols 的正确坐标后续查证后再换；图标替换是纯视觉层，
 * 不影响导航与业务逻辑。
 *
 * 键名与导航路由一一对应，调用侧只认 [forKey] / [forKeySelected]；
 * 后续换图标库只需改这两张 map，调用侧不动。
 */
object AppIcons {

    private val outlined = mapOf(
        "Home" to Icons.Outlined.Home,
        "Android" to Icons.Outlined.Android,
        "Api" to Icons.Outlined.Code,
        "Terminal" to Icons.Outlined.Build,
        "Settings" to Icons.Outlined.Settings,
    )

    /** 按 iconKey 取未选中态图标；未知 key 回退 Home，避免空图标崩溃。 */
    fun forKey(key: String): ImageVector = outlined[key] ?: Icons.Outlined.Home

    /** 按 iconKey 取选中态图标；filled 未接入前与未选中态同源。 */
    fun forKeySelected(key: String): ImageVector = outlined[key] ?: Icons.Outlined.Home
}
