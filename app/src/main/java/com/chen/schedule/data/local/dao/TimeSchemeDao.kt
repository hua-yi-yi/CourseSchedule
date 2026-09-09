package com.chen.schedule.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.chen.schedule.data.local.entity.TimeSchemeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeSchemeDao {
    @Query("SELECT * FROM time_schemes ORDER BY kind, id")
    fun getAllSchemes(): Flow<List<TimeSchemeEntity>>

    @Query("SELECT * FROM time_schemes ORDER BY kind, id")
    suspend fun getAllSchemesDirect(): List<TimeSchemeEntity>

    @Query("SELECT * FROM time_schemes WHERE id = :id LIMIT 1")
    suspend fun getSchemeById(id: Long): TimeSchemeEntity?

    @Query("SELECT * FROM time_schemes WHERE kind = :kind ORDER BY id")
    suspend fun getSchemesByKind(kind: Int): List<TimeSchemeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scheme: TimeSchemeEntity): Long

    @Update
    suspend fun update(scheme: TimeSchemeEntity)

    @Delete
    suspend fun delete(scheme: TimeSchemeEntity)

    @Query("DELETE FROM time_schemes WHERE id = :id")
    suspend fun deleteById(id: Long)
}
