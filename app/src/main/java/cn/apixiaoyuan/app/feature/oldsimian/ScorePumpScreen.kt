package cn.apixiaoyuan.app.feature.oldsimian

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import cn.apixiaoyuan.app.core.navigation.AppNavController
import cn.apixiaoyuan.app.core.design.component.AppScrollScaffold
import cn.apixiaoyuan.app.core.oldsimian.OldSimianPrefs
import cn.apixiaoyuan.app.core.oldsimian.ScorePump
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「自定义分数」二级页（增量模式）。
 *
 * ## 语义（2026-09-25 按用户拍板重写：增量模式）
 *
 * 参考项目 cn.nizou.sxd `Score` prefs 的 `custom_score_mode`：
 * 0 = 刷分增量（`postSavedExp`）、1 = 真自定义分数（`uploadExamResult`）。
 * 此前本项目选了模式 1（整卷上传），是**误判** —— 用户要的是模式 0。
 *
 * 模式 0 的协议已从 smali 逐行确证（详见 [ScorePump] KDoc）：
 * `postSavedExp` 的 body 是增量记录列表（`obtainExp` = 本次获得经验），
 * **不是**之前注释里说的「每天限 3 次」的那种限次语义 —— 那条结论
 * 来自参考项目对旧版接口的观察，与当前 body 结构（今日增量列表）不符，
 * 已修正。用户给一个增量值，客户端拆条上报即可。
 *
 * ## 组件选型
 *
 * 全部用 miuix 组件（[Card] / [TextField] / [Button]），与「老挂戏老叟」页同一套
 * 视觉语言，直接吃 `MiuixTheme` 的莫奈色板。
 */
@Composable
fun ScorePumpScreen(
    navController: AppNavController,
    viewModel: ScorePumpViewModel = viewModel(),
) {
    // 输入框初值取 prefs（上次的配置），本地状态不直接绑 prefs ——
    // 上报参数是「开始那一刻的快照」，中途改输入框不该写盘、更不该影响正在跑的一轮。
    var delta by remember { mutableStateOf(OldSimianPrefs.customScoreValue.takeIf { it > 0 }?.toString().orEmpty()) }

    val cur = viewModel.currentScore
    val deltaVal = delta.toIntOrNull() ?: 0

    AppScrollScaffold(title = "自定义分数", onBack = { navController.popBackStack() }) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ==================== 当前分数 ====================
            SectionCard(title = "当前分数") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when {
                            viewModel.loadingScore -> "读取中…"
                            cur != null -> "$cur"
                            else -> "—"
                        },
                        color = MiuixTheme.colorScheme.onSurfaceContainer,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { viewModel.refreshScore() }) { Text("刷新") }
                }
                viewModel.scoreError?.let {
                    Text(text = it, color = MiuixTheme.colorScheme.error)
                }
                Text(
                    text = "分数是本周分数（curWeekScore），由服务端按上报的增量记录累计。",
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
            }

            // ==================== 参数 ====================
            SectionCard(title = "参数") {
                NumberField(
                    title = "增加分数（增量）",
                    value = delta,
                    placeholder = "要加多少分，如 100",
                    onValueChange = { delta = it.filter(Char::isDigit) },
                )
                Text(
                    text = "单条增量上限 ${ScorePump.PER_ITEM_MAX}，超出自动拆条分批上报" +
                        "（单次最多 ${ScorePump.MAX_ITEMS_PER_BATCH} 条）。",
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
            }

            // ==================== 运行 ====================
            SectionCard(title = "运行") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            if (viewModel.running) {
                                viewModel.stop()
                            } else {
                                viewModel.start(delta = deltaVal)
                            }
                        },
                        enabled = viewModel.running || deltaVal > 0,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (viewModel.running) "停止" else "开始上报增量")
                    }
                }
                if (viewModel.progress.isNotEmpty()) {
                    Text(text = viewModel.progress, color = MiuixTheme.colorScheme.primary)
                }
                viewModel.message?.let {
                    Text(text = it, color = MiuixTheme.colorScheme.onSurfaceContainer)
                }
            }

            // ==================== 说明 ====================
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
                        text = "增量模式：走 POST /leo-star/android/exercise/rank/login/attend，" +
                            "body 为今日练习增量记录列表（finishTime / obtainExp / ruleType），" +
                            "obtainExp 即本次获得的经验值 —— 给多少加多少，服务端累计到周分数。",
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    )
                    Text(
                        text = "⚠️ 该端点在主域上，sign 参数未破前实测会被 417 拦截" +
                            "（solar-encoder）。代码已按确证协议写好，等 sign 后即可用。",
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    )
                    Text(
                        text = "「自定义分数（刷分）」开关默认关闭，需先在「老挂戏老叟」页打开。",
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    )
                }
            }
        }
    }
}

/** 分组卡片：标题 + 若干行。与「老挂戏老叟」页同款写法。 */
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
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                content()
            }
        }
    }
}

/**
 * 数字输入行。
 *
 * 用 `value: String` 重载的 [TextField]（miuix 三个重载里最直白的一个）+
 * `KeyboardType.Number`。过滤非数字在 `onValueChange` 里做 —— 目标分数、
 * 题目数、间隔都不允许负号与小数点，直接在入口拦掉比事后再 `coerceIn` 更清楚。
 */
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