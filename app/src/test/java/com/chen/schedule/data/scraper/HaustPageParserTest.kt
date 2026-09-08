package com.chen.schedule.data.scraper

import com.chen.schedule.domain.model.WeekType
import org.junit.Assert.*
import org.junit.Test

class HaustPageParserTest {
    private fun grid(rows: String) = "<table id='manualArrangeCourseTable'><thead><tr><th>节次/周次</th>${(1..7).joinToString("") { "<th>星期$it</th>" }}</tr></thead><tbody>$rows</tbody></table>"
    @Test fun preservesParenthesesAndCampus() {
        val c = HaustPageParser.parse(grid("<tr><td>第一节</td><td>高等数学A(1)(test_100.001) (教师甲)<br>(6-18周,1-2 节,教室甲(开元校区)</td></tr>")).single()
        assertEquals("高等数学A(1)", c.name)
        assertEquals("教室甲(开元校区)", c.classroom)
        assertEquals(18, c.endWeek)
    }
    @Test fun respectsRowspanAndExplicitSlotsAfterNoon() {
        val cs = HaustPageParser.parse(grid("""<tr><td>午1</td><td rowspan='2'>测试甲(test_1.1) (教师甲)<br>(1-2周,3-4 节)</td></tr>
            <tr><td>午2</td><td>测试乙(test_2.1) (教师乙)<br>(2周,5-6 节)</td></tr>"""))
        assertEquals(listOf(1,2), cs.map { it.dayOfWeek })
        assertEquals(5, cs[1].startSlot)
        assertEquals("", cs[1].classroom)
    }
    @Test fun preservesOddEvenAndChangingRooms() {
        val cs = HaustPageParser.parse(grid("""<tr><td>第一节</td><td>
            前端开发(test_1.1) (教师甲)<br>(单1-7周,1-2 节,甲室)<br>
            前端开发(test_1.1) (教师甲)<br>(9周,1-2 节,乙室)<br>
            线性代数(test_2.1) (教师乙)<br>(双2-12周,1-2 节,丙室)</td></tr>"""))
        assertEquals(3, cs.size)
        assertEquals(WeekType.ODD, cs[0].weekType)
        assertEquals(WeekType.EVEN, cs[2].weekType)
        assertEquals("乙室", cs[1].classroom)
    }
    @Test fun handlesSupAndEmptyLines() {
        val c = HaustPageParser.parse(grid("<tr><td>第五节</td><td>体育（3）<sup>体育舞蹈</sup>(test_1.1) (教师甲)<br><br><br>(1-16周,5-6 节)</td></tr>")).single()
        assertEquals("体育（3）体育舞蹈", c.name)
        assertEquals("", c.classroom)
    }
    @Test fun doesNotFillGapsBetweenWeeks() {
        val cs = HaustPageParser.parse(grid("<tr><td>第一节</td><td>测试(test_1.1) (教师甲)<br>(1,3,7-9周,1-2 节)</td></tr>"))
        assertEquals(listOf(1,3,7), cs.map { it.startWeek })
        assertFalse(cs.any { it.appliesToWeek(2) || it.appliesToWeek(5) })
    }
    @Test fun rejectsMalformedInsteadOfPartialImport() {
        assertTrue(runCatching { HaustPageParser.parse(grid("<tr><td>第一节</td><td>无法解析的课程</td></tr>")) }.isFailure)
        assertTrue(runCatching { HaustPageParser.parse("<html>登录页</html>") }.isFailure)
        assertTrue(runCatching { HaustPageParser.parse(grid("<tr><td>第一节</td><td></td></tr>")) }.isFailure)
    }
}
