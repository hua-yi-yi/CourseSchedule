package com.chen.schedule.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.chen.schedule.data.local.dao.CourseDao
import com.chen.schedule.data.local.dao.SemesterDao
import com.chen.schedule.data.local.dao.TimeSlotDao
import com.chen.schedule.data.local.entity.CourseEntity
import com.chen.schedule.data.local.entity.SemesterEntity
import com.chen.schedule.data.local.entity.TimeSlotEntity

@Database(
    entities = [CourseEntity::class, SemesterEntity::class, TimeSlotEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun semesterDao(): SemesterDao
    abstract fun timeSlotDao(): TimeSlotDao
}
