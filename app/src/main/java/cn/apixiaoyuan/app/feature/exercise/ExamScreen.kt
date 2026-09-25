package cn.apixiaoyuan.app.feature.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import cn.apixiaoyuan.app.core.design.component.AppScaffold
import cn.apixiaoyuan.app.core.model.ExamData
import cn.apixiaoyuan.app.core.model.ExamQuestion
import cn.apixiaoyuan.app.core.oldsimian.OldSimianPrefs

/**
 * 答题页。
 *
 * 出题链路的后半段：
 * ```
 * 进页面 → getExamInfo(keypointId, limit) → 拉回整套题
 *        → 逐题作答（本地填 userAnswer / status / costTime）
 *        → uploadExamResult(examId, body) 提交
 *        → 展示批改结果
 * ```
 *
 * **当前实现范围**：出题 + 列表展示 + 逐题「对/错」标记 + 提交。
 * 手写板（原版 `ScriptBoard` + tflite 笔迹识别）不在本轮——那是独立的
 * 识别链路，本页先用「点选答案」方式完成作答闭环，保证整条链路可跑通。
 *
 * 每题 `costTime` 下限 **5ms**（真机实测边界），作答时间从进页面开始计。
 *
 * @param keypointId 知识点 ID
 * @param limit      题目数量
 * @param title      知识点名（顶栏展示）
 */
@Composable
fun ExamScreen(
    navController: NavHostController,
    keypointId: Int,
    limit: Int,
    title: String,
    viewModel: ExamViewModel = viewModel(),
) {
    LaunchedEffect(keypointId, limit) {
        viewModel.load(keypointId = keypointId, limit = limit)
    }

    AppScaffold(title = title, onBack = { navController.popBackStack() }) { pad: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(pad)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                viewModel.loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                viewModel.error != null -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = viewModel.error ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            TextButton(onClick = { viewModel.retry() }) { Text("重试") }
                        }
                    }
                }

                viewModel.exam != null -> {
                    val exam = viewModel.exam!!
                    // 任一代答开关开启时，未作答也可提交（提交体会填好作答）。
                    // 读 prefs 的可观察状态 → 设置页改动后本页立即重组。
                    val autoAnswer = OldSimianPrefs.autoCorrect ||
                        (OldSimianPrefs.customAnswerEnabled &&
                            OldSimianPrefs.customAnswerText.isNotBlank())
                    ExamHeader(exam = exam, limit = limit)
                    exam.questions.orEmpty().forEachIndexed { index, q ->
                        QuestionCard(
                            index = index,
                            question = q,
                            selectedMark = viewModel.answers[q.id],
                            onAnswer = { ans -> viewModel.answer(q, ans) },
                        )
                    }
                    SubmitBar(
                        answeredCount = viewModel.answers.size,
                        total = exam.questions.orEmpty().size,
                        submitting = viewModel.submitting,
                        submitted = viewModel.submitted,
                        autoAnswer = autoAnswer,
                        onSubmit = { viewModel.submit() },
                    )
                }
            }
        }
    }
}

/** 顶部信息条：题量 + 进度。 */
@Composable
private fun ExamHeader(exam: ExamData, limit: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "共 ${exam.questions?.size ?: limit} 题",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            exam.keypoint?.let { kp ->
                Text(
                    text = "知识点：$kp",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 单题卡。
 *
 * 作答方式：点「对 / 错」二选一（先跑通链路；手写板接入后替换）。
 * 选择即写入 `userAnswer` 与 `status`。
 */
@Composable
private fun QuestionCard(
    index: Int,
    question: ExamQuestion,
    selectedMark: String?,
    onAnswer: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = question.content ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AnswerChip(
                    label = "答对",
                    selected = selectedMark == RIGHT_MARK,
                    onClick = { onAnswer(RIGHT_MARK) },
                )
                AnswerChip(
                    label = "答错",
                    selected = selectedMark == WRONG_MARK,
                    onClick = { onAnswer(WRONG_MARK) },
                )
                if (selectedMark != null) {
                    Text(
                        text = "已作答",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
        }
    }
}

/** 作答胶囊。 */
@Composable
private fun AnswerChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
        )
    }
}

/** 底部提交条。 */
@Composable
private fun SubmitBar(
    answeredCount: Int,
    total: Int,
    submitting: Boolean,
    submitted: Boolean,
    autoAnswer: Boolean,
    onSubmit: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = when {
                    submitted -> "已提交"
                    autoAnswer -> "代答已开启 · 已作答 $answeredCount / $total"
                    else -> "已作答 $answeredCount / $total"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (submitted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (submitting) {
                CircularProgressIndicator()
            } else if (!submitted) {
                TextButton(
                    onClick = onSubmit,
                    // 代答开启时不需要手动作答，直接可提交。
                    enabled = autoAnswer || answeredCount > 0,
                ) {
                    Text("提交")
                }
            }
        }
    }
}

/** 作答标记：答对。写入 `userAnswer`，提交时映射为 `status = 1`。 */
private const val RIGHT_MARK = "right"

/** 作答标记：答错。写入 `userAnswer`，提交时映射为 `status = -1`。 */
private const val WRONG_MARK = "wrong"
