package com.chen.schedule.util

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.Semester
import com.chen.schedule.domain.model.TimeScheme
import com.chen.schedule.domain.model.TimeSlot
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleBackupValidatorTest {
    private val semester = Semester(id = 1, name = "当前学期", startDate = 1725235200000, totalWeeks = 16)
    private val course = Course(name = "数学", semesterId = 1, endSlot = 2)
    private val slots = listOf(
        TimeSlot(slotNumber = 1, startTime = "08:00", endTime = "08:45"),
        TimeSlot(slotNumber = 2, startTime = "08:55", endTime = "09:40")
    )
    private fun backup() = ScheduleBackup(
        backupVersion = 2, semester = semester, courses = listOf(course), timeSlots = slots
    )

    @Test fun rejectsCourseSlotsMissingFromLegacySchedule() {
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleBackupValidator.validate(backup().copy(timeSlots = slots.take(1)))
        }
    }

    @Test fun rejectsWeeksOutsideSemesterAndSupportedRange() {
        listOf(17, 54, Int.MAX_VALUE).forEach { end ->
            assertThrows(IllegalArgumentException::class.java) {
                ScheduleBackupValidator.validate(backup().copy(courses = listOf(course.copy(endWeek = end))))
            }
        }
    }

    @Test(timeout = 1000) fun rejectsHugeSlotRangeWithoutExpandingIt() {
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleBackupValidator.validate(backup().copy(courses = listOf(course.copy(endSlot = Int.MAX_VALUE))))
        }
    }

    @Test fun validatesCoursesAgainstEachSemestersOwnScheme() {
        val second = semester.copy(id = 2, name = "其他学期", schemeId = 7)
        val schedule = TimeScheme(id = 7, name = "下午作息")
        val secondSlots = slots.map { it.copy(slotNumber = it.slotNumber + 2, schemeId = 7) }
        val secondCourse = course.copy(semesterId = 2, startSlot = 3, endSlot = 4)
        val data = backup().copy(
            semesters = listOf(semester, second),
            allCourses = listOf(course, secondCourse),
            schemes = listOf(schedule), schemeSlots = listOf(SchemeSlots(7, secondSlots))
        )
        ScheduleBackupValidator.validate(data)
        val error = assertThrows(IllegalArgumentException::class.java) {
            ScheduleBackupValidator.validate(data.copy(allCourses = listOf(course, secondCourse.copy(startSlot = 1))))
        }
        assertTrue(error.message.orEmpty().contains("其他学期"))
    }

    @Test fun checksNonCurrentSemesterWeekLimit() {
        val second = semester.copy(id = 2, name = "短学期", totalWeeks = 8)
        val data = backup().copy(
            semesters = listOf(semester, second), allCourses = listOf(course, course.copy(semesterId = 2))
        )
        val error = assertThrows(IllegalArgumentException::class.java) { ScheduleBackupValidator.validate(data) }
        assertTrue(error.message.orEmpty().contains("短学期"))
    }

    @Test fun reusesBuiltInScheduleWhenBackupOmitsItsSlots() {
        val builtIn = TimeScheme(id = 7, name = TimeSchemeTemplates.SUMMER_NAME, kind = TimeScheme.KIND_BUILT_IN)
        ScheduleBackupValidator.validate(backup().copy(
            semester = semester.copy(schemeId = 7), timeSlots = emptyList(), schemes = listOf(builtIn)
        ))
    }

    @Test fun validatesAgainstActualReusedBuiltInInsteadOfBackupSlots() {
        val builtIn = TimeScheme(id = 7, name = TimeSchemeTemplates.SUMMER_NAME, kind = TimeScheme.KIND_BUILT_IN)
        val data = backup().copy(
            semester = semester.copy(schemeId = 7), timeSlots = emptyList(), schemes = listOf(builtIn),
            schemeSlots = listOf(SchemeSlots(7, slots.map { it.copy(schemeId = 7) }))
        )
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleBackupValidator.validate(data, mapOf(builtIn.name to slots.take(1)))
        }
    }

    @Test fun acceptsEmptySemesterWithoutSlotsAndValidLegacyBackup() {
        ScheduleBackupValidator.validate(backup().copy(courses = emptyList(), timeSlots = emptyList()))
        ScheduleBackupValidator.validate(backup().copy(backupVersion = 1))
    }

    @Test fun rejectsInvalidUnusedSchemeAndSemester() {
        val unused = TimeScheme(id = 7, name = "未使用方案")
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleBackupValidator.validate(backup().copy(
                schemes = listOf(unused),
                schemeSlots = listOf(SchemeSlots(7, listOf(slots.first().copy(schemeId = 7, endTime = "07:00"))))
            ))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleBackupValidator.validate(backup().copy(semester = semester.copy(startDate = 0)))
        }
    }
}
