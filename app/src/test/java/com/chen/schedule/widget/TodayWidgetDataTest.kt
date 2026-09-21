package com.chen.schedule.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class TodayWidgetDataTest {

    @Test
    fun `timeDisplay returns formatted time range when times are present`() {
        val course = WidgetCourse(
            name = "高等数学",
            classroom = "公教楼B204",
            teacher = "张老师",
            startSlot = 1,
            endSlot = 2,
            startTime = "08:00",
            endTime = "09:40"
        )
        assertEquals("08:00 - 09:40", course.timeDisplay)
        assertEquals("第1-2节", course.slotDisplay)
    }

    @Test
    fun `timeDisplay falls back to slot display when times are blank`() {
        val course = WidgetCourse(
            name = "大学英语",
            classroom = "外语楼101",
            startSlot = 3,
            endSlot = 4,
            startTime = "",
            endTime = ""
        )
        assertEquals("第3-4节", course.timeDisplay)
        assertEquals("第3-4节", course.slotDisplay)
    }

    @Test
    fun `locationAndTeacherDisplay combines classroom and teacher correctly`() {
        val c1 = WidgetCourse(
            name = "数据结构",
            classroom = "实验楼302",
            teacher = "李老师",
            startSlot = 1,
            endSlot = 2
        )
        assertEquals("实验楼302 · 李老师", c1.locationAndTeacherDisplay)

        val c2 = WidgetCourse(
            name = "自习",
            classroom = "图书馆一楼",
            teacher = "",
            startSlot = 5,
            endSlot = 6
        )
        assertEquals("图书馆一楼", c2.locationAndTeacherDisplay)

        val c3 = WidgetCourse(
            name = "线上讲座",
            classroom = "",
            teacher = "王教授",
            startSlot = 7,
            endSlot = 8
        )
        assertEquals("未设地点 · 王教授", c3.locationAndTeacherDisplay)

        val c4 = WidgetCourse(
            name = "无信息课程",
            classroom = "",
            teacher = "",
            startSlot = 9,
            endSlot = 10
        )
        assertEquals("未设地点", c4.locationAndTeacherDisplay)
    }
}
