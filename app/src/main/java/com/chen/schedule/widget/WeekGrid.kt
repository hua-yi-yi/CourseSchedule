package com.chen.schedule.widget

import com.chen.schedule.domain.model.Course

/**
 * 周课表网格构建器(纯逻辑,供 4×4 大组件与单元测试使用)。
 *
 * 网格结构: [slot(1..maxSlots) 行] x [周一..周日 7 列]。
 * 课程跨越多个节次时,在每个包含的格子里都出现(与课程表视图一致)。
 */
object WeekGridBuilder {

    const val MAX_SLOTS = 12
    const val MIN_SLOTS = 8

    /** 网格中的一个单元格:同格多课时保留首课信息并计数 */
    data class Cell(
        val name: String,
        val color: Long,
        val extraCount: Int = 0
    )

    /**
     * 构建指定周的课表网格。
     *
     * @param courses 学期内全部课程(内部按周次过滤)
     * @param week    当前周
     * @param maxSlots 最大节次行数
     * @return maxSlots x 7 的网格;空单元格为 null
     */
    fun build(courses: List<Course>, week: Int, maxSlots: Int = MAX_SLOTS): List<List<Cell?>> {
        val grid = Array(maxSlots) { arrayOfNulls<Cell>(7) }
        for (course in courses) {
            if (!course.appliesToWeek(week)) continue
            val day = course.dayOfWeek - 1
            if (day !in 0..6) continue
            val start = course.startSlot.coerceIn(1, maxSlots)
            val end = course.endSlot.coerceIn(start, maxSlots)
            for (slot in start..end) {
                val idx = slot - 1
                grid[idx][day] = when (val cur = grid[idx][day]) {
                    null -> Cell(course.name, course.color, 0)
                    else -> cur.copy(extraCount = cur.extraCount + 1)
                }
            }
        }
        return grid.map { row -> row.toList() }
    }

    /** 实际需要渲染的节次行数:取课程最大节次(钳制在 [MIN_SLOTS, MAX_SLOTS]) */
    fun effectiveSlotCount(courses: List<Course>, maxSlots: Int = MAX_SLOTS): Int {
        val maxEnd = courses.maxOfOrNull { it.endSlot } ?: MIN_SLOTS
        return maxEnd.coerceIn(MIN_SLOTS, maxSlots)
    }
}
