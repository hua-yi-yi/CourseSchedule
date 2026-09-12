package com.chen.schedule

import android.app.Application
import com.chen.schedule.reminders.ClassReminderManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 启动时重排上课提醒(开机与日常启动都会走到这里)
        ClassReminderManager.rescheduleAsync(this)
    }
}
