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
import androidx.navigation.NavHostController
import cn.apixiaoyuan.app.core.design.component.AppScaffold

/**
 * 协议请求台。
 *
 * 顶栏由 [AppScaffold] 统一提供，statusBars（刘海/状态栏）留白由它负责；
 * 悬浮玻璃底栏是浮层，内容不再为它预留 96dp ——
 * 内容可以滑到底部被底栏遮住，这正是玻璃透明感成立的前提。
 */
@Composable
fun ReplScreen(navController: NavHostController, viewModel: ReplViewModel = viewModel()) {
    AppScaffold(title = "请求台", onBack = null) { pad: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(pad)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "协议请求台",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            OutlinedTextField(
                value = viewModel.method,
                onValueChange = viewModel::onMethodChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("方法") },
                singleLine = true,
            )
            OutlinedTextField(
                value = viewModel.url,
                onValueChange = viewModel::onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("URL") },
                singleLine = true,
            )
            OutlinedTextField(
                value = viewModel.body,
                onValueChange = viewModel::onBodyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("请求体") },
                minLines = 4,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { viewModel.send() }) { Text("发送") }
                TextButton(onClick = { viewModel.saveAsSample() }) { Text("存为样本") }
            }

            viewModel.lastResult?.let { result ->
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
                            text = "HTTP ${result.statusCode} · ${result.durationMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = result.body,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                text = "请求头",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            viewModel.headers.forEach { h ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Text(
                        text = "${h.name}: ${h.value}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}