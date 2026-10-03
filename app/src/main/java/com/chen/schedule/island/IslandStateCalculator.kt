package com.chen.schedule.island

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.TimeSlot
import com.chen.schedule.util.ScheduleStatus
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil

/**
 * 胶囊灵动岛实时状态计算引擎。
 * 纯函数设计，不依赖 Android 上下文，便于全面单元测试。
 */
object IslandStateCalculator {

    data class CourseTimeBlock(
        val course: Course,
        val startSlot: TimeSlot,
        val endSlot: TimeSlot,
        val startMillis: Long,
        val endMillis: Long
    )

    fun calculate(
        nowMillis: Long,
        slots: List<TimeSlot>,
        courses: List<Course>,
        currentWeek: Int,
        todayDayOfWeek: Int,
        leadMinutes: Int = 30,
        zone: ZoneId = ZoneId.systemDefault()
    ): IslandState {
        if (courses.isEmpty() || slots.isEmpty()) {
            return IslandState.None
        }

        val todayCourses = courses.filter {
            it.dayOfWeek == todayDayOfWeek && it.appliesToWeek(currentWeek)
        }

        if (todayCourses.isEmpty()) {
            return IslandState.Idle(
                todayTotalCourses = 0,
                finishedCourses = 0,
                compactText = "今日无课",
                subText = "自由享受休息时间"
            )
        }

        val slotByNumber = slots.associateBy { it.slotNumber }
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()

        val parsedBlocks = todayCourses.mapNotNull { course ->
            val startSlot = slotByNumber[course.startSlot] ?: return@mapNotNull null
            val endSlot = slotByNumber[course.endSlot] ?: startSlot
            val startTime = ScheduleStatus.parseTime(startSlot.startTime) ?: return@mapNotNull null
            val endTime = ScheduleStatus.parseTime(endSlot.endTime) ?: return@mapNotNull null

            val startMillis = today.atTime(startTime).atZone(zone).toInstant().toEpochMilli()
            val endMillis = today.atTime(endTime).atZone(zone).toInstant().toEpochMilli()
            if (endMillis <= startMillis) return@mapNotNull null

            CourseTimeBlock(
                course = course,
                startSlot = startSlot,
                endSlot = endSlot,
                startMillis = startMillis,
                endMillis = endMillis
            )
        }.sortedBy { it.startMillis }

        if (parsedBlocks.isEmpty()) {
            return IslandState.Idle(
                todayTotalCourses = todayCourses.size,
                finishedCourses = 0,
                compactText = "未设置作息时间",
                subText = "请在设置中配置节次时间"
            )
        }

        // 1. 检查是否有正在进行的课程
        val active = parsedBlocks.firstOrNull { nowMillis in it.startMillis until it.endMillis }
        if (active != null) {
            val remainingMs = (active.endMillis - nowMillis).coerceAtLeast(0L)
            val remainingMinutes = ceil(remainingMs / 60000.0).toInt().coerceAtLeast(0)
            val totalMs = (active.endMillis - active.startMillis).coerceAtLeast(1L)
            val totalMinutes = ceil(totalMs / 60000.0).toInt().coerceAtLeast(1)
            val progress = ((nowMillis - active.startMillis).toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
            val slotRange = formatSlotRange(active.course.startSlot, active.course.endSlot)
            val classroomDisplay = active.course.classroom.trim()
            val locationPrefix = if (classroomDisplay.isNotBlank()) "@$classroomDisplay · " else ""

            return IslandState.Ongoing(
                course = active.course,
                courseName = active.course.name,
                classroom = classroomDisplay,
                teacher = active.course.teacher.trim(),
                slotRange = slotRange,
                startTime = active.startSlot.startTime,
                endTime = active.endSlot.endTime,
                startMillis = active.startMillis,
                endMillis = active.endMillis,
                remainingMinutes = remainingMinutes,
                totalMinutes = totalMinutes,
                progress = progress,
                color = active.course.color,
                compactText = "${active.course.name} · 剩${remainingMinutes}分",
                subText = "${locationPrefix}${active.endSlot.endTime}下课"
            )
        }

        // 2. 检查后续是否有即将开始的课程
        val next = parsedBlocks.firstOrNull { it.startMillis > nowMillis }
        if (next != null) {
            val msUntilStart = (next.startMillis - nowMillis).coerceAtLeast(0L)
            val minutesUntilStart = ceil(msUntilStart / 60000.0).toInt().coerceAtLeast(0)
            val slotRange = formatSlotRange(next.course.startSlot, next.course.endSlot)
            val classroomDisplay = next.course.classroom.trim()
            val locationPrefix = if (classroomDisplay.isNotBlank()) "@$classroomDisplay · " else ""

            if (minutesUntilStart <= leadMinutes) {
                return IslandState.Upcoming(
                    course = next.course,
                    courseName = next.course.name,
                    classroom = classroomDisplay,
                    teacher = next.course.teacher.trim(),
                    slotRange = slotRange,
                    startTime = next.startSlot.startTime,
                    endTime = next.endSlot.endTime,
                    startMillis = next.startMillis,
                    minutesUntilStart = minutesUntilStart,
                    color = next.course.color,
                    compactText = "${minutesUntilStart}分后 · ${next.course.name}",
                    subText = "${locationPrefix}${next.startSlot.startTime}上课"
                )
            } else {
                val finished = parsedBlocks.count { it.endMillis <= nowMillis }
                return IslandState.Idle(
                    todayTotalCourses = parsedBlocks.size,
                    finishedCourses = finished,
                    nextCourse = next.course,
                    nextCourseStartTime = next.startSlot.startTime,
                    compactText = "${next.startSlot.startTime} · ${next.course.name}",
                    subText = "下节课还有 ${minutesUntilStart} 分钟"
                )
            }
        }

        // 3. 今日全部课程已结束
        val total = parsedBlocks.size
        return IslandState.Idle(
            todayTotalCourses = total,
            finishedCourses = total,
            nextCourse = null,
            nextCourseStartTime = null,
            compactText = "今日课程已结束",
            subText = "已完成今日全部 $total 门课程"
        )
    }

    private fun formatSlotRange(start: Int, end: Int): String =
        if (end > start) "第$start-$end 节" else "第$start 节"
}
