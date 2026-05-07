package com.hrcoach.data.repository

import com.hrcoach.data.db.WorkoutMetricsDao
import com.hrcoach.data.db.WorkoutMetricsEntity
import com.hrcoach.domain.model.WorkoutAdaptiveMetrics
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutMetricsRepository @Inject constructor(
    private val workoutMetricsDao: WorkoutMetricsDao
) {
    suspend fun saveWorkoutMetrics(metrics: WorkoutAdaptiveMetrics) {
        workoutMetricsDao.upsert(metrics.toEntity())
    }

    suspend fun getWorkoutMetrics(workoutId: Long): WorkoutAdaptiveMetrics? {
        return workoutMetricsDao.getByWorkoutId(workoutId)?.toDomain()
    }

    suspend fun getWorkoutMetrics(workoutIds: List<Long>): Map<Long, WorkoutAdaptiveMetrics> {
        if (workoutIds.isEmpty()) return emptyMap()
        return workoutMetricsDao.getByWorkoutIds(workoutIds)
            .associate { entity -> entity.workoutId to entity.toDomain() }
    }

    suspend fun getMetricsEntity(workoutId: Long): WorkoutMetricsEntity? {
        return workoutMetricsDao.getByWorkoutId(workoutId)
    }

    suspend fun deleteWorkoutMetrics(workoutId: Long) {
        workoutMetricsDao.deleteByWorkoutId(workoutId)
    }

    suspend fun pruneWorkoutMetrics(validWorkoutIds: Set<Long>) {
        if (validWorkoutIds.isEmpty()) {
            workoutMetricsDao.deleteAll()
            return
        }
        workoutMetricsDao.deleteAllExcept(validWorkoutIds.toList())
    }

    /**
     * Returns all workout metrics recorded within the last [limitDays] days,
     * ordered by recordedAtMs descending (most recent first).
     */
    suspend fun getRecentMetrics(limitDays: Int): List<WorkoutAdaptiveMetrics> {
        val cutoffMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(limitDays.toLong())
        return workoutMetricsDao.getMetricsSince(cutoffMs).map { it.toDomain() }
    }
}

private fun WorkoutAdaptiveMetrics.toEntity(): WorkoutMetricsEntity {
    return WorkoutMetricsEntity(
        workoutId = workoutId,
        recordedAtMs = recordedAtMs,
        avgPaceMinPerKm = avgPaceMinPerKm,
        avgHr = avgHr,
        hrAtSixMinPerKm = hrAtSixMinPerKm,
        settleDownSec = settleDownSec,
        settleUpSec = settleUpSec,
        longTermHrTrimBpm = longTermHrTrimBpm,
        responseLagSec = responseLagSec,
        efficiencyFactor = efficiencyFactor,
        aerobicDecoupling = aerobicDecoupling,
        efFirstHalf = efFirstHalf,
        efSecondHalf = efSecondHalf,
        heartbeatsPerKm = heartbeatsPerKm,
        paceAtRefHrMinPerKm = paceAtRefHrMinPerKm,
        trimpScore = trimpScore,
        trimpReliable = trimpReliable,
        environmentAffected = environmentAffected,
        cueCountsJson = cueCountsJson,
    )
}

private fun WorkoutMetricsEntity.toDomain(): WorkoutAdaptiveMetrics {
    return WorkoutAdaptiveMetrics(
        workoutId = workoutId,
        recordedAtMs = recordedAtMs,
        avgPaceMinPerKm = avgPaceMinPerKm,
        avgHr = avgHr,
        hrAtSixMinPerKm = hrAtSixMinPerKm,
        settleDownSec = settleDownSec,
        settleUpSec = settleUpSec,
        longTermHrTrimBpm = longTermHrTrimBpm,
        responseLagSec = responseLagSec,
        efficiencyFactor = efficiencyFactor,
        aerobicDecoupling = aerobicDecoupling,
        efFirstHalf = efFirstHalf,
        efSecondHalf = efSecondHalf,
        heartbeatsPerKm = heartbeatsPerKm,
        paceAtRefHrMinPerKm = paceAtRefHrMinPerKm,
        trimpScore = trimpScore,
        trimpReliable = trimpReliable,
        environmentAffected = environmentAffected,
        cueCountsJson = cueCountsJson,
    )
}
