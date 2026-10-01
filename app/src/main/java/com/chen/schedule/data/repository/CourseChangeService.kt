package com.chen.schedule.data.repository

import androidx.room.withTransaction
import com.chen.schedule.data.local.AppDatabase
import com.chen.schedule.domain.model.Course
import com.chen.schedule.util.ChangeScope
import com.chen.schedule.util.CourseChangePlanner
import javax.inject.Inject

class CourseChangeService @Inject constructor(private val db: AppDatabase, private val courses: CourseRepository, private val backup: com.chen.schedule.ui.settings.ScheduleBackupService) {
    suspend fun change(original: Course, edited: Course?, scope: ChangeScope, week: Int) = db.withTransaction {
        require(courses.getCourseById(original.id) == original) { "课程已变化，请重新打开" }
        val plan = CourseChangePlanner.plan(original, edited, scope, week)
        backup.createRecoveryPoint("课程修改前")
        courses.delete(original)
        courses.insertAll(plan)
    }
}
