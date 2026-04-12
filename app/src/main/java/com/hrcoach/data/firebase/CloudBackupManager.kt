package com.hrcoach.data.firebase

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.hrcoach.data.db.AchievementEntity
import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.data.db.TrackPointEntity
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.data.db.WorkoutMetricsEntity
import com.hrcoach.data.repository.AdaptiveProfileRepository
import com.hrcoach.data.repository.AudioSettingsRepository
import com.hrcoach.data.repository.AutoPauseSettingsRepository
import com.hrcoach.data.repository.OnboardingRepository
import com.hrcoach.data.repository.ThemePreferencesRepository
import com.hrcoach.data.repository.UserProfileRepository
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudBackupManager @Inject constructor(
    private val db: FirebaseDatabase,
    private val authManager: FirebaseAuthManager,
    private val userProfileRepo: UserProfileRepository,
    private val audioSettingsRepo: AudioSettingsRepository,
    private val themePrefsRepo: ThemePreferencesRepository,
    private val autoPauseRepo: AutoPauseSettingsRepository,
    private val adaptiveProfileRepo: AdaptiveProfileRepository,
    private val onboardingRepo: OnboardingRepository,
) {
    companion object {
        private const val TAG = "CloudBackupManager"
        private const val TIMEOUT_MS = 10_000L
        private const val BACKUP_VERSION = 1
    }

    fun isBackupEnabled(): Boolean = authManager.isGoogleLinked()

    // ── Profile ─────────────────────────────────────────────────────────

    suspend fun syncProfile() {
        if (!isBackupEnabled()) return
        runCatching {
            withTimeout(TIMEOUT_MS) {
                val ref = backupRef() ?: return@withTimeout
                val data = mapOf(
                    "maxHr" to userProfileRepo.getMaxHr(),
                    "age" to userProfileRepo.getAge(),
                    "weight" to userProfileRepo.getWeight(),
                    "weightUnit" to userProfileRepo.getWeightUnit(),
                    "distanceUnit" to userProfileRepo.getDistanceUnit(),
                    "partnerNudgesEnabled" to userProfileRepo.isPartnerNudgesEnabled(),
                    "onboardingCompleted" to onboardingRepo.isOnboardingCompleted(),
                )
                ref.child("profile").setValue(data).await()
                stampSync()
            }
        }.onFailure { Log.w(TAG, "syncProfile failed", it) }
    }

    // ── Settings ────────────────────────────────────────────────────────

    suspend fun syncSettings() {
        if (!isBackupEnabled()) return
        runCatching {
            withTimeout(TIMEOUT_MS) {
                val ref = backupRef() ?: return@withTimeout
                val audio = audioSettingsRepo.getAudioSettings()
                val data = mapOf(
                    "earconVolume" to audio.earconVolume,
                    "voiceVolume" to audio.voiceVolume,
                    "voiceVerbosity" to audio.voiceVerbosity.name,
                    "enableVibration" to audio.enableVibration,
                    "enableHalfwayReminder" to audio.enableHalfwayReminder,
                    "enableKmSplits" to audio.enableKmSplits,
                    "enableWorkoutComplete" to audio.enableWorkoutComplete,
                    "enableInZoneConfirm" to audio.enableInZoneConfirm,
                    "autoPauseEnabled" to autoPauseRepo.isAutoPauseEnabled(),
                    "themeMode" to themePrefsRepo.getThemeMode().name,
                )
                ref.child("settings").setValue(data).await()
                stampSync()
            }
        }.onFailure { Log.w(TAG, "syncSettings failed", it) }
    }

    // ── Adaptive Profile ────────────────────────────────────────────────

    suspend fun syncAdaptiveProfile() {
        if (!isBackupEnabled()) return
        runCatching {
            withTimeout(TIMEOUT_MS) {
                val ref = backupRef() ?: return@withTimeout
                val profile = adaptiveProfileRepo.getProfile()
                // Convert Map<Int, PaceHrBucket> keys to strings for Firebase
                val bucketsMap = profile.paceHrBuckets.map { (k, v) ->
                    k.toString() to mapOf("avgHr" to v.avgHr, "sampleCount" to v.sampleCount)
                }.toMap()
                val data = mapOf(
                    "longTermHrTrimBpm" to profile.longTermHrTrimBpm,
                    "responseLagSec" to profile.responseLagSec,
                    "paceHrBuckets" to bucketsMap,
                    "totalSessions" to profile.totalSessions,
                    "ctl" to profile.ctl,
                    "atl" to profile.atl,
                    "hrMax" to profile.hrMax,
                    "hrMaxIsCalibrated" to profile.hrMaxIsCalibrated,
                    "hrRest" to profile.hrRest,
                    "lastTuningDirection" to profile.lastTuningDirection?.name,
                )
                ref.child("adaptive").setValue(data).await()
                stampSync()
            }
        }.onFailure { Log.w(TAG, "syncAdaptiveProfile failed", it) }
    }

    // ── Workout + TrackPoints + Metrics (single atomic write) ───────────

    suspend fun syncWorkout(
        workout: WorkoutEntity,
        trackPoints: List<TrackPointEntity>,
        metrics: WorkoutMetricsEntity?,
    ) {
        if (!isBackupEnabled()) return
        runCatching {
            withTimeout(TIMEOUT_MS) {
                val ref = backupRef() ?: return@withTimeout
                val id = workout.id.toString()

                val updates = mutableMapOf<String, Any?>()

                // Workout summary
                updates["workouts/$id"] = mapOf(
                    "id" to workout.id,
                    "startTime" to workout.startTime,
                    "endTime" to workout.endTime,
                    "totalDistanceMeters" to workout.totalDistanceMeters,
                    "mode" to workout.mode,
                    "targetConfig" to workout.targetConfig,
                    "isSimulated" to workout.isSimulated,
                )

                // Track points — abbreviated keys for bandwidth
                val pointsList = trackPoints.map { tp ->
                    mapOf(
                        "id" to tp.id,
                        "ts" to tp.timestamp,
                        "lat" to tp.latitude,
                        "lng" to tp.longitude,
                        "hr" to tp.heartRate,
                        "dist" to tp.distanceMeters,
                        "alt" to tp.altitudeMeters,
                    )
                }
                updates["trackPoints/$id"] = pointsList

                // Metrics
                if (metrics != null) {
                    updates["metrics/$id"] = mapOf(
                        "workoutId" to metrics.workoutId,
                        "recordedAtMs" to metrics.recordedAtMs,
                        "avgPaceMinPerKm" to metrics.avgPaceMinPerKm,
                        "avgHr" to metrics.avgHr,
                        "hrAtSixMinPerKm" to metrics.hrAtSixMinPerKm,
                        "settleDownSec" to metrics.settleDownSec,
                        "settleUpSec" to metrics.settleUpSec,
                        "longTermHrTrimBpm" to metrics.longTermHrTrimBpm,
                        "responseLagSec" to metrics.responseLagSec,
                        "efficiencyFactor" to metrics.efficiencyFactor,
                        "aerobicDecoupling" to metrics.aerobicDecoupling,
                        "efFirstHalf" to metrics.efFirstHalf,
                        "efSecondHalf" to metrics.efSecondHalf,
                        "heartbeatsPerKm" to metrics.heartbeatsPerKm,
                        "paceAtRefHrMinPerKm" to metrics.paceAtRefHrMinPerKm,
                        "hrr1Bpm" to metrics.hrr1Bpm,
                        "trimpScore" to metrics.trimpScore,
                        "trimpReliable" to metrics.trimpReliable,
                        "environmentAffected" to metrics.environmentAffected,
                    )
                }

                ref.updateChildren(updates).await()
                stampSync()
            }
        }.onFailure { Log.w(TAG, "syncWorkout failed for id=${workout.id}", it) }
    }

    // ── Bootcamp ────────────────────────────────────────────────────────

    suspend fun syncBootcampEnrollment(enrollment: BootcampEnrollmentEntity) {
        if (!isBackupEnabled()) return
        runCatching {
            withTimeout(TIMEOUT_MS) {
                val ref = backupRef() ?: return@withTimeout
                val data = mapOf(
                    "id" to enrollment.id,
                    "goalType" to enrollment.goalType,
                    "targetMinutesPerRun" to enrollment.targetMinutesPerRun,
                    "runsPerWeek" to enrollment.runsPerWeek,
                    "preferredDays" to BootcampEnrollmentEntity.serializeDayPreferences(enrollment.preferredDays),
                    "startDate" to enrollment.startDate,
                    "currentPhaseIndex" to enrollment.currentPhaseIndex,
                    "currentWeekInPhase" to enrollment.currentWeekInPhase,
                    "status" to enrollment.status,
                    "tierIndex" to enrollment.tierIndex,
                    "tierPromptSnoozedUntilMs" to enrollment.tierPromptSnoozedUntilMs,
                    "tierPromptDismissCount" to enrollment.tierPromptDismissCount,
                    "illnessPromptSnoozedUntilMs" to enrollment.illnessPromptSnoozedUntilMs,
                    "pausedAtMs" to enrollment.pausedAtMs,
                    "targetFinishingTimeMinutes" to enrollment.targetFinishingTimeMinutes,
                )
                ref.child("bootcamp/enrollment").setValue(data).await()
                stampSync()
            }
        }.onFailure { Log.w(TAG, "syncBootcampEnrollment failed", it) }
    }

    suspend fun syncBootcampSession(session: BootcampSessionEntity) {
        if (!isBackupEnabled()) return
        runCatching {
            withTimeout(TIMEOUT_MS) {
                val ref = backupRef() ?: return@withTimeout
                val id = session.id.toString()
                val data = mapOf(
                    "id" to session.id,
                    "enrollmentId" to session.enrollmentId,
                    "weekNumber" to session.weekNumber,
                    "dayOfWeek" to session.dayOfWeek,
                    "sessionType" to session.sessionType,
                    "targetMinutes" to session.targetMinutes,
                    "presetId" to session.presetId,
                    "status" to session.status,
                    "completedWorkoutId" to session.completedWorkoutId,
                    "presetIndex" to session.presetIndex,
                    "completedAtMs" to session.completedAtMs,
                )
                ref.child("bootcamp/sessions/$id").setValue(data).await()
                stampSync()
            }
        }.onFailure { Log.w(TAG, "syncBootcampSession failed for id=${session.id}", it) }
    }

    // ── Achievement ─────────────────────────────────────────────────────

    suspend fun syncAchievement(achievement: AchievementEntity) {
        if (!isBackupEnabled()) return
        runCatching {
            withTimeout(TIMEOUT_MS) {
                val ref = backupRef() ?: return@withTimeout
                val id = achievement.id.toString()
                val data = mapOf(
                    "id" to achievement.id,
                    "type" to achievement.type,
                    "milestone" to achievement.milestone,
                    "goal" to achievement.goal,
                    "tier" to achievement.tier,
                    "prestigeLevel" to achievement.prestigeLevel,
                    "earnedAtMs" to achievement.earnedAtMs,
                    "triggerWorkoutId" to achievement.triggerWorkoutId,
                    "shown" to achievement.shown,
                )
                ref.child("achievements/$id").setValue(data).await()
                stampSync()
            }
        }.onFailure { Log.w(TAG, "syncAchievement failed for id=${achievement.id}", it) }
    }

    // ── Full Backup ─────────────────────────────────────────────────────

    suspend fun performFullBackup(
        workouts: List<WorkoutEntity>,
        trackPointsByWorkout: Map<Long, List<TrackPointEntity>>,
        metrics: List<WorkoutMetricsEntity>,
        enrollment: BootcampEnrollmentEntity?,
        sessions: List<BootcampSessionEntity>,
        achievements: List<AchievementEntity>,
    ) {
        if (!isBackupEnabled()) return

        // Write version marker first
        runCatching {
            withTimeout(TIMEOUT_MS) {
                backupRef()?.child("version")?.setValue(BACKUP_VERSION)?.await()
            }
        }.onFailure { Log.w(TAG, "Failed to write backup version", it) }

        syncProfile()
        syncSettings()
        syncAdaptiveProfile()

        val metricsById = metrics.associateBy { it.workoutId }
        for (workout in workouts) {
            val points = trackPointsByWorkout[workout.id] ?: emptyList()
            syncWorkout(workout, points, metricsById[workout.id])
        }

        if (enrollment != null) {
            syncBootcampEnrollment(enrollment)
            for (session in sessions) {
                syncBootcampSession(session)
            }
        }

        for (achievement in achievements) {
            syncAchievement(achievement)
        }

        Log.d(TAG, "Full backup complete: ${workouts.size} workouts, ${sessions.size} sessions, ${achievements.size} achievements")
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Writes version marker and lastSyncMs timestamp. Called by every incremental sync. */
    private suspend fun stampSync() {
        val ref = backupRef() ?: return
        ref.updateChildren(
            mapOf(
                "version" to BACKUP_VERSION,
                "lastSyncMs" to System.currentTimeMillis(),
            )
        ).await()
    }

    private fun backupRef() =
        authManager.getCurrentUid()?.let { db.reference.child("users/$it/backup") }
}
