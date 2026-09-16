package cn.apixiaoyuan.app.core.design.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 图标库统一出口。
 *
 * 当前以 Material Symbols 为底座，按液态玻璃 Tab 栏的选中态需要提供
 * outlined / filled 两套。图标键名与导航路由一一对应，后续拿到
 * cn.nizou.sxd 的图标资源后，只需在两张 map 里逐项替换，调用侧无需改动。
 *
 * 说明：这里刻意只使用 material-icons-core 内确实存在的图标（Home、Settings、
 * Android、Api、Terminal 均属于核心子集），避免引入 material-icons-extended
 * 导致包体膨胀；若后续需要更丰富的图标，再统一换到
 * com.composables:icons-material-symbols-outlined-cmp。
 */
object AppIcons {

    private val outlined = mapOf(
        "Home" to Icons.Outlined.Home,
        "Android" to Icons.Outlined.Android,
        "Api" to Icons.Outlined.Api,
        "Terminal" to Icons.Outlined.Terminal,
        "Settings" to Icons.Outlined.Settings,
    )

    private val filled = mapOf(
        "Home" to Icons.Filled.Home,
        "Android" to Icons.Outlined.Android,
        "Api" to Icons.Outlined.Api,
        "Terminal" to Icons.Outlined.Terminal,
        "Settings" to Icons.Filled.Settings,
    )

    /** 按 iconKey 取未选中态图标；未知 key 回退 Home，避免空图标崩溃。 */
    fun forKey(key: String): ImageVector = outlined[key] ?: Icons.Outlined.Home

    /** 按 iconKey 取选中态图标；未知 key 回退 Home。 */
    fun forKeySelected(key: String): ImageVector = filled[key] ?: Icons.Filled.Home
}