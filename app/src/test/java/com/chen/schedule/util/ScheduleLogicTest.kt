package com.chen.schedule.util

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.CoursePalette
import com.chen.schedule.domain.model.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleLogicTest {

    private val dayMillis = 1000L * 60 * 60 * 24
    private val now = 1_700_000_000_000L

    @Test
    fun `开学当天为第1周`() {
        assertEquals(1, WeekCalculator.currentWeek(now, 16, now))
    }

    @Test
    fun `开学后20天为第3周`() {
        assertEquals(3, WeekCalculator.currentWeek(now - 20 * dayMillis, 16, now))
    }

    @Test
    fun `超过总周数时钳制到末周`() {
        assertEquals(16, WeekCalculator.currentWeek(now - 365 * dayMillis, 16, now))
    }

    @Test
    fun `开学前钳制到第1周`() {
        assertEquals(1, WeekCalculator.currentWeek(now + 10 * dayMillis, 16, now))
    }

    @Test
    fun `总周数为0时不会崩溃`() {
        assertEquals(1, WeekCalculator.currentWeek(now, 0, now))
    }

    // ---------- Course.appliesToWeek ----------

    @Test
    fun `每周课程在范围内任意周有课`() {
        val course = Course(startWeek = 1, endWeek = 16, weekType = WeekType.ALL)
        assertTrue(course.appliesToWeek(5))
        assertTrue(course.appliesToWeek(16))
        assertFalse(course.appliesToWeek(17))
    }

    @Test
    fun `单周课程只在奇数周有课`() {
        val course = Course(startWeek = 1, endWeek = 8, weekType = WeekType.ODD)
        assertTrue(course.appliesToWeek(3))
        assertFalse(course.appliesToWeek(2))
        assertFalse(course.appliesToWeek(9))
    }

    @Test
    fun `双周课程只在偶数周有课`() {
        val course = Course(startWeek = 2, endWeek = 16, weekType = WeekType.EVEN)
        assertTrue(course.appliesToWeek(4))
        assertFalse(course.appliesToWeek(3))
    }

    // ---------- CoursePalette.assignColors ----------

    @Test
    fun `同名课程颜色稳定一致`() {
        val courses = CoursePalette.assignColors(
            listOf(
                Course(name = "高数"), Course(name = "高数"),
                Course(name = "英语"), Course(name = "英语")
            )
        )
        assertEquals(courses[0].color, courses[1].color)
        assertEquals(courses[2].color, courses[3].color)
        assertTrue(courses[0].color != courses[2].color)
    }

    @Test
    fun `超过调色板数量时颜色循环`() {
        val names = (1..20).map { "课程$it" }
        val colors = CoursePalette.assignColors(names.map { Course(name = it) }).map { it.color }.distinct()
        // 12 色调色板:15+ 个不同名称后必然出现重复颜色(循环使用)
        assertTrue(colors.size <= CoursePalette.colors.size)
    }
}
