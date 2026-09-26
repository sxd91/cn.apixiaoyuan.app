package cn.apixiaoyuan.app.feature.repl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.apixiaoyuan.app.core.navigation.AppNavController
import cn.apixiaoyuan.app.core.design.component.AppScrollScaffold

/**
 * 协议请求台。
 *
 * 顶栏由 [AppScaffold] 统一提供，statusBars（刘海/状态栏）留白由它负责；
 * 悬浮玻璃底栏是浮层，内容不再为它预留 96dp ——
 * 内容可以滑到底部被底栏遮住，这正是玻璃透明感成立的前提。
 */
@Composable
fun ReplScreen(navController: AppNavController, viewModel: ReplViewModel = viewModel()) {
    AppScrollScaffold(title = "请求台", onBack = null) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "协议请求台",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                viewModel.hostOptions().forEach { (alias, _) ->
                    TextButton(onClick = { viewModel.applyHost(alias) }) { Text(alias) }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                viewModel.methods.take(4).forEach { m ->
                    TextButton(
                        onClick = { viewModel.selectMethod(m) },
                        enabled = viewModel.requestMethod != m,
                    ) {
                        Text(m)
                    }
                }
            }

            OutlinedTextField(
                value = viewModel.url,
                onValueChange = { viewModel.onUrlChanged(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("URL") },
                singleLine = true,
            )

            OutlinedTextField(
                value = viewModel.body,
                onValueChange = { viewModel.body = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("请求体（POST/PUT/PATCH）") },
                minLines = 3,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { viewModel.send() }, enabled = !viewModel.loading) {
                    Text(if (viewModel.loading) "发送中…" else "发送")
                }
                TextButton(onClick = { viewModel.saveSample() }) { Text("存为样本") }
            }

            viewModel.sampleHint?.let { hint ->
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            viewModel.errorMessage?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (viewModel.statusCode != -1) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "HTTP ${viewModel.statusCode} · ${viewModel.elapsedMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        viewModel.responseBody?.let { bodyText ->
                            Text(
                                text = bodyText,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Text(
                text = "请求头",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            viewModel.headerLines.forEachIndexed { index, line ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = line,
                        onValueChange = { viewModel.setHeader(index, it) },
                        modifier = Modifier.weight(1f),
                        label = { Text("name: value") },
                        singleLine = true,
                    )
                    TextButton(onClick = { viewModel.removeHeader(index) }) { Text("删") }
                }
            }
            TextButton(onClick = { viewModel.addHeader() }) { Text("+ 请求头") }

            if (viewModel.history.isNotEmpty()) {
                Text(
                    text = "最近请求",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                viewModel.history.take(8).forEach { entry ->
                    Text(
                        text = "${entry.method} ${entry.status} ${entry.elapsedMs}ms  ${entry.url}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}