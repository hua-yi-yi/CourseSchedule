package com.chen.schedule.util

import com.chen.schedule.calendar.CalendarPlanner
import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.Semester
import com.chen.schedule.domain.model.TimeSlot
import java.time.Instant
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
        val events = CalendarPlanner.planEvents(semester, courses, slots, zone)
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

        for (event in events) {
            val uid = "course-${event.courseId}-${event.week}-${event.dayOfWeek}-${event.startSlot}@courseschedule"
            sb.append("BEGIN:VEVENT\r\n")
            sb.append("UID:").append(uid).append("\r\n")
            sb.append("DTSTAMP:").append(nowUtc).append("\r\n")
            sb.append("DTSTART:").append(UTC_FORMATTER.format(Instant.ofEpochMilli(event.startMillis))).append("\r\n")
            sb.append("DTEND:").append(UTC_FORMATTER.format(Instant.ofEpochMilli(event.endMillis))).append("\r\n")
            sb.append("SUMMARY:").append(escapeText(event.title)).append("\r\n")
            if (event.location.isNotBlank()) {
                sb.append("LOCATION:").append(escapeText(event.location)).append("\r\n")
            }
            sb.append("DESCRIPTION:").append(escapeText(event.description)).append("\r\n")
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
