package com.chen.schedule.util

import com.chen.schedule.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class UserFlowRegressionTest {
    private val original = Course(id = 9, name = "数学", classroom = "A101", startWeek = 1, endWeek = 20, importSource = "file")
    @Test fun onceMovesOnlyTheChosenOccurrence() {
        val result = CourseChangePlanner.plan(original, original.copy(dayOfWeek = 5, classroom = "A102"), ChangeScope.ONCE, 6)
        assertEquals(3, result.size)
        (1..20).forEach { week ->
            val course = result.single { it.appliesToWeek(week) }
            assertEquals(if (week == 6) 5 else 1, course.dayOfWeek)
            assertEquals(if (week == 6) "A102" else "A101", course.classroom)
        }
        assertTrue(result.all { it.importSource.isEmpty() })
    }
    @Test fun oddWeekCancellationPreservesEveryOtherOccurrence() {
        val odd = original.copy(weekType = WeekType.ODD)
        val result = CourseChangePlanner.plan(odd, null, ChangeScope.ONCE, 7)
        (1..20).forEach { week -> assertEquals(week % 2 == 1 && week != 7, result.any { it.appliesToWeek(week) }) }
        assertTrue(runCatching { CourseChangePlanner.plan(odd, null, ChangeScope.ONCE, 8) }.isFailure)
    }
    @Test fun futureChangesKeepPriorWeeksAndParity() {
        val odd = original.copy(weekType = WeekType.ODD)
        val result = CourseChangePlanner.plan(odd, odd.copy(classroom = "B201"), ChangeScope.FUTURE, 9)
        (1..20).filter { it % 2 == 1 }.forEach { week ->
            assertEquals(if (week < 9) "A101" else "B201", result.single { it.appliesToWeek(week) }.classroom)
        }
    }
    @Test fun boundaryCancellationProducesNoEmptyRecurrences() {
        assertEquals(1, CourseChangePlanner.plan(original, null, ChangeScope.ONCE, 1).size)
        assertEquals(1, CourseChangePlanner.plan(original, null, ChangeScope.ONCE, 20).size)
        assertTrue(CourseChangePlanner.plan(original.copy(startWeek = 6, endWeek = 6), null, ChangeScope.ONCE, 6).isEmpty())
    }
    @Test fun exactDuplicateDoesNotBecomeAnUpdateDueToProvenance() {
        assertTrue(ImportReview.suggestions(listOf(original.copy(id = 0, importSource = "")), listOf(original), "file").isEmpty())
        assertEquals(1, CourseImportRules.plan(listOf(original.copy(importSource = "")), listOf(original)).skipped)
    }
    @Test fun changedRoomSuggestsOnlyMatchingImportedCourse() {
        val incoming = original.copy(id = 0, classroom = "A102")
        assertEquals(mapOf(0 to 9L), ImportReview.suggestions(listOf(incoming), listOf(original), "file"))
        assertTrue(ImportReview.suggestions(listOf(incoming), listOf(original.copy(importSource = "")), "file").isEmpty())
        assertTrue(ImportReview.suggestions(listOf(incoming), listOf(original), "haust").isEmpty())
    }
    @Test fun ambiguousUpdatesRequireExplicitChoice() {
        val incoming = listOf(original.copy(id = 0, classroom = "A102"), original.copy(id = 0, classroom = "A103"))
        assertTrue(ImportReview.suggestions(incoming, listOf(original), "file").isEmpty())
    }
    @Test fun manualRecordsAndOtherSourcesCannotBeDeletedThroughImport() {
        val selection = ImportSelection(listOf(original.copy(id = 0)), removals = setOf(9))
        assertTrue(runCatching { ImportReview.validateSelection(selection, listOf(original.copy(importSource = "")), "file") }.isFailure)
        assertTrue(runCatching { ImportReview.validateSelection(selection, listOf(original), "haust") }.isFailure)
    }
    @Test fun oneOldRecordCannotBeBothRemovedAndReplaced() {
        val selection = ImportSelection(listOf(original.copy(id = 0)), mapOf(0 to 9L), setOf(9))
        assertTrue(runCatching { ImportReview.validateSelection(selection, listOf(original), "file") }.isFailure)
    }
}
