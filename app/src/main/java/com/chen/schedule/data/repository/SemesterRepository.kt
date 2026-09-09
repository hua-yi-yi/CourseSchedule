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

    suspend fun getSemesterById(id: Long): Semester? =
        semesterDao.getSemesterById(id)?.toDomain()

    suspend fun insert(semester: Semester): Long =
        semesterDao.insert(semester.toEntity())

    suspend fun update(semester: Semester) =
        semesterDao.update(semester.toEntity())

    suspend fun delete(semester: Semester) =
        semesterDao.delete(semester.toEntity())

    /** 清空全部学期(完整恢复备份前使用)。 */
    suspend fun deleteAll() = semesterDao.deleteAllSemesters()

    /** 切换当前学期,并恢复到该学期原先关联的作息方案(事务)。 */
    suspend fun switchCurrentSemester(id: Long) {
        val target = semesterDao.getSemesterById(id) ?: return
        semesterDao.switchCurrentSemesterWithScheme(id, target.schemeId)
    }

    /** 仅切换当前学期标记,不改动作息绑定。 */
    suspend fun setCurrentSemester(id: Long) {
        semesterDao.switchCurrentSemester(id)
    }

    suspend fun setSemesterScheme(semesterId: Long, schemeId: Long) =
        semesterDao.setSemesterScheme(semesterId, schemeId)

    private fun SemesterEntity.toDomain() = Semester(
        id = id, name = name, startDate = startDate,
        totalWeeks = totalWeeks, isCurrent = isCurrent, schemeId = schemeId
    )

    private fun Semester.toEntity() = SemesterEntity(
        id = id, name = name, startDate = startDate,
        totalWeeks = totalWeeks, isCurrent = isCurrent, schemeId = schemeId
    )
}
