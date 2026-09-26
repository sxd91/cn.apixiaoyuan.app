package cn.apixiaoyuan.app.feature.samples

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import cn.apixiaoyuan.app.core.database.Sample
import cn.apixiaoyuan.app.core.design.component.AppScrollScaffold
import cn.apixiaoyuan.app.core.design.icon.AppIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 样本库。
 *
 * 数据来自模块 12-13 落的 Room `sample` 表（[cn.apixiaoyuan.app.core.database.Sample]），
 * 列表通过 [cn.apixiaoyuan.app.core.samples.SampleRepository] 以 Flow 订阅，
 * 数据库一变页面就变。
 *
 * 两块内容：
 *  1. 重放结果面板 —— 最近一次重放的成败、状态码、耗时、响应体（未重放时不显示）
 *  2. 样本列表 —— 每条展示名字、方法、URL、是否需编码/解码、上次重放状态
 *
 * 顶栏与返回键由 [AppScaffold] 统一提供；
 * 悬浮底栏是浮层，内容不再为它预留 96dp —— 这正是玻璃透明感成立的前提。
 */
@Composable
fun SamplesScreen(
    navController: AppNavController,
    viewModel: SamplesViewModel = viewModel(),
) {
    AppScrollScaffold(title = "样本库", onBack = { navController.popBackStack() }) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = AppIcons.forKey("Samples"),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = if (viewModel.loading) "加载中" else "${viewModel.samples.size} 条样本",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when {
                viewModel.loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                viewModel.samples.isEmpty() -> EmptyState()

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            bottom = 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        viewModel.lastReplay?.let { replay ->
                            item(key = "replay-result") {
                                ReplayResultCard(replay = replay)
                            }
                        }

                        items(viewModel.samples, key = { it.id }) { sample ->
                            SampleCard(
                                sample = sample,
                                replaying = viewModel.replayingId == sample.id,
                                onReplay = { viewModel.replay(sample.id) },
                                onDelete = { viewModel.delete(sample.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 空态。说明样本从哪来，避免用户以为是 bug。 */
@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "还没有样本",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "在协议请求台里把感兴趣的请求存为样本",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 重放结果面板。
 *
 * 展示最近一次重放的完整结果。响应体可能很长，限高 240dp 并允许滚动，
 * 避免一个巨大响应把列表挤没。
 */
@Composable
private fun ReplayResultCard(replay: ReplayResult) {
    val okColor = if (replay.success) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (replay.success) "重放成功" else "重放失败",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = okColor,
                )
                Text(
                    text = "HTTP ${replay.statusCode} · ${replay.durationMs}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            replay.error?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (replay.body.isNotEmpty()) {
                Text(
                    text = replay.body.take(4000),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                )
                if (replay.body.length > 4000) {
                    Text(
                        text = "（响应体已截断，完整长度 ${replay.body.length} 字符）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * 单条样本卡。
 *
 * 展示：名字 + 方法 + URL + 编码/解码标记 + 上次重放状态；
 * 操作：重放（进行中显示进度）、删除。
 */
@Composable
private fun SampleCard(
    sample: Sample,
    replaying: Boolean,
    onReplay: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = sample.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = sample.method.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Text(
                text = sample.url,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
            )

            val flags = buildList {
                if (sample.needEncode) add("需编码")
                if (sample.needDecode) add("需解码")
                sample.lastReplayedAt?.let { ts ->
                    val ok = sample.lastReplaySuccess == true
                    add("上次重放 ${if (ok) "成功" else "失败"} · ${formatTime(ts)}")
                }
            }
            if (flags.isNotEmpty()) {
                Text(
                    text = flags.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (replaying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
                TextButton(onClick = onReplay, enabled = !replaying) {
                    Text("重放")
                }
                TextButton(onClick = onDelete, enabled = !replaying) {
                    Text("删除")
                }
            }
        }
    }
}

/** 把 epoch millis 格式化成 `MM-dd HH:mm`。 */
private fun formatTime(ts: Long): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(ts))
}
