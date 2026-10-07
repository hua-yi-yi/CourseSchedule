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
    @Test fun fallsBackToTheoryArrangementRows() {
        val html = """
            <html><body>
            <table id='manualArrangeCourseTable'><tr><th>节次/周次</th></tr></table>
            <table><tr><th>序号</th><th>课程</th><th>任课教师</th><th>周次</th><th>节次</th><th>地点</th></tr>
            <tr><td>1</td><td>[rjxy_5111003]后现代文化</td><td>刘俊</td><td>[1-10]</td><td>星期二[10-11节]</td><td>开元公教5-101多媒体</td></tr>
            <tr><td>[2-5]</td><td>星期四[10-11节]</td><td>开元公教5-102多媒体</td></tr></table>
            </body></html>
        """.trimIndent()
        val courses = HaustPageParser.parse(html)
        assertEquals(2, courses.size)
        assertEquals("后现代文化", courses[0].name)
        assertEquals("刘俊", courses[0].teacher)
        assertEquals(2, courses[0].dayOfWeek)
        assertEquals(10, courses[0].startSlot)
        assertEquals(11, courses[0].endSlot)
        assertEquals(4, courses[1].dayOfWeek)
        assertEquals("开元公教5-102多媒体", courses[1].classroom)
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

    private fun detailsTable(rows: String) =
        "<table><tr><th>序号</th><th>课程</th><th>任课教师</th><th>周次</th><th>节次</th><th>地点</th></tr>$rows</table>"

    /** 遗漏修复回归:网格有课的同时,明细表独有的课程也必须被保留。 */
    @Test fun keepsCoursesFoundOnlyInDetailsWhenGridHasOthers() {
        val html = """
            <html><body>
            ${grid("<tr><td>第一节</td><td>高等数学(test_1.1) (教师甲)<br>(1-16周,1-2 节,甲教室)</td></tr>")}
            ${detailsTable("<tr><td>1</td><td>[ty_1001]大学体育</td><td>教师乙</td><td>[1-16]</td><td>星期三[5-6节]</td><td>田径场</td></tr>")}
            </body></html>
        """.trimIndent()
        val courses = HaustPageParser.parse(html)
        assertEquals(2, courses.size)
        assertEquals(setOf("高等数学", "大学体育"), courses.map { it.name }.toSet())
        assertEquals(3, courses.first { it.name == "大学体育" }.dayOfWeek)
    }

    /** 同一门课在网格与明细都出现时只保留一条,避免课表上叠两块。 */
    @Test fun dedupesSameCourseFromBothSources() {
        val html = """
            <html><body>
            ${grid("<tr><td>第二节</td><td></td><td>大学物理(test_2.2) (教师丙)<br>(1-16周,3-4 节,物理楼201)</td></tr>")}
            ${detailsTable("<tr><td>1</td><td>[wl_1002]大学物理</td><td>教师丙</td><td>[1-16]</td><td>星期二[3-4节]</td><td>物理楼201</td></tr>")}
            </body></html>
        """.trimIndent()
        assertEquals(1, HaustPageParser.parse(html).size)
    }

    /** 网格畸形但明细可解析时,优先返回明细数据而不是整体失败。 */
    @Test fun recoversWithDetailsWhenGridIsMalformed() {
        val html = """
            <html><body>
            ${grid("<tr><td>第一节</td><td>无法解析的课程</td></tr>")}
            ${detailsTable("<tr><td>1</td><td>[hx_3003]无机化学</td><td>教师丁</td><td>[双1-8]</td><td>星期四[1-2节]</td><td>化学楼301</td></tr>")}
            </body></html>
        """.trimIndent()
        val courses = HaustPageParser.parse(html)
        assertEquals(1, courses.size)
        assertEquals(WeekType.EVEN, courses[0].weekType)
    }

    /** 明细行缺教室列时仍保留课程(教室为空)。 */
    @Test fun parsesDetailsRowWithMissingRoom() {
        val html = detailsTable("<tr><td>1</td><td>[rw_1004]人文导论</td><td>教师戊</td><td>[1-8]</td><td>星期五[7-8节]</td></tr>")
        val courses = HaustPageParser.parse(html)
        assertEquals(1, courses.size)
        assertEquals("", courses[0].classroom)
        assertEquals(5, courses[0].dayOfWeek)
    }

    /** 真实抓取的 2025–2026-1 全周课表页(含换教室、单双周、午休行、体育课 sup 标记)必须完整解析。 */
    @Test fun parsesRealCapturedPage() {
        val html = javaClass.getResourceAsStream("/haust/real-course-table.html")!!
            .bufferedReader().use { it.readText() }
        val courses = HaustPageParser.parse(html)
        assertTrue("真实页面至少应解析出 30 条安排,实际 ${courses.size}", courses.size >= 30)
        // 课程名不得夹带教务序号,也不得出现明细表误识别产生的符号名
        assertTrue(courses.none { it.name.contains("rjxy_") })
        assertTrue("不应产生空/符号名称课程: ${courses.map { it.name }}", courses.all { it.name.length >= 2 && !it.name.startsWith(",") && !it.name.startsWith("[") })
        assertTrue("教师列不得混入节次文本", courses.none { it.teacher.contains("节") })
        // 网格里「(1-6 8-11周,…)」空格分段:马克思主义基本原理两天各有 1-6 与 8-11 两段
        val marx = courses.filter { it.name == "马克思主义基本原理" }
        assertEquals(4, marx.size)
        assertTrue(marx.all { it.startSlot == 10 && it.endSlot == 11 })
        assertEquals(setOf(2, 4), marx.map { it.dayOfWeek }.toSet())
        listOf(2, 4).forEach { day ->
            val dayWeeks = (1..20).filter { w -> marx.filter { it.dayOfWeek == day }.any { it.appliesToWeek(w) } }
            assertEquals((1..6) + (8..11), dayWeeks)
        }
        // 同一格子先后换教室:算法设计与分析(星期三)1-5 周与 11-16 周在不同教室
        val algo = courses.filter { it.name == "算法设计与分析" && it.startSlot == 1 }
        assertEquals(listOf(1 to 5, 11 to 16), algo.map { it.startWeek to it.endWeek })
        assertTrue(algo.all { it.dayOfWeek == 3 })
        // 体育课:网格与明细表名称仅差一个空格,去重后只保留一条
        assertEquals(1, courses.count { it.name.replace(" ", "").startsWith("体育（3）") && it.startSlot == 5 })
        // 单双周共用格子
        val front = courses.first { it.name == "前端开发技术" && it.weekType == WeekType.ODD && it.startSlot == 1 }
        assertEquals(1, front.startWeek)
        assertEquals(7, front.endWeek)
        // 体育课 sup 名称
        val pe = courses.first { it.name.startsWith("体育") }
        assertEquals("体育（3）体育舞蹈", pe.name)
        // 教室保留校区后缀(不完整括号)
        val withCampus = courses.first { it.classroom.contains("开元校区") }
        assertTrue(withCampus.classroom.startsWith("开元公教"))
        // 明细表裸偶数周列表(线代星期五)与网格「双2-12周」是同一安排,不应重复出现
        val linear = courses.filter { it.name == "线性代数B" && it.dayOfWeek == 5 }
        assertEquals(1, linear.size)
        assertEquals(WeekType.EVEN, linear[0].weekType)
    }

    /** 网格周次段以空格分隔(真实教务数据)必须拆成多段,而不是抛 NumberFormatException。 */
    @Test fun gridWeekRangesSeparatedBySpace() {
        val cs = HaustPageParser.parse(grid("<tr><td>第一节</td><td>马克思主义基本原理(test_1.1) (教师甲)<br>(1-6 8-11周,10-11 节,教室甲)</td></tr>"))
        assertEquals(listOf(1 to 6, 8 to 11), cs.map { it.startWeek to it.endWeek })
        assertTrue(cs.all { it.classroom == "教室甲" && it.dayOfWeek == 1 })
    }

    /** 网格周次也可用「至/—/~」连接。 */
    @Test fun gridWeekRangeWithChineseDash() {
        val cs = HaustPageParser.parse(grid("<tr><td>第一节</td><td>大学语文(test_1.1) (教师甲)<br>(1至6周,1-2 节,教室甲)<br>大学语文(test_1.1) (教师甲)<br>(8—11周,1-2 节,教室乙)</td></tr>"))
        assertEquals(listOf(1 to 6, 8 to 11), cs.map { it.startWeek to it.endWeek })
        assertEquals("教室乙", cs[1].classroom)
    }

    /** 新版明细表:多列布局 + 跨行课程 + 续行「[1-6],[8-10]」与裸周次列表。 */
    @Test fun parsesNewDetailsLayoutWithoutPhantomNames() {
        val html = """
            <html><body>
            <table>
            <tr><th>序号</th><th>课程</th><th>任课教师</th><th>学分</th><th>起止周</th><th>考核方式</th><th>时间</th><th>地点</th></tr>
            <tr><th>周次</th><th>节次</th></tr>
            <tr><td rowspan='3'>2</td><td rowspan='3'>[rjxy_2611001]马克思主义基本原理</td><td rowspan='3'>刘鸿亮</td><td rowspan='3'>3</td><td rowspan='3'>1-6,8-11</td><td rowspan='3'>考试</td>
                <td>11</td><td>星期二[10-11节]</td><td>开元公教5-101</td></tr>
            <tr><td>[1-6],[8-10]</td><td>星期二[10-11节]</td><td>开元公教5-101</td></tr>
            <tr><td>[1-6],[8-10]</td><td>星期四[10-11节]</td><td>开元公教5-101</td></tr>
            <tr><td>5</td><td>[rjxy_1012049]线性代数B</td><td>和凌云</td><td>2.5</td><td>1-14</td><td>考试</td>
                <td>[1-14]</td><td>星期三[5-6节]</td><td>开元公教4-106</td></tr>
            <tr><td>2,4,6,8,10,12</td><td>星期五[1-2节]</td><td>开元公教4-106</td></tr>
            </table>
            </body></html>
        """.trimIndent()
        val cs = HaustPageParser.parse(html)
        // 不应出现名称为「,[8-10]」或教师为节次文本的课程
        assertTrue(cs.none { it.name.startsWith(",") || it.teacher.contains("节") })
        val marx = cs.filter { it.name == "马克思主义基本原理" }
        assertEquals(setOf(2, 4), marx.map { it.dayOfWeek }.toSet())
        assertTrue(marx.all { it.teacher == "刘鸿亮" && it.startSlot == 10 && it.endSlot == 11 })
        // 续行的两个周次段都要保留
        val tue = marx.filter { it.dayOfWeek == 2 }
        assertEquals(setOf(11, 1, 8), tue.map { it.startWeek }.toSet())
        // 裸偶数周列表识别为双周
        val linearFri = cs.first { it.name == "线性代数B" && it.dayOfWeek == 5 }
        assertEquals(WeekType.EVEN, linearFri.weekType)
        assertEquals(2, linearFri.startWeek)
        assertEquals(12, linearFri.endWeek)
    }

    /** 网格「双2-12周」与明细表裸列表「2,4,6,…」是同一安排时,并集不应重复。 */
    @Test fun unionDedupesWeekListAgainstGridRange() {
        val html = """
            <html><body>
            ${grid("<tr><td>第一节</td><td></td><td></td><td></td><td></td><td>线性代数B(test_2.1) (教师乙)<br>(双2-12周,1-2 节,公教4-106)</td></tr>")}
            <table><tr><th>序号</th><th>课程</th><th>任课教师</th><th>周次</th><th>节次</th><th>地点</th></tr>
            <tr><td>1</td><td>[rjxy_1012049]线性代数B</td><td>教师乙</td><td>[1-14]</td><td>星期三[5-6节]</td><td>公教4-106</td></tr>
            <tr><td>2,4,6,8,10,12</td><td>星期五[1-2节]</td><td>公教4-106</td></tr></table>
            </body></html>
        """.trimIndent()
        val cs = HaustPageParser.parse(html)
        val friday = cs.filter { it.dayOfWeek == 5 && it.name == "线性代数B" }
        assertEquals("星期五同一安排不应因周次写法不同而重复: $friday", 1, friday.size)
        assertEquals(WeekType.EVEN, friday[0].weekType)
        assertEquals(2, friday[0].startWeek)
        assertEquals(12, friday[0].endWeek)
        // 星期三的安排来自明细表,网格没有,必须保留
        assertEquals(1, cs.count { it.dayOfWeek == 3 && it.name == "线性代数B" })
    }
}
