package com.chen.schedule.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.chen.schedule.data.local.entity.CourseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses WHERE semesterId = :semesterId ORDER BY dayOfWeek, startSlot")
    fun getCoursesBySemester(semesterId: Long): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE semesterId = :semesterId AND dayOfWeek = :day ORDER BY startSlot")
    fun getCoursesByDay(semesterId: Long, day: Int): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE semesterId = :semesterId AND dayOfWeek = :day ORDER BY startSlot")
    suspend fun getCoursesByDayDirect(semesterId: Long, day: Int): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE semesterId = :semesterId")
    suspend fun getCoursesBySemesterDirect(semesterId: Long): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun getCourseById(id: Long): CourseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(course: CourseEntity): Long

    @Update
    suspend fun update(course: CourseEntity)

    @Delete
    suspend fun delete(course: CourseEntity)

    @Query("DELETE FROM courses WHERE semesterId = :semesterId")
    suspend fun deleteAllBySemester(semesterId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(courses: List<CourseEntity>)

    @Query("UPDATE courses SET color = :color WHERE name = :name AND semesterId = :semesterId")
    suspend fun updateColorByNameAndSemester(name: String, semesterId: Long, color: Long)
}
