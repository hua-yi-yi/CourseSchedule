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
    val isCurrent: Boolean = false
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
    @ColumnInfo(defaultValue = "0") val season: Int = 0
)
