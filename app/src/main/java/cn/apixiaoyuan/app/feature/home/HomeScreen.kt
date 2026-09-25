package cn.apixiaoyuan.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cn.apixiaoyuan.app.App
import cn.apixiaoyuan.app.core.design.component.AppScaffold
import cn.apixiaoyuan.app.core.design.icon.AppIcons
import cn.apixiaoyuan.app.core.navigation.RouteAccount
import cn.apixiaoyuan.app.core.navigation.RouteExercise
import cn.apixiaoyuan.app.core.navigation.RouteLogin
import cn.apixiaoyuan.app.core.navigation.RoutePk
import cn.apixiaoyuan.app.core.navigation.RouteSamples
import cn.apixiaoyuan.app.core.session.SessionStore
import com.materialkolor.dynamiccolor.ColorSpec

/**
 * 首页。
 *
 * 三块内容，自上而下：
 *  1. 莫奈色板预览卡 —— 读 [App] 的四个全局状态，展示当前取色结果
 *  2. 登录态卡 —— 读 [SessionStore]，展示 cookie 承载的登录态（R2 闭环的可见面）
 *  3. 快捷入口网格 —— 四个已实现页面（登录 / 练习 / PK / 样本库）的直达入口
 *
 * 顶栏由 [AppScaffold] 统一提供，statusBars（刘海/状态栏）留白由它负责；
 * 悬浮玻璃底栏是浮层，内容不再为它预留 96dp —— 内容可以滑到底部被底栏遮住，
 * 这正是玻璃透明感成立的前提。底部只吃 navigationBars（手势条）。
 *
 * 四个快捷入口的路由在 [cn.apixiaoyuan.app.core.navigation.AppNavHost] 里已注册，
 * 这里只做 navigate 触发，不改导航图。
 */
@Composable
fun HomeScreen(navController: NavHostController) {
    AppScaffold(title = "逆向系老挂", onBack = null) { pad: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(pad)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "逆向系老挂",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "小猿口算 3.141.1 逆向工作台",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PaletteCard()
            SessionCard(onClick = { navController.navigate(RouteAccount) })

            Text(
                text = "快捷入口",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )

            val entries = listOf(
                QuickEntry("登录", "Login", "账号域 cookie 登录", RouteLogin),
                QuickEntry("练习", "Exercise", "任务卡 / 经验 / 英语章节", RouteExercise),
                QuickEntry("口算 PK", "Pk", "H5 容器 + cookie 同步", RoutePk),
                QuickEntry("样本库", "Samples", "请求历史与回放", RouteSamples),
            )

            entries.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { entry ->
                        QuickEntryCard(
                            entry = entry,
                            modifier = Modifier.weight(1f),
                            onClick = { navController.navigate(entry.route) },
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** 快捷入口数据。 */
private data class QuickEntry(
    val label: String,
    val iconKey: String,
    val subtitle: String,
    val route: Any,
)

/**
 * 莫奈色板预览卡。
 *
 * 读 [App] 的四个全局状态：[App.colorScheme]、[App.paletteStyle]、
 * [App.colorSpec]、[App.seedColor]。这四个状态由 [ReverseOldGuyTheme]
 * 在每次重组时通过 SideEffect 写回，这里是只读消费。
 *
 * 五个色块依次取 ColorScheme 的 primary / secondary / tertiary /
 * surfaceVariant / error，用来肉眼核对取色是否符合预期。
 */
@Composable
private fun PaletteCard() {
    val scheme = App.colorScheme
    val swatches = listOfNotNull(
        scheme?.primary,
        scheme?.secondary,
        scheme?.tertiary,
        scheme?.surfaceVariant,
        scheme?.error,
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "莫奈色板",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (swatches.isEmpty()) {
                Text(
                    text = "取色未就绪",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    swatches.forEach { color ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(color),
                        )
                    }
                }
            }

            // material-kolor 5.0.0 的 ColorSpec 只扫到 SPEC_2025 一个已确证常量，
            // 其他版本按未知处理，不写猜测的枚举名。
            val specLabel = if (App.colorSpec == ColorSpec.SpecVersion.SPEC_2025) "2025" else "其他"
            Text(
                text = "风格 ${App.paletteStyle.name} · 规范 $specLabel · 种子 ${seedHex(App.seedColor)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 把种子色格式化成 `#AARRGGBB`，给色板卡展示用。 */
private fun seedHex(color: Color): String {
    val argb = color.toArgb()
    return "#%08X".format(argb)
}

/**
 * 登录态卡（用户卡片）。
 *
 * 读 [SessionStore]：`isLoggedIn` 与 `yfdU`（即 cookie 里的 `userid`，
 * 等于 `UserVO.userId`）。这两项是 R2 闭环的直接产物 —— cookie 承载登录态，
 * 这里把它的存在状态显式展示出来，避免「登录成功了但界面看不出来」。
 *
 * `yfdU` 为 null 或 -1 都按未登录处理（[SessionStore.isLoggedIn] 已封装）。
 *
 * **整卡可点**，跳账号页（宝贝学习账号切换 + 改密码）—— 用户明确要求
 * 「账号切换等功能的下级页面点击主页的用户卡片即可进入」。右侧的 `›`
 * 是「可进入」的视觉提示，与设置页的 [cn.apixiaoyuan.app.feature.settings.SettingsScreen]
 * 同款语义。
 */
@Composable
private fun SessionCard(onClick: () -> Unit) {
    val loggedIn = SessionStore.isLoggedIn
    val yfdU = SessionStore.yfdU
    val cookieCount = SessionStore.loadCookies().size

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (loggedIn) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AppIcons.forKey("Login"),
                    contentDescription = null,
                    tint = if (loggedIn) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = if (loggedIn) "已登录" else "未登录",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (loggedIn) "YFD_U $yfdU · cookie $cookieCount 条"
                    else "点击进入账号页登录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                imageVector = AppIcons.ChevronForward,
                contentDescription = "进入账号页",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 单个快捷入口卡：图标 + 标题 + 副标题，整卡可点。 */
@Composable
private fun QuickEntryCard(
    entry: QuickEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AppIcons.forKey(entry.iconKey),
                    contentDescription = entry.label,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = entry.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = entry.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
