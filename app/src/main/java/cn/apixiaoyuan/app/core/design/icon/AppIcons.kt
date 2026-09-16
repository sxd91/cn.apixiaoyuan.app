package cn.apixiaoyuan.app.core.design.icon

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.materialsymbols.outlined.Android
import com.composables.icons.materialsymbols.outlined.Api
import com.composables.icons.materialsymbols.outlined.Home
import com.composables.icons.materialsymbols.outlined.Settings
import com.composables.icons.materialsymbols.outlined.Terminal

/**
 * 图标库统一出口。
 *
 * 底座是 composablehorizons 的 Material Symbols
 * （com.composables:icons-material-symbols-outlined-cmp:2.2.1，
 * 与 cn.nizou.sxd 同款）。这套库把图标注册成
 * androidx.compose.material.icons.Icons.Outlined 上的扩展属性，
 * 所以 import 分两段：Icons 来自 androidx，五个图标来自 composables。
 *
 * 键名与导航路由一一对应，调用侧只认 [forKey] / [forKeySelected]；
 * 后续要替换成 cn.nizou.sxd 的图标资源，只需改这张 map。
 *
 * filled 变体（icons-material-symbols-outlined-filled-cmp）暂未引入：
 * 本地依赖缓存里只有 outlined 一套，filled 的包结构与接收者
 * 未经解包验证，先统一复用 outlined，等 CI 跑通再补。
 */
object AppIcons {

    private val outlined = mapOf(
        "Home" to Icons.Outlined.Home,
        "Android" to Icons.Outlined.Android,
        "Api" to Icons.Outlined.Api,
        "Terminal" to Icons.Outlined.Terminal,
        "Settings" to Icons.Outlined.Settings,
    )

    /** 按 iconKey 取未选中态图标；未知 key 回退 Home，避免空图标崩溃。 */
    fun forKey(key: String): ImageVector = outlined[key] ?: Icons.Outlined.Home

    /** 按 iconKey 取选中态图标；filled 未接入前与未选中态同源。 */
    fun forKeySelected(key: String): ImageVector = outlined[key] ?: Icons.Outlined.Home
}
