package com.chen.schedule.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class CalendarWeekTest {
    private val zone = ZoneId.of("America/New_York")
    private fun millis(date: String) = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test fun datePickerPreservesMondayOnBothSidesOfUtc() {
        val date = LocalDate.parse("2026-09-07")
        val utc = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        listOf(ZoneId.of("Asia/Shanghai"), zone).forEach { localZone ->
            val local = date.atStartOfDay(localZone).toInstant().toEpochMilli()
            assertEquals(utc, WeekCalculator.toPickerDateMillis(local, localZone))
            val restored = WeekCalculator.fromPickerDateMillis(utc, localZone)
            assertEquals(local, restored)
            assertEquals(date, WeekCalculator.semesterMonday(restored, localZone))
        }
    }

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
