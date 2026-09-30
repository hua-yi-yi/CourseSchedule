package com.chen.schedule.util

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.Semester
import com.chen.schedule.domain.model.TimeSlot
import com.chen.schedule.domain.model.WeekType

/** Shared parsing, validation and duplicate identity for every append-import entry point. */
object CourseImportRules {
    fun fromFields(fields: Map<String, String>): Course {
        val (startSlot, endSlot) = range(fields["startslot"], fields["endslot"], null, "节次")
        val (startWeek, endWeek) = range(fields["startweek"], fields["endweek"], 16, "周次")
        return Course(
            name = fields["name"].orEmpty().trim(),
            teacher = fields["teacher"].orEmpty().trim(),
            classroom = fields["classroom"].orEmpty().trim(),
            dayOfWeek = integer(fields["dayofweek"], label = "星期"),
            startSlot = startSlot, endSlot = endSlot, startWeek = startWeek, endWeek = endWeek,
            weekType = weekType(fields["weektype"]),
            color = fields["color"]?.let { it.toLongOrNull() ?: error("颜色必须是整数") } ?: 0xFF4CAF50,
            note = fields["note"].orEmpty()
        ).also(::validate)
    }

    fun integer(value: String?, default: Int? = null, label: String): Int =
        if (value == null) default ?: error("缺少$label")
        else value.trim().toIntOrNull() ?: error("$label 必须是整数")

    fun range(start: String?, end: String?, defaultEnd: Int?, label: String): Pair<Int, Int> {
        val pattern = Regex("\\s*(\\d+)\\s*-\\s*(\\d+)\\s*")
        val startRange = start?.let { pattern.matchEntire(it) }
        val endRange = end?.let { pattern.matchEntire(it) }
        val match = startRange ?: endRange
        if (match != null) {
            val first = integer(match.groupValues[1], label = label)
            val last = integer(match.groupValues[2], label = label)
            if (startRange != null && end != null) {
                require(if (endRange != null) integer(endRange.groupValues[1], label = label) == first && integer(endRange.groupValues[2], label = label) == last
                    else integer(end, label = label) == last) { "$label 范围与结束值不一致" }
            }
            if (endRange != null && start != null && startRange == null) {
                require(integer(start, label = label) == first) { "$label 范围与开始值不一致" }
            }
            return first to last
        }
        val first = integer(start, 1, label)
        return first to integer(end, defaultEnd ?: first + 1, label)
    }

    fun weekType(raw: String?): WeekType = when (raw?.trim()?.lowercase().orEmpty()) {
        "", "all", "每周" -> WeekType.ALL
        "odd", "单周" -> WeekType.ODD
        "even", "双周" -> WeekType.EVEN
        else -> error("周类型应为 all、odd 或 even")
    }

    fun validate(course: Course) {
        require(course.name.isNotBlank()) { "课程名称不能为空" }
        require(course.dayOfWeek in 1..7) { "星期应为 1–7" }
        require(course.startSlot > 0 && course.endSlot >= course.startSlot) { "节次必须为正数，结束节次不能早于开始节次" }
        require(course.startWeek in 1..53 && course.endWeek in course.startWeek..53) { "周次应为 1–53，结束周不能早于开始周" }
    }

    fun validateForSemester(courses: List<Course>, semester: Semester, slots: List<TimeSlot>) {
        require(ScheduleStatus.checkSemester(semester).done) { "请先完成学期设置" }
        require(ScheduleStatus.isSlotsValid(slots)) { "请先配置有效的作息时间" }
        val available = slots.map { it.slotNumber }.toSet()
        courses.forEachIndexed { index, course ->
            try {
                validate(course)
                require(course.endWeek <= semester.totalWeeks) { "课程周次超过学期的 ${semester.totalWeeks} 周" }
                // Compare counts rather than expanding an untrusted integer range.
                val count = course.endSlot.toLong() - course.startSlot + 1
                require(available.count { it in course.startSlot..course.endSlot }.toLong() == count) { "作息未覆盖第 ${course.startSlot}–${course.endSlot} 节，请先补全作息" }
            } catch (error: IllegalArgumentException) {
                throw IllegalArgumentException("第 ${index + 1} 条「${course.name}」：${error.message}", error)
            }
        }
    }

    fun identity(course: Course): Course = course.copy(
        id = 0, semesterId = 0, color = 0, note = "",
        name = course.name.trim(), teacher = course.teacher.trim(), classroom = course.classroom.trim()
    )

    data class Plan(val fresh: List<Course>, val skipped: Int)

    fun plan(incoming: List<Course>, existing: List<Course>): Plan {
        val seen = existing.map(::identity).toMutableSet()
        val fresh = incoming.filter { seen.add(identity(it)) }
            .map { it.copy(id = 0, name = it.name.trim(), teacher = it.teacher.trim(), classroom = it.classroom.trim()) }
        return Plan(fresh, incoming.size - fresh.size)
    }
}
