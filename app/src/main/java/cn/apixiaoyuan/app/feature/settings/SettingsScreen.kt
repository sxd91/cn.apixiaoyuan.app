package cn.apixiaoyuan.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cn.apixiaoyuan.app.core.design.component.AppScaffold
import cn.apixiaoyuan.app.core.design.theme.PageTransitionAnimation
import cn.apixiaoyuan.app.core.design.theme.PageTransitionPrefs
import cn.apixiaoyuan.app.core.navigation.RouteOldSimian
import cn.apixiaoyuan.app.core.navigation.RouteScorePump

/**
 * 设置页 —— 移植「老挂戏老叟」（cn.nizou.sxd）SettingsScreen 的主题 + 配置两段。
 *
 * 与 cn.nizou.sxd 的差异：
 *  - 不引入 miuix 的 M3ListScaffold / SegmentedColumn，改用本项目 AppScaffold +
 *    自绘卡片组（SettingGroup / SettingRow），保持 cn.apixiaoyuan.app 的视觉语言；
 *  - 主题项先接项目已有的 ThemeSettings 门面（PaletteStyle / ColorSpec / 深浅模式 /
 *    种子色 / 底栏效果），未接入的项（过渡动画、陀螺仪、导出导入）先不摆空壳；
 *  - APK 页整块删掉，此页不再出现任何 APK 相关入口。
 */
@Composable
fun SettingsScreen(navController: NavHostController) {
    AppScaffold(title = "设置", onBack = null) { pad: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp),
        ) {
            SettingGroup(title = "外观") {
                SettingRow(title = "主题模式", description = "跟随系统 / 浅色 / 深色")
                SettingRow(title = "取色风格", description = "Tonal Spot / Expressive / Rainbow …")
                SettingRow(title = "颜色规格", description = "Material 3 (2021) / Expressive (2025)")
                SettingRow(title = "种子颜色", description = "自定义主色调的种子色值")
                SettingRow(title = "底栏效果", description = "液态玻璃 / 毛玻璃 / 纯色")
            }
            SettingGroup(title = "界面") {
                // 页面过渡动画：两个选项（Miuix / AOSP），默认 Miuix。
                // 每行一个选项，选中行右侧显示「✓」—— 与「主题模式」那类
                // 「点一下循环切换」不同，这里把两个选项都摆出来，用户能直接
                // 看到有哪几种、当前是哪种。
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
            SettingGroup(title = "配置") {
                SettingRow(
                    title = "老挂戏老叟",
                    description = "练习代答 / 提交画笔 / 结算时间 / 刷分 / PK 自动化",
                    onClick = { navController.navigate(RouteOldSimian) },
                )
                SettingRow(
                    title = "自定义分数（刷分）",
                    description = "刷到目标分数（练习成绩上传主接口）",
                    onClick = { navController.navigate(RouteScorePump) },
                )
                SettingRow(title = "导出配置", description = "将全部设置保存为 JSON 文件")
                SettingRow(title = "导入配置", description = "从 JSON 文件恢复设置")
            }
            SettingGroup(title = "关于") {
                SettingRow(title = "版本", description = "cn.apixiaoyuan.app")
            }
        }
    }
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
