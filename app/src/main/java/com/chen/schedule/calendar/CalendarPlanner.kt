package com.chen.schedule.calendar

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.Semester
import com.chen.schedule.domain.model.TimeSlot
import com.chen.schedule.util.ScheduleStatus
import com.chen.schedule.util.WeekCalculator
import java.time.ZoneId

/**
 * 待同步至系统日历的单个课程日程项。
 */
data class CalendarEventItem(
    val courseId: Long,
    val title: String,
    val location: String,
    val description: String,
    val startMillis: Long,
    val endMillis: Long,
    val week: Int,
    val dayOfWeek: Int,
    val startSlot: Int,
    val endSlot: Int
)

/**
 * 系统日历日程规划器：
 * 将学期、课程及作息时间换算为具体的绝对时间戳与日程描述。
 * 遵循与 ICS 导出相同的排课与单双周规则。
 */
object CalendarPlanner {

    /**
     * 将学期课程展开为每一个生效周的具体日历日程。
     *
     * @param semester 学期信息（用于定位开学第 1 教学周周一）
     * @param courses 课程列表
     * @param slots 作息节次列表
     * @param zone 设备时区（默认系统默认时区）
     */
    fun planEvents(
        semester: Semester,
        courses: List<Course>,
        slots: List<TimeSlot>,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<CalendarEventItem> {
        if (courses.isEmpty()) return emptyList()
        val check = ScheduleStatus.checkScheme(semester, slots, courses)
        require(check.done) {
            if (check.missingSlots.isNotEmpty()) "作息缺少节次 ${check.missingSlots.joinToString()}，请先补全"
            else "作息时间无效，请先检查节次设置"
        }
        val slotMap = slots.associateBy { it.slotNumber }
        val semesterMonday = WeekCalculator.semesterMonday(semester.startDate, zone)
        val planned = mutableListOf<CalendarEventItem>()

        for (course in courses) {
            val startTimeStr = slotMap.getValue(course.startSlot).startTime
            val endTimeStr = slotMap.getValue(course.endSlot).endTime
            val startLocalTime = checkNotNull(ScheduleStatus.parseTime(startTimeStr))
            val endLocalTime = checkNotNull(ScheduleStatus.parseTime(endTimeStr))

            val slotRangeLabel = if (course.endSlot > course.startSlot) {
                "第 ${course.startSlot}-${course.endSlot} 节"
            } else {
                "第 ${course.startSlot} 节"
            }

            for (w in course.startWeek..course.endWeek) {
                if (!course.appliesToWeek(w)) continue

                val eventDate = semesterMonday
                    .plusWeeks((w - 1).toLong())
                    .plusDays((course.dayOfWeek - 1).toLong())

                val startZdt = eventDate.atTime(startLocalTime).atZone(zone)
                val endZdt = eventDate.atTime(endLocalTime).atZone(zone)
                require(endZdt.isAfter(startZdt)) { "${course.name}在 ${eventDate} 的结束时间无效，请检查作息和时区" }

                val desc = buildString {
                    if (course.teacher.isNotBlank()) append("任课教师: ").append(course.teacher).append("\n")
                    append("节次: ").append(slotRangeLabel).append(" (").append(startTimeStr).append(" - ").append(endTimeStr).append(")\n")
                    append("周次: 第 ").append(w).append(" 周")
                    if (course.note.isNotBlank()) append("\n备注: ").append(course.note)
                }

                planned.add(
                    CalendarEventItem(
                        courseId = course.id,
                        title = course.name.ifBlank { "未命名课程" },
                        location = course.classroom,
                        description = desc,
                        startMillis = startZdt.toInstant().toEpochMilli(),
                        endMillis = endZdt.toInstant().toEpochMilli(),
                        week = w,
                        dayOfWeek = course.dayOfWeek,
                        startSlot = course.startSlot,
                        endSlot = course.endSlot
                    )
                )
            }
        }

        return planned.sortedWith(compareBy({ it.startMillis }, { it.endMillis }, { it.courseId }))
    }
}
