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
import androidx.navigation.NavHostController
import cn.apixiaoyuan.app.core.design.component.AppScaffold
import cn.apixiaoyuan.app.core.oldsimian.OldSimianPrefs
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「自定义分数（刷分）」二级页。
 *
 * ## 与参考项目 cn.nizou.sxd `CustomScoreScreen` 的取舍
 *
 * 参考项目有两套模式（prefs `custom_score_mode`）：
 *  - 0 =「刷分增量」走 `postSavedExp` → 实为
 *    `POST /leo-star/android/exercise/rank/login/attend`，**服务端限次（真机实测每天约 3 次）**；
 *  - 1 =「真自定义分数」走练习成绩上传主接口 `PUT /leo-math/android/exams/v2/{examId}`。
 *
 * **本项目只保留模式 1**：模式 0 的接口有日限，刷不了几下就被拒，留着只会让用户
 * 以为功能坏了；而且它要构造 `LeoTodayExerciseListData`（字段至今未确证），是纯负债。
 *
 * 参考项目还有一处「`LegacyApiService.isReady()` 探测 + 未初始化提示」——
 * 那是 LSPosed 模块特有的问题（模块本体独立进程里拿不到宿主 ApiService）。
 * 本项目是内置客户端，接口就在自己进程里，**整块逻辑不需要**。
 *
 * ## 组件选型
 *
 * 全部用 miuix 组件（[Card] / [TextField] / [Button]），与「老挂戏老叟」页同一套
 * 视觉语言，直接吃 `MiuixTheme` 的莫奈色板。
 *
 * 数字输入用 [TextField] + `KeyboardType.Number` 而非 miuix 的 `NumberPicker`：
 * 目标分数没有合理上界（`NumberPicker` 必须给有限 `IntRange`，且 `visibleItemCount`
 * 要求奇数 ≥3），题目数与间隔同理 —— 输入框 + `coerceIn` 更贴合语义。
 *
 * ## 诚实告知
 *
 * 分数由服务端按**实际上传的练习记录**累计，客户端只能多刷几局逼近目标，
 * 不存在「直接改分数」的接口。页面底部的说明卡把这一点写清楚，不暗示做不到的事。
 */
@Composable
fun ScorePumpScreen(
    navController: NavHostController,
    viewModel: ScorePumpViewModel = viewModel(),
) {
    // 输入框初值取 prefs（上次的配置），本地状态不直接绑 prefs ——
    // 刷分参数是「开始那一刻的快照」，中途改输入框不该写盘、更不该影响正在跑的一轮。
    // 真正写盘发生在 ScorePumpViewModel.start() 里，一次性落定。
    var target by remember { mutableStateOf("") }
    var keypoint by remember { mutableStateOf(OldSimianPrefs.customScoreKeypoint) }
    var limit by remember { mutableStateOf(OldSimianPrefs.customScoreLimit.toString()) }
    var interval by remember { mutableStateOf(OldSimianPrefs.customScoreIntervalMs.toString()) }

    // 扫描到有效知识点后 prefs 会被写回，同步到输入框，让用户看得见「自动找到了哪个」。
    LaunchedEffect(OldSimianPrefs.customScoreKeypoint) {
        val kp = OldSimianPrefs.customScoreKeypoint
        if (kp.isNotEmpty() && kp != keypoint) keypoint = kp
    }

    val cur = viewModel.currentScore
    val goal = target.toIntOrNull()
    val lim = limit.toIntOrNull()?.coerceIn(
        OldSimianPrefs.SCORE_LIMIT_MIN,
        OldSimianPrefs.SCORE_LIMIT_MAX,
    ) ?: OldSimianPrefs.SCORE_LIMIT_DEFAULT
    val iv = interval.toLongOrNull()?.coerceIn(
        OldSimianPrefs.SCORE_INTERVAL_MIN.toLong(),
        OldSimianPrefs.SCORE_INTERVAL_MAX.toLong(),
    ) ?: OldSimianPrefs.SCORE_INTERVAL_DEFAULT.toLong()

    AppScaffold(title = "自定义分数", onBack = { navController.popBackStack() }) { pad: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(pad)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
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
                    text = "分数是本周分数（curWeekScore），由服务端按上传的练习记录累计。",
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
            }

            // ==================== 参数 ====================
            SectionCard(title = "参数") {
                NumberField(
                    title = "目标分数",
                    value = target,
                    placeholder = "必须大于当前分数",
                    onValueChange = { target = it.filter(Char::isDigit) },
                )
                NumberField(
                    title = "知识点 ID",
                    value = keypoint,
                    placeholder = "留空 = 自动扫描 1~${OldSimianPrefs.SCORE_KEYPOINT_MAX}",
                    onValueChange = { keypoint = it.filter(Char::isDigit) },
                )
                NumberField(
                    title = "每局题目数",
                    value = limit,
                    placeholder = "默认 ${OldSimianPrefs.SCORE_LIMIT_DEFAULT}" +
                        "（${OldSimianPrefs.SCORE_LIMIT_MIN}~${OldSimianPrefs.SCORE_LIMIT_MAX}）",
                    onValueChange = { limit = it.filter(Char::isDigit) },
                )
                NumberField(
                    title = "每局间隔（毫秒）",
                    value = interval,
                    placeholder = "默认 ${OldSimianPrefs.SCORE_INTERVAL_DEFAULT}" +
                        "（${OldSimianPrefs.SCORE_INTERVAL_MIN}~${OldSimianPrefs.SCORE_INTERVAL_MAX}）",
                    onValueChange = { interval = it.filter(Char::isDigit) },
                )
                Text(
                    text = "开始时会按以上数值取整（题目数 $lim、间隔 ${iv}ms）并写盘；" +
                        "知识点留空时，取题失败会从 1 遍历到 " +
                        "${OldSimianPrefs.SCORE_KEYPOINT_MAX} 找一个能出题的，找到后写回这里。",
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
                                viewModel.start(
                                    target = goal ?: 0,
                                    keypointId = keypoint,
                                    limit = lim,
                                    intervalMs = iv,
                                )
                            }
                        },
                        // 运行中永远可点（用来停止）；未运行时要求目标分数合法且已读到当前分数。
                        enabled = viewModel.running ||
                            (goal != null && cur != null && goal > cur),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (viewModel.running) "停止刷分" else "开始刷到目标分数")
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
                        text = "循环「取卷子 → 全对填充 → 上传」刷分，走练习成绩上传主接口" +
                            "（PUT /leo-math/android/exams/v2/{examId}），与「自动上分」同链路，" +
                            "没有登录参与接口的每天 3 次限制。",
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    )
                    Text(
                        text = "分数由服务端按实际上传的练习记录累计 —— 不存在「直接把分数改成 N」的" +
                            "接口，只能多刷几局逼近目标，可随时停止。",
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