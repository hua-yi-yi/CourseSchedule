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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** 无额外测试框架依赖的真机回归入口；只创建隔离数据库，不读取用户课表。 */
class RegressionInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        val tests = listOf<Pair<String, () -> Unit>>(
            "migration1to3" to { migration(1) },
            "migration2to3" to { migration(2) },
            "backupRoundTripAndRollback" to { backupRoundTrip() },
            "legacyBackupWithAndWithoutCourses" to { legacyBackup() },
            "apkCacheIdentityAndCorruption" to { apkCacheValidation() }
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
                old.execSQL("INSERT INTO courses VALUES (1,'原课程','教师','原地点',1,1,2,1,20,'all',4283215696,1,'备注')")
                old.execSQL("INSERT INTO time_slots (id,slotNumber,startTime,endTime,name) VALUES (1,1,'08:00','08:45','第一节')")
                old.version = version
            }
            val db = Room.databaseBuilder(targetContext, AppDatabase::class.java, name)
                .addMigrations(*AppDatabase.ALL_MIGRATIONS).build()
            try {
                check(db.courseDao().getCoursesBySemesterDirect(1).single().classroom == "原地点")
                check(db.semesterDao().getCurrentSemester()!!.schemeId == 0L)
                check(db.timeSlotDao().getTimeSlotsBySchemeDirect(0).single().startTime == "08:00")
                check(db.openHelper.writableDatabase.version == 3)
            } finally { db.close() }
        } finally { targetContext.deleteDatabase(name) }
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
            val legacyCourse = Course(name = "备份课程", semesterId = 42)
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
}
