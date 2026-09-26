package cn.apixiaoyuan.app.feature.api

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.apixiaoyuan.app.core.navigation.AppNavController
import cn.apixiaoyuan.app.core.design.component.AppListScaffold
import cn.apixiaoyuan.app.core.design.component.AppScrollScaffold

/**
 * 接口浏览器。
 *
 * 左列表右详情两态：列表按 group 分组展示全部已录入接口；点击进入详情，
 * 填参数后一键把请求送进协议请求台复现。
 *
 * ## UI 与设置页对齐（2026-09-26 修正）
 *
 * 此前列表态用 [cn.apixiaoyuan.app.core.design.component.AppScaffold] 而非
 * 列表骨架，导致：① 顶栏 inset 没进滚动容器 contentPadding，内容第一项被
 * 顶栏遮挡；② 手写了一个与顶栏重复的「接口浏览器」大标题；③ 行卡片圆角
 * 16dp，与设置页卡片组 20dp 不统一。
 *
 * 现在统一为：
 *  - 列表态 → [AppListScaffold]（LazyColumn 自动吃 topInset + 底部限位）；
 *  - 行卡片 / 详情卡片组 → 圆角 20dp + `surfaceContainerHigh`，与设置页
 *    `SettingGroup` 同一套视觉语言；
 *  - 移除与顶栏重复的标题、与返回键重复的「← 返回列表」按钮。
 */
@Composable
fun ApiScreen(
    navController: AppNavController,
    viewModel: ApiViewModel = viewModel(),
) {
    val selected = viewModel.selected
    if (selected == null) {
        ApiListPane(viewModel)
    } else {
        ApiDetailPane(viewModel, selected)
    }
}

/** 列表态：按 group 分组的接口清单。 */
@Composable
private fun ApiListPane(viewModel: ApiViewModel) {
    AppListScaffold(title = "接口浏览器", onBack = null) {
        val grouped = viewModel.groupedResults
        grouped.forEach { (group, endpoints) ->
            item(key = "header-$group") {
                Text(
                    text = group,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(endpoints, key = { "$group-${it.name}-${it.path}" }) { ep ->
                ApiRow(
                    endpoint = ep,
                    onClick = { viewModel.select(ep) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** 详情态：单个接口的完整定义 + 参数表单 + 发送。 */
@Composable
private fun ApiDetailPane(viewModel: ApiViewModel, endpoint: ApiRegistry.ApiEndpoint) {
    AppScrollScaffold(title = endpoint.name, onBack = { viewModel.back() }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ---- 接口信息 ----
            ApiCard {
                Text(
                    text = endpoint.path,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                endpoint.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // ---- 参数表单 ----
            if (endpoint.params.isNotEmpty()) {
                ApiCard {
                    endpoint.params.forEach { p ->
                        val idx = viewModel.paramValues.indexOfFirst { it.first == p.name }
                        val value = if (idx >= 0) viewModel.paramValues[idx].second else ""
                        val isBody = p.location == ApiRegistry.ParamIn.BODY
                        OutlinedTextField(
                            value = value,
                            onValueChange = { viewModel.setParam(p.name, it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("${p.name} (${p.type})") },
                            singleLine = !isBody,
                            minLines = if (isBody) 3 else 1,
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
            }

            // ---- 发送 ----
            TextButton(
                onClick = { viewModel.send() },
                enabled = !viewModel.loading,
            ) {
                Text(if (viewModel.loading) "发送中…" else "发送")
            }

            viewModel.errorMessage?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // ---- 响应 ----
            if (viewModel.statusCode != -1) {
                ApiCard {
                    Text(
                        text = "HTTP ${viewModel.statusCode} · ${viewModel.elapsedMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    viewModel.finalUrl?.let { url ->
                        Text(
                            text = url,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (viewModel.responseHeaders.isNotEmpty()) {
                        Text(
                            text = viewModel.responseHeaders.joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    viewModel.responseBody?.let { body ->
                        Text(
                            text = body,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/** 列表单行：方法徽标 + 名字 + 路径。 */
@Composable
private fun ApiRow(
    endpoint: ApiRegistry.ApiEndpoint,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MethodBadge(endpoint.method)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = endpoint.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = endpoint.path,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onClick) { Text("查看") }
        }
    }
}

/** 方法徽标（GET/POST）。 */
@Composable
private fun MethodBadge(method: String) {
    val color = when (method.uppercase()) {
        "GET" -> MaterialTheme.colorScheme.primary
        "POST" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = color),
    ) {
        Text(
            text = method,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** 详情页卡片组：圆角 20dp + `surfaceContainerHigh`，对齐设置页 `SettingGroup`。 */
@Composable
private fun ApiCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
    }
}