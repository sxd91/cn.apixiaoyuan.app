package cn.apixiaoyuan.app.feature.oldsimian

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.navigation.NavHostController
import cn.apixiaoyuan.app.core.design.component.AppScaffold
import cn.apixiaoyuan.app.core.navigation.RouteScorePump
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
 * 4. 自定义结算时间 —— 每题 `costTime` 固定为配置值（下限 5ms）
 * 5. 结束页自动化 —— PK 结算页自动开下一局（注入 JS）
 * 6. 去除排行榜展示动效 —— CSS 动画归零 + 静音（注入 JS）
 * 7. **PK 自动提交画笔** —— 题目页自动注入笔迹并触发画板提交
 *    （注入 `js/pk_auto_stroke.js`，遍历 Vue 组件树找活体画板）
 * 8. 自定义分数（刷分）—— 走 `PUT /leo-math/android/exams/v2/{examId}`
 *    （`uploadExamResult`，练习成绩上传主接口），循环「取卷 → 全对填充 → 上传」
 *    直到 `curWeekScore ≥ 目标`。算法在 [cn.apixiaoyuan.app.core.oldsimian.ScorePump]，
 *    状态机在 `ScorePumpViewModel`，参数页是本文件顶部的「打开刷分页」入口。
 *
 *    **此前的描述是错的**（已订正）：旧注释写「走
 *    `POST /leo-star/android/exercise/rank/login/attend`」—— 那是参考项目
 *    `postSavedExp` 的实际落点，**服务端限次（真机实测每天约 3 次）**，
 *    根本不是刷分该走的接口。
 *
 * ## 未接入（明确标注，不摆空壳骗自己）
 *
 *  - **无视名字限制**：本项目暂无昵称编辑入口；且昵称校验最终由服务端执行，
 *    客户端放开本地校验没有实际意义。等接入「个人资料编辑」时再接。
 *    （`LeoProfileApiService.updateUserInfo` 已存在可直接复用，但
 *    `UserVO.userId` / `primaryUserId` 是非空 `Int`，直接构造会输出
 *    `"userId":0` 污染请求，必须新建专用 body 类。）
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
                    summary = "每题耗时固定为下方数值（下限 5ms）",
                    checked = OldSimianPrefs.customCostEnabled,
                    onCheckedChange = {
                        OldSimianPrefs.customCostEnabled = it
                        OldSimianPrefs.persist()
                    },
                )
                if (OldSimianPrefs.customCostEnabled) {
                    // 用输入框而不是 Slider：范围 5..10000 跨度太大，
                    // 线性滑条每像素约 30ms，根本够不到 5ms 这种精确值。
                    CostFieldRow(
                        title = "每题耗时",
                        value = OldSimianPrefs.customCostMs,
                        range = OldSimianPrefs.COST_RANGE_MIN..OldSimianPrefs.COST_RANGE_MAX,
                        unit = " ms",
                        onCommit = {
                            OldSimianPrefs.customCostMs = it
                            OldSimianPrefs.persist()
                        },
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
                SwitchRow(
                    title = "自动提交画笔",
                    summary = "题目页自动注入笔迹并触发提交（遍历 Vue 组件树找活体画板）",
                    checked = OldSimianPrefs.pkStrokeEnabled,
                    onCheckedChange = {
                        OldSimianPrefs.pkStrokeEnabled = it
                        OldSimianPrefs.persist()
                    },
                )
                if (OldSimianPrefs.pkStrokeEnabled) {
                    CostFieldRow(
                        title = "提交次数",
                        value = OldSimianPrefs.pkStrokeCount,
                        range = OldSimianPrefs.PK_STROKE_COUNT_MIN..
                            OldSimianPrefs.PK_STROKE_COUNT_MAX,
                        onCommit = {
                            OldSimianPrefs.pkStrokeCount = it
                            OldSimianPrefs.persist()
                        },
                    )
                    CostFieldRow(
                        title = "两次提交间隔",
                        value = OldSimianPrefs.pkStrokeIntervalMs,
                        range = OldSimianPrefs.PK_STROKE_INTERVAL_MIN..
                            OldSimianPrefs.PK_STROKE_INTERVAL_MAX,
                        onCommit = {
                            OldSimianPrefs.pkStrokeIntervalMs = it
                            OldSimianPrefs.persist()
                        },
                    )
                }
            }

            // ==================== 分数 ====================
            SectionCard(title = "分数") {
                SwitchRow(
                    title = "自定义分数（刷分）",
                    summary = "循环「全对上传练习成绩」刷到目标分数，无日限；开启后进二级页设置参数",
                    checked = OldSimianPrefs.customScoreEnabled,
                    onCheckedChange = {
                        OldSimianPrefs.customScoreEnabled = it
                        OldSimianPrefs.persist()
                    },
                )
                if (OldSimianPrefs.customScoreEnabled) {
                    EntryRow(
                        title = "打开刷分页",
                        summary = "设置目标分数 / 知识点 / 每局题数与间隔",
                        onClick = { navController.navigate(RouteScorePump) },
                    )
                }
            }

            // ==================== 待接入 ====================
            SectionCard(title = "待接入") {
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

/**
 * 入口行：左标题+副标题，整行可点，右侧一个指示箭头。
 *
 * 与 [SwitchRow] 的区别是「点整行跳转」而不是「点开关」—— 二级页入口
 * 不该伪装成开关，所以不用 [Switch]，改用一个 `›` 提示可点。
 */
@Composable
private fun EntryRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, color = MiuixTheme.colorScheme.onSurfaceContainer)
            Text(text = summary, color = MiuixTheme.colorScheme.onSurfaceContainerVariant)
        }
        Text(text = "›", color = MiuixTheme.colorScheme.primary)
    }
}

/**
 * 数字输入行（通用）。
 *
 * 用 miuix [TextField]（`value: String` 重载）+ `KeyboardType.Number` 而不是
 * [SliderRow]：调用方的取值范围跨度都很大（耗时 5..10000、提交次数 1..50），
 * 线性滑条在 360dp 宽下每像素几十个单位，**根本选不到精确值**。
 * 输入框能精确落值，也顺手把非法输入过滤掉。
 *
 * 写盘时机：每次文本变化就解析并写盘，但**只在解析成功时**才写 ——
 * 清空重输的过程中会短暂出现空串，那时不动 prefs，避免把配置写成 0。
 *
 * `remember(value)` 而不是 `remember`：写盘后 `value` 被 `coerceIn` 夹过
 * （如输入 99999 → 10000），这一步会重建文本，让输入框立刻显示真实生效值，
 * 不会出现「框里 99999、实际 10000」的错位。
 *
 * @param title  行标题
 * @param value  当前生效值
 * @param range  合法区间；越界输入会被夹到边界后写盘
 * @param unit   单位后缀，仅用于「当前生效」提示
 */
@Composable
private fun CostFieldRow(
    title: String,
    value: Int,
    range: IntRange,
    unit: String = "",
    onCommit: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onSurfaceContainer,
        )
        TextField(
            value = text,
            onValueChange = { raw ->
                val digits = raw.filter(Char::isDigit)
                text = digits
                digits.toIntOrNull()?.let { parsed ->
                    val clamped = parsed.coerceIn(range.first, range.last)
                    if (clamped != value) onCommit(clamped)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = "${range.first}~${range.last}",
            useLabelAsPlaceholder = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
        Text(
            text = "当前生效：$value$unit",
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
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