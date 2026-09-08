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

    @Query("DELETE FROM time_slots")
    suspend fun deleteAll()
}
