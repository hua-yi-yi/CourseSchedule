package com.chen.schedule

import android.app.Activity
import android.app.Instrumentation
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Bundle
import androidx.room.Room
import com.chen.schedule.data.local.AppDatabase
import com.chen.schedule.data.repository.*
import com.chen.schedule.domain.model.Course
import com.chen.schedule.domain.model.Semester
import com.chen.schedule.domain.model.TimeSlot
import com.chen.schedule.ui.settings.ScheduleBackupService
import com.chen.schedule.util.ScheduleBackup
import com.chen.schedule.util.update.AppUpdateDownloader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** 无额外测试框架依赖的真机回归入口；只创建隔离数据库，不读取用户课表。 */
class RegressionInstrumentation : Instrumentation() {
    private var testArguments = Bundle()
    override fun onCreate(arguments: Bundle?) {
        testArguments = arguments ?: Bundle()
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val tests = if (testArguments.getString("testFilter") == "islandPermissionProbe")
            listOf("islandPermissionProbe" to {
                com.chen.schedule.island.islandPermissionProbe(targetContext,
                    testArguments.getString("expectedSound", "true").toBoolean(),
                    testArguments.getString("expectedSystem", "true").toBoolean())
            })
        else listOf<Pair<String, () -> Unit>>(
            "migration1to4" to { migration(1) },
            "migration2to4" to { migration(2) },
            "migration3to4" to { migration(3) },
            "reviewedUpdatesScopesAndRecovery" to { reviewedUpdates() },
            "backupRoundTripAndRollback" to { backupRoundTrip() },
            "legacyBackupWithAndWithoutCourses" to { legacyBackup() },
            "invalidFullBackupRejectedBeforeWrites" to { invalidFullBackup() },
            "importValidationDeduplicationAndRollback" to { importValidation() },
            "reminderBroadcastBoundaries" to { reminderBroadcastBoundaries() },
            "exactAlarmPermissionReceiver" to { com.chen.schedule.reminders.exactAlarmPermissionReceiverRegression(targetContext) },
            "apkCacheIdentityAndCorruption" to { apkCacheValidation() },
            "islandRuntimeAndMigration" to { com.chen.schedule.island.islandRuntimeRegression(this) },
            "capsuleIslandRegression" to { capsuleIslandRegression() }
        )
        var failures = 0
        tests.forEachIndexed { index, (name, test) ->
            val status = Bundle().apply {
                putString("class", RegressionInstrumentation::class.java.name)
                putString("test", name)
                putInt("numtests", tests.size)
                putInt("current", index + 1)
            }
            sendStatus(1, status)
            try { test(); sendStatus(0, status) }
            catch (error: Throwable) {
                failures++
                status.putString("stack", error.stackTraceToString())
                sendStatus(-2, status)
            }
        }
        finish(if (failures == 0) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply { putString("stream", "${tests.size - failures}/${tests.size} regression tests passed; failures=$failures\n") })
    }

    private fun migration(version: Int) = runBlocking<Unit> {
        val name = "regression-${UUID.randomUUID()}.db"
        val path = targetContext.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        val schema = JSONObject(context.assets.open("com.chen.schedule.data.local.AppDatabase/$version.json")
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        try {
            SQLiteDatabase.openOrCreateDatabase(path, null as SQLiteDatabase.CursorFactory?).use { old ->
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.getJSONArray("indices")
                    for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql")
                        .replace("\${TABLE_NAME}", table))
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
                old.execSQL("INSERT INTO semesters (id,name,startDate,totalWeeks,isCurrent) VALUES (1,'原学期',1725235200000,20,1)")
                old.execSQL("INSERT INTO courses (id,name,teacher,classroom,dayOfWeek,startSlot,endSlot,startWeek,endWeek,weekType,color,semesterId,note) VALUES (1,'原课程','教师','原地点',1,1,2,1,20,'all',4283215696,1,'备注')")
                old.execSQL("INSERT INTO time_slots (id,slotNumber,startTime,endTime,name) VALUES (1,1,'08:00','08:45','第一节')")
                old.version = version
            }
            val db = Room.databaseBuilder(targetContext, AppDatabase::class.java, name)
                .addMigrations(*AppDatabase.ALL_MIGRATIONS).build()
            try {
                check(db.courseDao().getCoursesBySemesterDirect(1).single().classroom == "原地点")
                check(db.semesterDao().getCurrentSemester()!!.schemeId == 0L)
                check(db.timeSlotDao().getTimeSlotsBySchemeDirect(0).single().startTime == "08:00")
                check(db.openHelper.writableDatabase.version == 4)
                check(db.courseDao().getCoursesBySemesterDirect(1).single().importSource.isEmpty())
            } finally { db.close() }
        } finally { targetContext.deleteDatabase(name) }
    }

