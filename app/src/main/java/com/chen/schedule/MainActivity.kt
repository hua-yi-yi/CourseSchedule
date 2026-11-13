package com.chen.schedule

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.chen.schedule.ui.navigation.AppNavHost
import com.chen.schedule.ui.theme.ScheduleTheme
import com.chen.schedule.ui.theme.ThemePrefs
import com.chen.schedule.ui.theme.rememberTimetableWallpaper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var timetableOpenRequest by mutableIntStateOf(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptTimetableRequest(intent)
    }

    private fun acceptTimetableRequest(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_TIMETABLE, false) == true) {
            timetableOpenRequest++
            intent.removeExtra(EXTRA_OPEN_TIMETABLE)
        }
    }

    override fun onStart() {
        super.onStart()
        com.chen.schedule.island.CapsuleIslandManager.sync(this)
        com.chen.schedule.reminders.ClassReminderManager.rescheduleAsync(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acceptTimetableRequest(intent)
        enableEdgeToEdge()
        setContent {
            val themeConfig by ThemePrefs.state.collectAsState()
            val wallpaper = rememberTimetableWallpaper(
                themeConfig.customBackgroundPath,
                themeConfig.customBackgroundRevision
            )
            ScheduleTheme(themeConfig = themeConfig, wallpaperTint = wallpaper?.tint) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavHost(openTimetableRequest = timetableOpenRequest, wallpaper = wallpaper)
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_TIMETABLE = "com.chen.schedule.OPEN_TIMETABLE"
    }
}
