package com.chen.schedule.util

import org.junit.Assert.*
import org.junit.Test

class JsonImporterTest {
    @Test fun sampleAndStructuredObjectsRemainCompatible() {
        assertEquals(4, JsonImporter.parse(JsonImporter.getSampleJson()).getOrThrow().size)
        assertEquals("高数", JsonImporter.parse("""{"courses":[{"name":"高数","dayOfWeek":1}]}""").getOrThrow().single().name)
    }
    @Test fun quotedIntegersLowercaseKeysAndRangesAreSupported() {
        val c = JsonImporter.parse("""[{"name":"高数","dayofweek":"2","startslot":"3-4","startweek":"2-12","weektype":"双周"}]""").getOrThrow().single()
        assertEquals(2, c.dayOfWeek)
        assertEquals(3, c.startSlot)
        assertEquals(4, c.endSlot)
        assertEquals(12, c.endWeek)
    }
    @Test fun invalidWeekdayIsRejectedWithoutClamping() {
        assertTrue(JsonImporter.parse("""[{"name":"高数","dayOfWeek":8}]""").isFailure)
    }
    @Test fun invalidRecordDoesNotProducePartialSuccess() {
        val result = JsonImporter.parse("""[{"name":"高数","dayOfWeek":1},{"dayOfWeek":2}]""")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("第 2 条"))
    }
    @Test fun invalidNumbersAndReversedRangesAreRejected() {
        listOf("\"nope\"", "0", "\"4-2\"", "\"1-2-3\"").forEach { value ->
            assertTrue(JsonImporter.parse("""[{"name":"高数","dayOfWeek":1,"startSlot":$value}]""").isFailure)
        }
    }
    @Test fun invalidWeekTypeAndOutOfBoundsWeeksAreRejected() {
        assertTrue(JsonImporter.parse("""[{"name":"高数","dayOfWeek":1,"weekType":"oddd"}]""").isFailure)
        assertTrue(JsonImporter.parse("""[{"name":"高数","dayOfWeek":1,"endWeek":54}]""").isFailure)
    }
    @Test fun missingWeekdayAndMalformedRowsAreRejected() {
        listOf("""[{"name":"高数"}]""", """[{"name":"高数","dayOfWeek":1},5]""", "{}", "[]").forEach {
            assertTrue(JsonImporter.parse(it).isFailure)
        }
    }
    @Test fun bomUnknownFieldsAndOptionalNullAreAccepted() {
        val c = JsonImporter.parse("\uFEFF" + """[{"name":"高数","dayOfWeek":1,"teacher":null,"extra":"ignore"}]""").getOrThrow().single()
        assertEquals("", c.teacher)
    }
}
