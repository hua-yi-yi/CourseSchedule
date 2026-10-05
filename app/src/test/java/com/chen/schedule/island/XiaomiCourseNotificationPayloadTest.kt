package com.chen.schedule.island

import com.chen.schedule.domain.model.Course
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaomiCourseNotificationPayloadTest {
    private val course = Course(name = "数学\"A\"\n班", classroom = "B204")
    private val upcoming = IslandState.Upcoming(
        course, course.name, course.classroom, "", "第1-2节", "08:00", "09:40", 1_800_000,
        30, 0, "30分后 · 数学", "B204 · 08:00上课"
    )
    private val ongoing = IslandState.Ongoing(
        course, course.name, course.classroom, "", "第1-2节", "08:00", "09:40", 1_800_000, 7_800_000,
        100, 100, 0f, 0, "数学", "B204 · 09:40下课"
    )

    private fun body(state: IslandState, protocol: Int) = Json.parseToJsonElement(
        XiaomiCourseNotificationPayload.create(state, protocol, "approved_course_business", 0)!!
    ).jsonObject.getValue("param_v2").jsonObject

    @Test
    fun `OS2只包含焦点通知与状态栏不附加岛模板`() {
        val body = body(upcoming, 2)
        assertEquals("1", body.getValue("protocol").jsonPrimitive.content)
        assertFalse(body.containsKey("param_island"))
        assertTrue(body.containsKey("ticker"))
        assertEquals("2", body.getValue("baseInfo").jsonObject.getValue("type").jsonPrimitive.content)
    }

    @Test
    fun `OS3采用已核实的静态图文摘要模板`() {
        val body = body(upcoming, 3)
        val island = body.getValue("param_island").jsonObject
        val big = island.getValue("bigIslandArea").jsonObject
        assertEquals("候课", big.getValue("imageTextInfoLeft").jsonObject.getValue("textInfo").jsonObject.getValue("title").jsonPrimitive.content)
        assertEquals("08:00", big.getValue("textInfo").jsonObject.getValue("title").jsonPrimitive.content)
        assertEquals(XiaomiCourseNotificationPayload.ICON_KEY, island.getValue("smallIslandArea").jsonObject.getValue("picInfo").jsonObject.getValue("pic").jsonPrimitive.content)
        assertEquals("1800", island.getValue("islandTimeout").jsonPrimitive.content)
    }

    @Test
    fun `课程名地点起止时间与上课状态均包含在展开态`() {
        val body = body(ongoing, 3)
        val base = body.getValue("baseInfo").jsonObject
        assertEquals(course.name, base.getValue("title").jsonPrimitive.content)
        assertEquals("正在上课 · B204", base.getValue("content").jsonPrimitive.content)
        assertEquals("08:00–09:40", base.getValue("subContent").jsonPrimitive.content)
        assertEquals("09:40", body.getValue("param_island").jsonObject.getValue("bigIslandArea").jsonObject.getValue("textInfo").jsonObject.getValue("title").jsonPrimitive.content)
    }

    @Test
    fun `关闭焦点权限不隐藏普通通知且没有计时器动态进度`() {
        val payload = XiaomiCourseNotificationPayload.create(ongoing, 3, "approved_course_business", 0)!!
        assertEquals("false", body(ongoing, 3).getValue("filterWhenNoPermission").jsonPrimitive.content)
        assertFalse(payload.contains("timerInfo"))
        assertFalse(payload.contains("progressInfo"))
        assertFalse(payload.contains("miui.focus.paramtextInfo"))
    }

    @Test
    fun `空闲过期无资格与未知协议不生成载荷`() {
        assertNull(XiaomiCourseNotificationPayload.create(IslandState.None, 3, "approved_course_business", 0))
        assertNull(XiaomiCourseNotificationPayload.create(IslandState.Idle(0, 0, compactText = "今日无课", subText = ""), 3, "approved_course_business", 0))
        assertNull(XiaomiCourseNotificationPayload.create(upcoming, 3, "", 0))
        assertNull(XiaomiCourseNotificationPayload.create(upcoming, 4, "approved_course_business", 0))
        assertNull(XiaomiCourseNotificationPayload.create(upcoming, 3, "approved_course_business", upcoming.startMillis))
        assertNull(XiaomiCourseNotificationPayload.create(
            upcoming.copy(startMillis = 12 * 60 * 60_000L + 1), 3, "approved_course_business", 0))
    }

    @Test
    fun `生命期按真实课程边界向上取整`() {
        val payload = XiaomiCourseNotificationPayload.create(upcoming, 3, "approved_course_business", upcoming.startMillis - 1)!!
        val body = Json.parseToJsonElement(payload).jsonObject.getValue("param_v2").jsonObject
        assertEquals("1", body.getValue("timeout").jsonPrimitive.content)
        assertEquals("1", body.getValue("param_island").jsonObject.getValue("islandTimeout").jsonPrimitive.content)
    }
}
