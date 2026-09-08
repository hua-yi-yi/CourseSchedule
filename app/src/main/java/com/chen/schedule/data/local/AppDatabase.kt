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
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE time_slots ADD COLUMN season INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
    abstract fun courseDao(): CourseDao
    abstract fun semesterDao(): SemesterDao
    abstract fun timeSlotDao(): TimeSlotDao
}
