package com.chen.schedule.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SemesterNameSuggestionsTest {

    @Test
    fun `七月起属于新学年的第一学期`() {
        val suggestions = SemesterNameSuggestions.generate(LocalDate.of(2026, 9, 11))
        assertEquals(
            listOf(
                "2026–2027学年第一学期",
                "2026–2027学年第二学期",
                "2025–2026学年第一学期"
            ),
            suggestions
        )
    }

    @Test
    fun `七月初同样归入新学年`() {
        val suggestions = SemesterNameSuggestions.generate(LocalDate.of(2026, 7, 1))
        assertEquals("2026–2027学年第一学期", suggestions.first())
    }

    @Test
    fun `六月底仍属于上一学年`() {
        val suggestions = SemesterNameSuggestions.generate(LocalDate.of(2026, 6, 30))
        assertEquals(
            listOf(
                "2025–2026学年第二学期",
                "2025–2026学年第一学期",
                "2024–2025学年第二学期"
            ),
            suggestions
        )
    }

    @Test
    fun `一月在年初同样跨年归入上一学年`() {
        val suggestions = SemesterNameSuggestions.generate(LocalDate.of(2026, 1, 15))
        assertEquals(
            listOf(
                "2025–2026学年第二学期",
                "2025–2026学年第一学期",
                "2024–2025学年第二学期"
            ),
            suggestions
        )
    }

    @Test
    fun `十二月末属于当年开始的学年`() {
        val suggestions = SemesterNameSuggestions.generate(LocalDate.of(2026, 12, 31))
        assertEquals("2026–2027学年第一学期", suggestions.first())
    }
}
