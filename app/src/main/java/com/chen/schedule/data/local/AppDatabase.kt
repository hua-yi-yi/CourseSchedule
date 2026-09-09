package com.chen.schedule.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.chen.schedule.data.local.dao.CourseDao
import com.chen.schedule.data.local.dao.SemesterDao
import com.chen.schedule.data.local.dao.TimeSchemeDao
import com.chen.schedule.data.local.dao.TimeSlotDao
import com.chen.schedule.data.local.entity.CourseEntity
import com.chen.schedule.data.local.entity.SemesterEntity
import com.chen.schedule.data.local.entity.TimeSchemeEntity
import com.chen.schedule.data.local.entity.TimeSlotEntity

@Database(
    entities = [
        CourseEntity::class,
        SemesterEntity::class,
        TimeSlotEntity::class,
        TimeSchemeEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE time_slots ADD COLUMN season INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * 2 → 3:引入作息方案。
         * - 新增 time_schemes 表;
         * - time_slots 增加 schemeId,默认 0 = 原有作息(升级前全局节次整体保留);
         * - semesters 增加 schemeId,默认 0 = 原有作息(已有学期不会被标记为未设置)。
         * 全部为 ADD COLUMN / CREATE TABLE,不删除、不改写任何既有行。
         */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `time_schemes` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`kind` INTEGER NOT NULL, " +
                        "`season` INTEGER NOT NULL DEFAULT 0, " +
                        "`createTime` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL("ALTER TABLE time_slots ADD COLUMN schemeId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE semesters ADD COLUMN schemeId INTEGER NOT NULL DEFAULT 0")
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
    }

    abstract fun courseDao(): CourseDao
    abstract fun semesterDao(): SemesterDao
    abstract fun timeSlotDao(): TimeSlotDao
    abstract fun timeSchemeDao(): TimeSchemeDao
}
