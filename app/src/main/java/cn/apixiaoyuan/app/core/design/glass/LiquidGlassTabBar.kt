package cn.apixiaoyuan.app.core.design.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.apixiaoyuan.app.core.design.icon.AppIcons
import kotlinx.coroutines.launch

/**
 * 液态玻璃悬浮 Tab 栏。
 *
 * 结构：底部悬浮胶囊，高 64dp，左右 16dp，距底 16dp，圆角 32dp。
 * 玻璃质感：半透明 Surface 基底 + 边缘高光描边 + 内阴影渐变，
 * 模糊层由 miuix-blur 提供（API >= 31 生效，以下降级为纯半透明）。
 * 选中态：高亮胶囊随选中项弹性位移，手指拖拽时实时跟随 x 坐标，松手吸附最近项。
 */

/** Tab 项数据：标题 + 图标键（对齐 AppIcons 的键名）。 */
data class TabItem(val label: String, val iconKey: String)

@Composable
fun LiquidGlassTabBar(
    items: List<TabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
    cornerRadius: Dp = 32.dp,
    horizontalMargin: Dp = 16.dp,
    bottomMargin: Dp = 16.dp,
) {
    if (items.isEmpty()) return

    val scope = rememberCoroutineScope()
    val indicatorOffset = remember { Animatable(selectedIndex.toFloat()) }
    var dragOffset by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(selectedIndex) {
        if (!isDragging) {
            indicatorOffset.animateTo(
                targetValue = selectedIndex.toFloat(),
                animationSpec = spring(
                    dampingRatio = 0.7f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }

    val surface = MaterialTheme.colorScheme.surface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer

    BoxWithConstraints(
        modifier = modifier
            .padding(horizontal = horizontalMargin, vertical = bottomMargin)
            .fillMaxWidth()
            .height(height),
    ) {
        // 容器实测宽度，指示器与槽位全部基于它计算，避免魔数导致位置跑偏。
        val containerWidth: Dp = maxWidth
        val slotWidth: Dp = containerWidth / items.size
        val indicatorWidth: Dp = if (slotWidth < 56.dp) slotWidth else 56.dp

        // 玻璃底：半透明基底 + 上下明暗描边模拟玻璃厚度。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(cornerRadius))
                .background(surface.copy(alpha = 0.72f))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.30f),
                            Color.White.copy(alpha = 0.05f),
                            Color.Black.copy(alpha = 0.08f),
                        ),
                    ),
                    shape = RoundedCornerShape(cornerRadius),
                ),
        )

        // 拖拽层：整条栏响应水平拖动，指示器实时跟随，松手吸附最近项。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(items.size) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isDragging = true
                            dragOffset = indicatorOffset.value
                        },
                        onDragEnd = {
                            isDragging = false
                            val target = dragOffset.roundToInt().coerceIn(0, items.size - 1)
                            onSelect(target)
                            scope.launch {
                                indicatorOffset.animateTo(
                                    targetValue = target.toFloat(),
                                    animationSpec = spring(
                                        dampingRatio = 0.7f,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                )
                            }
                        },
                        onDragCancel = { isDragging = false },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val slotWidthPx = slotWidth.toPx()
                            if (slotWidthPx > 0f) {
                                dragOffset = (dragOffset + dragAmount / slotWidthPx)
                                    .coerceIn(0f, (items.size - 1).toFloat())
                            }
                        },
                    )
                },
        ) {
            val current = if (isDragging) dragOffset else indicatorOffset.value
            val indicatorLeft = slotWidth * current + (slotWidth - indicatorWidth) / 2f

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = indicatorLeft)
                    .width(indicatorWidth)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(primaryContainer),
            )
        }

        // 图标行：选中项展开文字并放大图标，未选中只留图标。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(index) {
                            detectTapGestures { onSelect(index) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = if (selected) {
                                AppIcons.forKeySelected(item.iconKey)
                            } else {
                                AppIcons.forKey(item.iconKey)
                            },
                            contentDescription = item.label,
                            tint = if (selected) onPrimaryContainer else onSurfaceVariant,
                            modifier = Modifier.size(if (selected) 24.dp else 22.dp),
                        )
                        if (selected) {
                            Text(
                                text = item.label,
                                color = onPrimaryContainer,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
