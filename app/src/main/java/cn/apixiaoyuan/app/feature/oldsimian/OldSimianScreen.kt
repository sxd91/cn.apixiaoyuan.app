package cn.apixiaoyuan.app.feature.oldsimian

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「老挂戏老叟」功能页（设置 → 老挂戏老叟）。
 *
 * ## 页面纪律
 *
 * **页面完全用本项目自己的 UI 搭建**（[AppScaffold] 顶栏 + miuix 组件），
 * 不复用 cn.nizou.sxd 的 `MainPagerScreen` / `CustomScoreScreen` 等任何页面 ——
 * 这是用户明确要求的：「页面就不要用老挂戏老叟的了」。
 *
 * 组件选型：miuix 的 [Card] / [Switch] / [Slider] / [TextField]。
 * 理由：本项目主题根已切到 [MiuixTheme]（见 `MiuixAppTheme`），
 * 用 miuix 组件能直接吃到同一套莫奈色板，与顶栏渐变模糊风格统一。
 *
 * ## 与 cn.nizou.sxd 的根本差异（**重要**）
 *
 * | | cn.nizou.sxd | 本项目 |
 * |---|---|---|
 * | 架构 | LSPosed 模块，hook 宿主进程 | **内置客户端**，自己发请求 |
 * | 练习改答案 | hook 宿主的 Presenter / 提交包 | 改自己组装的 [cn.apixiaoyuan.app.core.model.ExamData] |
 * | PK 自动化 | hook 宿主的 `WebView.loadUrl` 注入 JS | 自己的 WebView 直接 `evaluateJavascript` |
 * | 笔迹 | `libauto_oral.so`（Rust jni） | 纯 Kotlin 字形库 [cn.apixiaoyuan.app.core.oldsimian.OralStrokes] |
 *
 * 所以**没有任何一行 hook 代码**，功能靠内置链路原生实现。
 *
 * ## 已接入（本轮）
 *
 * 1. 自动全部答对 —— 提交时用服务端下发的正确答案作答
 * 2. 自定义答案 —— 提交的 `userAnswer` 固定为指定值
 * 3. 提交画笔 —— 每题按实际作答生成笔迹 `script`（N 题 → N 条笔迹）
 * 4. 自定义结算时间 —— 每题 `costTime` 固定为配置值（服务端下限 300ms）
 * 5. 结束页自动化 —— PK 结算页自动开下一局（注入 JS）
 * 6. 去除排行榜展示动效 —— CSS 动画归零 + 静音（注入 JS）
 *
 * ## 未接入（明确标注，不摆空壳骗自己）
 *
 *  - **自定义分数（刷分）**：走 `POST /leo-star/android/exercise/rank/login/attend`，
 *    该接口 body 带 `@NeedEncode`，本项目 native 编码器（`EncodeBridge`）仍是恒等实现，
 *    且请求体模型 `LeoTodayExerciseListData` 字段是从 smali 推断的 —— 需 native 编码
 *    接入 + 真机确证字段后才可安全调用，否则只会拿 4xx。
 *  - **无视名字限制**：本项目暂无昵称编辑入口；且昵称校验最终由服务端执行，
 *    客户端放开本地校验没有实际意义。等接入「个人资料编辑」时再接。
 */
