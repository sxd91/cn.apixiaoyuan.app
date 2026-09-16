package cn.apixiaoyuan.app.feature.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import cn.apixiaoyuan.app.core.model.ExerciseSection
import cn.apixiaoyuan.app.core.model.LeoCurrentTaskInfo
import cn.apixiaoyuan.app.core.model.LeoTaskItem
import cn.apixiaoyuan.app.core.model.LeoUserCurrentExpData

/**
 * 练习页（读链路）。
 *
 * 三个数据区块：
 *  - 经验值卡：当前等级 / 经验 / 下级所需
 *  - 任务卡：任务列表 + 完成进度
 *  - 英语章节：按类型拉到的章节列表
 *
 * 数据由 [ExerciseViewModel] 并发拉取，全部经
 * [cn.apixiaoyuan.app.core.exercise.ExerciseRepository] 兜底。
 * 任一失败显示空态或错误提示，不崩。
 *
 * 底部预留 96dp 给 LiquidGlassTabBar。
 */
@Composable
fun ExerciseScreen(
    navController: NavHostController,
    viewModel: ExerciseViewModel = viewModel(),
) {
    // 首帧拉一次。
    LaunchedEffect(Unit) {
        if (!viewModel.loaded) viewModel.load()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "练习",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "任务卡 · 经验值 · 英语章节（读链路）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (viewModel.loading && !viewModel.loaded) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("加载中…", style = MaterialTheme.typography.bodyMedium)
            }
        }

        viewModel.errorMessage?.let { msg ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { viewModel.retry() }) { Text("重试") }
                }
            }
        }

        // ---- 经验值 ----
        viewModel.exp?.let { exp ->
            ExpCard(exp)
        }

        // ---- 任务卡 ----
        viewModel.tasks?.let { tasks ->
            TasksCard(tasks)
        }

        // ---- 英语章节 ----
        if (viewModel.englishSections.isNotEmpty()) {
            HorizontalDivider()
            Text("英语章节", style = MaterialTheme.typography.titleSmall)
            viewModel.englishSections.forEach { section ->
                SectionRow(section)
            }
        }

        // 拉完但三块全空且无错误（服务端返回空结构）。
        if (viewModel.loaded && !viewModel.loading &&
            viewModel.exp == null && viewModel.tasks == null &&
            viewModel.englishSections.isEmpty() && viewModel.errorMessage == null
        ) {
            Text(
                text = "暂无数据。若刚登录，请确认 cookie 已落盘后重试。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { viewModel.retry() }) { Text("重新拉取") }
        }
    }
}

@Composable
private fun ExpCard(exp: LeoUserCurrentExpData) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "经验值",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Lv.${exp.currentLevel}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "${exp.currentExp} / ${exp.nextLevelExp} exp",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
            if (exp.nextLevelExp > 0) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (exp.currentExp.toFloat() / exp.nextLevelExp).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (exp.maxLevelReached) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "已满级",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun TasksCard(tasks: LeoCurrentTaskInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "今日任务",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${tasks.finishedCount}/${tasks.totalCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (tasks.tasks.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "任务列表为空（字段为推断，可能服务端结构不同）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                tasks.tasks.forEach { task ->
                    TaskRow(task)
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: LeoTaskItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (task.finished) "✓" else "○",
            style = MaterialTheme.typography.bodyLarge,
            color = if (task.finished) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = task.title ?: "任务 #${task.taskId}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (task.exp > 0) {
            Text(
                text = "+${task.exp}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun SectionRow(section: ExerciseSection) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = section.title ?: "章节 #${section.sectionId}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            if (section.unitIds.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${section.unitIds.size} 个单元：${section.unitIds.take(3).joinToString(", ")}" +
                        if (section.unitIds.size > 3) " …" else "",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}