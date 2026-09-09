package com.chen.schedule.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.TimeSlot
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val DSLOT_H = 60
private val DTIME_COL = 56

@Composable
fun DayView(
    courses: List<Course>,
    timeSlots: List<TimeSlot>,
    isToday: Boolean = true,
    onCourseClick: (Course) -> Unit,
    onBlankCellClick: (slotNumber: Int) -> Unit = {}
) {
    val visibleSlots = com.chen.schedule.util.TimetableSlots.rows(timeSlots, courses)
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    val now = LocalTime.now()
    val currentSlot = visibleSlots.takeIf { isToday }?.find { slot ->
        try {
            val start = LocalTime.parse(slot.startTime, DateTimeFormatter.ofPattern("HH:mm"))
            val end = LocalTime.parse(slot.endTime, DateTimeFormatter.ofPattern("HH:mm"))
            now in start..end
        } catch (_: Exception) {
            false
        }
    }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (currentSlot != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "现在",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "第 ${currentSlot.slotNumber} 节 · ${currentSlot.startTime}-${currentSlot.endTime}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Spacer(Modifier.height(4.dp))
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                val contentWidthDp = maxWidth - DTIME_COL.dp
                val timeColPx: Float
                val slotHPx: Float
                with(density) {
                    timeColPx = DTIME_COL.dp.toPx()
                    slotHPx = DSLOT_H.dp.toPx()
                }

                // 空白格点击:按内容坐标反推节次。课程卡片自带 clickable,会先消费点击。
                // 该 Box 尺寸等于网格内容高度,offset 已是内容坐标,无需再加滚动偏移。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(visibleSlots, onBlankCellClick) {
                            detectTapGestures { offset ->
                                if (offset.x < timeColPx) return@detectTapGestures
                                val slotIndex = (offset.y / slotHPx).toInt()
                                val slot = visibleSlots.getOrNull(slotIndex)
                                    ?: return@detectTapGestures
                                onBlankCellClick(slot.slotNumber)
                            }
                        }
                ) {
                    // Layer 1: 时间轴 + 网格
                    Column {
                    visibleSlots.forEach { slot ->
                        val isCurrent = currentSlot?.slotNumber == slot.slotNumber
                        Row(modifier = Modifier.fillMaxWidth().height(DSLOT_H.dp)) {
                            // 时间列: 节次号胶囊 + 时间
                            Column(
                                modifier = Modifier
                                    .width(DTIME_COL.dp)
                                    .fillMaxHeight()
                                    .padding(end = 8.dp, top = 6.dp),
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isCurrent) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceContainerHighest
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "${slot.slotNumber}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (slot.startTime.isNotBlank()) {
                                    Text(
                                        slot.startTime,
                                        fontSize = 9.sp,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        slot.endTime,
                                        fontSize = 9.sp,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            // 网格单元
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(10.dp))
                                    .then(
                                        if (isCurrent) Modifier.background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
                                        ) else Modifier
                                    )
                                    .border(
                                        0.5.dp,
                                        if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                        else MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(10.dp)
                                    )
                            )
                        }
                    }
                }

                // Layer 2: 课程卡片
                courses.forEach { course ->
                    val firstRow = visibleSlots.indexOfFirst { it.slotNumber >= course.startSlot }
                    val lastRow = visibleSlots.indexOfLast { it.slotNumber <= course.endSlot }
                    if (firstRow < 0 || lastRow < firstRow) return@forEach
                    val span = lastRow - firstRow + 1
                    val xPx = timeColPx.toInt()
                    val yPx = (firstRow * slotHPx).toInt()

                    val accent = Color(course.color)
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(xPx, yPx) }
                            .width(contentWidthDp)
                            .height((DSLOT_H * span).dp)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent.copy(alpha = 0.16f))
                            .border(1.dp, accent.copy(alpha = 0.38f), RoundedCornerShape(12.dp))
                            .clickable { onCourseClick(course) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(accent)
                            )
                            Column(modifier = Modifier.padding(start = 10.dp).fillMaxWidth()) {
                                Text(
                                    course.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (course.teacher.isNotBlank()) {
                                    Text(
                                        course.teacher,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (course.classroom.isNotBlank()) {
                                    Text(
                                        course.classroom,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    "${course.startSlot}-${course.endSlot}节 · ${course.weekType.label}",
                                    fontSize = 10.sp,
                                    color = accent
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }
}
