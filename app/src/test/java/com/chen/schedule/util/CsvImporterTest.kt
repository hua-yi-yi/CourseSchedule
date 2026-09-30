package com.chen.schedule.util

import org.junit.Assert.*
import org.junit.Test

class CsvImporterTest {
    @Test fun sampleStillParses() { assertEquals(4, CsvImporter.parse(CsvImporter.getSampleCsv()).getOrThrow().size) }
    @Test fun chineseAndQuotedBomHeadersAreSupported() {
        val c = CsvImporter.parse("\uFEFF\"课程名称\",\"星期\"\r\n高数,2\r\n").getOrThrow().single()
        assertEquals(2, c.dayOfWeek)
        assertEquals(16, c.endWeek)
    }
    @Test fun escapedQuotesAndCommasRemainInFieldContent() {
        val c = CsvImporter.parse("name,dayOfWeek,note\n高数,1,\"老师说\"\"复习\"\",带教材\"").getOrThrow().single()
        assertEquals("老师说\"复习\",带教材", c.note)
    }
    @Test fun quotedMultilineNotesDoNotCreateExtraCourses() {
        val c = CsvImporter.parse("name,dayOfWeek,note\n高数,1,\"第一行\n第二行\"").getOrThrow().single()
        assertEquals("第一行\n第二行", c.note)
    }
    @Test fun slotAndWeekRangesUseTheirActualEndValues() {
        val c = CsvImporter.parse("name,dayOfWeek,startSlot,startWeek\n高数,1,3-4,2-16").getOrThrow().single()
        assertEquals(4, c.endSlot)
        assertEquals(16, c.endWeek)
    }
    @Test fun invalidRecordRejectsWholeFileWithRecordNumber() {
        val result = CsvImporter.parse("name,dayOfWeek\n高数,1\n英语,8")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("第 2 条"))
    }
    @Test fun malformedNumbersAndInvertedWeeksAreRejected() {
        assertTrue(CsvImporter.parse("name,dayOfWeek\n高数,周二").isFailure)
        assertTrue(CsvImporter.parse("name,dayOfWeek,startWeek,endWeek\n高数,1,10,2").isFailure)
    }
    @Test fun unterminatedQuotesAndExtraColumnsAreRejected() {
        assertTrue(CsvImporter.parse("name,dayOfWeek,note\n高数,1,\"未闭合").isFailure)
        assertTrue(CsvImporter.parse("name,dayOfWeek\n高数,1,多余").isFailure)
    }
    @Test fun duplicateHeadersAndMissingRequiredValuesAreRejected() {
        assertTrue(CsvImporter.parse("name,课程名,dayOfWeek\n高数,英语,1").isFailure)
        assertTrue(CsvImporter.parse("name,dayOfWeek\n高数").isFailure)
    }
}
