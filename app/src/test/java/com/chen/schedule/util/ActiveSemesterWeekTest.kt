package com.chen.schedule.util

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ActiveSemesterWeekTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun millis(date: String) = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun week(date: String) = WeekCalculator.activeWeek(millis("2026-09-07"), 2, millis(date), zone)
    @Test fun beforeSemesterHasNoActiveWeek() { assertNull(week("2026-09-06")) }
    @Test fun firstMondayAndSundayAreWeekOne() { assertEquals(1, week("2026-09-07")); assertEquals(1, week("2026-09-13")) }
    @Test fun lastWeekEndsOnSunday() { assertEquals(2, week("2026-09-20")); assertNull(week("2026-09-21")) }
    @Test fun longAfterSemesterHasNoActiveWeek() { assertNull(week("2027-01-01")) }
    @Test fun invalidConfigurationHasNoActiveWeek() {
        assertNull(WeekCalculator.activeWeek(0, 16))
        assertNull(WeekCalculator.activeWeek(1, 0))
        assertNull(WeekCalculator.activeWeek(1, 54))
    }
    @Test fun midweekStartStillUsesMondayAlignedTeachingWeeks() {
        assertEquals(1, WeekCalculator.activeWeek(millis("2026-09-09"), 2, millis("2026-09-07"), zone))
    }
    @Test fun daylightSavingBoundaryUsesCalendarDays() {
        val ny = ZoneId.of("America/New_York")
        fun at(date: String) = LocalDate.parse(date).atStartOfDay(ny).toInstant().toEpochMilli()
        assertEquals(2, WeekCalculator.activeWeek(at("2026-03-02"), 2, at("2026-03-09"), ny))
    }
}
