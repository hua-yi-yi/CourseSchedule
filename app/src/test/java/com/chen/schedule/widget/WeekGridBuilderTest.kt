package com.chen.schedule.widget

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekGridBuilderTest {

    private fun course(
        name: String = "高数",
        day: Int = 1,
        start: Int = 1,
        end: Int = 2,
        startWeek: Int = 1,
        endWeek: Int = 16,
        weekType: WeekType = WeekType.ALL,
        color: Long = 0xFF4CAF50
    ) = Course(name = name, dayOfWeek = day, startSlot = start, endSlot = end,
        startWeek = startWeek, endWeek = endWeek, weekType = weekType, color = color)

    @Test
    fun `课程放入正确的星期与节次格子`() {
        val grid = WeekGridBuilder.build(listOf(course(name = "高数", day = 1, start = 1, end = 2)), week = 5)
        assertEquals("高数", grid[0][0]?.name)
        assertEquals("高数", grid[1][0]?.name)
        assertNull(grid[2][0])
        assertNull(grid[0][1]) // 周二无课
    }

    @Test
    fun `跨节课程在每个包含的节次都显示`() {
        val grid = WeekGridBuilder.build(listOf(course(name = "数据结构", day = 3, start = 3, end = 4)), week = 1)
        assertEquals("数据结构", grid[2][2]?.name)
        assertEquals("数据结构", grid[3][2]?.name)
        assertNull(grid[4][2])
    }

    @Test
    fun `单周课程只在奇数周显示`() {
        val c = course(name = "单周课", day = 2, start = 1, end = 1, weekType = WeekType.ODD)
        assertNotNull(WeekGridBuilder.build(listOf(c), week = 3)[0][1])
        assertNull(WeekGridBuilder.build(listOf(c), week = 2)[0][1])
    }

    @Test
    fun `双周课程只在偶数周显示`() {
        val c = course(name = "双周课", day = 2, start = 1, end = 1, weekType = WeekType.EVEN)
        assertNull(WeekGridBuilder.build(listOf(c), week = 3)[0][1])
        assertNotNull(WeekGridBuilder.build(listOf(c), week = 4)[0][1])
    }

    @Test
    fun `超出周次范围的课程不显示`() {
        val c = course(name = "短课", day = 1, start = 1, end = 1, startWeek = 1, endWeek = 4)
        assertNotNull(WeekGridBuilder.build(listOf(c), week = 3)[0][0])
        assertNull(WeekGridBuilder.build(listOf(c), week = 5)[0][0])
    }

    @Test
    fun `同一格多门课程保留首课并计数`() {
        val courses = listOf(
            course(name = "A", day = 1, start = 1, end = 1),
            course(name = "B", day = 1, start = 1, end = 1)
        )
        val cell = WeekGridBuilder.build(courses, week = 1)[0][0]
        assertEquals("A", cell?.name)
        assertEquals(1, cell?.extraCount)
    }

    @Test
    fun `超过最大节次数的课程被钳制`() {
        val c = course(name = "晚课", day = 5, start = 11, end = 13)
        val grid = WeekGridBuilder.build(listOf(c), week = 1) // maxSlots 默认 12
        assertEquals("晚课", grid[10][4]?.name)   // 第11节
        assertEquals("晚课", grid[11][4]?.name)   // 第12节(钳制)
        assertEquals(12, grid.size)
    }

    @Test
    fun `无效星期被忽略`() {
        val c = course(name = "越界", day = 8, start = 1, end = 1)
        val grid = WeekGridBuilder.build(listOf(c), week = 1)
        assertTrue(grid.flatten().all { it == null })
    }

    @Test
    fun `实际节次行数取最大节次并钳制范围`() {
        assertEquals(8, WeekGridBuilder.effectiveSlotCount(emptyList()))
        assertEquals(12, WeekGridBuilder.effectiveSlotCount(listOf(course(end = 16))))
        assertEquals(9, WeekGridBuilder.effectiveSlotCount(listOf(course(end = 9))))
    }

    @Test
    fun `浅色底用深色文字`() {
        assertEquals(WeekGridBuilder.TEXT_DARK, WeekGridBuilder.textColorFor(0xFFFFF176L)) // 浅黄
        assertEquals(WeekGridBuilder.TEXT_DARK, WeekGridBuilder.textColorFor(0xFFC8E6C9L)) // 浅绿
    }

    @Test
    fun `深色底用白色文字`() {
        assertEquals(WeekGridBuilder.TEXT_LIGHT, WeekGridBuilder.textColorFor(0xFF1E6BE0L)) // 品牌蓝
        assertEquals(WeekGridBuilder.TEXT_LIGHT, WeekGridBuilder.textColorFor(0xFF2E7D32L)) // 深绿
    }

    @Test
    fun `单元格正确保存教室与起止时间并格式化展示`() {
        val timeSlots = listOf(
            com.chen.schedule.domain.model.TimeSlot(slotNumber = 1, startTime = "08:00", endTime = "08:45"),
            com.chen.schedule.domain.model.TimeSlot(slotNumber = 2, startTime = "08:55", endTime = "09:40")
        )
        val c = Course(
            name = "高数",
            classroom = "教101",
            teacher = "张老师",
            dayOfWeek = 1,
            startSlot = 1,
            endSlot = 2,
            startWeek = 1,
            endWeek = 16,
            weekType = WeekType.ALL
        )
        val grid = WeekGridBuilder.build(listOf(c), week = 1, timeSlots = timeSlots)
        val cell1 = grid[0][0]
        val cell2 = grid[1][0]
        assertNotNull(cell1)
        assertNotNull(cell2)

        assertEquals("教101", cell1?.classroom)
        assertEquals("08:00", cell1?.startTime)
        assertEquals("09:40", cell1?.endTime)
        assertEquals(0, cell1?.slotIndexInCourse)
        assertEquals(2, cell1?.courseSpan)

        val (main1, sub1) = WeekGridBuilder.formatCellText(cell1!!)
        assertEquals("高数", main1)
        assertEquals("@教101", sub1)

        val (main2, sub2) = WeekGridBuilder.formatCellText(cell2!!)
        assertEquals("@教101", main2)
        assertEquals("08:00-09:40", sub2)
    }

    @Test
    fun `单节课单元格主文本为课程名副文本为教室`() {
        val timeSlots = listOf(
            com.chen.schedule.domain.model.TimeSlot(slotNumber = 3, startTime = "10:00", endTime = "10:45")
        )
        val c = Course(
            name = "班会",
            classroom = "综201",
            dayOfWeek = 2,
            startSlot = 3,
            endSlot = 3,
            startWeek = 1,
            endWeek = 16
        )
        val grid = WeekGridBuilder.build(listOf(c), week = 1, timeSlots = timeSlots)
        val cell = grid[2][1]
        assertNotNull(cell)
        val (main, sub) = WeekGridBuilder.formatCellText(cell!!)
        assertEquals("班会", main)
        assertEquals("@综201", sub)
    }
}
