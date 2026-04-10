package com.hrcoach.domain.engine

import android.util.Log
import com.hrcoach.data.db.TrackPointEntity
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.domain.model.AdaptiveProfile
import com.hrcoach.domain.model.WorkoutAdaptiveMetrics
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.ZoneStatus
import com.hrcoach.util.JsonCodec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rebuilds [AdaptiveProfile] from scratch by replaying all real (non-simulated)
 * workout track points through [AdaptivePaceController] in chronological order.
 *
 * Call after deleting a workout so the deleted run has zero lasting effect on
 * long-term trim, pace-HR buckets, CTL/ATL, or any other persisted field.
 */
@Singleton
class AdaptiveProfileRebuilder @Inject constructor() {

    companion object {
        private const val TAG = "AdaptiveProfileRebuilder"

        /**
         * Pure function — no I/O. Pass pre-loaded data; caller handles persistence.
         *
         * Returns the recomputed profile. If [isWorkoutRunning] returns true the
         * profile is still computed and returned, but the caller must not persist it
         * to avoid racing with an active workout's own profile save.
         *
         * @param workouts Real workouts in ascending startTime order (use getAllRealWorkoutsAsc).
         * @param trackPointsByWorkout Map from workout id to its track points.
         * @param metricsByWorkout Map from workout id to its stored metrics (for TRIMP/CTL/ATL).
         * @param isWorkoutRunning Lambda consulted by caller before persisting the result.
         */
        fun rebuild(
            workouts: List<WorkoutEntity>,
            trackPointsByWorkout: Map<Long, List<TrackPointEntity>>,
            metricsByWorkout: Map<Long, WorkoutAdaptiveMetrics>,
            isWorkoutRunning: () -> Boolean,
            age: Int? = null
        ): AdaptiveProfile {
            // Consulted so the lambda fires during this call (callers check return value separately)
            isWorkoutRunning()

            var profile = AdaptiveProfile()
            var lastProcessedStartTime: Long? = null

            workouts.forEachIndexed { index, workout ->
                val points = trackPointsByWorkout[workout.id]
                if (points.isNullOrEmpty()) return@forEachIndexed

                val config = runCatching {
                    JsonCodec.gson.fromJson(workout.targetConfig, WorkoutConfig::class.java)
                }.getOrElse {
                    Log.w(TAG, "Could not parse config for workout ${workout.id}, skipping")
                    return@forEachIndexed
                }

                val controller = AdaptivePaceController(config = config, initialProfile = profile)
                val zoneEngine = ZoneEngine(config)

                points.sortedBy { it.timestamp }.forEach { point ->
                    val elapsedSeconds = (point.timestamp - workout.startTime) / 1000L
                    val targetHr = config.targetHrAtElapsedSeconds(elapsedSeconds)
                        ?: config.targetHrAtDistance(point.distanceMeters)
                    val zoneStatus = if (targetHr != null && point.heartRate > 0) {
                        zoneEngine.evaluate(point.heartRate, targetHr)
                    } else {
                        ZoneStatus.NO_DATA
                    }
                    controller.evaluateTick(
                        nowMs = point.timestamp,
                        hr = point.heartRate,
                        connected = point.heartRate > 0,
                        targetHr = targetHr,
                        distanceMeters = point.distanceMeters,
                        actualZone = zoneStatus
                    )
                }

                val session = controller.finishSession(
                    workoutId = workout.id,
                    endedAtMs = workout.endTime
                )
                // finishSession resets ctl/atl/hrMax to defaults — preserve accumulated values
                val prevCtl = profile.ctl
                val prevAtl = profile.atl
                val prevHrMax = profile.hrMax
                profile = session.updatedProfile

                // Apply CTL/ATL from stored TRIMP score
                val daysSinceLast = if (lastProcessedStartTime != null) {
                    ((workout.startTime - lastProcessedStartTime!!) / 86_400_000L)
                        .toInt().coerceAtLeast(1)
                } else 1
                val trimp = metricsByWorkout[workout.id]?.trimpScore
                val loadResult = FitnessLoadCalculator.updateLoads(
                    currentCtl = prevCtl,
                    currentAtl = prevAtl,
                    trimpScore = if (trimp != null && trimp > 0f) trimp else 0f,
                    daysSinceLast = daysSinceLast
                )
                profile = profile.copy(ctl = loadResult.ctl, atl = loadResult.atl)

                // hrMax: track highest reliably observed HR across sessions
                val sessionMax = points.maxOfOrNull { it.heartRate } ?: 0
                val currentMax = prevHrMax ?: 0
                if (sessionMax > currentMax) {
                    profile = profile.copy(hrMax = sessionMax, hrMaxIsCalibrated = false)
                } else if (currentMax > 0) {
                    profile = profile.copy(hrMax = currentMax)
                }

                // Submaximal HRmax inference (first 3 qualifying workouts)
                if (age != null && profile.totalSessions <= 3 && !profile.hrMaxIsCalibrated) {
                    val hrSamples = points.sortedBy { it.timestamp }.map {
                        HrSample(it.timestamp, it.heartRate)
                    }
                    val currentMax = profile.hrMax ?: (220 - age)
                    val subMaxEstimate = SubMaxHrEstimator.estimate(
                        samples = hrSamples,
                        currentHrMax = currentMax,
                        age = age,
                        isCalibrated = profile.hrMaxIsCalibrated
                    )
                    if (subMaxEstimate != null) {
                        profile = profile.copy(hrMax = subMaxEstimate)
                    }
                }

                // hrRest: derive from track points using the same lower-wins proxy as the service
                val restProxy = MetricsCalculator.computeRestingHrProxy(points.sortedBy { it.timestamp })
                if (restProxy != null) {
                    val updatedRest = HrCalibrator.updateHrRest(
                        currentHrRest = profile.hrRest ?: restProxy,
                        candidate = restProxy
                    )
                    profile = profile.copy(hrRest = updatedRest)
                }

                lastProcessedStartTime = workout.startTime
            }

            return profile
        }
    }
}
