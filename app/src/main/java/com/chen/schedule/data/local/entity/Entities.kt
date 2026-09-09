package com.chen.schedule.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "semesters")
data class SemesterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startDate: Long,
    val totalWeeks: Int,
    val isCurrent: Boolean = false,
    /** 关联作息方案 id:0 = 原有作息(全局),>0 = time_schemes.id。 */
    @ColumnInfo(defaultValue = "0") val schemeId: Long = 0
)

@Entity(
    tableName = "courses",
    foreignKeys = [ForeignKey(
        entity = SemesterEntity::class,
        parentColumns = ["id"],
        childColumns = ["semesterId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("semesterId")]
)
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val teacher: String = "",
    val classroom: String = "",
    val dayOfWeek: Int,
    val startSlot: Int,
    val endSlot: Int,
    val startWeek: Int,
    val endWeek: Int,
    val weekType: String = "all",
    val color: Long = 0xFF4CAF50,
    val semesterId: Long,
    val note: String = ""
)

@Entity(tableName = "time_slots")
data class TimeSlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slotNumber: Int,
    val startTime: String,
    val endTime: String,
    val name: String = "",
    /** 作息套别:0=通用(默认),1=夏季(5/1–9/30),2=冬季(10/1–4/30)。 */
    @ColumnInfo(defaultValue = "0") val season: Int = 0,
    /** 归属性息方案 id:0 = 原有作息(全局),>0 = time_schemes.id。 */
    @ColumnInfo(defaultValue = "0") val schemeId: Long = 0
)

/**
 * 作息方案表。内置模板(夏季/冬季)与用户自定义方案都落在这里。
 * 「原有作息」不占行,由 [TimeSlotEntity.schemeId] = 0 表示,保证旧数据零迁移。
 */
@Entity(tableName = "time_schemes")
data class TimeSchemeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** 0 = 内置模板,1 = 自定义方案。 */
    val kind: Int = 1,
    /** 内置模板的季节标记:0=通用,1=夏季,2=冬季。 */
    @ColumnInfo(defaultValue = "0") val season: Int = 0,
    @ColumnInfo(defaultValue = "0") val createTime: Long = 0
)
