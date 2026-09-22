package com.chen.schedule.widget

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.TimeSlot

/**
 * 周课表网格构建器(纯逻辑,供 4×4 大组件与单元测试使用)。
 *
 * 网格结构: [slot(1..maxSlots) 行] x [周一..周日 7 列]。
 * 课程跨越多个节次时,在每个包含的格子里都出现(与课程表视图一致)。
 */
object WeekGridBuilder {

    const val MAX_SLOTS = 12
    const val MIN_SLOTS = 8

    /** 网格中的一个单元格:包含课程名、颜色、地点、时间段与同格重叠计数 */
    data class Cell(
        val name: String,
        val color: Long,
        val classroom: String = "",
        val teacher: String = "",
        val startTime: String = "",
        val endTime: String = "",
        val slotIndexInCourse: Int = 0,
        val courseSpan: Int = 1,
        val extraCount: Int = 0
    ) {
        val timeDisplay: String
            get() = if (startTime.isNotBlank() && endTime.isNotBlank()) {
                "$startTime-$endTime"
            } else if (startTime.isNotBlank()) {
                startTime
            } else {
                ""
            }
    }

    /**
     * 构建指定周的课表网格。
     *
     * @param courses 学期内全部课程(内部按周次过滤)
     * @param week    当前周
     * @param maxSlots 最大节次行数
     * @param timeSlots 当前学期配置的作息时段(用于匹配起止时间)
     * @return maxSlots x 7 的网格;空单元格为 null
     */
    fun build(
        courses: List<Course>,
        week: Int,
        maxSlots: Int = MAX_SLOTS,
        timeSlots: List<TimeSlot> = emptyList()
    ): List<List<Cell?>> {
        val grid = Array(maxSlots) { arrayOfNulls<Cell>(7) }
        for (course in courses) {
            if (!course.appliesToWeek(week)) continue
            val day = course.dayOfWeek - 1
            if (day !in 0..6) continue
            val start = course.startSlot.coerceIn(1, maxSlots)
            val end = course.endSlot.coerceIn(start, maxSlots)
            val span = end - start + 1
            val startTime = timeSlots.find { it.slotNumber == course.startSlot }?.startTime ?: ""
            val endTime = timeSlots.find { it.slotNumber == course.endSlot }?.endTime ?: ""

            for (slot in start..end) {
                val idx = slot - 1
                val slotIndexInCourse = slot - start
                val newCell = Cell(
                    name = course.name,
                    color = course.color,
                    classroom = course.classroom,
                    teacher = course.teacher,
                    startTime = startTime,
                    endTime = endTime,
                    slotIndexInCourse = slotIndexInCourse,
                    courseSpan = span,
                    extraCount = 0
                )
                grid[idx][day] = when (val cur = grid[idx][day]) {
                    null -> newCell
                    else -> cur.copy(extraCount = cur.extraCount + 1)
                }
            }
        }
        return grid.map { row -> row.toList() }
    }

    /**
     * 格式化单元格主标题与副标题，智能兼顾课程名、地点与具体起止时间展示。
     */
    fun formatCellText(cell: Cell): Pair<String, String> {
        val nameWithExtra = if (cell.extraCount > 0) "${cell.name}+${cell.extraCount}" else cell.name
        val classroomText = if (cell.classroom.isNotBlank()) "@${cell.classroom.trim()}" else ""

        return when {
            // 单节课 (span == 1): 主文本为课程名, 副文本优先为地点, 其次为起止时间
            cell.courseSpan <= 1 -> {
                val sub = if (classroomText.isNotBlank()) classroomText else cell.timeDisplay
                Pair(nameWithExtra, sub)
            }
            // 多节课的第 1 节 (slotIndex == 0): 重点展示课程名称, 并带上地点
            cell.slotIndexInCourse == 0 -> {
                val sub = if (classroomText.isNotBlank()) classroomText else cell.timeDisplay
                Pair(nameWithExtra, sub)
            }
            // 多节课的第 2 节 (slotIndex == 1): 展示地点与具体起止时间段
            cell.slotIndexInCourse == 1 -> {
                val main = if (classroomText.isNotBlank()) classroomText else nameWithExtra
                val sub = if (cell.timeDisplay.isNotBlank()) cell.timeDisplay else cell.teacher
                Pair(main, sub)
            }
            // 多节课的第 3 节及以后: 展示时间与教师
            else -> {
                val main = if (cell.timeDisplay.isNotBlank()) cell.timeDisplay else nameWithExtra
                val sub = cell.teacher
                Pair(main, sub)
            }
        }
    }

    /** 实际需要渲染的节次行数:取课程最大节次(钳制在 [MIN_SLOTS, MAX_SLOTS]) */
    fun effectiveSlotCount(courses: List<Course>, maxSlots: Int = MAX_SLOTS): Int {
        val maxEnd = courses.maxOfOrNull { it.endSlot } ?: MIN_SLOTS
        return maxEnd.coerceIn(MIN_SLOTS, maxSlots)
    }

    /**
     * 小组件格子上的文字颜色:按课程背景色的相对亮度选深字或白字。
     * 浅色底(如浅黄/浅绿)配白字几乎不可读,是小组件的已知问题。
     */
    fun textColorFor(color: Long): Long {
        val r = (color shr 16 and 0xFF) / 255.0
        val g = (color shr 8 and 0xFF) / 255.0
        val b = (color and 0xFF) / 255.0
        val luminance = 0.299 * r + 0.587 * g + 0.114 * b
        return if (luminance > 0.6) TEXT_DARK else TEXT_LIGHT
    }

    const val TEXT_DARK = 0xFF1B1B1B
    const val TEXT_LIGHT = 0xFFFFFFFF
}
