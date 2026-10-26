package com.chen.schedule.util

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.WeekType
import org.junit.Assert.*
import org.junit.Test

class DiagnosticReporterTest {

    @Test
    fun testBuildPayloadAndSerialize() {
        val courses = listOf(
            Course(
                name = "高等数学",
                teacher = "张老师",
                classroom = "101",
                dayOfWeek = 1,
                startSlot = 1,
                endSlot = 2,
                startWeek = 1,
                endWeek = 16,
                weekType = WeekType.ALL
            )
        )
        val payload = DiagnosticReporter.buildPayload(
            source = "test_source",
            semesterName = "2025-2026 第一学期",
            totalWeeks = 16,
            courses = courses,
            error = "测试错误",
            note = "排课测试"
        )
        assertEquals(1, payload.incomingCount)
        assertEquals("test_source", payload.source)
        val jsonStr = DiagnosticReporter.toJsonString(payload)
        assertTrue(jsonStr.contains("高等数学"))
        assertTrue(jsonStr.contains("2025-2026 第一学期"))

        val markdown = DiagnosticReporter.buildMarkdownReport(payload)
        assertTrue(markdown.contains("课表提取问题诊断反馈"))
        assertTrue(markdown.contains("高等数学"))
        assertTrue(markdown.contains("张老师"))
        assertTrue(markdown.contains("排课测试"))

        val issueUrl = DiagnosticReporter.buildGithubIssueUrl(payload)
        assertTrue(issueUrl.startsWith("https://github.com/hua-yi-yi/CourseSchedule/issues/new?title="))
        assertTrue(issueUrl.contains("test_source"))
    }
}
