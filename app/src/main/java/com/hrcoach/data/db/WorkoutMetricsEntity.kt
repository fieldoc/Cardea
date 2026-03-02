package com.hrcoach.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "workout_metrics",
    primaryKeys = ["workoutId"],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["workoutId"], unique = true)]
)
data class WorkoutMetricsEntity(
    val workoutId: Long,
    val recordedAtMs: Long,
    val avgPaceMinPerKm: Float? = null,
    val avgHr: Float? = null,
    val hrAtSixMinPerKm: Float? = null,
    val settleDownSec: Float? = null,
    val settleUpSec: Float? = null,
    val longTermHrTrimBpm: Float = 0f,
    val responseLagSec: Float = 25f,
    val efficiencyFactor: Float? = null,
    val aerobicDecoupling: Float? = null,
    val efFirstHalf: Float? = null,
    val efSecondHalf: Float? = null,
    val heartbeatsPerKm: Float? = null,
    val paceAtRefHrMinPerKm: Float? = null
)
