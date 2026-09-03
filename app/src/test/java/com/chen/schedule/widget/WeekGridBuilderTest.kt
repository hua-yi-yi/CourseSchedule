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
}
