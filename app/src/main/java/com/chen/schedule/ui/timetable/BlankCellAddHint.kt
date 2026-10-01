package com.chen.schedule.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** 待确认的空白格；位置为格内比例，滚动或字体缩放后仍跟随该格。 */
data class PendingBlankCell(
    val week: Int,
    val dayOfWeek: Int,
    val slotNumber: Int,
    val positionInCell: Offset
)

/** 只显示预选提示；普通触摸由网格处理，避免扩大命中范围覆盖相邻课程。 */
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
    val hintSize = minOf(32.dp, cellWidth, cellHeight)
    val density = LocalDensity.current
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
            .size(hintSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
            .semantics(mergeDescendants = true) {
                onClick(label = "添加课程") { onConfirm(); true }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = "再次点击添加课程",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(hintSize * 0.65f)
        )
    }
}
