package cn.apixiaoyuan.app.feature.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.apixiaoyuan.app.core.account.SubAccountItem
import cn.apixiaoyuan.app.core.design.component.AppScrollScaffold
import cn.apixiaoyuan.app.core.design.icon.AppIcons
import cn.apixiaoyuan.app.core.navigation.AppNavController
import cn.apixiaoyuan.app.core.navigation.Route
import cn.apixiaoyuan.app.core.navigation.RouteAccount
import cn.apixiaoyuan.app.core.navigation.RouteExercise
import cn.apixiaoyuan.app.core.navigation.RouteLogin
import cn.apixiaoyuan.app.core.navigation.RoutePk
import cn.apixiaoyuan.app.core.navigation.RouteSamples
import cn.apixiaoyuan.app.core.session.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 首页。
 *
 * 三块内容，自上而下：
 *  1. 登录态卡 —— 读 [SessionStore]，展示 cookie 承载的登录态
 *  2. 子账号卡片列表 —— 登录后展示名下全部宝贝账号，点哪个切哪个（**本轮新增**）
 *  3. 快捷入口网格 —— 四个已实现页面的直达入口
 *
 * 顶栏由 [AppScrollScaffold] 统一提供，statusBars 留白由它负责；
 * 悬浮玻璃底栏是浮层，内容不再为它预留 96dp —— 内容可以滑到底部被底栏遮住。
 *
 * ## 子账号卡片（用户 2026-09-26 明确要求）
 *
 * 通过登录给出的信息自动识别有几个子账号，有几个就在主页显示几个；
 * 子账号的**名字和头像**都要获取；卡片显示在「已登录卡片」下方；
 * 点哪个切哪个；**被选中的那个账号卡片名字下方显示「当前账号」**；
 * 未选中的显示 `uid`。
 */
@Composable
fun HomeScreen(navController: AppNavController) {
    val viewModel: HomeViewModel = viewModel()

    AppScrollScaffold(title = "逆向系老挂", onBack = null) {
        Column(
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

            SessionCard(onClick = { navController.navigate(RouteAccount) })

            // ---- 子账号卡片列表 ----
            SubAccountsSection(viewModel)

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
    val route: Route,
)

/**
 * 登录态卡（用户卡片）。
 *
 * 读 [SessionStore]：`isLoggedIn` 与 `yfdU`。整卡可点，跳账号页。
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

/**
 * 子账号卡片列表区。
 *
 * 未登录 → 不显示；登录但列表空 → 不显示（避免「没有宝贝账号」干扰主页）；
 * 有列表 → 每个账号一张卡，头像 + 名字 + 当前账号标注 / uid。
 */
@Composable
private fun SubAccountsSection(viewModel: HomeViewModel) {
    if (!SessionStore.isLoggedIn) return
    if (viewModel.loadingAccounts && viewModel.subAccounts.isEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(
                text = "正在拉取宝贝账号…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    if (viewModel.subAccounts.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "宝贝学习账号",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        viewModel.subAccounts.forEach { item ->
            SubAccountCard(item = item, onClick = { viewModel.switchTo(item) })
        }
    }

    viewModel.message?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * 单个子账号卡片。
 *
 * 头像 + 名字；当前账号显示「当前账号」，非当前账号显示 `uid`。
 * 非当前账号整卡可点（切换）。
 */
@Composable
private fun SubAccountCard(item: SubAccountItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !item.isCurrent, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isCurrent) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Avatar(url = item.avatarUrl, size = 40.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.nickname,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (item.isCurrent) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = if (item.isCurrent) "当前账号" else "uid ${item.userId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.isCurrent) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (!item.isCurrent) {
                Icon(
                    imageVector = AppIcons.ChevronForward,
                    contentDescription = "切换",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * 轻量网络头像：不引 coil/glide，直接用 [BitmapFactory] 在 IO 线程下载。
 *
 * 加载失败 / url 为空时回退一个带首字母的纯色圆。
 */
@Composable
private fun Avatar(url: String?, size: androidx.compose.ui.unit.Dp) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = url) {
        value = url?.takeIf { it.isNotBlank() }?.let { u ->
            withContext(Dispatchers.IO) {
                runCatching {
                    val conn = URL(u).openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.instanceFollowRedirects = true
                    conn.connect()
                    if (conn.responseCode in 200..299) {
                        conn.inputStream.use { BitmapFactory.decodeStream(it) }
                    } else {
                        null
                    }
                }.getOrNull()
            }
        }
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = AppIcons.forKey("Login"),
                contentDescription = "头像",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(size / 2),
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