    private fun reviewedUpdates() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(targetContext, AppDatabase::class.java).build()
        try {
            val courses = CourseRepository(db.courseDao())
            val semesters = SemesterRepository(db.semesterDao())
            val slots = TimeSlotRepository(db.timeSlotDao())
            val schemes = TimeSchemeRepository(db, db.timeSchemeDao(), db.timeSlotDao(), db.semesterDao(), db.courseDao())
            val backup = ScheduleBackupService(db, courses, semesters, slots, schemes, targetContext)
            val imports = CourseImportService(db, semesters, courses, slots, backup)
            val changes = CourseChangeService(db, courses, backup)
            val schemeId = schemes.createCustomScheme("作息", listOf(com.chen.schedule.domain.model.TimeSlot(slotNumber = 1)), false)
            val sem = semesters.insert(Semester(name = "更新测试", schemeId = schemeId, totalWeeks = 20))
            semesters.setCurrentSemester(sem)
            val oldId = courses.insert(Course(name = "数学", classroom = "A101", endSlot = 1, semesterId = sem, importSource = "file"))
            val manualId = courses.insert(Course(name = "手动课程", endSlot = 1, semesterId = sem))
            val newCourse = Course(name = "数学", classroom = "A102", endSlot = 1)
            val review = imports.review(listOf(newCourse), "file")
            val selection = com.chen.schedule.util.ImportSelection(listOf(newCourse), mapOf(0 to oldId))
            val result = imports.applyReviewed(selection, review)
            check(result.replaced == 1)
            check(courses.getCourseById(manualId) != null)
            val updated = courses.getCoursesBySemester(sem).first().single { it.name == "数学" }
            check(updated.classroom == "A102")
            check(backup.recoveryPoints().isNotEmpty())
            check(runCatching { imports.applyReviewed(selection, review) }.isFailure)
            changes.change(updated, updated.copy(dayOfWeek = 5), com.chen.schedule.util.ChangeScope.ONCE, 6)
            val split = courses.getCoursesBySemester(sem).first().filter { it.name == "数学" }
            check(split.single { it.appliesToWeek(6) }.dayOfWeek == 5)
            check(split.single { it.appliesToWeek(5) }.dayOfWeek == 1)
            check(split.single { it.appliesToWeek(7) }.dayOfWeek == 1)
            check(split.all { it.importSource.isEmpty() })
            val snapshot = backup.createRecoveryPoint("测试快照")!!
            val preview = backup.previewRestore(Uri.fromFile(snapshot))
            check("更新测试" in preview.summary)
            val before = courses.getAllCourses().first()
            snapshot.appendText(" ")
            check(runCatching { backup.importData(Uri.fromFile(snapshot), preview.digest) }.isFailure)
            check(courses.getAllCourses().first() == before)
            val stale = imports.review(listOf(newCourse), "file")
            courses.insert(Course(name = "后来新增", endSlot = 1, semesterId = sem))
            check(runCatching { imports.applyReviewed(com.chen.schedule.util.ImportSelection(listOf(newCourse)), stale) }.isFailure)
        } finally { db.close() }
    }

    private fun backupRoundTrip() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(targetContext, AppDatabase::class.java).build()
        val file = File.createTempFile("backup-regression", ".json", targetContext.cacheDir)
        try {
            val courses = CourseRepository(db.courseDao())
            val semesters = SemesterRepository(db.semesterDao())
            val slots = TimeSlotRepository(db.timeSlotDao())
            val schemes = TimeSchemeRepository(db, db.timeSchemeDao(), db.timeSlotDao(), db.semesterDao(), db.courseDao())
            val service = ScheduleBackupService(db, courses, semesters, slots, schemes, targetContext)
            val scheme = schemes.createCustomScheme("测试作息", listOf(TimeSlot(slotNumber = 1, startTime = "08:00", endTime = "08:45")), false)
            val first = semesters.insert(Semester(name = "第一学期", startDate = 1725235200000, totalWeeks = 20, isCurrent = true, schemeId = scheme))
            val second = semesters.insert(Semester(name = "第二学期", startDate = 1740960000000, totalWeeks = 20, schemeId = scheme))
            courses.insert(Course(name = "A", dayOfWeek = 1, startSlot = 1, endSlot = 1, startWeek = 1, endWeek = 20, semesterId = first))
            courses.insert(Course(name = "B", dayOfWeek = 2, startSlot = 1, endSlot = 1, startWeek = 1, endWeek = 20, semesterId = second))
            service.exportToUri(Uri.fromFile(file))
            repeat(2) {
                val result = service.importData(Uri.fromFile(file))
                check(result.fullRestore && result.count == 2)
                val allSemesters = semesters.getAllSemesters().first()
                check(allSemesters.size == 2)
                check(schemes.getAllSchemesDirect().count { it.name == "测试作息" } == 1)
                check(courses.getAllCourses().first().associate { course ->
                    course.name to allSemesters.single { it.id == course.semesterId }.name
                } == mapOf("A" to "第一学期", "B" to "第二学期"))
                check(allSemesters.map { it.schemeId }.distinct().size == 1)
                check(schemes.currentSlots().single().startTime == "08:00")
            }
            val beforeSemesters = semesters.getAllSemesters().first()
            val beforeCourses = courses.getAllCourses().first()
            val beforeSchemes = schemes.getAllSchemesDirect()
            val beforeSlots = slots.getAllTimeSlots().first()
            // 在删除旧数据后、写入新课程时强制失败，验证整个恢复事务确实回滚。
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER regression_abort BEFORE INSERT ON courses BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
            check(runCatching { service.importData(Uri.fromFile(file)) }.isFailure)
            check(semesters.getAllSemesters().first() == beforeSemesters)
            check(courses.getAllCourses().first() == beforeCourses)
            check(schemes.getAllSchemesDirect() == beforeSchemes)
            check(slots.getAllTimeSlots().first() == beforeSlots)
        } finally { file.delete(); db.close() }
    }

    private fun legacyBackup() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(targetContext, AppDatabase::class.java).build()
        val file = File.createTempFile("legacy-backup-regression", ".json", targetContext.cacheDir)
        try {
            val courses = CourseRepository(db.courseDao())
            val semesters = SemesterRepository(db.semesterDao())
            val slots = TimeSlotRepository(db.timeSlotDao())
            val schemes = TimeSchemeRepository(db, db.timeSchemeDao(), db.timeSlotDao(), db.semesterDao(), db.courseDao())
            val service = ScheduleBackupService(db, courses, semesters, slots, schemes, targetContext)
            val oldId = semesters.insert(Semester(name = "本机旧学期", totalWeeks = 20))
            courses.insert(Course(name = "本机旧课", semesterId = oldId))
            val legacySemester = Semester(id = 42, name = "备份学期", startDate = 1725235200000,
                totalWeeks = 20, isCurrent = true)
            val legacySlot = TimeSlot(slotNumber = 1, startTime = "08:00", endTime = "08:45")
            val legacyCourse = Course(name = "备份课程", semesterId = 42, endSlot = 1)
            val backup = ScheduleBackup(semester = legacySemester, courses = listOf(legacyCourse),
                timeSlots = listOf(legacySlot))
            val content = Json.encodeToString(ScheduleBackup.serializer(), backup)
            check("backupVersion" !in content)
            file.writeText(content)

            val restored = service.importData(Uri.fromFile(file))
            check(restored.fullRestore && restored.count == 1)
            check(semesters.getAllSemesters().first().single().name == "备份学期")
            check(courses.getAllCourses().first().single().name == "备份课程")
            check(slots.getAllTimeSlots().first().single { it.schemeId == 0L }.startTime == "08:00")

            file.writeText(Json.encodeToString(ScheduleBackup.serializer(), backup.copy(courses = emptyList())))
            val emptyRestored = service.importData(Uri.fromFile(file))
            check(emptyRestored.fullRestore && emptyRestored.count == 0)
            check(semesters.getAllSemesters().first().single().name == "备份学期")
            check(courses.getAllCourses().first().isEmpty())
            check(slots.getAllTimeSlots().first().single { it.schemeId == 0L }.startTime == "08:00")
        } finally { file.delete(); db.close() }
    }

    private fun invalidFullBackup() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(targetContext, AppDatabase::class.java).build()
        val file = File.createTempFile("invalid-full-backup", ".json", targetContext.cacheDir)
        try {
            val courses = CourseRepository(db.courseDao())
            val semesters = SemesterRepository(db.semesterDao())
            val slots = TimeSlotRepository(db.timeSlotDao())
            val schemes = TimeSchemeRepository(db, db.timeSchemeDao(), db.timeSlotDao(), db.semesterDao(), db.courseDao())
            val service = ScheduleBackupService(db, courses, semesters, slots, schemes, targetContext)
            val id = semesters.insert(Semester(name = "保留学期", startDate = 1725235200000, totalWeeks = 16))
            semesters.setCurrentSemester(id)
            slots.insertAll(listOf(
                TimeSlot(slotNumber = 1, startTime = "08:00", endTime = "08:45"),
                TimeSlot(slotNumber = 2, startTime = "08:55", endTime = "09:40")
            ))
            courses.insert(Course(name = "保留课程", semesterId = id))
            service.exportToUri(Uri.fromFile(file))
            val valid = Json.decodeFromString(ScheduleBackup.serializer(), file.readText())
            val originalCourse = valid.allCourses.single()
            val beforeSemesters = semesters.getAllSemesters().first()
            val beforeCourses = courses.getAllCourses().first()
            val beforeSchemes = schemes.getAllSchemesDirect()
            val beforeSlots = slots.getAllTimeSlots().first()
            val beforeRecoveryPoints = service.recoveryPoints()
            val invalidCourses = listOf(
                originalCourse.copy(endSlot = 3),
                originalCourse.copy(endSlot = Int.MAX_VALUE),
                originalCourse.copy(endWeek = 17),
                originalCourse.copy(endWeek = 54)
            )
            for (invalid in invalidCourses) {
                file.writeText(Json.encodeToString(ScheduleBackup.serializer(), valid.copy(allCourses = listOf(invalid))))
                check(runCatching { service.previewRestore(Uri.fromFile(file)) }.isFailure)
                check(runCatching { service.importData(Uri.fromFile(file)) }.isFailure)
                check(semesters.getAllSemesters().first() == beforeSemesters)
                check(courses.getAllCourses().first() == beforeCourses)
                check(schemes.getAllSchemesDirect() == beforeSchemes)
                check(slots.getAllTimeSlots().first() == beforeSlots)
                check(service.recoveryPoints() == beforeRecoveryPoints)
            }
            file.writeText(Json.encodeToString(ScheduleBackup.serializer(), valid))
            val preview = service.previewRestore(Uri.fromFile(file))
            check(service.importData(Uri.fromFile(file), preview.digest).count == 1)
            check(courses.getAllCourses().first().single().name == "保留课程")
        } finally { file.delete(); db.close() }
    }

    private fun importValidation() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(targetContext, AppDatabase::class.java).build()
        try {
            val courses = CourseRepository(db.courseDao())
            val semesters = SemesterRepository(db.semesterDao())
            val slots = TimeSlotRepository(db.timeSlotDao())
            val schemes = TimeSchemeRepository(db, db.timeSchemeDao(), db.timeSlotDao(), db.semesterDao(), db.courseDao())
            val backup = ScheduleBackupService(db, courses, semesters, slots, schemes, targetContext)
            val service = CourseImportService(db, semesters, courses, slots, backup)
            val id = semesters.insert(Semester(name = "测试学期", startDate = 1, totalWeeks = 16, isCurrent = true))
            slots.insertAll(listOf(TimeSlot(slotNumber = 1), TimeSlot(slotNumber = 2, startTime = "09:00", endTime = "09:45")))
            val course = Course(name = "测试课程")
            val first = service.importCourses(listOf(course, course), id)
            check(first.added == 1 && first.skipped == 1)
            val again = service.importCourses(listOf(course.copy(note = "新备注", color = 1)), id)
            check(again.added == 0 && again.skipped == 1)
            val roomChange = service.importCourses(listOf(course.copy(classroom = "101")), id)
            check(roomChange.added == 1)
            val concurrent = kotlinx.coroutines.coroutineScope {
                val incoming = listOf(course.copy(classroom = "并发教室"))
                val one = async { service.importCourses(incoming, id) }
                val two = async { service.importCourses(incoming, id) }
                listOf(one.await(), two.await())
            }
            check(concurrent.sumOf { it.added } == 1 && concurrent.sumOf { it.skipped } == 1)
            val before = courses.getAllCourses().first()
            check(runCatching { service.importCourses(listOf(course.copy(name = "不应写入"), course.copy(dayOfWeek = 8)), id) }.isFailure)
            check(courses.getAllCourses().first() == before)
            check(runCatching { service.importCourses(listOf(course.copy(endSlot = 3)), id) }.isFailure)
            check(runCatching { service.importCourses(listOf(course.copy(endWeek = 17)), id) }.isFailure)
            val other = semesters.insert(Semester(name = "另一学期", startDate = 1, totalWeeks = 16, isCurrent = false))
            semesters.setCurrentSemester(other)
            check(runCatching { service.importCourses(listOf(course), id) }.isFailure)
            check(courses.getAllCourses().first() == before)
        } finally { db.close() }
    }

    @Suppress("DEPRECATION")
    private fun reminderBroadcastBoundaries() {
        val pm = targetContext.packageManager
        val internal = pm.getReceiverInfo(android.content.ComponentName(targetContext,
            com.chen.schedule.reminders.ClassReminderReceiver::class.java), 0)
        val boot = pm.getReceiverInfo(android.content.ComponentName(targetContext,
            com.chen.schedule.reminders.BootReceiver::class.java), 0)
        check(!internal.exported && boot.exported)
        // Unknown actions must return without consulting app data or posting a notification.
        com.chen.schedule.reminders.ClassReminderReceiver().onReceive(targetContext, android.content.Intent("unknown"))
        com.chen.schedule.reminders.BootReceiver().onReceive(targetContext, android.content.Intent("unknown"))
    }

    private fun apkCacheValidation() {
        val scratch = File(targetContext.cacheDir, "apk-regression-${UUID.randomUUID()}")
        val isolated = object : android.content.ContextWrapper(targetContext) {
            override fun getCacheDir(): File = scratch
        }
        try {
            val updates = File(scratch, "updates").apply { mkdirs() }
            val downloader = AppUpdateDownloader(isolated)
            val version = targetContext.packageManager.getPackageInfo(targetContext.packageName, 0).versionName!!
            val expected = File(updates, "CourseSchedule-v$version.apk")
            File(targetContext.applicationInfo.sourceDir).copyTo(expected)
            check(downloader.checkExistingCompletedApk("v$version") == expected)
            expected.copyTo(File(updates, "CourseSchedule-v999.0.0.apk"))
            check(downloader.checkExistingCompletedApk("v999.0.0") == null)
            expected.writeText("<html>" + "x".repeat(3 * 1024 * 1024) + "</html>")
            check(downloader.checkExistingCompletedApk("v$version") == null)
        } finally { scratch.deleteRecursively() }
    }

    private fun capsuleIslandRegression() {
        val prefs = com.chen.schedule.island.IslandPrefs.init(targetContext)
        val origEnabled = prefs.enabled
        val origLead = prefs.leadMinutes
        val origPosition = prefs.positionY
        val origMode = prefs.mode
        try {
            prefs.enabled = true
            check(prefs.enabled)
            prefs.leadMinutes = 25
            check(prefs.leadMinutes == 25)
            prefs.positionY = 120
            check(prefs.positionY == 120)
            prefs.mockMode = true
            prefs.mockState = 0
            check(prefs.mockMode)
            check(prefs.mockState == 0)

            val dummyCourse = Course(
                id = 999L,
                name = "高等数学",
                teacher = "陈老师",
                classroom = "教一 101",
                dayOfWeek = 1,
                startSlot = 1,
                endSlot = 2,
                color = 0xFF3B82F6
            )

            // View instantiation, state rendering and click events on Main UI thread
            runOnMainSync {
                val view = com.chen.schedule.island.CapsuleIslandView(targetContext)
                check(view.visibility == android.view.View.VISIBLE)
                val ongoingState = com.chen.schedule.island.IslandState.Ongoing(
                    course = dummyCourse,
                    courseName = dummyCourse.name,
                    classroom = dummyCourse.classroom,
                    teacher = dummyCourse.teacher,
                    slotRange = "第 1-2 节",
                    startTime = "08:00",
                    endTime = "09:40",
                    startMillis = System.currentTimeMillis() - 40 * 60 * 1000L,
                    endMillis = System.currentTimeMillis() + 60 * 60 * 1000L,
                    remainingMinutes = 60,
                    totalMinutes = 100,
                    progress = 0.40f,
                    color = dummyCourse.color,
                    compactText = "高数 · 剩60分",
                    subText = "@A101 · 09:40下课"
                )
                view.updateState(ongoingState)
                // Test expand click
                view.performClick()
                // Test Upcoming state
                val upcomingState = com.chen.schedule.island.IslandState.Upcoming(
                    course = dummyCourse,
                    courseName = dummyCourse.name,
                    classroom = dummyCourse.classroom,
                    teacher = dummyCourse.teacher,
                    slotRange = "第 3-4 节",
                    startTime = "10:00",
                    endTime = "11:40",
                    startMillis = System.currentTimeMillis() + 15 * 60 * 1000L,
                    minutesUntilStart = 15,
                    color = dummyCourse.color,
                    compactText = "15分后 · 高数",
                    subText = "@A101 · 10:00上课"
                )
                view.updateState(upcomingState)
                // Test Idle state
                view.updateState(
                    com.chen.schedule.island.IslandState.Idle(
                        todayTotalCourses = 4,
                        finishedCourses = 2,
                        nextCourse = dummyCourse,
                        nextCourseStartTime = "14:00",
                        compactText = "下节 14:00",
                        subText = "今日还剩 2 节课"
                    )
                )
                // Test None state
                view.updateState(com.chen.schedule.island.IslandState.None)
            }

            // Test CapsuleIslandManager API calls
            com.chen.schedule.island.CapsuleIslandManager.refresh(targetContext)
            com.chen.schedule.island.CapsuleIslandManager.setMockTest(targetContext, true, 0)
            com.chen.schedule.island.CapsuleIslandManager.stop(targetContext)
            check(prefs.enabled) // stopping the service must not change user intent
        } finally {
            prefs.enabled = origEnabled
            prefs.leadMinutes = origLead
            prefs.positionY = origPosition
            prefs.mode = origMode
            com.chen.schedule.island.CapsuleIslandManager.setMockTest(targetContext, false)
        }
    }
}

