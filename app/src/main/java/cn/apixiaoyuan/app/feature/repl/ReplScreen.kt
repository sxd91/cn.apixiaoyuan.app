package cn.apixiaoyuan.app.feature.repl

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController

/**
 * 协议请求台。
 *
 * 与接口浏览器（`feature/api`）的分工：
 *  - 接口浏览器：路径与参数固定，用户只填值，从 `ApiRegistry` 渲染表单
 *  - 请求台（本页）：URL / Method / Headers / Body 全部自由输入，
 *    用于打未落盘 Kotlin 定义的接口、或清单外的任意请求
 *
 * 两者共享 `core.session.PersistentCookieJar`，登录态一致；
 * 都不走 `@NeedDecode` 拦截，看到的是原始响应字节。
 *
 * 底部预留 96dp 给 LiquidGlassTabBar。
 */
@Composable
fun ReplScreen(
    navController: NavHostController,
    viewModel: ReplViewModel = viewModel(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "协议请求台",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "任意 URL / Method / Headers / Body，原始响应展示",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()

        // ---- Method 选择 ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            viewModel.methods.take(5).forEach { m ->
                FilterChip(
                    selected = viewModel.requestMethod == m,
                    onClick = { viewModel.selectMethod(m) },
                    label = { Text(m, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }

        // ---- URL ----
        OutlinedTextField(
            value = viewModel.url,
            onValueChange = { viewModel.onUrlChanged(it) },
            label = { Text("URL") },
            placeholder = { Text("https://xyks.yuanfudao.com/leo-...") },
            singleLine = false,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        // ---- 域名快捷填充 ----
        val hosts = viewModel.hostOptions()
        if (hosts.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "域名",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                hosts.forEach { (alias, _) ->
                    AssistChip(
                        onClick = { viewModel.applyHost(alias) },
                        label = {
                            Text(
                                text = alias,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }
        }

        // ---- Headers ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("请求头", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { viewModel.addHeader() }) { Text("+") }
        }
        viewModel.headerLines.forEachIndexed { index, line ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = line,
                    onValueChange = { viewModel.setHeader(index, it) },
                    placeholder = { Text("Name: Value") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { viewModel.removeHeader(index) }) { Text("×") }
            }
        }

        // ---- Body ----
        if (viewModel.requestMethod in listOf("POST", "PUT", "PATCH", "DELETE")) {
            OutlinedTextField(
                value = viewModel.body,
                onValueChange = { viewModel.body = it },
                label = { Text("请求体 (JSON)") },
                placeholder = { Text("{\n  \"key\": \"value\"\n}") },
                minLines = 3,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        HorizontalDivider()

        // ---- 发送 ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.material3.Button(
                onClick = { viewModel.send() },
                enabled = !viewModel.loading,
                modifier = Modifier.weight(1f),
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

            androidx.compose.material3.OutlinedButton(
                onClick = { viewModel.saveSample() },
                enabled = viewModel.url.isNotBlank(),
            ) {
                Text("存为样本")
            }
        }

        viewModel.sampleHint?.let { hint ->
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = if (viewModel.sampleSaved) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
            )
        }

        // ---- 响应状态 ----
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

        // ---- 响应头 ----
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

        // ---- 响应体 ----
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

        // ---- 请求历史 ----
        if (viewModel.history.isNotEmpty()) {
            HorizontalDivider()
            Text("请求历史", style = MaterialTheme.typography.titleSmall)
            viewModel.history.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (entry.status in 200..299)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = entry.method,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = entry.url,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${entry.status} · ${entry.elapsedMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}