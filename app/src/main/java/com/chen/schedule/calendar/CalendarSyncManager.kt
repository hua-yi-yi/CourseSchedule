package com.chen.schedule.calendar

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
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
    fun findCalendarId(context: Context, semesterId: Long): Long? {
        val uri = CalendarContract.Calendars.CONTENT_URI
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "(${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND ${CalendarContract.Calendars.NAME} = ?)"
        val selectionArgs = arrayOf(ACCOUNT_NAME, ACCOUNT_TYPE, calendarNameForSemester(semesterId))

        return runCatching {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        }.getOrNull()
    }

    /**
     * 查询系统日历中该学期已存在的日程数量。
     */
    fun querySyncedEventCount(context: Context, semesterId: Long): Int {
        val calId = findCalendarId(context, semesterId) ?: return 0
        val projection = arrayOf(CalendarContract.Events._ID)
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ?"
        val selectionArgs = arrayOf(calId.toString())

        return runCatching {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                cursor.count
            } ?: 0
        }.getOrDefault(0)
    }

    /**
     * 获取或创建该学期的独立专属日历。
     */
    fun getOrCreateCalendarId(context: Context, semester: Semester): Long {
        val existing = findCalendarId(context, semester.id)
        if (existing != null) return existing

        val syncUri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
            .build()

        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
            put(CalendarContract.Calendars.NAME, calendarNameForSemester(semester.id))
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, calendarDisplayName(semester.name))
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF4A90E2.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, ZoneId.systemDefault().id)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        }

        val insertedUri = context.contentResolver.insert(syncUri, values)
            ?: throw IllegalStateException("创建日历账户失败，请检查系统日历服务")
        return ContentUris.parseId(insertedUri)
    }

    /**
     * 清空指定日历下的所有课程日程与提醒。
     */
    fun clearEvents(context: Context, calendarId: Long): Int {
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ?"
        val selectionArgs = arrayOf(calendarId.toString())
        return runCatching {
            context.contentResolver.delete(CalendarContract.Events.CONTENT_URI, selection, selectionArgs)
        }.getOrDefault(0)
    }

    /**
     * 彻底删除该学期的独立日历。
     */
    fun deleteCalendar(context: Context, semesterId: Long): Boolean {
        val calId = findCalendarId(context, semesterId) ?: return false
        val syncUri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calId)
            .buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
            .build()

        val deletedRows = runCatching {
            context.contentResolver.delete(syncUri, null, null)
        }.getOrDefault(0)

        // 若部分系统限制删除 Calendar，回退为清空该日历下的所有事件
        if (deletedRows <= 0) {
            clearEvents(context, calId)
        }
        return true
    }

    /**
     * 全量同步学期课程至系统日历。
     *
     * 采用分批批处理（50 条/批），自动设置提醒（VALARM/Reminders），
     * 具备完全幂等性（已有事件自动先清理后写入）。
     */
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

        val calId = getOrCreateCalendarId(context, semester)
        // 先清空旧日程，避免反复同步产生重复项
        clearEvents(context, calId)

        if (events.isEmpty()) {
            return@runCatching 0
        }

        val zoneIdStr = zone.id
        var insertedCount = 0

        // 每批最多 50 个事件，防止单次 Binder 传输超出限制
        for (chunk in events.chunked(50)) {
            val ops = ArrayList<ContentProviderOperation>()
            var opIndex = 0

            for (event in chunk) {
                val eventOpIndex = opIndex
                ops.add(
                    ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
                        .withValue(CalendarContract.Events.CALENDAR_ID, calId)
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
                insertedCount++
            }

            context.contentResolver.applyBatch(CalendarContract.AUTHORITY, ops)
        }

        insertedCount
    }
}
