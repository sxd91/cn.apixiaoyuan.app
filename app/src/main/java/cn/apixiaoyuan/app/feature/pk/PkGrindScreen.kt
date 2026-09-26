package cn.apixiaoyuan.app.feature.pk

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.apixiaoyuan.app.core.account.SubAccountItem
import cn.apixiaoyuan.app.core.design.component.AppScrollScaffold
import cn.apixiaoyuan.app.core.navigation.AppNavController
import cn.apixiaoyuan.app.core.pk.PkPointItem
import cn.apixiaoyuan.app.core.pk.PkStrokeMode
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PkGrindScreen(
    navController: AppNavController,
    viewModel: PkGrindViewModel = viewModel(),
) {
    var roundsText by remember { mutableStateOf("1") }
    var costTimeText by remember { mutableStateOf("") }
    var submitDelayText by remember { mutableStateOf("0") }
    var roundIntervalText by remember { mutableStateOf("0") }
    var selectedPointId by remember { mutableStateOf(0) }
    var strokeMode by remember { mutableStateOf(PkStrokeMode.ARC) }

    val rounds = roundsText.toIntOrNull() ?: 0
    val costTimeMs = costTimeText.toLongOrNull()
    val submitDelayMs = submitDelayText.toLongOrNull() ?: 0L
    val roundIntervalMs = roundIntervalText.toLongOrNull() ?: 0L

    AppScrollScaffold(title = "刷 PK 对局", onBack = { navController.popBackStack() }) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = "账号") {
                Text(
                    text = "当前 userid：${viewModel.currentUserId ?: "未登录"}",
                    color = MiuixTheme.colorScheme.onSurfaceContainer,
                )
                val subs = viewModel.subAccounts
                if (subs.isNullOrEmpty()) {
                    Text(
                        text = "无子账号（或未登录）",
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    )
                } else {
                    subs.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.switchSubAccount(item) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = item.nickname +
                                    (if (item.isCurrent) "（当前）" else "") +
                                    (if (item.isPrimary) " · 主账号" else ""),
                                color = if (item.isCurrent) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.onSurfaceContainer,
                                modifier = Modifier.weight(1f),
                            )
                            Text(text = item.userId.toString(), color = MiuixTheme.colorScheme.onSurfaceContainerVariant)
                        }
                    }
                }
            }

            SectionCard(title = "分数") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "总胜场 ${viewModel.totalWinCount ?: "—"} · 本周 ${viewModel.weekWinCount ?: "—"}",
                        color = MiuixTheme.colorScheme.onSurfaceContainer,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { viewModel.refreshAll() }) { Text("刷新") }
                }
            }

            SectionCard(title = "对局类型") {
                val points = viewModel.points
                when {
                    viewModel.loading -> Text("加载中…", color = MiuixTheme.colorScheme.onSurfaceContainerVariant)
                    viewModel.loadError != null -> Text(
                        text = viewModel.loadError ?: "",
                        color = MiuixTheme.colorScheme.error,
                    )
                    points.isNullOrEmpty() -> Text(
                        text = "无可用对局类型",
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    )
                    else -> points.forEach { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPointId = p.pointId }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = (if (selectedPointId == p.pointId) "✓ " else "") +
                                    (p.pointName ?: "知识点 ${p.pointId}"),
                                color = if (selectedPointId == p.pointId) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.onSurfaceContainer,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "胜 ${p.winCount}",
                                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                            )
                        }
                    }
                }
            }

            SectionCard(title = "参数") {
                NumberField(
                    title = "对局数",
                    value = roundsText,
                    placeholder = "刷几局",
                    onValueChange = { roundsText = it.filter(Char::isDigit) },
                )
                NumberField(
                    title = "costTime（毫秒，留空=自动）",
                    value = costTimeText,
                    placeholder = "如 100，留空自动",
                    onValueChange = { costTimeText = it.filter(Char::isDigit) },
                )
                NumberField(
                    title = "出题后提交间隔（毫秒）",
                    value = submitDelayText,
                    placeholder = "如 0",
                    onValueChange = { submitDelayText = it.filter(Char::isDigit) },
                )
                NumberField(
                    title = "循环间隔（毫秒）",
                    value = roundIntervalText,
                    placeholder = "每局之间，如 0",
                    onValueChange = { roundIntervalText = it.filter(Char::isDigit) },
                )
                Text(
                    text = "画笔算法",
                    color = MiuixTheme.colorScheme.onSurfaceContainer,
                )
                PkStrokeMode.entries.forEach { m ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { strokeMode = m }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = (if (strokeMode == m) "✓ " else "") + m.displayName,
                            color = if (strokeMode == m) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.onSurfaceContainer,
                        )
                    }
                }
            }

            SectionCard(title = "运行") {
                Button(
                    onClick = {
                        if (viewModel.running) {
                            viewModel.stop()
                        } else {
                            viewModel.start(
                                rounds = rounds,
                                pointId = selectedPointId,
                                costTimeMs = costTimeMs,
                                submitDelayMs = submitDelayMs,
                                roundIntervalMs = roundIntervalMs,
                                strokeMode = strokeMode,
                            )
                        }
                    },
                    enabled = viewModel.running || (rounds > 0 && selectedPointId > 0),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (viewModel.running) "停止" else "开始 API 刷局")
                }
                if (viewModel.progress.isNotEmpty()) {
                    Text(text = viewModel.progress, color = MiuixTheme.colorScheme.primary)
                }
                viewModel.message?.let {
                    Text(text = it, color = MiuixTheme.colorScheme.onSurfaceContainer)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MiuixTheme.colorScheme.onSurfaceContainerHigh,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "说明",
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurfaceContainerHigh,
                    )
                    Text(
                        text = "纯 API 刷局：出题（旧版明文 match）→ 弧线笔迹组装 body → " +
                            "gzip+原生加密 → sign → 提交。提交接口有独立频控（约数分钟级），" +
                            "连续刷局过快会被 400/403 拦截，建议循环间隔 ≥ 5 分钟。",
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainer,
                contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun NumberField(
    title: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, color = MiuixTheme.colorScheme.onSurfaceContainer)
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = placeholder,
            useLabelAsPlaceholder = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
    }
}
