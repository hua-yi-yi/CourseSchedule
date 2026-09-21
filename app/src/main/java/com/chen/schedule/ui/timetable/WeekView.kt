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
private val TIME_COL = 36

@Composable
fun WeekView(
    clusters: List<com.chen.schedule.util.CourseConflictDetector.CourseCluster>,
    timeSlots: List<TimeSlot>,
    showWeekend: Boolean,
    semesterStartDate: Long?,
    currentWeek: Int,
    onCourseClick: (primaryCourse: Course, cluster: List<Course>) -> Unit,
    onBlankCellClick: (dayOfWeek: Int, slotNumber: Int) -> Unit = { _, _ -> }
) {
    val days = DayOfWeek.entries.filter { showWeekend || it.index <= 5 }
    val allCourses = clusters.flatMap { it.allCourses }
    val visibleSlots = com.chen.schedule.util.TimetableSlots.rows(timeSlots, allCourses)
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
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 4.dp),
        shape = RoundedCornerShape(10.dp),
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
                    .height(32.dp)
            ) {
                Box(
                    modifier = Modifier.width(TIME_COL.dp).fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "节",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                days.forEach { day ->
                    val isToday = dateOf(day.index) == today
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(vertical = 2.dp, horizontal = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(6.dp))
                                .then(
                                    if (isToday) Modifier.background(
                                        MaterialTheme.colorScheme.primary
                                    ) else Modifier
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                        ) {
                            Text(
                                day.label,
                                fontSize = 11.sp,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isToday) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            dateOf(day.index)?.let { d ->
                                Text(
                                    "${d.monthValue}/${d.dayOfMonth}",
                                    fontSize = 8.5.sp,
                                    fontWeight = if (isToday) FontWeight.Medium else FontWeight.Normal,
                                    color = if (isToday) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ===== Grid + 课程卡片 =====
            // 垂直滚动由主页统一接管:上滑时头部与周切换随内容一起滑出屏幕
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth()
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
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (slot.startTime.isNotBlank()) {
                                            Text(
                                                slot.startTime,
                                                fontSize = 8.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                slot.endTime,
                                                fontSize = 8.sp,
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

                    // Layer 2: 课程卡片 (支持同一时段多门课层叠展示)
                    clusters.forEach { cluster ->
                        val course = cluster.primaryCourse
                        val dayIndex = days.indexOfFirst { it.index == course.dayOfWeek }
                        if (dayIndex < 0) return@forEach

                        val firstRow = visibleSlots.indexOfFirst { it.slotNumber >= course.startSlot }
                        val lastRow = visibleSlots.indexOfLast { it.slotNumber <= course.endSlot }
                        if (firstRow < 0 || lastRow < firstRow) return@forEach
                        val span = lastRow - firstRow + 1
                        val xPx = (timeColPx + dayIndex * cellWidthPx).toInt()
                        val yPx = (firstRow * slotHPx).toInt()

                        val accent = Color(course.color)

                        // 若有多门课重叠, 绘制一层底层底框产生层叠视觉提示
                        if (cluster.isOverlapping) {
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(xPx + 2, yPx + 2) }
                                    .width(cellWidthDp)
                                    .height((SLOT_H * span).dp)
                                    .padding(1.5.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(accent.copy(alpha = 0.12f))
                                    .border(0.8.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                            )
                        }

                        CourseBlock(
                            course = course,
                            span = span,
                            accent = accent,
                            modifier = Modifier
                                .offset { IntOffset(xPx, yPx) }
                                .width(cellWidthDp)
                                .height((SLOT_H * span).dp)
                                .padding(1.5.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(accent.copy(alpha = 0.16f))
                                .border(1.dp, accent.copy(alpha = 0.50f), RoundedCornerShape(8.dp))
                                .clickable { onCourseClick(course, cluster.allCourses) }
                                .padding(horizontal = if (cellWidthDp < 60.dp) 2.5.dp else 5.dp, vertical = 3.5.dp),
                            compact = span == 1,
                            narrow = cellWidthDp < 60.dp,
                            overlapCount = cluster.overlapCount
                        )
                    }
                }
            }
        }
    }
}

/** 兼容旧版调用的 WeekView 重载 */
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
    val clusters = com.chen.schedule.util.CourseConflictDetector.resolveClusters(courses)
    WeekView(
        clusters = clusters,
        timeSlots = timeSlots,
        showWeekend = showWeekend,
        semesterStartDate = semesterStartDate,
        currentWeek = currentWeek,
        onCourseClick = { primary, _ -> onCourseClick(primary) },
        onBlankCellClick = onBlankCellClick
    )
}

/** 课程块:左侧色条 + 信息列(周视图与日视图共用视觉语言)，右上角标注重叠门数 */
@Composable
internal fun CourseBlock(
    course: Course,
    span: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    narrow: Boolean = false,
    overlapCount: Int = 1
) {
    Box(modifier = modifier) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (!narrow) Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(accent)
            )
            Column(modifier = Modifier.padding(start = if (narrow) 0.dp else 4.dp).fillMaxWidth()) {
                Text(
                    course.name,
                    fontSize = if (narrow) 10.5.sp else 11.5.sp,
                    lineHeight = if (narrow) 12.5.sp else 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (span >= 2) (if (narrow) 3 else 2) else 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // 地点完整展示: 放宽行数限制至 4-5 行并允许自动软折行, 确保超长教室名完整呈现
                if (course.classroom.isNotBlank()) {
                    androidx.compose.foundation.layout.Spacer(Modifier.height(1.dp))
                    Text(
                        "@${course.classroom}",
                        fontSize = if (narrow) 8.5.sp else 9.sp,
                        lineHeight = if (narrow) 10.5.sp else 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = if (span >= 2) 5 else 3,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = true,
                        color = accent.copy(alpha = 0.95f)
                    )
                }
                if (!compact) {
                    if (course.teacher.isNotBlank() && span >= 3) {
                        Text(
                            course.teacher,
                            fontSize = 8.sp,
                            lineHeight = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        // 重叠角标：右上角展示「X门」徽标
        if (overlapCount > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(3.5.dp))
                    .background(accent.copy(alpha = 0.92f))
                    .padding(horizontal = 3.dp, vertical = 0.5.dp)
            ) {
                Text(
                    text = "${overlapCount}门",
                    color = Color.White,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 8.5.sp
                )
            }
        }
    }
}
