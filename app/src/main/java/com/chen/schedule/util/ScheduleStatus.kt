package com.chen.schedule.util

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.Semester
import com.chen.schedule.domain.model.TimeSlot
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 「已完成 / 待设置」状态判定。
 *
 * 原则:状态完全由已成功保存的数据实时计算,与用户是否进入过页面无关。
 * 未到开学日期、已放假、暂时没有课程,都不会导致「待设置」。
 */
object ScheduleStatus {

    /** 总周数的合理上限,与旧版校验保持一致。 */
    const val MIN_WEEKS = 1
    const val MAX_WEEKS = 53

    private val TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm")

    /** 学期「已完成」的判定结果。 */
    data class SemesterCheck(
        val done: Boolean,
        /** 未满足的项,用于引导面板逐项提示。 */
        val issues: List<Issue>
    )

    /** 作息「已完成」的判定结果。 */
    data class SchemeCheck(
        val done: Boolean,
        val issues: List<Issue>,
        /** 课程用到但方案缺失的节次编号(升序)。 */
        val missingSlots: List<Int>
    )

    enum class Issue(val label: String) {
        NO_SEMESTER("尚未选择当前学期"),
        NAME_EMPTY("学期名称不能为空"),
        DATE_INVALID("开学日期无效"),
        WEEKS_INVALID("总周数无效(应为 $MIN_WEEKS–$MAX_WEEKS)"),
        NO_SCHEME("当前学期尚未关联作息方案"),
        NO_SLOTS("作息方案至少需要一个节次"),
        SLOT_NUMBER_INVALID("节次编号无效或重复"),
        SLOT_TIME_INVALID("存在结束时间不晚于开始时间的节次"),
        SLOT_ORDER_INVALID("节次时间存在重叠或倒序"),
        SLOT_COVERAGE_MISSING("作息未覆盖课程使用的节次")
    }

    // ============ 学期 ============

    fun checkSemester(semester: Semester?): SemesterCheck {
        if (semester == null) {
            return SemesterCheck(false, listOf(Issue.NO_SEMESTER))
        }
        val issues = buildList {
            if (semester.name.isBlank()) add(Issue.NAME_EMPTY)
            if (semester.startDate <= 0L) add(Issue.DATE_INVALID)
            if (semester.totalWeeks !in MIN_WEEKS..MAX_WEEKS) add(Issue.WEEKS_INVALID)
        }
        return SemesterCheck(issues.isEmpty(), issues)
    }

    // ============ 作息 ============

    /**
     * @param semester 当前学期(null 视为未关联作息)
     * @param slots 当前学期所关联方案的节次
     * @param courses 当前学期的课程(用于覆盖性校验)
     */
    fun checkScheme(
        semester: Semester?,
        slots: List<TimeSlot>,
        courses: List<Course>
    ): SchemeCheck {
        val issues = mutableListOf<Issue>()
        var missingSlots: List<Int> = emptyList()

        if (semester == null) {
            issues += Issue.NO_SCHEME
            return SchemeCheck(false, issues, emptyList())
        }
        if (slots.isEmpty()) {
            issues += Issue.NO_SLOTS
            return SchemeCheck(false, issues, emptyList())
        }

        val ordered = slots.sortedBy { it.slotNumber }

        if (slots.any { it.slotNumber <= 0 } ||
            slots.map { it.slotNumber }.distinct().size != slots.size
        ) {
            issues += Issue.SLOT_NUMBER_INVALID
        }

        val parsed = slots.map { slot ->
            val start = parseTime(slot.startTime)
            val end = parseTime(slot.endTime)
            Triple(slot, start, end)
        }

        if (parsed.any { (_, start, end) -> start == null || end == null || !end.isAfter(start) }) {
            issues += Issue.SLOT_TIME_INVALID
        }

        // 排序后不得重叠或倒序(前一节结束 <= 后一节开始)
        val orderOk = ordered
            .mapNotNull { slot -> parseTime(slot.endTime)?.let { e -> slot to e } }
            .zipWithNext()
            .all { (a, b) ->
                val nextStart = parseTime(b.first.startTime) ?: return@all false
                !a.second.isAfter(nextStart)
            }
        if (!orderOk) issues += Issue.SLOT_ORDER_INVALID

        // 覆盖课程使用的节次
        val availableNumbers = slots.map { it.slotNumber }.toSet()
        val usedNumbers = courses.flatMap { course ->
            if (course.endSlot < course.startSlot) emptyList()
            else (course.startSlot..course.endSlot).toList()
        }.filter { it > 0 }
        missingSlots = usedNumbers.filterNot { it in availableNumbers }.distinct().sorted()
        if (missingSlots.isNotEmpty()) issues += Issue.SLOT_COVERAGE_MISSING

        return SchemeCheck(issues.isEmpty(), issues, missingSlots)
    }

    /** 单套节次自身是否有效(编号/时间/顺序),不含课程覆盖校验。 */
    fun isSlotsValid(slots: List<TimeSlot>): Boolean {
        if (slots.isEmpty()) return false
        if (slots.any { it.slotNumber <= 0 }) return false
        if (slots.map { it.slotNumber }.distinct().size != slots.size) return false
        val parsed = slots.map { Triple(it, parseTime(it.startTime), parseTime(it.endTime)) }
        if (parsed.any { (_, start, end) -> start == null || end == null || !end.isAfter(start) }) return false
        return slots.sortedBy { it.slotNumber }
            .mapNotNull { slot -> parseTime(slot.endTime)?.let { slot to it } }
            .zipWithNext()
            .all { (a, b) ->
                val nextStart = parseTime(b.first.startTime) ?: return@all false
                !a.second.isAfter(nextStart)
            }
    }

    /** 解析 "HH:mm" / "H:mm"。非法返回 null。 */
    fun parseTime(value: String): LocalTime? = try {
        LocalTime.parse(value.trim(), TIME_FORMAT)
    } catch (_: Exception) {
        null
    }
}
