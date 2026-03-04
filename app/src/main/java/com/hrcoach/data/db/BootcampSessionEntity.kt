package com.hrcoach.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bootcamp_sessions",
    foreignKeys = [
        ForeignKey(
            entity = BootcampEnrollmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["enrollmentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("enrollmentId")]
)
data class BootcampSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val enrollmentId: Long,
    val weekNumber: Int,
    val dayOfWeek: Int,
    val sessionType: String,
    val targetMinutes: Int,
    val presetId: String? = null,
    val status: String = "SCHEDULED",
    val completedWorkoutId: Long? = null
)
