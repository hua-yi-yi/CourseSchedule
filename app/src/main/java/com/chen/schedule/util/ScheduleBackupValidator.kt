package com.chen.schedule.util

import com.chen.schedule.domain.model.TimeSlot

/** Validate the same effective semesters and time schemes that a full restore will write. */
object ScheduleBackupValidator {
    fun validate(
        backup: ScheduleBackup,
        builtInSlots: Map<String, List<TimeSlot>> = TimeSchemeTemplates.builtIns.associate {
            it.name to TimeSchemeTemplates.slotsOf(it.startTimes)
        }
    ) {
        require(backup.backupVersion in ScheduleBackup.LEGACY_VERSION..ScheduleBackup.CURRENT_VERSION) {
            "不支持此备份版本"
        }
        BackupRestorePlanner.validateReferences(backup)
        require(ScheduleStatus.checkSemester(backup.semester).done) { "备份中的当前学期设置无效" }
        val plan = BackupRestorePlanner.plan(backup) { it }
        val slotsByScheme = backup.schemeSlots.associate { it.schemeId to it.slots }
        // Check unused backed-up schemes too, without requiring an empty semester to have a schedule.
        (slotsByScheme.values + listOf(plan.legacySlots)).forEach { slots ->
            require(slots.isEmpty() || ScheduleStatus.isSlotsValid(slots)) { "备份中的作息节次无效" }
        }
        val actions = BackupRestorePlanner.schemeActions(backup, builtInSlots.keys)
        val coursesBySemester = plan.courses.groupBy({ it.first }, { it.second })
        plan.semesters.forEachIndexed { index, semester ->
            require(ScheduleStatus.checkSemester(semester).done) { "学期「${semester.name}」设置无效" }
            val courses = coursesBySemester[index].orEmpty()
            if (courses.isEmpty()) return@forEachIndexed
            val slots = if (semester.schemeId == 0L) plan.legacySlots else {
                when (val action = actions.getValue(semester.schemeId)) {
                    is BackupRestorePlanner.SchemeAction.ReuseBuiltIn -> builtInSlots.getValue(action.builtInName)
                    is BackupRestorePlanner.SchemeAction.CreateCustom -> action.slots
                }
            }
            try {
                CourseImportRules.validateForSemester(courses, semester, slots)
            } catch (error: IllegalArgumentException) {
                throw IllegalArgumentException("学期「${semester.name}」：${error.message}", error)
            }
        }
    }
}
