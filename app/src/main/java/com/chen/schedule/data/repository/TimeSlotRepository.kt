package com.chen.schedule.data.repository

import com.chen.schedule.data.local.dao.TimeSlotDao
import com.chen.schedule.data.local.entity.TimeSlotEntity
import com.chen.schedule.domain.model.TimeScheme
import com.chen.schedule.domain.model.TimeSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimeSlotRepository @Inject constructor(
    private val timeSlotDao: TimeSlotDao
) {
    /** 全部方案的节次(调试/导出用)。展示时请按方案过滤。 */
    fun getAllTimeSlots(): Flow<List<TimeSlot>> =
        timeSlotDao.getAllTimeSlots().map { list -> list.map { it.toDomain() } }

    /** 指定方案的节次。 */
    fun getTimeSlotsByScheme(schemeId: Long): Flow<List<TimeSlot>> =
        timeSlotDao.getTimeSlotsByScheme(schemeId).map { list -> list.map { it.toDomain() } }

    suspend fun getTimeSlotsBySchemeDirect(schemeId: Long): List<TimeSlot> =
        timeSlotDao.getTimeSlotsBySchemeDirect(schemeId).map { it.toDomain() }

    /** 当前(默认)方案的节次 = 原有作息。 */
    fun getLegacyTimeSlots(): Flow<List<TimeSlot>> = getTimeSlotsByScheme(TimeScheme.LEGACY_ID)

    suspend fun insert(timeSlot: TimeSlot): Long =
        timeSlotDao.insert(timeSlot.toEntity())

    suspend fun update(timeSlot: TimeSlot) =
        timeSlotDao.update(timeSlot.toEntity())

    suspend fun delete(timeSlot: TimeSlot) =
        timeSlotDao.delete(timeSlot.toEntity())

    suspend fun insertAll(timeSlots: List<TimeSlot>) =
        timeSlotDao.insertAll(timeSlots.map { it.toEntity() })

    suspend fun replaceAll(timeSlots: List<TimeSlot>) =
        timeSlotDao.replaceAll(timeSlots.map { it.toEntity() })

    /** 事务内替换单个方案的整套节次。 */
    suspend fun replaceScheme(schemeId: Long, timeSlots: List<TimeSlot>) =
        timeSlotDao.replaceScheme(schemeId, timeSlots.map { it.copy(schemeId = schemeId).toEntity() })

    suspend fun deleteAll() = timeSlotDao.deleteAll()

    suspend fun countByScheme(schemeId: Long) = timeSlotDao.countByScheme(schemeId)

    private fun TimeSlotEntity.toDomain() = TimeSlot(
        id = id, slotNumber = slotNumber,
        startTime = startTime, endTime = endTime, name = name,
        season = season, schemeId = schemeId
    )

    private fun TimeSlot.toEntity() = TimeSlotEntity(
        id = id, slotNumber = slotNumber,
        startTime = startTime, endTime = endTime, name = name,
        season = season, schemeId = schemeId
    )
}
