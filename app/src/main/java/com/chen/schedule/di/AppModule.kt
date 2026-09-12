package com.chen.schedule.di

import android.content.Context
import androidx.room.Room
import com.chen.schedule.data.local.AppDatabase
import com.chen.schedule.data.local.dao.CourseDao
import com.chen.schedule.data.local.dao.SemesterDao
import com.chen.schedule.data.local.dao.TimeSchemeDao
import com.chen.schedule.data.local.dao.TimeSlotDao
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "course_schedule.db"
        ).addMigrations(*AppDatabase.ALL_MIGRATIONS).build()

    @Provides
    fun provideCourseDao(db: AppDatabase): CourseDao = db.courseDao()

    @Provides
    fun provideSemesterDao(db: AppDatabase): SemesterDao = db.semesterDao()

    @Provides
    fun provideTimeSlotDao(db: AppDatabase): TimeSlotDao = db.timeSlotDao()

    @Provides
    fun provideTimeSchemeDao(db: AppDatabase): TimeSchemeDao = db.timeSchemeDao()
}

/**
 * 供非 Hilt 组件(如 Glance 小组件、提醒调度器)访问数据层的入口。
 * 这些组件不参与 Activity/ViewModel 的注入链,通过 EntryPoint 复用同一批单例。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DatabaseEntryPoint {
    fun courseDao(): CourseDao
    fun semesterDao(): SemesterDao
    fun timeSlotDao(): TimeSlotDao
    fun timeSchemeDao(): TimeSchemeDao
    fun courseRepository(): com.chen.schedule.data.repository.CourseRepository
    fun semesterRepository(): com.chen.schedule.data.repository.SemesterRepository
    fun timeSlotRepository(): com.chen.schedule.data.repository.TimeSlotRepository
}
