package com.chen.schedule.data.repository

import com.chen.schedule.data.local.dao.TimeSlotDao
import com.chen.schedule.data.local.entity.TimeSlotEntity
import com.chen.schedule.domain.model.TimeSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimeSlotRepository @Inject constructor(
    private val timeSlotDao: TimeSlotDao
) {
    fun getAllTimeSlots(): Flow<List<TimeSlot>> =
        timeSlotDao.getAllTimeSlots().map { list -> list.map { it.toDomain() } }

    suspend fun insert(timeSlot: TimeSlot): Long =
        timeSlotDao.insert(timeSlot.toEntity())

    suspend fun update(timeSlot: TimeSlot) =
        timeSlotDao.update(timeSlot.toEntity())

    suspend fun delete(timeSlot: TimeSlot) =
        timeSlotDao.delete(timeSlot.toEntity())

    suspend fun insertAll(timeSlots: List<TimeSlot>) =
        timeSlotDao.insertAll(timeSlots.map { it.toEntity() })

    suspend fun deleteAll() = timeSlotDao.deleteAll()

    private fun TimeSlotEntity.toDomain() = TimeSlot(
        id = id, slotNumber = slotNumber,
        startTime = startTime, endTime = endTime, name = name
    )

    private fun TimeSlot.toEntity() = TimeSlotEntity(
        id = id, slotNumber = slotNumber,
        startTime = startTime, endTime = endTime, name = name
    )
}
