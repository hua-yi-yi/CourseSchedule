package com.chen.schedule.util

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.TimeSlot
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class TodaySummaryTest {
    private val courses = listOf(Course(name = "数学", startSlot = 1, endSlot = 2), Course(name = "英语", startSlot = 3, endSlot = 3))
    private val slots = listOf(TimeSlot(slotNumber = 1, startTime = "08:00", endTime = "08:45"), TimeSlot(slotNumber = 2, startTime = "08:55", endTime = "09:40"), TimeSlot(slotNumber = 3, startTime = "10:00", endTime = "10:45"))
    private fun at(time: String) = TodaySummary.describe(courses, slots, LocalTime.parse(time))

    @Test fun beforeSchool() { assertTrue(at("07:30").contains("08:00 数学")) }
    @Test fun duringSecondSlot() { assertTrue(at("09:10").contains("进行中 数学")) }
    @Test fun withinContinuousCourseBreak() { assertTrue(at("08:50").contains("进行中 数学")) }
    @Test fun betweenCourses() { assertTrue(at("09:50").contains("10:00 英语")) }
    @Test fun atStart() { assertTrue(at("10:00").contains("进行中 英语")) }
    @Test fun atEnd() { assertTrue(at("10:45").contains("今日课程已结束")) }
    @Test fun afterSchool() { assertTrue(at("18:00").contains("今日课程已结束")) }
    @Test fun missingTimes() { assertTrue(TodaySummary.describe(courses, emptyList(), LocalTime.NOON).contains("设置作息")) }
    @Test fun noCourses() { assertTrue(TodaySummary.describe(emptyList(), slots, LocalTime.NOON).contains("没有课程")) }
}
