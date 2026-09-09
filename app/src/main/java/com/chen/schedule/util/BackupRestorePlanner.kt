package com.chen.schedule.util

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.Semester
import com.chen.schedule.domain.model.TimeScheme
import com.chen.schedule.domain.model.TimeSlot

/**
 * 备份恢复的**纯逻辑**计划器:把备份里的学期、课程、作息引用换算成本地重建方案。
 *
 * 抽出来是为了可单测——完整的恢复流程需要 Room,而这里的映射规则(尤其是
 * 「其他学期与课程也要恢复、课程按学期重新映射、作息方案 id 重新映射」)
 * 正是容易出错的地方。
 *
 * 约定:
 * - 返回的 [RestorePlan.semesters] 中 `id = 0`(待插入),`schemeId` 已通过 [schemeIdMapper] 映射;
 * - 课程按「学期下标」返回,由调用方在插入学期后把下标换成真实 id;
 * - 备份里找不到对应学期的课程会被丢弃(不静默挂到别的学期上)。
 */
object BackupRestorePlanner {

    /** 恢复计划。 */
    data class RestorePlan(
        val semesters: List<Semester>,
        val currentSemesterIndex: Int,
        /** 课程 + 它应归属的学期下标(对应 [semesters] 的位置)。 */
        val courses: List<Pair<Int, Course>>,
        /** 「原有作息」(schemeId = 0)桶的节次。 */
        val legacySlots: List<TimeSlot>
    ) {
        val semesterCount: Int get() = semesters.size
    }

    /**
     * @param schemeIdMapper 把备份中的方案 id 映射为本地新建的方案 id;0(原有作息)必须原样返回 0
     */
    fun plan(backup: ScheduleBackup, schemeIdMapper: (Long) -> Long): RestorePlan {
        // 1) 学期:旧备份只有单个学期;v2 用 semesters 全量
        val sourceSemesters = if (backup.semesters.isNotEmpty()) backup.semesters else listOf(backup.semester)
        val indexByOldId = sourceSemesters
            .mapIndexed { index, semester -> semester.id to index }
            .toMap()

        val semesters = sourceSemesters.map { old ->
            old.copy(id = 0, isCurrent = false, schemeId = schemeIdMapper(old.schemeId))
        }

        // 2) 当前学期:优先匹配备份中标记的当前学期,否则第一个
        val currentSemesterIndex = indexByOldId[backup.semester.id]
            ?.takeIf { it in semesters.indices }
            ?: 0

        // 3) 课程:有 allCourses 就按学期映射,否则全部归入当前学期
        val courses: List<Pair<Int, Course>> = if (backup.allCourses.isNotEmpty()) {
            backup.allCourses.mapNotNull { course ->
                indexByOldId[course.semesterId]?.let { index -> index to course.copy(id = 0) }
            }
        } else {
            backup.courses.map { currentSemesterIndex to it.copy(id = 0) }
        }

        // 4) 「原有作息」桶:优先取 schemeSlots 中的 0 号桶,回落 v1 兼容字段
        val legacySlots = (backup.schemeSlots.firstOrNull { it.schemeId == 0L }?.slots
            ?.takeIf { it.isNotEmpty() }
            ?: backup.timeSlots.filter { it.schemeId == 0L })
            .map { it.copy(id = 0, schemeId = 0) }

        return RestorePlan(
            semesters = semesters,
            currentSemesterIndex = currentSemesterIndex,
            courses = courses,
            legacySlots = legacySlots
        )
    }

    /** 单个备份方案该怎么恢复。 */
    sealed interface SchemeAction {
        /** 内置模板由代码定义,按名称复用本机已有方案(不新建,避免重复副本)。 */
        data class ReuseBuiltIn(val builtInName: String) : SchemeAction

        /** 新建一个自定义方案。 */
        data class CreateCustom(val name: String, val slots: List<TimeSlot>) : SchemeAction
    }

    /**
     * 算出备份里每个方案(键为备份中的方案 id)应如何恢复。
     *
     * 调用方需先清空本机已有的自定义方案(`deleteAllCustomSchemes`),否则重复恢复
     * 同一备份会累积出多份同名方案。
     *
     * @param builtInNames 本机已存在的内置模板名称集合
     */
    fun schemeActions(backup: ScheduleBackup, builtInNames: Set<String>): Map<Long, SchemeAction> =
        backup.schemes.associate { scheme ->
            val slots = backup.schemeSlots
                .firstOrNull { it.schemeId == scheme.id }
                ?.slots?.map { it.copy(id = 0) }
                ?: emptyList()
            scheme.id to if (scheme.kind == TimeScheme.KIND_BUILT_IN && scheme.name in builtInNames) {
                SchemeAction.ReuseBuiltIn(scheme.name)
            } else {
                SchemeAction.CreateCustom(scheme.name, slots)
            }
        }
}
