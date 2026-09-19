package com.chen.schedule.util

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CourseConflictDetectorTest {

    @Test
    fun sameDayAndSlot_oddEvenWeeks_noConflict() {
        val oddCourse = Course(
            id = 1L,
            name = "单周高数",
            dayOfWeek = 1,
            startSlot = 1,
            endSlot = 2,
            startWeek = 1,
            endWeek = 16,
            weekType = WeekType.ODD
        )
        val evenCourse = Course(
            id = 2L,
            name = "双周线代",
            dayOfWeek = 1,
            startSlot = 1,
            endSlot = 2,
            startWeek = 1,
            endWeek = 16,
            weekType = WeekType.EVEN
        )

        val conflict = CourseConflictDetector.checkConflict(oddCourse, evenCourse)
        assertNull("单双周在同一节次不发生冲突", conflict)
    }

    @Test
    fun sameDayAndSlot_oddAndAllWeeks_conflicts() {
        val oddCourse = Course(
            id = 1L,
            name = "单周高数",
            dayOfWeek = 1,
            startSlot = 1,
            endSlot = 2,
            startWeek = 1,
            endWeek = 6,
            weekType = WeekType.ODD
        )
        val allCourse = Course(
            id = 2L,
            name = "每周英语",
            dayOfWeek = 1,
            startSlot = 1,
            endSlot = 2,
            startWeek = 1,
            endWeek = 6,
            weekType = WeekType.ALL
        )

        val conflict = CourseConflictDetector.checkConflict(oddCourse, allCourse)
        assertNotNull("单周与每周课程在单周产生冲突", conflict)
        assertEquals(listOf(1, 3, 5), conflict!!.overlappingWeeks)
        assertEquals(1, conflict.overlapStartSlot)
        assertEquals(2, conflict.overlapEndSlot)
        assertEquals("第 1-2 节", conflict.slotDescription)
        assertEquals("第 1, 3, 5 周", conflict.weekDescription)
    }

    @Test
    fun differentDayOfWeek_noConflict() {
        val course1 = Course(id = 1L, dayOfWeek = 1, startSlot = 1, endSlot = 2, startWeek = 1, endWeek = 16)
        val course2 = Course(id = 2L, dayOfWeek = 2, startSlot = 1, endSlot = 2, startWeek = 1, endWeek = 16)
        assertNull(CourseConflictDetector.checkConflict(course1, course2))
    }

    @Test
    fun differentSlots_adjacent_noConflict() {
        val course1 = Course(id = 1L, dayOfWeek = 3, startSlot = 1, endSlot = 2, startWeek = 1, endWeek = 16)
        val course2 = Course(id = 2L, dayOfWeek = 3, startSlot = 3, endSlot = 4, startWeek = 1, endWeek = 16)
        assertNull(CourseConflictDetector.checkConflict(course1, course2))
    }

    @Test
    fun overlappingSlots_partialOverlap_conflicts() {
        val course1 = Course(id = 1L, dayOfWeek = 4, startSlot = 1, endSlot = 3, startWeek = 1, endWeek = 8)
        val course2 = Course(id = 2L, dayOfWeek = 4, startSlot = 2, endSlot = 4, startWeek = 5, endWeek = 12)

        val conflict = CourseConflictDetector.checkConflict(course1, course2)
        assertNotNull(conflict)
        assertEquals(2, conflict!!.overlapStartSlot)
        assertEquals(3, conflict.overlapEndSlot)
        assertEquals((5..8).toList(), conflict.overlappingWeeks)
        assertEquals("第 2-3 节", conflict.slotDescription)
        assertEquals("第 5-8 周", conflict.weekDescription)
    }

    @Test
    fun disjointWeeks_noConflict() {
        val course1 = Course(id = 1L, dayOfWeek = 5, startSlot = 1, endSlot = 2, startWeek = 1, endWeek = 8)
        val course2 = Course(id = 2L, dayOfWeek = 5, startSlot = 1, endSlot = 2, startWeek = 9, endWeek = 16)
        assertNull(CourseConflictDetector.checkConflict(course1, course2))
    }

    @Test
    fun sameCourseId_noConflictWithSelf() {
        val course = Course(id = 10L, dayOfWeek = 1, startSlot = 1, endSlot = 2, startWeek = 1, endWeek = 16)
        assertNull(CourseConflictDetector.checkConflict(course, course))
    }
}
