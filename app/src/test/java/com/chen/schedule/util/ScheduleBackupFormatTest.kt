package com.chen.schedule.util

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.Semester
import com.chen.schedule.domain.model.TimeScheme
import com.chen.schedule.domain.model.TimeSlot
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份格式 v1/v2 的回归测试,对应评审问题「多方案备份无法正常恢复」。
 */
class ScheduleBackupFormatTest {

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    private fun slot(number: Int, schemeId: Long, start: String, end: String) = TimeSlot(
        slotNumber = number, startTime = start, endTime = end,
        name = "第${number}节", schemeId = schemeId
    )

    private fun semester(schemeId: Long) = Semester(
        id = 1, name = "2026 秋季", startDate = 1_700_000_000_000L,
        totalWeeks = 18, isCurrent = true, schemeId = schemeId
    )

    private fun course() = Course(
        name = "数学", dayOfWeek = 1, startSlot = 1, endSlot = 2,
        startWeek = 1, endWeek = 16, semesterId = 1
    )

    /** v2 导出必须显式写入 backupVersion=2,否则与 v1 无法区分。 */
    @Test fun v2ExportWritesVersionAndDefaults() {
        val backup = ScheduleBackup(
            backupVersion = ScheduleBackup.CURRENT_VERSION,
            semester = semester(schemeId = 0),
            courses = listOf(course()),
            timeSlots = listOf(slot(1, 0, "08:00", "08:45")),
            schemes = emptyList(),
            schemeSlots = listOf(SchemeSlots(0, listOf(slot(1, 0, "08:00", "08:45"))))
        )
        val text = json.encodeToString(ScheduleBackup.serializer(), backup)
        assertTrue(text, "\"backupVersion\": 2" in text)
        val restored = json.decodeFromString(ScheduleBackup.serializer(), text)
        assertEquals(ScheduleBackup.CURRENT_VERSION, restored.backupVersion)
        assertEquals(backup, restored)
    }

    /** 旧文件没有 backupVersion 字段时,必须落到 v1 分支(默认值 = LEGACY_VERSION)。 */
    @Test fun v1JsonWithoutVersionDecodesAsLegacy() {
        val legacyJson = """
            {
              "semester": {"id":1,"name":"旧学期","startDate":1700000000000,"totalWeeks":18,"isCurrent":true},
              "courses": [],
              "timeSlots": [{"slotNumber":1,"startTime":"08:00","endTime":"08:45","name":"第一节"}]
            }
        """.trimIndent()
        val restored = Json { ignoreUnknownKeys = true }
            .decodeFromString(ScheduleBackup.serializer(), legacyJson)
        assertEquals(ScheduleBackup.LEGACY_VERSION, restored.backupVersion)
        assertEquals(1, restored.timeSlots.size)
        assertTrue(restored.schemeSlots.isEmpty())
        assertTrue(restored.schemes.isEmpty())
    }

    /**
     * 多方案备份:不同方案可以各自都有「第1节」。
     * 合并成一套会误报「编号重复」——这正是原缺陷。
     */
    @Test fun multipleSchemesMayShareSlotNumbersWhenValidatedPerScheme() {
        val summer = listOf(slot(1, 10, "08:00", "08:45"), slot(2, 10, "08:55", "09:40"))
        val winter = listOf(slot(1, 11, "08:30", "09:15"), slot(2, 11, "09:25", "10:10"))

        // 逐方案校验:都合法
        assertTrue(ScheduleStatus.isSlotsValid(summer))
        assertTrue(ScheduleStatus.isSlotsValid(winter))

        // 合并后的旧校验方式:重复编号 → 失败(证明不能再合并)
        assertFalse(ScheduleStatus.isSlotsValid(summer + winter))
    }

    /** v1 兼容字段只承载「原有作息」,不含其他方案的节次。 */
    @Test fun v1CompatibilityFieldExcludesOtherSchemes() {
        val all = listOf(
            slot(1, 0, "08:00", "08:45"),
            slot(1, 10, "08:00", "08:45"),
            slot(2, 10, "08:55", "09:40")
        )
        val legacyOnly = all.filter { it.schemeId == 0L }
        assertEquals(listOf(1), legacyOnly.map { it.slotNumber })

        val backup = ScheduleBackup(
            backupVersion = ScheduleBackup.CURRENT_VERSION,
            semester = semester(schemeId = 10),
            courses = listOf(course()),
            timeSlots = legacyOnly,
            schemes = listOf(
                TimeScheme(id = 10, name = "自定义", kind = TimeScheme.KIND_CUSTOM)
            ),
            schemeSlots = all.groupBy { it.schemeId }
                .map { (id, slots) -> SchemeSlots(id, slots) }
        )
        val restored = json.decodeFromString(
            ScheduleBackup.serializer(),
            json.encodeToString(ScheduleBackup.serializer(), backup)
        )
        assertEquals(2, restored.schemeSlots.size)
        assertEquals(1, restored.timeSlots.size)
        assertEquals(10L, restored.semester.schemeId)
    }
}
