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

    data class RestorePreview(val uri: Uri, val digest: String, val summary: String)
    private val recoveryDir get() = java.io.File(context.filesDir, "recovery")
    fun recoveryPoints(): List<java.io.File> = recoveryDir.listFiles()?.filter { it.extension == "json" }
        ?.sortedByDescending { it.lastModified() }.orEmpty()

    suspend fun createRecoveryPoint(reason: String): java.io.File? = withContext(Dispatchers.IO) {
        if (semesterRepository.getCurrentSemester() == null) return@withContext null
        recoveryDir.mkdirs()
        val safeReason = reason.replace(Regex("[^\\p{L}\\p{N}-]"), "-")
        val file = java.io.File(recoveryDir, "${System.currentTimeMillis()}-$safeReason-${java.util.UUID.randomUUID()}.json")
        val temp = java.io.File(recoveryDir, file.name + ".tmp")
        try {
            exportToUri(Uri.fromFile(temp))
            check(temp.renameTo(file)) { "无法保存恢复点，未执行数据修改" }
            recoveryPoints().drop(10).forEach { it.delete() }
            file
        } finally { temp.delete() }
    }

    suspend fun clearSemester(semesterId: Long) = database.withTransaction {
        require(semesterRepository.getCurrentSemester()?.id == semesterId) { "当前学期已切换，请重试" }
        createRecoveryPoint("清空前")
        courseRepository.deleteAllBySemester(semesterId)
    }
    suspend fun deleteCourse(course: com.chen.schedule.domain.model.Course) = database.withTransaction {
        require(courseRepository.getCourseById(course.id) == course) { "课程已变化，请重新打开" }
        createRecoveryPoint("课程删除前")
        courseRepository.delete(course)
    }
    suspend fun undoDelete(course: com.chen.schedule.domain.model.Course) = database.withTransaction {
        require(semesterRepository.getSemesterById(course.semesterId) != null) { "原学期已不存在，请使用恢复点" }
        require(courseRepository.getCourseById(course.id) == null) { "课表已变化，请使用恢复点" }
        val existing = courseRepository.getCoursesBySemester(course.semesterId).first()
        require(existing.none { CourseImportRules.identity(it) == CourseImportRules.identity(course) }) { "相同课程已经恢复" }
        courseRepository.insert(course)
    }
    private fun digest(text: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    suspend fun previewRestore(uri: Uri): RestorePreview = withContext(Dispatchers.IO) {
        val text = requireNotNull(context.contentResolver.openInputStream(uri)).bufferedReader().use { it.readText() }
        val format = Json { ignoreUnknownKeys = true }
        val element = format.parseToJsonElement(text)
        val summary = if (ScheduleBackupFormat.isFullBackup(element)) {
            val data = format.decodeFromString(ScheduleBackup.serializer(), text)
            require(data.backupVersion in ScheduleBackup.LEGACY_VERSION..ScheduleBackup.CURRENT_VERSION) { "不支持此备份版本" }
            BackupRestorePlanner.validateReferences(data)
            val semesters = data.semesters.ifEmpty { listOf(data.semester) }
            val courses = data.allCourses.ifEmpty { data.courses }
            "完整恢复：${semesters.size} 个学期、${courses.size} 条课程安排、${data.schemes.size} 套作息。\n学期：${semesters.joinToString { it.name }}\n恢复后当前学期：${data.semester.name}\n将替换本机全部学期、课程与作息。"
        } else {
            val courses = JsonImporter.parse(text).getOrThrow()
            require(courses.isNotEmpty()) { "没有课程" }
            val current = semesterRepository.getCurrentSemester() ?: error("请先创建学期")
            CourseImportRules.validateForSemester(courses, current, timeSlotRepository.getTimeSlotsBySchemeDirect(current.schemeId))
            "仅课程恢复：${courses.size} 条安排，将替换当前学期「${current.name}」的课程。"
        }
        RestorePreview(uri, digest(text), summary + "\n操作前将自动保存恢复点。")
    }

    suspend fun importData(uri: Uri, expectedDigest: String? = null): RestoreResult {
        // Pair(实际写入课程数, 是否完整恢复);仅课程 JSON 只替换当前学期
        return withContext(Dispatchers.IO) {
            val jsonString = requireNotNull(context.contentResolver.openInputStream(uri)).bufferedReader().use { it.readText() }
            require(expectedDigest == null || digest(jsonString) == expectedDigest) { "文件已变化，请重新预览" }
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
                if (backup == null) {
                    val current = semesterRepository.getCurrentSemester() ?: error("请先创建学期")
                    CourseImportRules.validateForSemester(courses, current, timeSlotRepository.getTimeSlotsBySchemeDirect(current.schemeId))
                }
                createRecoveryPoint("恢复前")
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
