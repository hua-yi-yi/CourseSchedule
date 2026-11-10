package com.chen.schedule.calendar

import android.content.ContentProvider
import android.content.ContentProviderOperation
import android.content.ContentProviderResult
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.OperationApplicationException
import android.content.pm.PackageManager
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import com.chen.schedule.domain.model.Semester

fun calendarSyncKeepsPreviousEventsOnFailure(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    val provider = TestCalendarProvider()
    val resolver = ContentResolver.wrap(provider)
    val isolated = object : ContextWrapper(context) {
        override fun getContentResolver(): ContentResolver = resolver
        override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
            PackageManager.PERMISSION_GRANTED
    }
    val semester = Semester(id = 1, name = "Calendar sync regression")
    val old = CalendarEventItem(1, "Old", "", "", 1000, 2000, 1, 1, 1, 1)
    val replacement = List(51) { old.copy(title = "New ${it + 1}") }
    fun sync(events: List<CalendarEventItem>) =
        CalendarSyncManager.syncSemesterEvents(isolated, semester, events, reminderMinutes = 0)

    check(sync(listOf(old)).getOrThrow() == 1)
    val publishedCalendar = provider.calendars.entries.single().key
    val originalEvents = provider.events.toMap()

    provider.failOnTitle = "New 51"
    check(sync(replacement).isFailure)
    check(provider.calendars.keys == setOf(publishedCalendar))
    check(provider.events == originalEvents)

    provider.failOnTitle = null
    provider.failMove = true
    check(sync(replacement).isFailure)
    check(provider.calendars.keys == setOf(publishedCalendar))
    check(provider.events == originalEvents)

    provider.failMove = false
    repeat(2) {
        check(sync(replacement).getOrThrow() == replacement.size)
        check(provider.calendars.keys == setOf(publishedCalendar))
        check(provider.events.size == replacement.size)
        check(provider.events.values.all { it.calendarId == publishedCalendar && it.title.startsWith("New ") })
    }
}

private class TestCalendarProvider : ContentProvider() {
    data class Event(val calendarId: Long, val title: String)
    val calendars = mutableMapOf<Long, String>()
    val events = mutableMapOf<Long, Event>()
    var failOnTitle: String? = null
    var failMove = false
    private var nextId = 1L

    override fun onCreate() = true
    override fun getType(uri: Uri): String? = null

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): MatrixCursor {
        val filter = checkNotNull(selectionArgs).last()
        val ids = when (uri.pathSegments.first()) {
            "calendars" -> calendars.filterValues { it == filter }.keys
            "events" -> events.filterValues { it.calendarId == filter.toLong() }.keys
            else -> error("Unexpected query: $uri")
        }
        return MatrixCursor(arrayOf("_id")).apply { ids.forEach { addRow(arrayOf(it)) } }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        val id = nextId++
        val data = checkNotNull(values)
        when (uri.pathSegments.first()) {
            "calendars" -> {
                check(data.getAsInteger(CalendarContract.Calendars.VISIBLE) == 0)
                calendars[id] = data.getAsString(CalendarContract.Calendars.NAME)
            }
            "events" -> {
                val title = data.getAsString(CalendarContract.Events.TITLE)
                if (title == failOnTitle) throw OperationApplicationException("Test insertion failure")
                events[id] = Event(data.getAsLong(CalendarContract.Events.CALENDAR_ID), title)
            }
            else -> error("Unexpected insertion: $uri")
        }
        return ContentUris.withAppendedId(uri, id)
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        if (uri.pathSegments.first() == "calendars") {
            val id = ContentUris.parseId(uri)
            events.values.removeAll { it.calendarId == id }
            return if (calendars.remove(id) != null) 1 else 0
        }
        val id = checkNotNull(selectionArgs).single().toLong()
        val matches = events.filterValues { it.calendarId == id }.keys
        events.keys.removeAll(matches)
        return matches.size
    }

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?
    ): Int {
        val data = checkNotNull(values)
        if (uri.pathSegments.first() == "calendars") {
            check(data.getAsInteger(CalendarContract.Calendars.VISIBLE) == 1)
            return if (calendars.replace(ContentUris.parseId(uri), data.getAsString(CalendarContract.Calendars.NAME)) != null) 1 else 0
        }
        if (failMove) return 0
        val source = checkNotNull(selectionArgs).single().toLong()
        val destination = data.getAsLong(CalendarContract.Events.CALENDAR_ID)
        val matches = events.filterValues { it.calendarId == source }
        matches.forEach { (id, event) -> events[id] = event.copy(calendarId = destination) }
        return matches.size
    }

    override fun applyBatch(operations: ArrayList<ContentProviderOperation>): Array<ContentProviderResult> {
        val savedCalendars = calendars.toMap()
        val savedEvents = events.toMap()
        return try {
            super.applyBatch(operations)
        } catch (error: Exception) {
            calendars.clear()
            calendars.putAll(savedCalendars)
            events.clear()
            events.putAll(savedEvents)
            throw error
        }
    }
}
