package com.chen.schedule

import android.app.Application
import com.chen.schedule.reminders.ClassReminderManager
import com.chen.schedule.island.CapsuleIslandManager
import com.chen.schedule.island.IslandPrefs
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltAndroidApp
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        com.chen.schedule.ui.theme.ThemePrefs.init(this)
        IslandPrefs.init(this)
        CapsuleIslandManager.sync(this)
        // 启动时重排上课提醒(开机与日常启动都会走到这里)
        ClassReminderManager.rescheduleAsync(this)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            IslandPrefs.state.map { Triple(it.enabled, it.mode, it.leadMinutes) }
                .distinctUntilChanged().drop(1).collect {
                    ClassReminderManager.rescheduleAsync(this@App)
                    withContext(Dispatchers.Main) { CapsuleIslandManager.sync(this@App) }
                }
        }
    }
}
