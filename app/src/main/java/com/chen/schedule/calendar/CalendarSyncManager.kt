package com.chen.schedule.calendar

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.chen.schedule.domain.model.Semester
import java.time.ZoneId

/**
 * Android 系统日历 (CalendarProvider) 同步管理器。
 *
 * 在本地创建专有的独立日历账户 ("CourseSchedule")，与用户日常私人日程完全隔离，
 * 并支持一键幂等全量同步/覆盖与一键清理。
 */
object CalendarSyncManager {

    const val ACCOUNT_NAME = "CourseSchedule"
    const val ACCOUNT_TYPE = CalendarContract.ACCOUNT_TYPE_LOCAL

    /**
     * 检查是否已获得日历读写权限。
     */
    fun hasCalendarPermissions(context: Context): Boolean {
        val read = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val write = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        return read && write
    }

    fun calendarNameForSemester(semesterId: Long): String = "CourseSchedule_sem_$semesterId"

    fun calendarDisplayName(semesterName: String): String =
        if (semesterName.isNotBlank()) "课程表 - $semesterName" else "课程表"

    /**
     * 查询指定学期对应的专属日历 ID。
     */
    private fun findCalendarId(resolver: ContentResolver, name: String): Long? {
        val uri = CalendarContract.Calendars.CONTENT_URI
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "(${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND ${CalendarContract.Calendars.NAME} = ?)"
        val selectionArgs = arrayOf(ACCOUNT_NAME, ACCOUNT_TYPE, name)

        val cursor = resolver.query(uri, projection, selection, selectionArgs, null)
            ?: error("无法查询系统日历")
        return cursor.use { if (it.moveToFirst()) it.getLong(0) else null }
    }

    /**
     * 查询系统日历中该学期已存在的日程数量。
     */
    fun querySyncedEventCount(context: Context, semesterId: Long): Int = runCatching {
        val resolver = context.contentResolver
        val id = findCalendarId(resolver, calendarNameForSemester(semesterId)) ?: return@runCatching 0
        eventCount(resolver, id)
    }.getOrDefault(0)

    private val activeCalendarEvents =
        "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DELETED} = 0"

