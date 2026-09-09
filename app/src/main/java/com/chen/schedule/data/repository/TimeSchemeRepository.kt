package com.chen.schedule.data.repository

import androidx.room.withTransaction
import com.chen.schedule.data.local.AppDatabase
import com.chen.schedule.data.local.dao.CourseDao
import com.chen.schedule.data.local.dao.SemesterDao
import com.chen.schedule.data.local.dao.TimeSchemeDao
import com.chen.schedule.data.local.dao.TimeSlotDao
import com.chen.schedule.data.local.entity.SemesterEntity
import com.chen.schedule.data.local.entity.TimeSchemeEntity
import com.chen.schedule.data.local.entity.TimeSlotEntity
import com.chen.schedule.domain.model.Semester
import com.chen.schedule.domain.model.TimeScheme
import com.chen.schedule.domain.model.TimeSlot
import com.chen.schedule.util.TimeSchemeTemplates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 作息方案仓库:负责方案的增删改查、与学期的绑定,以及「选择方案」的落地。
 *
 * 关键约束:
 * - 「原有作息」([TimeScheme.LEGACY_ID] = 0)是保留 id,不对应 time_schemes 行,
 *   旧版本的全局节次天然归属于它,因此升级后无需搬运数据。
 * - 选择方案只是切换学期与节次的 schemeId 绑定,**不删除**任何其他方案。
 * - 修改内置模板时采用「复制为自定义方案」(copy-on-write)。
 * - 删除方案前检查引用,并把孤立节次回落到原有作息,避免课程无声消失。
 */
