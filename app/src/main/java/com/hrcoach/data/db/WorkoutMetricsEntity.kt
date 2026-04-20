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
    val paceAtRefHrMinPerKm: Float? = null,
    val hrr1Bpm: Float? = null,
    val trimpScore: Float? = null,
    val trimpReliable: Boolean = true,
    val environmentAffected: Boolean = false,
    // JSON map of CoachingEvent.name -> count, populated at workout stop from
    // CoachingAudioManager.consumeCueCounts(). Read by the post-run "Sounds heard today"
    // section. Null for workouts pre-migration or with no cues (OFF verbosity, etc.).
    val cueCountsJson: String? = null,
)
