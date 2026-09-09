package com.chen.schedule.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.chen.schedule.data.local.entity.TimeSlotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeSlotDao {
    @Query("SELECT * FROM time_slots ORDER BY slotNumber")
    fun getAllTimeSlots(): Flow<List<TimeSlotEntity>>

    @Query("SELECT * FROM time_slots WHERE schemeId = :schemeId ORDER BY slotNumber")
    fun getTimeSlotsByScheme(schemeId: Long): Flow<List<TimeSlotEntity>>

    @Query("SELECT * FROM time_slots WHERE schemeId = :schemeId ORDER BY slotNumber")
    suspend fun getTimeSlotsBySchemeDirect(schemeId: Long): List<TimeSlotEntity>

    @Query("SELECT * FROM time_slots WHERE slotNumber = :slotNumber LIMIT 1")
    suspend fun getTimeSlotByNumber(slotNumber: Int): TimeSlotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(timeSlot: TimeSlotEntity): Long

    @Update
    suspend fun update(timeSlot: TimeSlotEntity)

    @Delete
    suspend fun delete(timeSlot: TimeSlotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(timeSlots: List<TimeSlotEntity>)

    @androidx.room.Transaction
    suspend fun replaceAll(slots: List<TimeSlotEntity>) {
        deleteAll()
        insertAll(slots)
    }

    /** 事务内替换单个方案的整套节次:只影响该方案,不触碰其他方案/学期。 */
    @androidx.room.Transaction
    suspend fun replaceScheme(schemeId: Long, slots: List<TimeSlotEntity>) {
        deleteByScheme(schemeId)
        insertAll(slots)
    }

    @Query("DELETE FROM time_slots")
    suspend fun deleteAll()

    @Query("DELETE FROM time_slots WHERE schemeId = :schemeId")
    suspend fun deleteByScheme(schemeId: Long)

    /** 统计某方案的节次数量(用于状态判定)。 */
    @Query("SELECT COUNT(*) FROM time_slots WHERE schemeId = :schemeId")
    suspend fun countByScheme(schemeId: Long): Int
}
