package com.chen.schedule.data.repository

import com.chen.schedule.data.local.dao.SemesterDao
import com.chen.schedule.data.local.entity.SemesterEntity
import com.chen.schedule.domain.model.Semester
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SemesterRepository @Inject constructor(
    private val semesterDao: SemesterDao
) {
    fun getAllSemesters(): Flow<List<Semester>> =
        semesterDao.getAllSemesters().map { list -> list.map { it.toDomain() } }

    suspend fun getCurrentSemester(): Semester? =
        semesterDao.getCurrentSemester()?.toDomain()

    suspend fun insert(semester: Semester): Long =
        semesterDao.insert(semester.toEntity())

    suspend fun update(semester: Semester) =
        semesterDao.update(semester.toEntity())

    suspend fun delete(semester: Semester) =
        semesterDao.delete(semester.toEntity())

    suspend fun setCurrentSemester(id: Long) {
        semesterDao.switchCurrentSemester(id)
    }

    private fun SemesterEntity.toDomain() = Semester(
        id = id, name = name, startDate = startDate,
        totalWeeks = totalWeeks, isCurrent = isCurrent
    )

    private fun Semester.toEntity() = SemesterEntity(
        id = id, name = name, startDate = startDate,
        totalWeeks = totalWeeks, isCurrent = isCurrent
    )
}
