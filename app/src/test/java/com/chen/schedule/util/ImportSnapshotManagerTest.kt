package com.chen.schedule.util

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.WeekType
import org.junit.Assert.*
import org.junit.Test

class ImportSnapshotManagerTest {

    @Test
    fun testSanitizeRawContent() {
        val rawHtml = """
            <html>
            <head><script>alert('xss');</script><style>.cls{color:red;}</style></head>
            <body>
            <div id="user_info">学号：202108889999 姓名：张大三 手机号：13812345678</div>
            <table id="Table1">
                <tr><td>高等数学</td><td>周一第1-2节</td></tr>
            </table>
            </body>
            </html>
        """.trimIndent()

        val sanitized = ImportSnapshotManager.sanitizeRawContent(rawHtml)
        // Table1 extracted
        assertTrue(sanitized.contains("<table id=\"Table1\">"))
        assertTrue(sanitized.contains("高等数学"))
        // Script and style stripped
        assertFalse(sanitized.contains("alert('xss')"))
        assertFalse(sanitized.contains(".cls{color:red;}"))
        // Sensitive personal info masked or excluded
        assertFalse(sanitized.contains("202108889999"))
        assertFalse(sanitized.contains("张大三"))
        assertFalse(sanitized.contains("13812345678"))
    }

    @Test
    fun testBuildExportMarkdownAndGithubUrl() {
        val snapshot = ImportSnapshot(
            source = "正方教务系统",
            semesterName = "2025-2026 第一学期",
            totalWeeks = 16,
            courseCount = 1,
            rawSnippet = "<table><tr><td>测试课</td></tr></table>",
            courses = listOf(
                DiagnosticCourseItem(
                    name = "大学英语",
                    teacher = "李老师",
                    classroom = "302",
                    dayOfWeek = 2,
                    startSlot = 3,
                    endSlot = 4,
                    startWeek = 1,
                    endWeek = 16,
                    weekType = "ALL"
                )
            )
        )

        val markdown = ImportSnapshotManager.buildExportMarkdown(snapshot)
        assertTrue(markdown.contains("课表导入本地快照诊断报告"))
        assertTrue(markdown.contains("正方教务系统"))
        assertTrue(markdown.contains("大学英语"))
        assertTrue(markdown.contains("李老师"))
        assertTrue(markdown.contains("测试课"))

        val issueUrl = ImportSnapshotManager.buildGithubIssueUrl(snapshot)
        assertTrue(issueUrl.startsWith("https://github.com/hua-yi-yi/CourseSchedule/issues/new?title="))
        assertTrue(issueUrl.contains("%E6%AD%A3%E6%96%B9%E6%95%99%E5%8A%A1%E7%B3%BB%E7%BB%9F"))
    }
}