@Composable
fun OldSimianScreen(navController: NavHostController) {
    AppScaffold(title = "老挂戏老叟", onBack = { navController.popBackStack() }) { pad: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(pad)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ==================== 练习 ====================
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
                    title = "自定义答案",
                    summary = "提交的作答固定为下方内容；判对错仍按是否等于正确答案",
                    checked = OldSimianPrefs.customAnswerEnabled,
                    onCheckedChange = {
                        OldSimianPrefs.customAnswerEnabled = it
                        OldSimianPrefs.persist()
                    },
                )
                if (OldSimianPrefs.customAnswerEnabled) {
                    AnswerFieldRow(
                        value = OldSimianPrefs.customAnswerText,
                        onValueChange = {
                            OldSimianPrefs.customAnswerText = it
                            OldSimianPrefs.persist()
                        },
                    )
                }
                SwitchRow(
                    title = "提交画笔",
                    summary = "按题目数量提交等量笔迹（N 道题生成 N 条 script）",
                    checked = OldSimianPrefs.strokeEnabled,
                    onCheckedChange = {
                        OldSimianPrefs.strokeEnabled = it
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
                        valueText = "${OldSimianPrefs.customCostMs} ms",
                        value = OldSimianPrefs.customCostMs.toFloat(),
                        valueRange = OldSimianPrefs.COST_RANGE_MIN.toFloat()..
                            OldSimianPrefs.COST_RANGE_MAX.toFloat(),
                        onValueChange = { OldSimianPrefs.customCostMs = it.toInt() },
                        onValueChangeFinished = { OldSimianPrefs.persist() },
                    )
                }
            }

            // ==================== PK ====================
            SectionCard(title = "PK") {
                SwitchRow(
                    title = "结束页自动化",
                    summary = "结算页自动开下一局（注入 H5 脚本，三级策略）",
                    checked = OldSimianPrefs.autoNextRound,
                    onCheckedChange = {
                        OldSimianPrefs.autoNextRound = it
                        OldSimianPrefs.persist()
                    },
                )
                if (OldSimianPrefs.autoNextRound) {
                    SliderRow(
                        title = "开下一局间隔",
                        valueText = "${OldSimianPrefs.nextRoundIntervalMs} ms",
                        value = OldSimianPrefs.nextRoundIntervalMs.toFloat(),
                        valueRange = OldSimianPrefs.NEXT_ROUND_INTERVAL_MIN.toFloat()..
                            OldSimianPrefs.NEXT_ROUND_INTERVAL_MAX.toFloat(),
                        onValueChange = { OldSimianPrefs.nextRoundIntervalMs = it.toInt() },
                        onValueChangeFinished = { OldSimianPrefs.persist() },
                    )
                }
                SwitchRow(
                    title = "去除排行榜展示动效",
                    summary = "CSS 动画归零 + 音效静音（只动样式，不碰答题节奏）",
                    checked = OldSimianPrefs.noRankingAnim,
                    onCheckedChange = {
                        OldSimianPrefs.noRankingAnim = it
                        OldSimianPrefs.persist()
                    },
                )
            }

            // ==================== 待接入 ====================
            SectionCard(title = "待接入") {
                SwitchRow(
                    title = "自定义分数（刷分）",
                    summary = "待 native 请求编码器接入后启用：上报接口 body 需编码",
                    checked = OldSimianPrefs.customScoreEnabled,
                    enabled = false,
                    onCheckedChange = {},
                )
                SwitchRow(
                    title = "无视名字限制",
                    summary = "待接入「个人资料编辑」后启用：本项目当前无昵称编辑链路",
                    checked = OldSimianPrefs.ignoreNicknameRestriction,
                    enabled = false,
                    onCheckedChange = {},
                )
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
                        text = "关于",
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurfaceContainerHigh,
                    )
                    Text(
                        text = "本项目是内置客户端：功能直接作用于自己发出的请求与" +
                            "自己的 WebView，不 hook 任何进程。",
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    )
                    Text(
                        text = "所有开关默认关闭。开启「自动全部答对」「自定义答案」" +
                            "会改变练习记录的真实性，请自行判断是否使用。",
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
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
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
    enabled: Boolean = true,
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
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

/** 滑条行：标题 + 当前值 + Slider。 */
@Composable
private fun SliderRow(
    title: String,
    valueText: String,
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
            Text(text = valueText, color = MiuixTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
        )
    }
}

/**
 * 自定义答案输入行。
 *
 * 用 miuix [TextField] + `rememberTextFieldState`（Compose Foundation 的新
 * TextField API，miuix 的 `TextField(state: TextFieldState, ...)` 就是这个签名）。
 *
 * 持久化时机：`LaunchedEffect(state.text)` —— 文本每变一次就写盘。
 * 这里不防抖是刻意的：答案文本很短（上限 [OldSimianPrefs.CUSTOM_ANSWER_MAX_LEN]
 * 个字符），写盘是 SharedPreferences 的 apply()（异步），开销可忽略。
 */
@Composable
private fun AnswerFieldRow(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val state = rememberTextFieldState(value)
    LaunchedEffect(state.text.toString()) {
        val text = state.text.toString().take(OldSimianPrefs.CUSTOM_ANSWER_MAX_LEN)
        if (text != value) onValueChange(text)
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "答案内容",
            color = MiuixTheme.colorScheme.onSurfaceContainer,
        )
        TextField(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            label = "如 12 或 +",
            useLabelAsPlaceholder = true,
        )
    }
}