    private fun eventCount(resolver: ContentResolver, calendarId: Long): Int {
        val cursor = resolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            activeCalendarEvents,
            arrayOf(calendarId.toString()),
            null
        ) ?: error("无法查询课程日程")
        return cursor.use { it.count }
    }

    private fun asSyncAdapter(uri: Uri): Uri =
        uri.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
            .build()

    private fun calendarUri(id: Long): Uri =
        asSyncAdapter(ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, id))

    private fun createStagingCalendar(resolver: ContentResolver, name: String, zone: ZoneId): Long {
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
            put(CalendarContract.Calendars.NAME, name)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "课程表（同步中）")
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF4A90E2.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, zone.id)
            put(CalendarContract.Calendars.VISIBLE, 0)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        }

        val insertedUri = resolver.insert(asSyncAdapter(CalendarContract.Calendars.CONTENT_URI), values)
            ?: throw IllegalStateException("创建日历账户失败，请检查系统日历服务")
        return ContentUris.parseId(insertedUri)
    }

    private fun deleteStagingCalendar(resolver: ContentResolver, name: String) {
        val id = findCalendarId(resolver, name) ?: return
        check(resolver.delete(calendarUri(id), null, null) == 1) { "无法清理临时课表日历" }
    }

    private fun deleteEvents(resolver: ContentResolver, id: Long): ContentProviderOperation =
        ContentProviderOperation.newDelete(asSyncAdapter(CalendarContract.Events.CONTENT_URI))
            .withSelection(activeCalendarEvents, arrayOf(id.toString()))
            .withExpectedCount(eventCount(resolver, id))
            .build()

    @Synchronized
    fun deleteCalendar(context: Context, semesterId: Long): Boolean {
        val resolver = context.contentResolver
        val name = calendarNameForSemester(semesterId)
        deleteStagingCalendar(resolver, "${name}_staging")
        val id = findCalendarId(resolver, name) ?: return false
        val deleted = runCatching { resolver.delete(calendarUri(id), null, null) }.getOrDefault(0)
        if (deleted == 0) {
            resolver.applyBatch(CalendarContract.AUTHORITY, arrayListOf(deleteEvents(resolver, id)))
        }
        return true
    }

    /** 先在隐藏日历中分批写入，再在一次 Provider 事务中发布完整结果。 */
    @Synchronized
    fun syncSemesterEvents(
        context: Context,
        semester: Semester,
        events: List<CalendarEventItem>,
        reminderMinutes: Int,
        zone: ZoneId = ZoneId.systemDefault()
    ): Result<Int> = runCatching {
        if (!hasCalendarPermissions(context)) {
            throw SecurityException("未获得系统日历读写权限")
        }

        val resolver = context.contentResolver
        val name = calendarNameForSemester(semester.id)
        val stagingName = "${name}_staging"
        val currentId = findCalendarId(resolver, name)
        deleteStagingCalendar(resolver, stagingName)
        val stagingId = createStagingCalendar(resolver, stagingName, zone)
        try {
            insertEvents(resolver, stagingId, events, reminderMinutes, zone)
            val publish = if (currentId == null) {
                arrayListOf(
                    ContentProviderOperation.newUpdate(calendarUri(stagingId))
                        .withValue(CalendarContract.Calendars.NAME, name)
                        .withValue(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, calendarDisplayName(semester.name))
                        .withValue(CalendarContract.Calendars.VISIBLE, 1)
                        .withExpectedCount(1)
                        .build()
                )
            } else {
                arrayListOf(deleteEvents(resolver, currentId)).apply {
                    if (events.isNotEmpty()) {
                        add(
                            ContentProviderOperation.newUpdate(asSyncAdapter(CalendarContract.Events.CONTENT_URI))
                                .withSelection(activeCalendarEvents, arrayOf(stagingId.toString()))
                                .withValue(CalendarContract.Events.CALENDAR_ID, currentId)
                                // 重新调度由隐藏日历移入正式日历的提醒。
                                .withValue(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
                                .withExpectedCount(events.size)
                                .build()
                        )
                    }
                    add(ContentProviderOperation.newDelete(calendarUri(stagingId)).withExpectedCount(1).build())
                }
            }
            resolver.applyBatch(CalendarContract.AUTHORITY, publish)
        } catch (error: Exception) {
            // 按临时名称清理，避免在 Provider 已完成首次发布时误删正式日历。
            runCatching { deleteStagingCalendar(resolver, stagingName) }.onFailure(error::addSuppressed)
            throw error
        }
        events.size
    }

    private fun insertEvents(
        resolver: ContentResolver,
        calendarId: Long,
        events: List<CalendarEventItem>,
        reminderMinutes: Int,
        zone: ZoneId
    ) {
        val zoneIdStr = zone.id

        // 每批最多 50 个事件，防止单次 Binder 传输超出限制
        for (chunk in events.chunked(50)) {
            val ops = ArrayList<ContentProviderOperation>()
            var opIndex = 0

            for (event in chunk) {
                val eventOpIndex = opIndex
                ops.add(
                    ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
                        .withValue(CalendarContract.Events.CALENDAR_ID, calendarId)
                        .withValue(CalendarContract.Events.TITLE, event.title)
                        .withValue(CalendarContract.Events.DESCRIPTION, event.description)
                        .withValue(CalendarContract.Events.EVENT_LOCATION, event.location)
                        .withValue(CalendarContract.Events.DTSTART, event.startMillis)
                        .withValue(CalendarContract.Events.DTEND, event.endMillis)
                        .withValue(CalendarContract.Events.EVENT_TIMEZONE, zoneIdStr)
                        .withValue(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
                        .build()
                )
                opIndex++

                if (reminderMinutes > 0) {
                    ops.add(
                        ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI)
                            .withValueBackReference(CalendarContract.Reminders.EVENT_ID, eventOpIndex)
                            .withValue(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                            .withValue(CalendarContract.Reminders.MINUTES, reminderMinutes)
                            .build()
                    )
                    opIndex++
                }
            }

            resolver.applyBatch(CalendarContract.AUTHORITY, ops)
        }
    }
}
