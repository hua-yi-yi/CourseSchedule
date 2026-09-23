package com.chen.schedule.ui.settings

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.chen.schedule.data.local.AppDatabase
import com.chen.schedule.data.repository.CourseRepository
import com.chen.schedule.data.repository.SemesterRepository
import com.chen.schedule.data.repository.TimeSchemeRepository
import com.chen.schedule.data.repository.TimeSlotRepository
import com.chen.schedule.util.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class ScheduleBackupService @Inject constructor(
    private val database: AppDatabase,
    private val courseRepository: CourseRepository,
    private val semesterRepository: SemesterRepository,
    private val timeSlotRepository: TimeSlotRepository,
    private val timeSchemeRepository: TimeSchemeRepository,
    @ApplicationContext private val context: Context
) {
    data class RestoreResult(val count: Int, val fullRestore: Boolean)

    suspend fun exportToUri(uri: Uri) {
        withContext(Dispatchers.IO) {
            val data = database.withTransaction {
                val sem = semesterRepository.getCurrentSemester() ?: error("没有当前学期")
                val allSlots = timeSlotRepository.getAllTimeSlots().first()
                val schemes = timeSchemeRepository.getAllSchemesDirect()
                    .filter { !it.isLegacy }
                ScheduleBackup(
                    backupVersion = ScheduleBackup.CURRENT_VERSION,
                    semester = sem,
                    courses = courseRepository.getCoursesBySemester(sem.id).first(),
                    // v1 兼容字段:只放「原有作息」(schemeId = 0)的节次,避免多套作息
                    // 合并后出现重复编号;其他方案各自放在 schemeSlots 中。
                    timeSlots = allSlots.filter { it.schemeId == 0L },
                    schemes = schemes,
                    schemeSlots = allSlots
                        .groupBy { it.schemeId }
                        .map { (schemeId, slots) -> SchemeSlots(schemeId, slots) },
                    semesters = semesterRepository.getAllSemesters().first(),
                    allCourses = courseRepository.getAllCourses().first()
                )
            }
            // encodeDefaults = true 才能写入 backupVersion=2 / 空列等,
            // 否则 v2 与 v1 无法区分。
            val json = Json { prettyPrint = true; encodeDefaults = true }
                .encodeToString(ScheduleBackup.serializer(), data)
            requireNotNull(context.contentResolver.openOutputStream(uri)).use {
                it.write(json.toByteArray(Charsets.UTF_8))
            }
        }
    }

    suspend fun importData(uri: Uri): RestoreResult {
        // Pair(实际写入课程数, 是否完整恢复);仅课程 JSON 只替换当前学期
        return withContext(Dispatchers.IO) {
            val jsonString = requireNotNull(context.contentResolver.openInputStream(uri)).bufferedReader().use { it.readText() }
            val format = Json { ignoreUnknownKeys = true }
            val element = format.parseToJsonElement(jsonString)
            val backup = if (ScheduleBackupFormat.isFullBackup(element))
                format.decodeFromString(ScheduleBackup.serializer(), jsonString) else null
            require(backup == null || backup.backupVersion in
                ScheduleBackup.LEGACY_VERSION..ScheduleBackup.CURRENT_VERSION) { "不支持此备份版本" }
            val courses = backup?.courses ?: JsonImporter.parse(jsonString).getOrThrow()
            require(backup != null || courses.isNotEmpty()) { "未找到课程，未修改现有数据" }
            // 完整备份要校验备份中的全部课程(跨学期),而不仅是当前学期
            val coursesToValidate = backup?.let {
                if (it.allCourses.isNotEmpty()) it.allCourses else it.courses
            } ?: courses
            require(coursesToValidate.all { it.name.isNotBlank() && it.dayOfWeek in 1..7 && it.startSlot > 0 && it.endSlot >= it.startSlot && it.startWeek > 0 && it.endWeek >= it.startWeek }) { "课程数据无效" }
            backup?.let { data ->
                BackupRestorePlanner.validateReferences(data)
                require(data.semester.totalWeeks in 1..53) { "学期周数无效" }
                val semestersInBackup = if (data.semesters.isNotEmpty()) data.semesters else listOf(data.semester)
                require(semestersInBackup.all { it.totalWeeks in 1..53 }) { "学期周数无效" }
                require(semestersInBackup.all { it.name.isNotBlank() }) { "学期名称无效" }
                val isNewFormat = data.backupVersion >= 2
                if (isNewFormat) {
                    // 新版:各方案各自校验(不同方案可以都有「第1节」,合在一起会误报重复)。
                    data.schemeSlots.forEach { entry ->
                        if (entry.slots.isNotEmpty()) {
                            require(com.chen.schedule.util.ScheduleStatus.isSlotsValid(entry.slots)) {
                                "作息方案(编号 ${entry.schemeId})节次无效"
                            }
                        }
                    }
                    // 「原有作息」桶同样按单套校验
                    val legacy = data.schemeSlots.firstOrNull { it.schemeId == 0L }?.slots
                        ?: data.timeSlots.filter { it.schemeId == 0L }
                    if (legacy.isNotEmpty()) {
                        require(com.chen.schedule.util.ScheduleStatus.isSlotsValid(legacy)) {
                            "原有作息节次无效"
                        }
                    }
                } else {
                    // 旧备份:只有单套作息,走原有解析器校验
                    if (data.timeSlots.isNotEmpty()) com.chen.schedule.util.TimeSlotParser.parse(
                        data.timeSlots.joinToString("\n") { slot -> "${slot.slotNumber} ${slot.startTime}-${slot.endTime}" })
                }
            }
            database.withTransaction {
                val data = backup
                if (data == null) {
                    // 仅课程 JSON(旧格式):替换当前学期课程,行为不变
                    val current = semesterRepository.getCurrentSemester() ?: error("请先创建学期")
                    courseRepository.deleteAllBySemester(current.id)
                    courseRepository.insertAll(courses.map { it.copy(id = 0, semesterId = current.id) })
                    RestoreResult(courses.size, fullRestore = false)
                } else {
                    // ===== 完整恢复:学期 / 课程 / 作息方案全部按备份重建 =====

                    // 1) 先清空旧的自定义方案(避免重复恢复累积同名方案),再补齐内置模板
                    timeSchemeRepository.deleteAllCustomSchemes()
                    timeSchemeRepository.ensureBuiltInSchemes()
                    val builtInsByName = timeSchemeRepository.getAllSchemesDirect()
                        .filter { it.isBuiltIn }
                        .associateBy { it.name }
                    val schemeMap = mutableMapOf<Long, Long>()
                    val actions = com.chen.schedule.util.BackupRestorePlanner
                        .schemeActions(data, builtInsByName.keys)
                    actions.forEach { (oldSchemeId, action) ->
                        val newId = when (action) {
                            is com.chen.schedule.util.BackupRestorePlanner.SchemeAction.ReuseBuiltIn ->
                                builtInsByName.getValue(action.builtInName).id
                            is com.chen.schedule.util.BackupRestorePlanner.SchemeAction.CreateCustom ->
                                timeSchemeRepository.createCustomScheme(
                                    action.name, action.slots, setCurrent = false
                                )
                        }
                        schemeMap[oldSchemeId] = newId
                    }

                    // 2) 用纯逻辑计划器算出「要建哪些学期、课程归哪个学期」
                    val plan = com.chen.schedule.util.BackupRestorePlanner.plan(data) { old ->
                        if (old == 0L) 0L else schemeMap[old] ?: 0L
                    }

                    // 3) 清空本地学期与课程,按备份完整重建
                    courseRepository.deleteAll()
                    semesterRepository.deleteAll()

                    val insertedIds = plan.semesters.map { semesterRepository.insert(it) }
                    val currentId = insertedIds.getOrNull(plan.currentSemesterIndex)
                        ?: error("备份中没有学期")
                    semesterRepository.setCurrentSemester(currentId)

                    // 4) 课程:按学期下标换成新插入的学期 id
                    val toInsert = plan.courses.mapNotNull { (index, course) ->
                        insertedIds.getOrNull(index)?.let { newSemesterId ->
                            course.copy(semesterId = newSemesterId)
                        }
                    }
                    if (toInsert.isNotEmpty()) courseRepository.insertAll(toInsert)

                    // 5) 「原有作息」桶(空则清空,避免残留旧数据)
                    timeSlotRepository.replaceScheme(0L, plan.legacySlots)

                    // 6) 返回实际写入的课程数(全部学期)与「完整恢复」标记
                    RestoreResult(toInsert.size, fullRestore = true)
                }
            }
        }
    }
}
