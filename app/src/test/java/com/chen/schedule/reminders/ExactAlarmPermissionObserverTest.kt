package com.chen.schedule.reminders

import org.junit.Assert.assertEquals
import org.junit.Test

class ExactAlarmPermissionObserverTest {
    @Test
    fun grantOnReturningFromSettingsRequestsOneReschedule() {
        var allowed = false
        var reschedules = 0
        val observer = ExactAlarmPermissionObserver(
            isAllowed = { allowed },
            onGranted = { reschedules++ }
        )

        observer.onResume() // Opening the app's settings does not change its existing alarms.
        assertEquals(0, reschedules)

        allowed = true // The user grants access in Android's alarm settings.
        observer.onResume()
        assertEquals(1, reschedules)

        observer.onResume() // Later navigation and resumes must not keep rebuilding alarms.
        observer.onResume()
        assertEquals(1, reschedules)
    }

    @Test
    fun denyingThePermissionDoesNotRebuildAlarms() {
        var reschedules = 0
        val observer = ExactAlarmPermissionObserver(
            isAllowed = { false },
            onGranted = { reschedules++ }
        )

        observer.onResume()
        observer.onResume()
        assertEquals(0, reschedules)
    }

    @Test
    fun existingPermissionDoesNotRebuildAlarmsOnEveryResume() {
        var reschedules = 0
        val observer = ExactAlarmPermissionObserver(
            isAllowed = { true },
            onGranted = { reschedules++ }
        )

        observer.onResume()
        observer.onResume()
        assertEquals(0, reschedules)
    }

    @Test
    fun grantingAgainAfterARevocationRebuildsAlarmsUsingTheCurrentPermission() {
        var allowed = true
        var reschedules = 0
        val observer = ExactAlarmPermissionObserver(
            isAllowed = { allowed },
            onGranted = { reschedules++ }
        )

        allowed = false
        observer.onResume()
        assertEquals(0, reschedules)

        allowed = true
        observer.onResume()
        observer.onResume()
        assertEquals(1, reschedules)
    }

    @Test
    fun aGrantRevokedBeforeReturningDoesNotRequestExactAlarms() {
        var allowed = false
        var reschedules = 0
        val observer = ExactAlarmPermissionObserver(
            isAllowed = { allowed },
            onGranted = { reschedules++ }
        )

        allowed = true
        allowed = false // Android access was revoked again before the page resumes.
        observer.onResume()
        assertEquals(0, reschedules)
    }
}
