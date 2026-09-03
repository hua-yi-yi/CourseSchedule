package com.chen.schedule.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.DayOfWeek
import com.chen.schedule.domain.model.TimeSlot

private val SLOT_H = 60
private val TIME_COL = 48

@Composable
fun WeekView(
    courses: List<Course>,
    timeSlots: List<TimeSlot>,
    showWeekend: Boolean,
    onCourseClick: (Course) -> Unit
) {
    val days = DayOfWeek.entries.filter { showWeekend || it.index <= 5 }
    val slots = timeSlots.ifEmpty {
        (1..12).map { TimeSlot(slotNumber = it, startTime = "", endTime = "", name = "$it") }
    }
    val maxSlots = (courses.maxOfOrNull { it.endSlot } ?: 12).coerceAtMost(12)
    val visibleSlots = slots.filter { it.slotNumber <= maxSlots }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .height(40.dp)
        ) {
            Box(
                modifier = Modifier.width(TIME_COL.dp).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Text("节/时间", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            days.forEach { day ->
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(day.label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // Grid + course overlay using BoxWithConstraints for cell width
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState)
        ) {
            val cellWidthDp = (maxWidth - TIME_COL.dp) / days.size
            val cellWidthPx: Float
            val timeColPx: Float
            val slotHPx: Float
            with(density) {
                cellWidthPx = cellWidthDp.toPx()
                timeColPx = TIME_COL.dp.toPx()
                slotHPx = SLOT_H.dp.toPx()
            }

            // Layer 1: background grid
            Column {
                visibleSlots.forEach { slot ->
                    Row(modifier = Modifier.fillMaxWidth().height(SLOT_H.dp)) {
                        // Time column
                        Box(
                            modifier = Modifier
                                .width(TIME_COL.dp)
                                .fillMaxHeight()
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${slot.slotNumber}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                if (slot.startTime.isNotBlank()) {
                                    Text(slot.startTime, fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(slot.endTime, fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        // Day cells (empty grid)
                        days.forEach { _ ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                            )
                        }
                    }
                }
            }

            // Layer 2: course cards overlaid on the grid
            courses.forEach { course ->
                val dayIndex = days.indexOfFirst { it.index == course.dayOfWeek }
                if (dayIndex < 0) return@forEach

                val slotStart = course.startSlot.coerceIn(1, maxSlots)
                val slotEnd = course.endSlot.coerceIn(slotStart, maxSlots)
                val span = slotEnd - slotStart + 1

                val xPx = (timeColPx + dayIndex * cellWidthPx).toInt()
                val yPx = ((slotStart - 1) * slotHPx).toInt()
                val hPx = (span * slotHPx).toInt()

                val bgColor = Color(course.color)
                Box(
                    modifier = Modifier
                        .offset { IntOffset(xPx, yPx) }
                        .width(cellWidthDp)
                        .height((SLOT_H * span).dp)
                        .padding(2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .background(bgColor.copy(alpha = 0.15f))
                        .border(1.dp, bgColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .clickable { onCourseClick(course) }
                        .padding(4.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            course.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (course.teacher.isNotBlank()) {
                            Text(course.teacher, fontSize = 9.sp,
                                maxLines = 3, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (course.classroom.isNotBlank()) {
                            Text(course.classroom, fontSize = 9.sp,
                                maxLines = 3, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (span > 1) {
                            Text("${course.startSlot}-${course.endSlot}节",
                                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
