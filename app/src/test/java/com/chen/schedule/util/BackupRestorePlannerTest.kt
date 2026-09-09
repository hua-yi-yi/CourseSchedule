package com.chen.schedule.util

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.Semester
import com.chen.schedule.domain.model.TimeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 完整恢复的映射规则回归测试,对应评审问题
 * 「导出包含全部学期和课程,导入却只读取当前学期」。
 */
class BackupRestorePlannerTest {

    private fun semester(id: Long, name: String, schemeId: Long, isCurrent: Boolean = false) =
        Semester(id = id, name = name, startDate = 1_700_000_000_000L, totalWeeks = 18,
            isCurrent = isCurrent, schemeId = schemeId)

    private fun course(name: String, semesterId: Long) = Course(
        name = name, dayOfWeek = 1, startSlot = 1, endSlot = 2,
        startWeek = 1, endWeek = 16, semesterId = semesterId
    )

    private fun slot(number: Int, schemeId: Long) = TimeSlot(
        slotNumber = number, startTime = "08:00", endTime = "08:45",
        name = "第${number}节", schemeId = schemeId
    )

    /** 三个学期 + 跨学期课程都必须进入恢复计划,并按学期下标归位。 */
    @Test fun restoresAllSemestersAndRemapsCourses() {
        val backup = ScheduleBackup(
            backupVersion = ScheduleBackup.CURRENT_VERSION,
            semester = semester(2, "2026 秋季", 10, isCurrent = true),
            courses = listOf(course("当前学期课", 2)),
            timeSlots = emptyList(),
            semesters = listOf(
                semester(1, "2025 秋季", 0),
                semester(2, "2026 秋季", 10, isCurrent = true),
                semester(3, "2027 春季", 11)
            ),
            allCourses = listOf(
                course("A", 1),
                course("B", 2),
                course("C", 3)
            )
        )
        // 备份方案 10 → 本地 100,11 → 101
        val plan = BackupRestorePlanner.plan(backup) { old -> when (old) { 10L -> 100L; 11L -> 101L; else -> 0L } }

        assertEquals(3, plan.semesterCount)
        assertEquals(listOf("2025 秋季", "2026 秋季", "2027 春季"), plan.semesters.map { it.name })
        assertEquals(listOf(0L, 100L, 101L), plan.semesters.map { it.schemeId })
        // 当前学期应是备份中标记 isCurrent 的那个(下标 1)
        assertEquals(1, plan.currentSemesterIndex)
        assertTrue(plan.semesters.all { it.id == 0L && !it.isCurrent })

        assertEquals(3, plan.courses.size)
        // 提示数量必须取实际写入的课程总数(3),而不是备份中当前学期的 1 条
        assertEquals(1, backup.courses.size)
        assertTrue(plan.courses.size > backup.courses.size)
        assertEquals(listOf(0 to "A", 1 to "B", 2 to "C"), plan.courses.map { it.first to it.second.name })
        assertTrue(plan.courses.all { it.second.id == 0L })
    }

    /** 课程若指向备份中不存在的学期,应被丢弃,而不是挂到别的学期上。 */
    @Test fun dropsCoursesWithUnknownSemester() {
        val backup = ScheduleBackup(
            backupVersion = ScheduleBackup.CURRENT_VERSION,
            semester = semester(1, "唯一学期", 0, isCurrent = true),
            courses = emptyList(),
            timeSlots = emptyList(),
            semesters = listOf(semester(1, "唯一学期", 0, isCurrent = true)),
            allCourses = listOf(course("有效", 1), course("孤儿", 999))
        )
        val plan = BackupRestorePlanner.plan(backup) { 0L }
        assertEquals(1, plan.courses.size)
        assertEquals("有效", plan.courses.first().second.name)
    }

    /** 旧版备份(无 semesters / allCourses)仍按单学期恢复,课程挂到该学期。 */
    @Test fun legacyBackupFallsBackToSingleSemester() {
        val backup = ScheduleBackup(
            backupVersion = ScheduleBackup.LEGACY_VERSION,
            semester = semester(1, "旧学期", 0, isCurrent = true),
            courses = listOf(course("旧课", 1)),
            timeSlots = listOf(slot(1, 0))
        )
        val plan = BackupRestorePlanner.plan(backup) { 0L }
        assertEquals(1, plan.semesterCount)
        assertEquals(0, plan.currentSemesterIndex)
        assertEquals(listOf("旧课"), plan.courses.map { it.second.name })
        assertEquals(0, plan.courses.first().first)
        assertEquals(listOf(1), plan.legacySlots.map { it.slotNumber })
    }

