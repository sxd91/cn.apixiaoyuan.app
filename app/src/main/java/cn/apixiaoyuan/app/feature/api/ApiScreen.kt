package cn.apixiaoyuan.app.feature.api

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController

/**
 * 接口浏览器。
 *
 * 两种视图状态，由 [ApiViewModel.selected] 切换：
 *  - null  → 列表页：搜索框 + 按 Service 分组的接口列表
 *  - 非 null → 详情页：参数表单 + 发送按钮 + 响应展示（状态码 / 头 / 体）
 *
 * 数据源是 [ApiRegistry] 的静态元数据，不经 Retrofit——详见 [ApiViewModel]
 * 的类注释。这样未落盘的第二批接口也能立刻试打，且能看到原始响应体
 * （不经过反序列化，也不走 @NeedDecode）。
 *
 * 底部预留 96dp 给 LiquidGlassTabBar。
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

@Composable
private fun ApiListPane(viewModel: ApiViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp, bottom = 96.dp),
    ) {
        Text(
            text = "接口浏览器",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "共 ${ApiRegistry.all.size} 个接口，${ApiRegistry.grouped.size} 个分组",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = viewModel.keyword,
            onValueChange = { viewModel.updateKeyword(it) },
            label = { Text("搜索接口名 / 路径 / 分组") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

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

@Composable
private fun ApiRow(endpoint: ApiRegistry.ApiEndpoint, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MethodBadge(endpoint.method)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = endpoint.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (endpoint.needDecode) {
                    Spacer(Modifier.width(6.dp))
                    Tag("解码", MaterialTheme.colorScheme.tertiary)
                }
                if (endpoint.needEncode) {
                    Spacer(Modifier.width(6.dp))
                    Tag("编码", MaterialTheme.colorScheme.tertiary)
                }
                if (endpoint.deprecated) {
                    Spacer(Modifier.width(6.dp))
                    Tag("旧版", MaterialTheme.colorScheme.outline)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${endpoint.baseUrl}  ${endpoint.path}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            endpoint.note?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MethodBadge(method: String) {
    val color = when (method) {
        "GET" -> MaterialTheme.colorScheme.primary
        "POST" -> MaterialTheme.colorScheme.tertiary
        "PUT" -> MaterialTheme.colorScheme.secondary
        "DELETE" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = method,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun ApiDetailPane(viewModel: ApiViewModel, endpoint: ApiRegistry.ApiEndpoint) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextButton(onClick = { viewModel.back() }) { Text("← 返回列表") }

        Row(verticalAlignment = Alignment.CenterVertically) {
            MethodBadge(endpoint.method)
            Spacer(Modifier.width(8.dp))
            Text(
                text = endpoint.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = "${endpoint.baseUrl}  ${endpoint.path}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        endpoint.note?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (endpoint.params.isNotEmpty()) {
            HorizontalDivider()
            Text("参数", style = MaterialTheme.typography.titleSmall)
            endpoint.params.forEach { p ->
                val value = viewModel.paramValues.firstOrNull { it.first == p.name }?.second ?: ""
                OutlinedTextField(
                    value = value,
                    onValueChange = { viewModel.setParam(p.name, it) },
                    label = {
                        Text("${p.name}  [${p.location.name}${if (p.required) " *" else ""}]")
                    },
                    supportingText = p.note?.let { n -> { Text(n) } },
                    singleLine = p.location != ApiRegistry.ParamIn.BODY,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (p.type.contains("Int") || p.type.contains("Long"))
                            KeyboardType.Number else KeyboardType.Text,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        HorizontalDivider()

        androidx.compose.material3.Button(
            onClick = { viewModel.send() },
            enabled = !viewModel.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (viewModel.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("发送")
            }
        }

        viewModel.finalUrl?.let {
            Text(
                text = "URL: $it",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (viewModel.statusCode >= 0 || viewModel.errorMessage != null) {
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (viewModel.statusCode >= 0) "HTTP ${viewModel.statusCode}" else "请求失败",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (viewModel.statusCode in 200..299)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "${viewModel.elapsedMs} ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        viewModel.errorMessage?.let {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        if (viewModel.responseHeaders.isNotEmpty()) {
            Text("响应头", style = MaterialTheme.typography.titleSmall)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    viewModel.responseHeaders.forEach { h ->
                        Text(
                            text = h,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        viewModel.responseBody?.let { body ->
            Text("响应体", style = MaterialTheme.typography.titleSmall)
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}
