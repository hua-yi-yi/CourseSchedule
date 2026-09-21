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

    @Test
    fun groupOverlappingCourses_clustersOverlappingSlots() {
        val courseA = Course(id = 1L, name = "计算机网络", dayOfWeek = 1, startSlot = 1, endSlot = 2)
        val courseB = Course(id = 2L, name = "操作系统", dayOfWeek = 1, startSlot = 2, endSlot = 3)
        val courseC = Course(id = 3L, name = "大学体育", dayOfWeek = 1, startSlot = 5, endSlot = 6)

        val groups = CourseConflictDetector.groupOverlappingCourses(listOf(courseA, courseB, courseC))
        assertEquals(2, groups.size)
        // A 与 B 在第 2 节相交，聚类在一起
        assertEquals(2, groups[0].size)
        assertEquals(setOf(1L, 2L), groups[0].map { it.id }.toSet())
        // C 独立聚类
        assertEquals(1, groups[1].size)
        assertEquals(3L, groups[1].first().id)
    }

    @Test
    fun resolveClusters_respectsPreferredCourseIds() {
        val course1 = Course(id = 101L, name = "概率论", dayOfWeek = 2, startSlot = 3, endSlot = 4)
        val course2 = Course(id = 102L, name = "离散数学", dayOfWeek = 2, startSlot = 3, endSlot = 4)

        // 默认无偏好时，第一门为 primaryCourse
        val clustersDefault = CourseConflictDetector.resolveClusters(listOf(course1, course2))
        assertEquals(1, clustersDefault.size)
        val cluster1 = clustersDefault.first()
        org.junit.Assert.assertTrue(cluster1.isOverlapping)
        assertEquals(2, cluster1.overlapCount)
        assertEquals(101L, cluster1.primaryCourse.id)

        // 指定偏好 102L 时，102L 成为代表课程并在最上层展示
        val clustersPreferred = CourseConflictDetector.resolveClusters(
            listOf(course1, course2),
            preferredCourseIds = setOf(102L)
        )
        assertEquals(1, clustersPreferred.size)
        assertEquals(102L, clustersPreferred.first().primaryCourse.id)
    }

    @Test
    fun resolveClusters_multiDaysAndMultipleClusters() {
        val mon1 = Course(id = 1L, dayOfWeek = 1, startSlot = 1, endSlot = 2)
        val mon2 = Course(id = 2L, dayOfWeek = 1, startSlot = 1, endSlot = 2)
        val wed = Course(id = 3L, dayOfWeek = 3, startSlot = 3, endSlot = 4)

        val clusters = CourseConflictDetector.resolveClusters(listOf(mon1, mon2, wed))
        assertEquals(2, clusters.size)
        val monCluster = clusters.first { it.primaryCourse.dayOfWeek == 1 }
        val wedCluster = clusters.first { it.primaryCourse.dayOfWeek == 3 }

        org.junit.Assert.assertTrue(monCluster.isOverlapping)
        assertEquals(2, monCluster.overlapCount)
        org.junit.Assert.assertFalse(wedCluster.isOverlapping)
        assertEquals(1, wedCluster.overlapCount)
    }
}
