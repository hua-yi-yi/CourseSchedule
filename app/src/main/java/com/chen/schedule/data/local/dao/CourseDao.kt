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

    /** 全部课程(跨学期),用于完整备份。 */
    @Query("SELECT * FROM courses ORDER BY semesterId, dayOfWeek, startSlot")
    fun getAllCourses(): Flow<List<CourseEntity>>

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

    /** 清空全部课程(完整恢复备份前使用)。 */
    @Query("DELETE FROM courses")
    suspend fun deleteAllCourses()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(courses: List<CourseEntity>)

    @Query("UPDATE courses SET color = :color WHERE name = :name AND semesterId = :semesterId")
    suspend fun updateColorByNameAndSemester(name: String, semesterId: Long, color: Long)

    /** 引用到指定节次的课程(用于删除/减少节次前的引用检查)。 */
    @Query(
        "SELECT * FROM courses WHERE semesterId = :semesterId " +
            "AND startSlot <= :slotNumber AND endSlot >= :slotNumber ORDER BY dayOfWeek, startSlot"
    )
    suspend fun getCoursesUsingSlot(semesterId: Long, slotNumber: Int): List<CourseEntity>

    /** 跨学期统计节次被引用情况(方案可能被多个学期共享)。 */
    @Query(
        "SELECT COUNT(*) FROM courses WHERE startSlot <= :slotNumber AND endSlot >= :slotNumber"
    )
    suspend fun countCoursesUsingSlot(slotNumber: Int): Int
}
