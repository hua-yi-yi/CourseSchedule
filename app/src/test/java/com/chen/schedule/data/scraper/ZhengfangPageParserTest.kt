package com.chen.schedule.data.scraper

import com.chen.schedule.domain.model.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ZhengfangPageParserTest {

    // ---------- 周次文本解析 ----------

    @Test
    fun `解析普通周次范围`() {
        assertEquals(
            ZhengfangPageParser.WeekInfo(1, 16, WeekType.ALL),
            ZhengfangPageParser.parseWeekInfo("1-16周")
        )
    }

    @Test
    fun `解析单周标记`() {
        assertEquals(
            ZhengfangPageParser.WeekInfo(1, 8, WeekType.ODD),
            ZhengfangPageParser.parseWeekInfo("1-8周(单)")
        )
    }

    @Test
    fun `解析双周标记(全角括号)`() {
        assertEquals(
            ZhengfangPageParser.WeekInfo(2, 16, WeekType.EVEN),
            ZhengfangPageParser.parseWeekInfo("2-16周（双）")
        )
    }

    @Test
    fun `解析单周次`() {
        assertEquals(
            ZhengfangPageParser.WeekInfo(3, 3, WeekType.ALL),
            ZhengfangPageParser.parseWeekInfo("第3周")
        )
    }

    @Test
    fun `解析波浪线范围`() {
        assertEquals(
            ZhengfangPageParser.WeekInfo(1, 16, WeekType.ALL),
            ZhengfangPageParser.parseWeekInfo("1～16周")
        )
    }

    @Test
    fun `周次文本中的单双标记不会影响信息行分类`() {
        // "高等数学(双语)" 不含数字+周,不应被误判为单周
        assertNull(ZhengfangPageParser.parseWeekInfo("高等数学(双语)"))
    }

    @Test
    fun `无周次信息返回null`() {
        assertNull(ZhengfangPageParser.parseWeekInfo("教学楼101"))
    }

    // ---------- 单元格行拆分 ----------

    @Test
    fun `单元格按br拆分并过滤空行`() {
        val lines = ZhengfangPageParser.parseCellLines("高等数学<br>张老师<br><br>1-16周")
        assertEquals(listOf("高等数学", "张老师", "1-16周"), lines)
    }

    @Test
    fun `单元格兼容div分隔`() {
        val lines = ZhengfangPageParser.parseCellLines("<div>高等数学</div><div>张老师</div><div>1-16周</div>")
        assertEquals(listOf("高等数学", "张老师", "1-16周"), lines)
    }

    // ---------- 课表解析 ----------

    private val sampleHtml = """
        <table id="Table1">
        <tr><td>节次</td><td>星期一</td><td>星期二</td><td>星期三</td><td>星期四</td><td>星期五</td><td>星期六</td><td>星期日</td></tr>
        <tr><td>1</td><td>高等数学<br>张老师<br>教学楼101<br>1-16周</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
        <tr><td>2</td><td>大学英语<br>李老师<br>教学楼202<br>1-8周(单)</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
        <tr><td>3</td><td rowspan="2">数据结构<br>王老师<br>实验楼301<br>1-16周</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
        <tr><td>4</td><td>体育<br>赵老师<br>1-16周</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
        </table>
    """.trimIndent()

    @Test
    fun `解析课表-基本信息与节次`() {
        val courses = ZhengfangPageParser.parseCourses(sampleHtml)
        assertEquals(4, courses.size)

        val math = courses.first { it.name == "高等数学" }
        assertEquals(1, math.dayOfWeek)
        assertEquals(1, math.startSlot)
        assertEquals(1, math.endSlot)
        assertEquals("张老师", math.teacher)
        assertEquals("教学楼101", math.classroom)
        assertEquals(WeekType.ALL, math.weekType)
    }

    @Test
    fun `解析课表-单双周`() {
        val courses = ZhengfangPageParser.parseCourses(sampleHtml)
        val english = courses.first { it.name == "大学英语" }
        assertEquals(WeekType.ODD, english.weekType)
        assertEquals(1, english.startWeek)
        assertEquals(8, english.endWeek)
    }

    @Test
    fun `解析课表-rowspan跨节合并为1-2节连排`() {
        val courses = ZhengfangPageParser.parseCourses(sampleHtml)
        val ds = courses.first { it.name == "数据结构" }
        assertEquals(3, ds.startSlot)
        assertEquals(4, ds.endSlot)
        assertEquals(1, ds.dayOfWeek)
    }

    @Test
    fun `解析课表-被rowspan覆盖的列正确映射到周二`() {
        val courses = ZhengfangPageParser.parseCourses(sampleHtml)
        val pe = courses.first { it.name == "体育" }
        assertEquals(2, pe.dayOfWeek)
        assertEquals(4, pe.startSlot)
        assertEquals("赵老师", pe.teacher)
        assertEquals("", pe.classroom) // 无教室信息
    }

    @Test
    fun `解析课表-同名课程颜色一致`() {
        val courses = ZhengfangPageParser.parseCourses(sampleHtml)
        val colors = courses.filter { it.name == "高等数学" }.map { it.color }
        assertEquals(1, colors.size)
    }

    @Test
    fun `解析课表-相邻行重复课程自动合并`() {
        val html = """
            <table id="Table1">
            <tr><td>1</td><td>英语听说<br>1-16周</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
            <tr><td>2</td><td>英语听说<br>1-16周</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
            </table>
        """.trimIndent()
        val courses = ZhengfangPageParser.parseCourses(html)
        assertEquals(1, courses.size)
        assertEquals(1, courses[0].startSlot)
        assertEquals(2, courses[0].endSlot)
    }

    @Test
    fun `同一单元格的多门课程分别保留各自周次`() {
        val html = """
            <table id="Table1"><tr><td>1</td><td>
            课程甲<br>甲老师<br>甲教室<br>1-8周(单)<br>
            课程乙<br>乙老师<br>乙教室<br>9-16周(双)
            </td></tr></table>
        """.trimIndent()
        val courses = ZhengfangPageParser.parseCourses(html)
        assertEquals(2, courses.size)
        val first = courses.single { it.name == "课程甲" }
        assertEquals("甲老师", first.teacher)
        assertEquals("甲教室", first.classroom)
        assertEquals(1, first.startWeek)
        assertEquals(8, first.endWeek)
        assertEquals(WeekType.ODD, first.weekType)
        val second = courses.single { it.name == "课程乙" }
        assertEquals("乙老师", second.teacher)
        assertEquals("乙教室", second.classroom)
        assertEquals(9, second.startWeek)
        assertEquals(16, second.endWeek)
        assertEquals(WeekType.EVEN, second.weekType)
    }

    @Test
    fun `多门课程缺少教师和教室仍分别关联周次`() {
        val html = """
            <table id="Table1"><tr><td>1</td><td>
            课程甲<br>1-8周(单)<br>课程乙<br>9-16周(双)
            </td></tr></table>
        """.trimIndent()
        val courses = ZhengfangPageParser.parseCourses(html)
        assertEquals(listOf("课程甲", "课程乙"), courses.map { it.name })
        assertEquals(listOf(WeekType.ODD, WeekType.EVEN), courses.map { it.weekType })
        assertEquals(listOf(1, 9), courses.map { it.startWeek })
        assertEquals(listOf(8, 16), courses.map { it.endWeek })
        assertEquals(listOf("", ""), courses.map { it.teacher })
        assertEquals(listOf("", ""), courses.map { it.classroom })
    }

    @Test
    fun `单门课程的周次夹在信息行中保持兼容`() {
        val html = """
            <table id="Table1"><tr><td>1</td><td>
            课程甲<br>1-8周(单)<br>甲老师<br>甲教室
            </td></tr></table>
        """.trimIndent()
        val course = ZhengfangPageParser.parseCourses(html).single()
        assertEquals("课程甲", course.name)
        assertEquals("甲老师", course.teacher)
        assertEquals("甲教室", course.classroom)
        assertEquals(WeekType.ODD, course.weekType)
    }

    @Test
    fun `多门课程的周次在块开头或信息之间仍分别关联`() {
        val cells = listOf(
            "1-8周(单)<br>课程甲<br>甲老师<br>甲教室<br>9-16周(双)<br>课程乙<br>乙老师<br>乙教室",
            "课程甲<br>1-8周(单)<br>甲老师<br>甲教室<br>课程乙<br>9-16周(双)<br>乙老师<br>乙教室"
        )
        cells.forEach { cell ->
            val courses = ZhengfangPageParser.parseCourses(
                "<table id='Table1'><tr><td>1</td><td>$cell</td></tr></table>"
            )
            assertEquals(listOf("课程甲", "课程乙"), courses.map { it.name })
            assertEquals(listOf("甲老师", "乙老师"), courses.map { it.teacher })
            assertEquals(listOf("甲教室", "乙教室"), courses.map { it.classroom })
            assertEquals(listOf(1, 9), courses.map { it.startWeek })
            assertEquals(listOf(8, 16), courses.map { it.endWeek })
            assertEquals(listOf(WeekType.ODD, WeekType.EVEN), courses.map { it.weekType })
        }
    }

    @Test
    fun `未提供周次的多门课程保持旧格式兼容`() {
        val html = """
            <table id="Table1"><tr><td>1</td><td>
            课程甲<br>甲老师<br>甲教室<br>课程乙<br>乙老师<br>乙教室
            </td></tr></table>
        """.trimIndent()
        val courses = ZhengfangPageParser.parseCourses(html)
        assertEquals(listOf("课程甲", "课程乙"), courses.map { it.name })
        assertEquals(listOf(1, 1), courses.map { it.startWeek })
        assertEquals(listOf(16, 16), courses.map { it.endWeek })
        assertEquals(listOf(WeekType.ALL, WeekType.ALL), courses.map { it.weekType })
    }

    @Test
    fun `多门课程只有一条周次时拒绝歧义`() {
        val html = """
            <table id="Table1"><tr><td>1</td><td>
            课程甲<br>甲老师<br>甲教室<br>课程乙<br>乙老师<br>乙教室<br>1-8周(单)
            </td></tr></table>
        """.trimIndent()
        assertThrows(IllegalArgumentException::class.java) { ZhengfangPageParser.parseCourses(html) }
    }

    @Test
    fun `周二课程穿插时周一相邻重复课程仍合并`() {
        val html = """
            <table id="Table1">
            <tr><td>1</td><td>英语听说<br>1-16周</td><td>周二课程<br>1-16周</td></tr>
            <tr><td>2</td><td>英语听说<br>1-16周</td><td></td></tr>
            </table>
        """.trimIndent()
        val courses = ZhengfangPageParser.parseCourses(html)
        assertEquals(2, courses.size)
        val english = courses.single { it.name == "英语听说" }
        assertEquals(1, english.dayOfWeek)
        assertEquals(1, english.startSlot)
        assertEquals(2, english.endSlot)
        assertEquals(2, courses.single { it.name == "周二课程" }.dayOfWeek)
    }

    @Test
    fun `解析课表-表头行被跳过`() {
        val html = """
            <table id="Table1">
            <tr><td>节次</td><td>星期一</td><td>星期二</td><td>星期三</td><td>星期四</td><td>星期五</td><td>星期六</td><td>星期日</td></tr>
            <tr><td>时间</td><td>08:00</td><td>08:00</td><td>08:00</td><td>08:00</td><td>08:00</td><td>08:00</td><td>08:00</td></tr>
            <tr><td>1</td><td>高等数学<br>张老师<br>教学楼101<br>1-16周</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
            </table>
        """.trimIndent()
        val courses = ZhengfangPageParser.parseCourses(html)
        assertEquals(1, courses.size)
        assertEquals("高等数学", courses[0].name)
    }
}
