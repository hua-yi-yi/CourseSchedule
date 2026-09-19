package com.chen.schedule.util

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.Semester
import com.chen.schedule.domain.model.TimeSlot
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * iCalendar (.ics) 日历导出工具 (符合 RFC 5545 规范)。
 *
 * 将学期课表转换为日历事件，兼容各大手机系统日历 (小米/华为/OPPO/vivo/Apple/Google Calendar 等) 及可穿戴设备。
 * 针对高校复杂的教学周、单双周安排，按实际生效周次展开为独立的 VEVENT，
 * 确保导入后每个单节日程精准对齐，且支持在日历端单独请假、改期或删除。
 */
object IcsExporter {

    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmmss")
    private val UTC_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneId.of("UTC"))

    /**
     * 生成当前学期课程的 .ics 文本。
     *
     * @param semester 学期信息 (用于获取开学日期与学期名称)
     * @param courses 课程列表
     * @param slots 该学期绑定的作息节次 (用于获取各节次起止时间)
     * @param zone 设备时区 (默认系统时区)
     * @param alarmMinutes 提前提醒分钟数 (默认提前 20 分钟，<= 0 时不生成 VALARM)
     */
    fun export(
        semester: Semester,
        courses: List<Course>,
        slots: List<TimeSlot>,
        zone: ZoneId = ZoneId.systemDefault(),
        alarmMinutes: Int = 20
    ): String {
        val slotMap = slots.associateBy { it.slotNumber }
        val semesterMonday = WeekCalculator.semesterMonday(semester.startDate, zone)
        val nowUtc = UTC_FORMATTER.format(Instant.now())
        val calName = escapeText(if (semester.name.isNotBlank()) "${semester.name} 课程表" else "课程表")
        val zoneIdStr = zone.id

        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\r\n")
        sb.append("VERSION:2.0\r\n")
        sb.append("PRODID:-//Chen//CourseSchedule//CN\r\n")
        sb.append("CALSCALE:GREGORIAN\r\n")
        sb.append("METHOD:PUBLISH\r\n")
        sb.append("X-WR-CALNAME:").append(calName).append("\r\n")
        sb.append("X-WR-TIMEZONE:").append(zoneIdStr).append("\r\n")

        for (course in courses) {
            val startSlot = slotMap[course.startSlot]
            val endSlot = slotMap[course.endSlot] ?: startSlot

            val startTimeStr = startSlot?.startTime ?: "08:00"
            val endTimeStr = endSlot?.endTime ?: "08:45"

            val startLocalTime = runCatching {
                LocalTime.parse(startTimeStr.padStart(5, '0'))
            }.getOrDefault(LocalTime.of(8, 0))

            val endLocalTime = runCatching {
                LocalTime.parse(endTimeStr.padStart(5, '0'))
            }.getOrDefault(startLocalTime.plusMinutes(45))

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

                val dateStr = eventDate.format(DATE_FORMATTER)
                val dtStart = "${dateStr}T${startLocalTime.format(TIME_FORMATTER)}"
                val dtEnd = "${dateStr}T${endLocalTime.format(TIME_FORMATTER)}"

                val uid = "course-${course.id}-$w-${course.dayOfWeek}-${course.startSlot}@courseschedule"

                val desc = buildString {
                    if (course.teacher.isNotBlank()) append("任课教师: ").append(course.teacher).append("\n")
                    append("节次: ").append(slotRangeLabel).append(" (").append(startTimeStr).append(" - ").append(endTimeStr).append(")\n")
                    append("周次: 第 ").append(w).append(" 周")
                    if (course.note.isNotBlank()) append("\n备注: ").append(course.note)
                }

                sb.append("BEGIN:VEVENT\r\n")
                sb.append("UID:").append(uid).append("\r\n")
                sb.append("DTSTAMP:").append(nowUtc).append("\r\n")
                sb.append("DTSTART;TZID=").append(zoneIdStr).append(":").append(dtStart).append("\r\n")
                sb.append("DTEND;TZID=").append(zoneIdStr).append(":").append(dtEnd).append("\r\n")
                sb.append("SUMMARY:").append(escapeText(course.name)).append("\r\n")
                if (course.classroom.isNotBlank()) {
                    sb.append("LOCATION:").append(escapeText(course.classroom)).append("\r\n")
                }
                sb.append("DESCRIPTION:").append(escapeText(desc)).append("\r\n")
                sb.append("STATUS:CONFIRMED\r\n")

                if (alarmMinutes > 0) {
                    sb.append("BEGIN:VALARM\r\n")
                    sb.append("ACTION:DISPLAY\r\n")
                    sb.append("DESCRIPTION:上课提醒\r\n")
                    sb.append("TRIGGER:-PT").append(alarmMinutes).append("M\r\n")
                    sb.append("END:VALARM\r\n")
                }

                sb.append("END:VEVENT\r\n")
            }
        }

        sb.append("END:VCALENDAR\r\n")
        return sb.toString()
    }

    /**
     * RFC 5545 文本转义规则：
     * 反斜杠 \ 转为 \\，分号 ; 转为 \;，逗号 , 转为 \,，换行符转为 \n
     */
    fun escapeText(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")
            .replace("\r", "\\n")
    }
}