@Singleton
class TimeSchemeRepository @Inject constructor(
    private val database: AppDatabase,
    private val schemeDao: TimeSchemeDao,
    private val timeSlotDao: TimeSlotDao,
    private val semesterDao: SemesterDao,
    private val courseDao: CourseDao
) {

    /** 全部方案(含合成的「原有作息」),内置模板在前。 */
    fun getAllSchemes(): Flow<List<TimeScheme>> =
        schemeDao.getAllSchemes().map { rows -> listOf(legacyScheme()) + rows.map { it.toDomain() } }

    suspend fun getAllSchemesDirect(): List<TimeScheme> =
        listOf(legacyScheme()) + schemeDao.getAllSchemesDirect().map { it.toDomain() }

    suspend fun getSchemeById(id: Long): TimeScheme? =
        if (id == TimeScheme.LEGACY_ID) legacyScheme() else schemeDao.getSchemeById(id)?.toDomain()

    /** 某方案的节次(实时)。 */
    fun getSlotsOfScheme(schemeId: Long): Flow<List<TimeSlot>> =
        timeSlotDao.getTimeSlotsByScheme(schemeId).map { list -> list.map { it.toDomain() } }

    suspend fun getSlotsOfSchemeDirect(schemeId: Long): List<TimeSlot> =
        timeSlotDao.getTimeSlotsBySchemeDirect(schemeId).map { it.toDomain() }

    /** 引用了某方案的学期(用于「编辑共享方案会影响哪些学期」提示)。 */
    suspend fun semestersUsingScheme(schemeId: Long): List<Semester> =
        semesterDao.getSemestersUsingScheme(schemeId).map { it.toDomain() }

    /**
     * 确保内置模板行存在,并返回它们的 id(按内置顺序)。
     * 幂等:已存在同名同 kind 的方案时直接复用,不重复插入。
     */
    suspend fun ensureBuiltInSchemes(): List<TimeScheme> = database.withTransaction {
        val existing = schemeDao.getSchemesByKind(TimeScheme.KIND_BUILT_IN)
        TimeSchemeTemplates.builtIns.map { builtIn ->
            val found = existing.firstOrNull { it.name == builtIn.name }
            if (found != null) {
                found.toDomain()
            } else {
                val id = schemeDao.insert(
                    TimeSchemeEntity(
                        name = builtIn.name,
                        kind = TimeScheme.KIND_BUILT_IN,
                        season = builtIn.season,
                        createTime = System.currentTimeMillis()
                    )
                )
                val slots = TimeSchemeTemplates.slotsOf(builtIn.startTimes, id).map { it.toEntity() }
                timeSlotDao.replaceScheme(id, slots)
                TimeScheme(id = id, name = builtIn.name, kind = TimeScheme.KIND_BUILT_IN, season = builtIn.season)
            }
        }
    }

    /** 内置模板的规范节次(schemeId 按给定值补齐),用于预览。 */
    fun previewSlots(builtInName: String): List<TimeSlot> {
        val builtIn = TimeSchemeTemplates.builtIns.firstOrNull { it.name == builtInName } ?: return emptyList()
        return TimeSchemeTemplates.slotsOf(builtIn.startTimes)
    }

    /**
     * 选择一个方案供某学期使用(事务):
     * 切换该学期的 schemeId 绑定;若该学期是当前学期,则课表读取到的新节次随之生效。
     * 其他学期、其他方案完全不受影响。
     */
    suspend fun selectSchemeForSemester(semesterId: Long, schemeId: Long) = database.withTransaction {
        semesterDao.setSemesterScheme(semesterId, schemeId)
    }

    /** 当前学期所绑定的方案 id(无当前学期时回落到原有作息)。 */
    suspend fun currentSchemeId(): Long =
        semesterDao.getCurrentSemester()?.schemeId ?: TimeScheme.LEGACY_ID

    /** 当前方案下的节次。 */
    suspend fun currentSlots(): List<TimeSlot> =
        getSlotsOfSchemeDirect(currentSchemeId())

    /**
     * 新建自定义方案。
     * @param slots 初始节次(schemeId 会被重写为本方案 id)
     * @param setCurrent 是否同时设为「当前学期」使用的方案
     */
    suspend fun createCustomScheme(
        name: String,
        slots: List<TimeSlot>,
        setCurrent: Boolean
    ): Long = database.withTransaction {
        val id = schemeDao.insert(
            TimeSchemeEntity(
                name = name,
                kind = TimeScheme.KIND_CUSTOM,
                season = TimeSchemeTemplates.SEASON_NONE,
                createTime = System.currentTimeMillis()
            )
        )
        timeSlotDao.replaceScheme(id, slots.map { it.copy(id = 0, schemeId = id).toEntity() })
        if (setCurrent) {
            semesterDao.getCurrentSemester()?.let { semesterDao.setSemesterScheme(it.id, id) }
        }
        id
    }

    /** 保存(覆盖)某自定义方案的整套节次,所有改动在事务内完成。 */
    suspend fun saveSchemeSlots(schemeId: Long, slots: List<TimeSlot>) = database.withTransaction {
        timeSlotDao.replaceScheme(schemeId, slots.map { it.copy(id = 0, schemeId = schemeId).toEntity() })
    }

    /** 重命名自定义方案。 */
    suspend fun renameScheme(schemeId: Long, name: String) = database.withTransaction {
        schemeDao.getSchemeById(schemeId)?.let { schemeDao.update(it.copy(name = name)) }
    }

    /**
     * 把内置模板复制成新的自定义方案(编辑内置模板时必须先复制)。
     *
     * [slots] 为用户在编辑页实际修改后的节次——必须保存它,而不是重新套用原始模板,
     * 否则「修改后提示成功、改动却丢失」。传 null 时才回落到模板的原始节次。
     *
     * @return 新方案 id
     */
    suspend fun copyBuiltInToCustom(
        builtInName: String,
        newName: String,
        setCurrent: Boolean,
        slots: List<TimeSlot>? = null
    ): Long = database.withTransaction {
        val builtIn = TimeSchemeTemplates.builtIns.firstOrNull { it.name == builtInName }
            ?: error("未知的内置模板:$builtInName")
        val id = schemeDao.insert(
            TimeSchemeEntity(
                name = newName,
                kind = TimeScheme.KIND_CUSTOM,
                season = builtIn.season,
                createTime = System.currentTimeMillis()
            )
        )
        val finalSlots = slots?.takeIf { it.isNotEmpty() }
            ?: TimeSchemeTemplates.slotsOf(builtIn.startTimes)
        timeSlotDao.replaceScheme(id, finalSlots.map { it.copy(id = 0, schemeId = id).toEntity() })
        if (setCurrent) {
            semesterDao.getCurrentSemester()?.let { semesterDao.setSemesterScheme(it.id, id) }
        }
        id
    }

    /**
     * 复制任意已有方案为新的自定义方案。
     */
    suspend fun duplicateScheme(sourceId: Long, newName: String, setCurrent: Boolean): Long =
        database.withTransaction {
            val source = if (sourceId == TimeScheme.LEGACY_ID) legacyScheme() else schemeDao.getSchemeById(sourceId)?.toDomain()
                ?: error("方案不存在")
            val slots = getSlotsOfSchemeDirect(sourceId)
            val id = schemeDao.insert(
                TimeSchemeEntity(
                    name = newName,
                    kind = TimeScheme.KIND_CUSTOM,
                    season = source.season,
                    createTime = System.currentTimeMillis()
                )
            )
            timeSlotDao.replaceScheme(id, slots.map { it.copy(id = 0, schemeId = id).toEntity() })
            if (setCurrent) {
                semesterDao.getCurrentSemester()?.let { semesterDao.setSemesterScheme(it.id, id) }
            }
            id
        }

    /**
     * 删除自定义方案。
     *
     * **不会**把被删方案的节次并入「原有作息」(那会产生重复的「第1节」并污染其他学期)。
     * 若仍有学期引用该方案,必须由调用方指定一个替代方案 [replacementSchemeId],
     * 这些学期会被改绑到替代方案,再删除本方案及其节次。
     *
     * @throws IllegalArgumentException 存在引用但未提供合法的替代方案
     */
    suspend fun deleteScheme(schemeId: Long, replacementSchemeId: Long? = null) = database.withTransaction {
        require(schemeId != TimeScheme.LEGACY_ID) { "「原有作息」不可删除" }
        val scheme = schemeDao.getSchemeById(schemeId) ?: return@withTransaction
        require(scheme.kind == TimeScheme.KIND_CUSTOM) { "内置模板不可删除" }

        val referencing = semesterDao.getSemestersUsingScheme(schemeId)
        if (referencing.isNotEmpty()) {
            val target = replacementSchemeId
            require(target != null && target != schemeId) { "请先为关联的 ${referencing.size} 个学期选择替代作息方案" }
            require(target == TimeScheme.LEGACY_ID || schemeDao.getSchemeById(target) != null) {
                "替代作息方案不存在"
            }
            referencing.forEach { semesterDao.setSemesterScheme(it.id, target) }
        }

        // 只删除本方案自己的节次,绝不并入 0 号桶
        timeSlotDao.deleteByScheme(schemeId)
        schemeDao.deleteById(schemeId)
    }

    // ============ 节次编辑(作用于当前方案) ============

    /**
     * 清空所有**自定义**方案及其节次(完整恢复备份前使用)。
     *
     * 内置模板由代码定义、按名称复用,「原有作息」是 0 号桶,二者都保留。
     * 不清空会导致重复恢复同一备份时累积出多份同名方案。
     */
    suspend fun deleteAllCustomSchemes() = database.withTransaction {
        schemeDao.getSchemesByKind(TimeScheme.KIND_CUSTOM).forEach { scheme ->
            timeSlotDao.deleteByScheme(scheme.id)
            schemeDao.deleteById(scheme.id)
        }
    }

    /** 直接在方案内新增一个节次(自动接续在最后一节之后)。 */
    suspend fun addSlotToScheme(schemeId: Long): Long = database.withTransaction {
        val slots = getSlotsOfSchemeDirect(schemeId)
        val last = slots.maxByOrNull { it.slotNumber }
        val nextNumber = (last?.slotNumber ?: 0) + 1
        val startTime = last?.endTime?.takeIf { it.isNotBlank() } ?: "08:00"
        timeSlotDao.insert(
            TimeSlot(
                slotNumber = nextNumber,
                startTime = startTime,
                endTime = addMinutes(startTime, 45),
                name = "第${nextNumber}节",
                schemeId = schemeId
            ).toEntity()
        )
    }

    /** "HH:mm" + 分钟,跨小时累加,不跨天回绕。 */
    private fun addMinutes(time: String, minutes: Int): String {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val total = (hour * 60 + minute + minutes).coerceIn(0, 24 * 60 - 1)
        return String.format("%02d:%02d", total / 60, total % 60)
    }

    // ============ 映射 ============

    private fun legacyScheme() = TimeScheme(
        id = TimeScheme.LEGACY_ID,
        name = TimeScheme.LEGACY_NAME,
        kind = TimeScheme.KIND_CUSTOM,
        season = TimeSchemeTemplates.SEASON_NONE,
        createTime = 0L
    )

    private fun TimeSchemeEntity.toDomain() = TimeScheme(
        id = id, name = name, kind = kind, season = season, createTime = createTime
    )

    private fun TimeSlotEntity.toDomain() = TimeSlot(
        id = id, slotNumber = slotNumber, startTime = startTime, endTime = endTime,
        name = name, season = season, schemeId = schemeId
    )

    private fun TimeSlot.toEntity() = TimeSlotEntity(
        id = id, slotNumber = slotNumber, startTime = startTime, endTime = endTime,
        name = name, season = season, schemeId = schemeId
    )

    private fun SemesterEntity.toDomain() = Semester(
        id = id, name = name, startDate = startDate, totalWeeks = totalWeeks,
        isCurrent = isCurrent, schemeId = schemeId
    )
}
