package com.chen.schedule.ui.timetable

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** 待确认的空白格；位置为格内比例，滚动或字体缩放后仍跟随该格。 */
data class PendingBlankCell(
    val week: Int,
    val dayOfWeek: Int,
    val slotNumber: Int,
    val positionInCell: Offset
)

/**
 * 首次点选空白格的预选提示：
 * - 带有弹跳入场动画与精致阴影；
 * - 宽单元格（如日视图）显示「再次点击添加课程」微胶囊；
 * - 窄单元格（周视图）显示高对比度「+」浮钮；
 * - 支持直接点击提示按钮本身或再次点击网格触发确认。
 */
@Composable
internal fun BlankCellAddHint(
    cellWidth: Dp,
    cellHeight: Dp,
    cellLeftPx: Float,
    cellTopPx: Float,
    positionInCell: Offset,
    onConfirm: () -> Unit
) {
    if (cellWidth <= 0.dp || cellHeight <= 0.dp) return

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.5f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "hint_scale"
    )

    val isWideCell = cellWidth > 90.dp
    val hintHeight = minOf(32.dp, cellHeight)
    val density = LocalDensity.current

    if (isWideCell) {
        // 日视图等宽单元格：优雅的药丸胶囊「+ 再次点击添加课程」
        val position = with(density) {
            val widthPx = cellWidth.toPx()
            val heightPx = cellHeight.toPx()
            val hPx = hintHeight.toPx()
            val centerY = (positionInCell.y * heightPx).coerceIn(hPx / 2f, heightPx - hPx / 2f)
            IntOffset(
                (cellLeftPx + 16.dp.toPx()).roundToInt(),
                (cellTopPx + centerY - hPx / 2f).roundToInt()
            )
        }

        Box(
            modifier = Modifier
                .offset { position }
                .scale(scale)
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                .clickable { onConfirm() }
                .semantics(mergeDescendants = true) {
                    onClick(label = "添加课程") { onConfirm(); true }
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "再次点击添加课程",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    } else {
        // 周视图窄单元格：带阴影与明显描边的「+」圆纽
        val hintSize = minOf(32.dp, cellWidth, cellHeight)
        val position = with(density) {
            val widthPx = cellWidth.toPx()
            val heightPx = cellHeight.toPx()
            val radiusPx = hintSize.toPx() / 2f
            val centerX = (positionInCell.x * widthPx).coerceIn(radiusPx, widthPx - radiusPx)
            val centerY = (positionInCell.y * heightPx).coerceIn(radiusPx, heightPx - radiusPx)
            IntOffset(
                (cellLeftPx + centerX - radiusPx).roundToInt(),
                (cellTopPx + centerY - radiusPx).roundToInt()
            )
        }

        Box(
            modifier = Modifier
                .offset { position }
                .scale(scale)
                .size(hintSize)
                .shadow(elevation = 4.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .border(1.5.dp, Color.White, CircleShape)
                .clickable { onConfirm() }
                .semantics(mergeDescendants = true) {
                    onClick(label = "添加课程") { onConfirm(); true }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "再次点击添加课程",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(hintSize * 0.65f)
            )
        }
    }
}