    /** 原有作息桶优先取 schemeSlots 的 0 号桶,且 id/schemeId 重置。 */
    @Test fun legacyBucketComesFromSchemeSlotsAndIsReset() {
        val backup = ScheduleBackup(
            backupVersion = ScheduleBackup.CURRENT_VERSION,
            semester = semester(1, "学期", 0, isCurrent = true),
            courses = emptyList(),
            timeSlots = listOf(slot(9, 0)), // 兼容字段应被 schemeSlots 覆盖
            schemeSlots = listOf(
                SchemeSlots(0, listOf(slot(1, 0), slot(2, 0))),
                SchemeSlots(10, listOf(slot(1, 10)))
            ),
            semesters = listOf(semester(1, "学期", 0, isCurrent = true))
        )
        val plan = BackupRestorePlanner.plan(backup) { 0L }
        assertEquals(listOf(1, 2), plan.legacySlots.map { it.slotNumber })
        assertTrue(plan.legacySlots.all { it.id == 0L && it.schemeId == 0L })
    }

    // ===== 方案恢复动作(对应「重复恢复累积同名方案」) =====

    /** 内置模板按名称复用,自定义方案才新建 —— 每个备份方案恰好一个动作。 */
    @Test fun builtInSchemesAreReusedByNameAndCustomsCreated() {
        val backup = ScheduleBackup(
            backupVersion = ScheduleBackup.CURRENT_VERSION,
            semester = semester(1, "学期", 10, isCurrent = true),
            courses = emptyList(),
            timeSlots = emptyList(),
            schemes = listOf(
                com.chen.schedule.domain.model.TimeScheme(
                    id = 20, name = "夏季作息", kind = com.chen.schedule.domain.model.TimeScheme.KIND_BUILT_IN
                ),
                com.chen.schedule.domain.model.TimeScheme(
                    id = 10, name = "我的作息", kind = com.chen.schedule.domain.model.TimeScheme.KIND_CUSTOM
                )
            ),
            schemeSlots = listOf(SchemeSlots(10, listOf(slot(1, 10)))),
            semesters = listOf(semester(1, "学期", 10, isCurrent = true))
        )
        val actions = BackupRestorePlanner.schemeActions(backup, setOf("夏季作息", "冬季作息"))

        assertEquals(2, actions.size)
        val builtIn = actions.getValue(20L)
        assertTrue(builtIn is BackupRestorePlanner.SchemeAction.ReuseBuiltIn)
        assertEquals("夏季作息", (builtIn as BackupRestorePlanner.SchemeAction.ReuseBuiltIn).builtInName)

        val custom = actions.getValue(10L)
        assertTrue(custom is BackupRestorePlanner.SchemeAction.CreateCustom)
        custom as BackupRestorePlanner.SchemeAction.CreateCustom
        assertEquals("我的作息", custom.name)
        assertEquals(listOf(1), custom.slots.map { it.slotNumber })
        assertTrue(custom.slots.all { it.id == 0L })
    }

    /** 本机没有对应内置模板时,回落为新建自定义方案(不丢数据)。 */
    @Test fun builtInWithoutLocalCounterpartFallsBackToCustom() {
        val backup = ScheduleBackup(
            backupVersion = ScheduleBackup.CURRENT_VERSION,
            semester = semester(1, "学期", 0, isCurrent = true),
            courses = emptyList(),
            timeSlots = emptyList(),
            schemes = listOf(
                com.chen.schedule.domain.model.TimeScheme(
                    id = 20, name = "夏季作息", kind = com.chen.schedule.domain.model.TimeScheme.KIND_BUILT_IN
                )
            ),
            semesters = listOf(semester(1, "学期", 0, isCurrent = true))
        )
        val actions = BackupRestorePlanner.schemeActions(backup, emptySet())
        assertTrue(actions.getValue(20L) is BackupRestorePlanner.SchemeAction.CreateCustom)
    }

    /**
     * 恢复动作完全由备份决定:对同一份备份重复计算,结果一致。
     * 配合恢复前 `deleteAllCustomSchemes()` 即可保证不会累积重复方案。
     */
    @Test fun schemeActionsAreDeterministicForSameBackup() {
        val backup = ScheduleBackup(
            backupVersion = ScheduleBackup.CURRENT_VERSION,
            semester = semester(1, "学期", 7, isCurrent = true),
            courses = emptyList(),
            timeSlots = emptyList(),
            schemes = listOf(
                com.chen.schedule.domain.model.TimeScheme(
                    id = 7, name = "方案A", kind = com.chen.schedule.domain.model.TimeScheme.KIND_CUSTOM
                )
            ),
            schemeSlots = listOf(SchemeSlots(7, listOf(slot(1, 7)))),
            semesters = listOf(semester(1, "学期", 7, isCurrent = true))
        )
        val first = BackupRestorePlanner.schemeActions(backup, setOf("夏季作息"))
        val second = BackupRestorePlanner.schemeActions(backup, setOf("夏季作息"))
        assertEquals(first, second)
        assertEquals(1, first.size)
    }
}
