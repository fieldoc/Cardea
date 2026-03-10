package com.hrcoach.data.db

import androidx.room.ColumnInfo
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
    @ColumnInfo(defaultValue = "SCHEDULED") val status: String = STATUS_SCHEDULED,
    val completedWorkoutId: Long? = null,
    val presetIndex: Int? = null,
    val completedAtMs: Long? = null,
) {
    companion object {
        const val STATUS_SCHEDULED = "SCHEDULED"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_SKIPPED = "SKIPPED"
        const val STATUS_DEFERRED = "DEFERRED"
    }
}
