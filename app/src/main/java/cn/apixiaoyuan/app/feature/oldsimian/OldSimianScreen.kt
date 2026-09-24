package cn.apixiaoyuan.app.feature.oldsimian

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cn.apixiaoyuan.app.core.design.component.AppScaffold
import cn.apixiaoyuan.app.core.oldsimian.OldSimianPrefs
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「老挂戏老叟」功能页（设置 → 老挂戏老叟）。
 *
 * **页面完全用本项目自己的 UI 搭建**（[AppScaffold] 顶栏 + miuix 组件），
 * 不复用 cn.nizou.sxd 的 `MainPagerScreen` / `CustomScoreScreen` 等任何页面 ——
 * 这是用户明确要求的：「页面就不要用老挂戏老叟的了」。
 *
 * 组件选型：miuix 的 [Card] / [Switch] / [Slider] / [Text]。
 * 理由：本项目主题根已切到 [MiuixTheme]（见 `MiuixAppTheme`），
 * 用 miuix 组件能直接吃到同一套莫奈色板，与顶栏渐变模糊风格统一。
 *
 * 与 cn.nizou.sxd 的实现差异（**重要**）：
 *  - 那边是 LSPosed 模块，开关生效靠 hook 宿主进程；
 *  - 这里是内置客户端，开关直接作用于**本项目自己组装请求体**的代码路径
 *    （见 [cn.apixiaoyuan.app.feature.exercise.ExamViewModel.buildSubmitBody]），
 *    无需任何 hook。
 *
 * 当前已接入的能力（B1 批次）：
 *  1. 自动全部答对 —— 练习提交时用服务端下发的正确答案填 `userAnswer`
 *  2. 自定义结算时间 —— 每题 `costTime` 固定为配置值（服务端下限 300ms）
 *
 * 未接入的能力（B2 / B3，待后续批次）：
 *  - 自定义分数（刷分）、结束页自动化、去除排行榜动效、提交画笔
 */
@Composable
fun OldSimianScreen(navController: NavHostController) {
    AppScaffold(title = "老挂戏老叟", onBack = { navController.popBackStack() }) { pad: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(pad)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- 练习 ----
            SectionCard(title = "练习") {
                SwitchRow(
                    title = "自动全部答对",
                    summary = "提交时用服务端下发的正确答案作答，整卷全对",
                    checked = OldSimianPrefs.autoCorrect,
                    onCheckedChange = {
                        OldSimianPrefs.autoCorrect = it
                        OldSimianPrefs.persist()
                    },
                )
                SwitchRow(
                    title = "自定义结算时间",
                    summary = "每题耗时固定为下方数值（服务端下限 300ms）",
                    checked = OldSimianPrefs.customCostEnabled,
                    onCheckedChange = {
                        OldSimianPrefs.customCostEnabled = it
                        OldSimianPrefs.persist()
                    },
                )
                if (OldSimianPrefs.customCostEnabled) {
                    SliderRow(
                        title = "每题耗时",
                        value = OldSimianPrefs.customCostMs.toFloat(),
                        valueRange = OldSimianPrefs.COST_RANGE_MIN.toFloat()..
                            OldSimianPrefs.COST_RANGE_MAX.toFloat(),
                        onValueChange = { OldSimianPrefs.customCostMs = it.toInt() },
                        onValueChangeFinished = { OldSimianPrefs.persist() },
                    )
                }
            }

            // ---- 说明 ----
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
                        text = "关于",
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurfaceContainerHigh,
                    )
                    Text(
                        text = "本项目是内置客户端：功能直接作用于自己发出的请求，" +
                            "不需要 hook 宿主进程。",
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    )
                    Text(
                        text = "刷分、结束页自动化、提交画笔将在后续批次接入。",
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    )
                }
            }
        }
    }
}

/** 分组卡片：标题 + 若干行。 */
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
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                content()
            }
        }
    }
}

/** 开关行：左标题+副标题，右 Switch。 */
@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, color = MiuixTheme.colorScheme.onSurfaceContainer)
            Text(text = summary, color = MiuixTheme.colorScheme.onSurfaceContainerVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 滑条行：标题 + 当前值 + Slider。 */
@Composable
private fun SliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onSurfaceContainer,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${value.toInt()} ms",
                color = MiuixTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
        )
    }
}
