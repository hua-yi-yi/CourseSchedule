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
        val extraCount: Int = 0,
        // 输入列表中的身份，避免把同名但不同地点/安排的相邻课程误合并。
        val sourceIndex: Int = -1
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
        for ((sourceIndex, course) in courses.withIndex()) {
            if (!course.appliesToWeek(week)) continue
            val day = course.dayOfWeek - 1
            if (day !in 0..6) continue
            if (course.startSlot > maxSlots || course.endSlot < 1 || course.endSlot < course.startSlot) continue
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
                    extraCount = 0,
                    sourceIndex = sourceIndex
                )
                grid[idx][day] = when (val cur = grid[idx][day]) {
                    null -> newCell
                    else -> cur.copy(extraCount = cur.extraCount + 1)
                }
            }
        }
        return grid.map { row -> row.toList() }
    }

    data class Block(val startRow: Int, val rowSpan: Int, val cell: Cell?)

    /** 每个星期独立跨节合并，空节保留，保证七列与左侧时间轴始终对齐。 */
    fun blocks(grid: List<List<Cell?>>, dayIndex: Int): List<Block> {
        val result = mutableListOf<Block>()
        var row = 0
        while (row < grid.size) {
            val first = grid[row].getOrNull(dayIndex)
            var end = row + 1
            var extraCount = first?.extraCount ?: 0
            if (first != null && first.sourceIndex >= 0) {
                while (end < grid.size) {
                    val next = grid[end].getOrNull(dayIndex) ?: break
                    if (next.sourceIndex != first.sourceIndex ||
                        next.slotIndexInCourse != first.slotIndexInCourse + end - row) break
                    extraCount = maxOf(extraCount, next.extraCount)
                    end++
                }
            }
            result += Block(row, end - row, first?.copy(extraCount = extraCount))
            row = end
        }
        return result
    }

    /** 只在所有列都没有跨节课程的位置分段，LazyColumn 不会把一个课程块切开。 */
    fun bands(columns: List<List<Block>>, rowCount: Int): List<IntRange> {
        val result = mutableListOf<IntRange>()
        var start = 0
        for (boundary in 1..rowCount) {
            val crosses = columns.any { blocks -> blocks.any {
                it.startRow < boundary && it.startRow + it.rowSpan > boundary
            } }
            if (!crosses) {
                result += start until boundary
                start = boundary
            }
        }
        return result
    }

    /** 为地点文本分配足够高度，再将额外空间均匀分给课程占用的节次。 */
    fun rowHeights(
        columns: List<List<Block>>,
        rowCount: Int,
        minimumHeight: Float,
        requiredHeight: (Cell) -> Float
    ): List<Float> {
        val heights = MutableList(rowCount) { minimumHeight }
        columns.flatten().filter { it.cell != null }.sortedBy { it.rowSpan }.forEach { block ->
            val rows = block.startRow until block.startRow + block.rowSpan
            val available = rows.sumOf { heights[it].toDouble() }.toFloat()
            val extra = (requiredHeight(block.cell!!) - available).coerceAtLeast(0f) / block.rowSpan
            rows.forEach { heights[it] += extra }
        }
        return heights
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

    /**
     * 为浅色/深色主题生成与课程原色协调的柔和浅底色(与主页 WeekView 的 accent.copy(alpha = 0.16f) 保持一致)。
     */
    fun pastelColorFor(color: Long, isDark: Boolean = false): Long {
        val r = (color shr 16 and 0xFF).toInt()
        val g = (color shr 8 and 0xFF).toInt()
        val b = (color and 0xFF).toInt()
        return if (!isDark) {
            val pr = (r * 0.20 + 255 * 0.80).toInt().coerceIn(0, 255)
            val pg = (g * 0.20 + 255 * 0.80).toInt().coerceIn(0, 255)
            val pb = (b * 0.20 + 255 * 0.80).toInt().coerceIn(0, 255)
            (0xFFL shl 24) or (pr.toLong() shl 16) or (pg.toLong() shl 8) or pb.toLong()
        } else {
            val pr = (r * 0.30 + 36 * 0.70).toInt().coerceIn(0, 255)
            val pg = (g * 0.30 + 36 * 0.70).toInt().coerceIn(0, 255)
            val pb = (b * 0.30 + 36 * 0.70).toInt().coerceIn(0, 255)
            (0xFFL shl 24) or (pr.toLong() shl 16) or (pg.toLong() shl 8) or pb.toLong()
        }
    }

    const val TEXT_DARK = 0xFF1B1B1B
    const val TEXT_LIGHT = 0xFFFFFFFF
}
