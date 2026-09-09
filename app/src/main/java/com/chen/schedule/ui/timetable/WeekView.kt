package com.chen.schedule.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.DayOfWeek
import com.chen.schedule.domain.model.TimeSlot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val SLOT_H = 60
private val TIME_COL = 48

@Composable
fun WeekView(
    courses: List<Course>,
    timeSlots: List<TimeSlot>,
    showWeekend: Boolean,
    semesterStartDate: Long?,
    currentWeek: Int,
    onCourseClick: (Course) -> Unit,
    onBlankCellClick: (dayOfWeek: Int, slotNumber: Int) -> Unit = { _, _ -> }
) {
    val days = DayOfWeek.entries.filter { showWeekend || it.index <= 5 }
    val visibleSlots = com.chen.schedule.util.TimetableSlots.rows(timeSlots, courses)
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    val semesterMonday = semesterStartDate?.let {
        com.chen.schedule.util.WeekCalculator.semesterMonday(it)
    }
    val today = LocalDate.now()

    fun dateOf(dayIndex: Int): LocalDate? = semesterMonday?.plusDays(
        ((currentWeek - 1) * 7 + (dayIndex - 1)).toLong()
    )

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
            // ===== Header: 星期 + 日期 =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .height(46.dp)
            ) {
                Box(
                    modifier = Modifier.width(TIME_COL.dp).fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "节次",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                days.forEach { day ->
                    val isToday = dateOf(day.index) == today
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(
                                if (isToday) Modifier.background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                ) else Modifier
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                    ) {
                        Text(
                            day.label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isToday) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                        dateOf(day.index)?.let { d ->
                            Text(
                                "${d.monthValue}/${d.dayOfMonth}",
                                fontSize = 10.sp,
                                color = if (isToday) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ===== Grid + 课程卡片 =====
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
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

                // 空白格点击:按内容坐标反推星期与节次。
                // 课程卡片自带 clickable,会先消费点击,因此不会误触。
                // 该 Box 尺寸等于网格内容高度,offset 已是内容坐标,无需再加滚动偏移。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(days, visibleSlots, onBlankCellClick) {
                            detectTapGestures { offset ->
                                val relativeX = offset.x - timeColPx
                                if (relativeX < 0f) return@detectTapGestures
                                val dayIndex = (relativeX / cellWidthPx).toInt()
                                    .coerceIn(0, days.size - 1)
                                val slotIndex = (offset.y / slotHPx).toInt()
                                val slot = visibleSlots.getOrNull(slotIndex)
                                    ?: return@detectTapGestures
                                onBlankCellClick(days[dayIndex].index, slot.slotNumber)
                            }
                        }
                ) {
                    // Layer 1: 背景网格
                    Column {
                        visibleSlots.forEach { slot ->
                            Row(modifier = Modifier.fillMaxWidth().height(SLOT_H.dp)) {
                                // 时间列
                                Box(
                                    modifier = Modifier
                                        .width(TIME_COL.dp)
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "${slot.slotNumber}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (slot.startTime.isNotBlank()) {
                                            Text(
                                                slot.startTime,
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                slot.endTime,
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                // 星期格
                                days.forEach { day ->
                                    val isToday = dateOf(day.index) == today
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                            .then(
                                                if (isToday) Modifier.background(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                                ) else Modifier
                                            )
                                    )
                                }
                            }
                        }
                    }

                    // Layer 2: 课程卡片
                    courses.forEach { course ->
                        val dayIndex = days.indexOfFirst { it.index == course.dayOfWeek }
                        if (dayIndex < 0) return@forEach

                        val firstRow = visibleSlots.indexOfFirst { it.slotNumber >= course.startSlot }
                        val lastRow = visibleSlots.indexOfLast { it.slotNumber <= course.endSlot }
                        if (firstRow < 0 || lastRow < firstRow) return@forEach
                        val span = lastRow - firstRow + 1
                        val xPx = (timeColPx + dayIndex * cellWidthPx).toInt()
                        val yPx = (firstRow * slotHPx).toInt()

                        val accent = Color(course.color)
                        CourseBlock(
                            course = course,
                            span = span,
                            accent = accent,
                            modifier = Modifier
                                .offset { IntOffset(xPx, yPx) }
                                .width(cellWidthDp)
                                .height((SLOT_H * span).dp)
                                .padding(2.5.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(accent.copy(alpha = 0.16f))
                                .border(1.dp, accent.copy(alpha = 0.38f), RoundedCornerShape(9.dp))
                                .clickable { onCourseClick(course) }
                                .padding(horizontal = if (cellWidthDp < 60.dp) 3.dp else 7.dp, vertical = 5.dp),
                            compact = span == 1,
                            narrow = cellWidthDp < 60.dp
                        )
                    }
                }
            }
        }
    }
}

/** 课程块:左侧色条 + 信息列(周视图与日视图共用视觉语言) */
@Composable
internal fun CourseBlock(
    course: Course,
    span: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    narrow: Boolean = false
) {
    Row(modifier = modifier) {
        if (!narrow) Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        Column(modifier = Modifier.padding(start = if (narrow) 0.dp else 6.dp).fillMaxWidth()) {
            Text(
                course.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = if (narrow && span > 1) 3 else 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!compact) {
                if (course.teacher.isNotBlank()) {
                    Text(
                        course.teacher,
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (course.classroom.isNotBlank()) {
                    Text(
                        course.classroom,
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (span > 1) {
                    Text(
                        "${course.startSlot}-${course.endSlot}节",
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = accent
                    )
                }
            }
        }
    }
}
