package com.chen.schedule.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.chen.schedule.data.local.entity.SemesterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SemesterDao {
    @Query("SELECT * FROM semesters ORDER BY startDate DESC")
    fun getAllSemesters(): Flow<List<SemesterEntity>>

    @Query("SELECT * FROM semesters WHERE isCurrent = 1 LIMIT 1")
    suspend fun getCurrentSemester(): SemesterEntity?

    @Query("SELECT * FROM semesters WHERE id = :id")
    suspend fun getSemesterById(id: Long): SemesterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(semester: SemesterEntity): Long

    @Update
    suspend fun update(semester: SemesterEntity)

    @Delete
    suspend fun delete(semester: SemesterEntity)

    @Query("UPDATE semesters SET isCurrent = 0")
    suspend fun clearCurrentSemester()

    /** 清空全部学期(完整恢复备份前使用)。 */
    @Query("DELETE FROM semesters")
    suspend fun deleteAllSemesters()

    @Query("UPDATE semesters SET isCurrent = 1 WHERE id = :id")
    suspend fun setCurrentSemester(id: Long)

    @Query("UPDATE semesters SET schemeId = :schemeId WHERE id = :id")
    suspend fun setSemesterScheme(id: Long, schemeId: Long)

    @Query("SELECT COUNT(*) FROM semesters WHERE schemeId = :schemeId")
    suspend fun countSemestersUsingScheme(schemeId: Long): Int

    @Query("SELECT * FROM semesters WHERE schemeId = :schemeId")
    suspend fun getSemestersUsingScheme(schemeId: Long): List<SemesterEntity>

    @Transaction
    suspend fun switchCurrentSemester(id: Long) {
        clearCurrentSemester()
        setCurrentSemester(id)
    }

    /** 事务内切换当前学期并绑定其作息方案(0 = 原有作息)。 */
    @Transaction
    suspend fun switchCurrentSemesterWithScheme(id: Long, schemeId: Long) {
        clearCurrentSemester()
        setSemesterScheme(id, schemeId)
        setCurrentSemester(id)
    }
}
