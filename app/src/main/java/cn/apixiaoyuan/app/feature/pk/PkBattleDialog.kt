package cn.apixiaoyuan.app.feature.pk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.apixiaoyuan.app.core.pk.PkBattleEngine
import cn.apixiaoyuan.app.core.pk.PkMode
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * PK 秒结算 / 循环 / 多玩法并发的「弹窗刷轮数」（AOC 同款弹窗，但刷 PK）。
 *
 * ## 与 AOC 的区别
 *
 * AOC 的弹窗刷的是**练习**（刷分）；这个弹窗刷的是 **PK**，且「并发」语义是
 * **多玩法并发**（math / multi / final / 语文 同时各跑一条循环），不是同一玩法
 * 并发多局 —— 这是用户明确拍板的语义。
 *
 * ## 组件选型
 *
 * 外壳用 material3 [AlertDialog]（与 TotpGateDialog 一致的项目先例），
 * 内部内容用 miuix [Text] / [Switch] / [TextField]，直接吃 [MiuixTheme]。
 *
 * ## 参数
 *
 * - 轮数：每个玩法刷几轮；
 * - 知识点 ID：PK 首页 pointList 提供，默认 1；
 * - 玩法勾选：数学 / 多人 / 巅峰 / 语文；
 * - 失败重试上限（固定用 Engine 默认，暂不暴露输入，避免表单过重）。
 */
@Composable
fun PkBattleDialog(
    viewModel: PkBattleViewModel,
    onDismiss: () -> Unit,
) {
    var roundsText by remember { mutableStateOf("1") }
    var pointIdText by remember { mutableStateOf("1") }
    var selectedModes by remember { mutableStateOf(setOf(PkMode.MATH)) }

    val rounds = roundsText.toIntOrNull() ?: 0
    val pointId = pointIdText.toIntOrNull() ?: 1

    AlertDialog(
        onDismissRequest = {
            if (!viewModel.running) onDismiss()
        },
        title = { Text("PK 秒结算 · 刷轮数") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ---- 轮数 / 知识点 ----
                NumberField(
                    title = "轮数（每个玩法）",
                    value = roundsText,
                    placeholder = "刷几轮",
                    onValueChange = { roundsText = it.filter(Char::isDigit) },
                )
                NumberField(
                    title = "知识点 ID",
                    value = pointIdText,
                    placeholder = "PK 首页知识点编号",
                    onValueChange = { pointIdText = it.filter(Char::isDigit) },
                )

                // ---- 玩法勾选 ----
                Text(
                    text = "玩法（勾选即并发，各跑各的循环）",
                    color = MiuixTheme.colorScheme.onSurfaceContainer,
                )
                PkMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = mode.displayName,
                            color = MiuixTheme.colorScheme.onSurfaceContainer,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = mode in selectedModes,
                            onCheckedChange = { checked ->
                                selectedModes = if (checked) {
                                    selectedModes + mode
                                } else {
                                    selectedModes - mode
                                }
                            },
                        )
                    }
                }

                // ---- 运行状态 ----
                if (viewModel.running || viewModel.progress.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = viewModel.progress,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
                viewModel.message?.let {
                    Text(text = it, color = MiuixTheme.colorScheme.onSurfaceContainer)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !viewModel.running && rounds > 0 && selectedModes.isNotEmpty(),
                onClick = {
                    viewModel.start(
                        rounds = rounds,
                        modes = selectedModes,
                        pointId = pointId,
                        maxRetry = PkBattleEngine.DEFAULT_MAX_RETRY,
                    )
                },
            ) {
                Text(if (viewModel.running) "刷取中…" else "开始")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (viewModel.running) viewModel.stop() else onDismiss()
                },
            ) {
                Text(if (viewModel.running) "停止" else "关闭")
            }
        },
    )
}

/** 数字输入行（弹窗内，非过滤版本沿用 ScorePumpScreen 的写法）。 */
@Composable
private fun NumberField(
    title: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
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