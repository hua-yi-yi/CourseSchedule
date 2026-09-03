package com.chen.schedule.data.local.entity

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
    val name: String = ""
)
