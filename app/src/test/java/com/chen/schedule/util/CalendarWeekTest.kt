package com.chen.schedule.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CalendarWeekTest {
    private val zone = ZoneId.of("America/New_York")
    private fun millis(date: String) = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test fun midweekStartAlignsToMonday() {
        assertEquals(LocalDate.parse("2026-09-07"), WeekCalculator.semesterMonday(millis("2026-09-09"), zone))
    }
    @Test fun nextMondayStartsWeekTwo() {
        assertEquals(2, WeekCalculator.currentWeek(millis("2026-09-09"), 16, millis("2026-09-14"), zone))
    }
    @Test fun daylightSavingDoesNotDelayWeekBoundary() {
        assertEquals(2, WeekCalculator.currentWeek(millis("2026-03-02"), 16, millis("2026-03-09"), zone))
    }
    @Test fun beforeSemesterClampsToOne() {
        assertEquals(1, WeekCalculator.currentWeek(millis("2026-09-09"), 16, millis("2026-08-01"), zone))
    }
}
