package cn.apixiaoyuan.app.feature.api

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import cn.apixiaoyuan.app.core.design.component.AppScaffold
import cn.apixiaoyuan.app.core.design.component.AppScrollScaffold

/**
 * 接口浏览器。
 *
 * 左列表右详情两态：列表按 group 分组展示全部已录入接口；点击进入详情，
 * 可一键把请求送进协议请求台复现。
 *
 * 顶栏由 [AppScaffold] 统一提供（主 Tab 页无返回键，详情页有返回键）；
 * statusBars 留白由它负责，悬浮底栏是浮层、内容不再为它预留 96dp。
 */
@Composable
fun ApiScreen(
    navController: NavHostController,
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
    AppScaffold(title = "接口浏览器", onBack = null) { pad: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "接口浏览器",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer12()

            val grouped = viewModel.groupedResults
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                grouped.forEach { (group, endpoints) ->
                    item(key = "header-$group") {
                        Text(
                            text = group,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(endpoints, key = { "$group-${it.name}-${it.path}" }) { ep ->
                        ApiRow(ep) { viewModel.select(ep) }
                    }
                }
            }
        }
    }
}

/** 详情态：单个接口的完整定义 + 送进请求台。 */
@Composable
private fun ApiDetailPane(viewModel: ApiViewModel, endpoint: ApiRegistry.ApiEndpoint) {
    AppScrollScaffold(title = endpoint.name, onBack = { viewModel.back() }) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextButton(onClick = { viewModel.back() }) { Text("← 返回列表") }

            Text(
                text = endpoint.path,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = endpoint.note ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (viewModel.loading) {
                Text(
                    text = "发送中…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            viewModel.responseBody?.let { body ->
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 列表单行：方法徽标 + 名字 + 路径。 */
@Composable
private fun ApiRow(endpoint: ApiRegistry.ApiEndpoint, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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

@Composable
private fun Spacer12() {
    Box(modifier = Modifier.padding(top = 12.dp))
}