package com.chen.schedule.util

import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.Semester
import com.chen.schedule.domain.model.TimeScheme
import com.chen.schedule.domain.model.TimeSlot
import kotlinx.serialization.Serializable

/**
 * 备份格式。
 *
 * - v1(旧版):只含 semester / courses / timeSlots,导入时按「原有作息」处理,完全兼容。
 * - v2(本版):额外携带作息方案与每套方案的节次;旧备份导入后行为不变。
 *
 * 注意:[backupVersion] 的默认值必须是 [LEGACY_VERSION]——旧文件没有这个字段,
 * 反序列化时才能落到「单套作息」的旧版校验分支;导出时用 `encodeDefaults = true`
 * 显式写入 2,两者配合才能正确区分 v1 / v2。
 */
@Serializable
data class ScheduleBackup(
    val backupVersion: Int = LEGACY_VERSION,
    val semester: Semester,
    val courses: List<Course>,
    /** v1 兼容字段:仅「原有作息」(schemeId = 0)的节次;其他方案见 [schemeSlots]。 */
    val timeSlots: List<TimeSlot>,
    /** v2:全部方案(不含合成的「原有作息」)。 */
    val schemes: List<TimeScheme> = emptyList(),
    /** v2:每个方案 id 对应的节次。 */
    val schemeSlots: List<SchemeSlots> = emptyList(),
    /** v2:所有学期(用于保留多学期数据);旧版为空。 */
    val semesters: List<Semester> = emptyList(),
    val allCourses: List<Course> = emptyList()
) {
    companion object {
        const val CURRENT_VERSION = 2
        const val LEGACY_VERSION = 1
    }
}

@Serializable
data class SchemeSlots(
    val schemeId: Long,
    val slots: List<TimeSlot>
)
