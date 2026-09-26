package cn.apixiaoyuan.app.feature.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import cn.apixiaoyuan.app.core.navigation.AppNavController
import cn.apixiaoyuan.app.core.design.component.AppScrollScaffold
import cn.apixiaoyuan.app.core.model.ExerciseScopeKeypoint
import cn.apixiaoyuan.app.core.model.ExerciseSection
import cn.apixiaoyuan.app.core.model.ExerciseType
import cn.apixiaoyuan.app.core.model.LeoUserCurrentExpData
import cn.apixiaoyuan.app.core.navigation.RouteExam

/**
 * 练习页。
 *
 * 三段，自上而下：
 *  1. **数学出题** —— 题型选择（口算练习 / 竖式计算 / 单位换算 …）+
 *     题目数量选择（10 / 20 / 30 / 60 / 100）+ 知识点列表，点知识点出题。
 *     题型与题量口径严格照原版 `nj/r` 枚举，见 [ExerciseType]。
 *  2. **今日经验** —— `/leo-star/android/exercise/rank/pre-fetch`
 *  3. **英语章节** —— `/leo-english/android/exercise/{type}`
 *
 * 顶栏由 [AppScaffold] 统一提供；悬浮玻璃底栏是浮层，内容不再为它预留 96dp。
 */
@Composable
fun ExerciseScreen(
    navController: AppNavController,
    viewModel: ExerciseViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { viewModel.load() }

    AppScrollScaffold(title = "练习", onBack = { navController.popBackStack() }) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "练习",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // ---- 数学出题 ----
            MathSection(viewModel, navController)

            // ---- 概览 ----
            when {
                viewModel.loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                viewModel.errorMessage != null -> {
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
                                text = viewModel.errorMessage ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            TextButton(onClick = { viewModel.retry() }) { Text("重新拉取") }
                        }
                    }
                }

                else -> {
                    viewModel.exp?.let { ExpCard(it) }
                    viewModel.englishSections.forEach { section ->
                        SectionCard(section)
                    }
                }
            }
        }
    }
}

/**
 * 数学出题段：题型选择 + 题量选择 + 知识点列表。
 *
 * 题型取自 [ExerciseType.practiceable]（排除纯展示/打印类），
 * 题量取自当前题型的 [ExerciseType.chooseNumArray]。
 * 题量行只在 `canChooseNum` 为 true 时出现 —— 对齐原版
 * `nj/r.canChooseNum` 的显隐语义。
 */
@Composable
private fun MathSection(
    viewModel: ExerciseViewModel,
    navController: AppNavController,
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "数学练习",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // 题型选择
            Text(
                text = "题型",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExerciseType.practiceable.forEach { type ->
                    Chip(
                        label = type.displayName,
                        selected = type == viewModel.selectedType,
                        onClick = { viewModel.selectType(type) },
                    )
                }
            }

            // 题量选择（仅 canChooseNum 题型显示，对齐原版）
            if (viewModel.selectedType.canChooseNum) {
                Text(
                    text = "题目数量",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    viewModel.selectedType.chooseNumArray.forEach { num ->
                        Chip(
                            label = num.toString(),
                            selected = num == viewModel.selectedNum,
                            onClick = { viewModel.selectNum(num) },
                        )
                    }
                }
            }

            // 知识点列表
            when {
                viewModel.mathLoading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                viewModel.mathError != null -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = viewModel.mathError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.retryMath() }) { Text("重试") }
                    }
                }

                else -> {
                    Text(
                        text = "选择知识点开始练习",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    viewModel.keypoints.forEach { kp ->
                        KeypointRow(
                            keypoint = kp,
                            onClick = {
                                navController.navigate(
                                    RouteExam(
                                        keypointId = kp.id,
                                        limit = viewModel.selectedNum,
                                        title = kp.name ?: "练习",
                                    )
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 单个知识点行：名称 + 已练次数 + 题量提示，整行可点进答题页。 */
@Composable
private fun KeypointRow(
    keypoint: ExerciseScopeKeypoint,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = keypoint.name ?: "知识点 ${keypoint.id}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val sub = buildString {
                append("已练 ${keypoint.practiceCnt} 次")
                if (keypoint.questionCnt > 0) {
                    append(" · 默认 ${keypoint.questionCnt} 题")
                }
            }
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 胶囊选择器。选中态用 primaryContainer 高亮。 */
@Composable
private fun Chip(
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

/** 本周分数卡。数据源为 `getCurrentUserExp` 的 `curWeekScore`。 */
@Composable
private fun ExpCard(exp: LeoUserCurrentExpData) {
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
            Text(
                text = "本周分数",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = exp.curWeekScore.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            val multiple = exp.expectedMultiple.multiple
            if (multiple > 1) {
                Text(
                    text = "当前 ${multiple} 倍经验",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 单个章节卡。 */
@Composable
private fun SectionCard(section: ExerciseSection) {
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
                text = section.title ?: "章节 ${section.sectionId}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${section.unitIds.size} 个单元",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
