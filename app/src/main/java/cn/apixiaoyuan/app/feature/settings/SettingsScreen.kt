package cn.apixiaoyuan.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cn.apixiaoyuan.app.core.design.component.AppScaffold
import cn.apixiaoyuan.app.core.design.component.LocalScrollBottomLimit
import cn.apixiaoyuan.app.core.design.theme.PageTransitionAnimation
import cn.apixiaoyuan.app.core.design.theme.PageTransitionPrefs
import cn.apixiaoyuan.app.core.design.theme.ThemePrefs
import cn.apixiaoyuan.app.core.navigation.RouteOldSimian
import cn.apixiaoyuan.app.core.settings.AppConfig
import cn.apixiaoyuan.app.core.settings.ConfigTransfer
import com.materialkolor.dynamiccolor.ColorSpec
import kotlinx.serialization.json.Json

/**
 * 设置页 —— 移植「老挂戏老叟」（cn.nizou.sxd）SettingsScreen 的主题 + 配置两段。
 *
 * ## 外观五项与导出/导入已全部接线（2026-09-25）
 *
 * 此前这七项全是空壳（有行无 onClick）。现已全部接上：
 *  - 主题模式 / 取色风格 / 颜色规格 / 底栏效果：点开就**就地展开选项**，
 *    选中项右侧显示「✓」，改完立即写 [ThemePrefs] 并触发全应用重组；
 *  - 种子颜色：展开预设色板（16 色）点选，同样即时生效；
 *  - 导出 / 导入配置：走 SAF（[exportLauncher] / [importLauncher]），
 *    JSON 覆盖「老挂戏老叟」18 个键 + 外观五项 + 过渡动画。
 *
 * 之所以用「就地展开」而不是跳二级页：这些都是**即时可见**的视觉开关，
 * 展开后能直接看到颜色/风格变化，跳页反而要来回切才能对比。
 *
 * 与 cn.nizou.sxd 的差异：
 *  - 不引入 miuix 的 M3ListScaffold / SegmentedColumn，改用本项目 AppScaffold +
 *    自绘卡片组（SettingGroup / SettingRow），保持 cn.apixiaoyuan.app 的视觉语言；
 *  - APK 页整块删掉，此页不再出现任何 APK 相关入口。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(navController: NavHostController) {
    // 导出/导入的结果提示。SAF 是异步回调，提示必须落在状态里。
    var transferHint by remember { mutableStateOf<String?>(null) }

    // SAF：导出 —— 让用户在系统文件管理器里选保存位置。
    val exportLauncher = rememberLauncherForCreateJson { uri ->
        if (uri == null) {
            transferHint = "导出已取消"
            return@rememberLauncherForCreateJson
        }
        transferHint = runCatching {
            val json = Json { prettyPrint = true }.encodeToString(AppConfig.serializer(), ConfigTransfer.export())
            writeTextToUri(uri, json)
            "已导出配置"
        }.getOrElse { "导出失败：${it.message}" }
    }

    // SAF：导入 —— 选一个之前导出的 JSON。
    val importLauncher = rememberLauncherForOpenJson { uri ->
        if (uri == null) {
            transferHint = "导入已取消"
            return@rememberLauncherForOpenJson
        }
        transferHint = runCatching {
            val text = readTextFromUri(uri)
                ?: return@runCatching "导入失败：读不到文件内容"
            val cfg = Json { ignoreUnknownKeys = true }.decodeFromString(AppConfig.serializer(), text)
            ConfigTransfer.import(cfg) ?: "已导入配置"
        }.getOrElse { "导入失败：${it.message}" }
    }

    AppScaffold(title = "设置", onBack = null) { pad: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp),
        ) {
            // ---- 外观 ----
            SettingGroup(title = "外观") {
                // 主题模式：三态单选，展开式。
                OptionGroup(label = "主题模式") {
                    ThemePrefs.ThemeMode.entries.forEach { m ->
                        OptionChip(
                            text = m.displayName,
                            selected = ThemePrefs.mode == m,
                            onClick = { ThemePrefs.mode = m; ThemePrefs.persist() },
                        )
                    }
                }
                // 取色风格：9 项直接铺开（名字短，铺开比弹窗更快选）。
                OptionGroup(label = "取色风格") {
                    ThemePrefs.paletteStyleOptions.forEach { style ->
                        OptionChip(
                            text = style.name,
                            selected = ThemePrefs.paletteStyle == style,
                            onClick = { ThemePrefs.paletteStyle = style; ThemePrefs.persist() },
                        )
                    }
                }
                // 颜色规格：2021 / 2025 两版。
                OptionGroup(label = "颜色规格") {
                    ThemePrefs.colorSpecOptions.forEach { spec ->
                        OptionChip(
                            text = if (spec == ColorSpec.SpecVersion.SPEC_2021) "2021" else "2025",
                            selected = ThemePrefs.colorSpec == spec,
                            onClick = { ThemePrefs.colorSpec = spec; ThemePrefs.persist() },
                        )
                    }
                }
                // 种子颜色：预设色板圆点。
                OptionGroup(label = "种子颜色") {
                    ThemePrefs.presetSeedColors.forEach { color ->
                        ColorDot(
                            color = color,
                            selected = ThemePrefs.seedColor == color,
                            onClick = { ThemePrefs.seedColor = color; ThemePrefs.persist() },
                        )
                    }
                }
                // 底栏效果：三态。
                OptionGroup(label = "底栏效果") {
                    ThemePrefs.BottomBarMode.entries.forEach { mode ->
                        OptionChip(
                            text = mode.displayName,
                            selected = ThemePrefs.bottomBarMode == mode,
                            onClick = { ThemePrefs.bottomBarMode = mode; ThemePrefs.persist() },
                        )
                    }
                }
            }

            // ---- 界面 ----
            SettingGroup(title = "界面") {
                Text(
                    text = "页面过渡动画",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp),
                )
                PageTransitionAnimation.entries.forEach { option ->
                    SettingRow(
                        title = option.displayName,
                        description = when (option) {
                            PageTransitionAnimation.MIUIX ->
                                "进场页整屏滑入，被覆盖页让位 1/4 宽度（默认）"
                            PageTransitionAnimation.AOSP ->
                                "进出只做轻微位移 + 淡入淡出，被覆盖页不动"
                        },
                        selected = PageTransitionPrefs.animation == option,
                        onClick = { PageTransitionPrefs.update(option) },
                    )
                }
            }

            // ---- 配置 ----
            SettingGroup(title = "配置") {
                SettingRow(
                    title = "老挂戏老叟",
                    description = "练习代答 / 提交画笔 / 结算时间 / 刷分 / PK 自动化",
                    onClick = { navController.navigate(RouteOldSimian) },
                )
                SettingRow(
                    title = "导出配置",
                    description = "将全部设置保存为 JSON 文件",
                    onClick = { exportLauncher("apixiaoyuan-config.json") },
                )
                SettingRow(
                    title = "导入配置",
                    description = "从 JSON 文件恢复设置",
                    onClick = { importLauncher() },
                )
            }

            // ---- 关于 ----
            SettingGroup(title = "关于") {
                SettingRow(title = "版本", description = "cn.apixiaoyuan.app")
            }

            transferHint?.let { hint ->
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                )
            }

            // 滚动终点：底栏显示时把最后一项顶到不被遮挡处（玻璃折射要靠这个）。
            val scrollLimit = LocalScrollBottomLimit.current
            if (scrollLimit > 0.dp) {
                androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = scrollLimit))
            }
        }
    }
}

/**
 * 就地展开的选项组：标题 + 一行可换行的选项 chip。
 *
 * 用 [FlowRow] 而不是 LazyRow：选项数量固定且少（最多 9 个），
 * FlowRow 自动换行且不需要测量滚动，比横向滚动列表更好点。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionGroup(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

/** 单个选项 chip。选中时用 primary 容器色高亮 + 前方打勾。 */
@Composable
private fun OptionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = if (selected) "✓ $text" else text,
            style = MaterialTheme.typography.bodyMedium,
            color = fg,
        )
    }
}

/** 种子色圆点。选中时加一圈 primary 描边（用 padding + 背景模拟）。 */
@Composable
private fun ColorDot(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(if (selected) 40.dp else 34.dp)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.primary)
                } else {
                    Modifier
                }
            )
            .padding(if (selected) 4.dp else 0.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
    )
}

/** 设置分组：标题 + 圆角卡片组。 */
@Composable
private fun SettingGroup(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            content()
        }
    }
}

/**
 * 设置项行：标题 + 副标题，点击回调可空（占位行先不给跳转）。
 *
 * @param selected 单选行的选中态；null 表示这不是单选行（不显示勾）。
 */
@Composable
private fun SettingRow(
    title: String,
    description: String?,
    selected: Boolean? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (selected == true) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
