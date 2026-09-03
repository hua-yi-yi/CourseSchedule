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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
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
    showWeekend: Boolean,
    onCourseClick: (Course) -> Unit
) {
    val slots = timeSlots.ifEmpty {
        (1..12).map { TimeSlot(slotNumber = it, startTime = "", endTime = "", name = "$it") }
    }
    val maxSlots = (courses.maxOfOrNull { it.endSlot } ?: 12).coerceAtMost(12)
    val visibleSlots = slots.filter { it.slotNumber <= maxSlots }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    val now = LocalTime.now()
    val currentSlot = visibleSlots.find { slot ->
        try {
            val start = LocalTime.parse(slot.startTime, DateTimeFormatter.ofPattern("HH:mm"))
            val end = LocalTime.parse(slot.endTime, DateTimeFormatter.ofPattern("HH:mm"))
            now in start..end
        } catch (_: Exception) { false }
    }?.slotNumber

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(scrollState)
    ) {
        val contentWidthDp = maxWidth - DTIME_COL.dp
        val contentWidthPx: Float
        val timeColPx: Float
        val slotHPx: Float
        with(density) {
            contentWidthPx = contentWidthDp.toPx()
            timeColPx = DTIME_COL.dp.toPx()
            slotHPx = DSLOT_H.dp.toPx()
        }

        // Layer 1: grid background
        Column {
            visibleSlots.forEach { slot ->
                val isCurrent = currentSlot == slot.slotNumber
                Row(modifier = Modifier.fillMaxWidth().height(DSLOT_H.dp)) {
                    Column(
                        modifier = Modifier
                            .width(DTIME_COL.dp)
                            .fillMaxHeight()
                            .padding(end = 6.dp, top = 4.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            "${slot.slotNumber}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrent) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface
                        )
                        if (slot.startTime.isNotBlank()) {
                            Text(slot.startTime, fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(slot.endTime, fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(
                                if (isCurrent) Modifier.border(
                                    1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(8.dp)
                                ) else Modifier
                            )
                    )
                }
            }
        }

        // Layer 2: course cards
        courses.forEach { course ->
            val slotStart = course.startSlot.coerceIn(1, maxSlots)
            val slotEnd = course.endSlot.coerceIn(slotStart, maxSlots)
            val span = slotEnd - slotStart + 1

            val xPx = timeColPx.toInt()
            val yPx = ((slotStart - 1) * slotHPx).toInt()
            val hPx = (span * slotHPx).toInt()

            val bgColor = Color(course.color)
            Box(
                modifier = Modifier
                    .offset { IntOffset(xPx, yPx) }
                    .width(contentWidthDp)
                    .height((DSLOT_H * span).dp)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .background(bgColor.copy(alpha = 0.15f))
                    .border(1.dp, bgColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .clickable { onCourseClick(course) }
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(bgColor)
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            course.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (course.teacher.isNotBlank()) {
                            Text(course.teacher, fontSize = 10.sp,
                                maxLines = 3, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (course.classroom.isNotBlank()) {
                            Text(course.classroom, fontSize = 10.sp,
                                maxLines = 3, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            "${course.startSlot}-${course.endSlot}节 | ${course.weekType.label}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
