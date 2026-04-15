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
import kotlinx.coroutines.CancellationException
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
        private const val FULL_BACKUP_TIMEOUT_MS = 120_000L
        const val BACKUP_VERSION = 1

        // Abbreviated track-point keys — kept short to minimise Firebase write cost.
        // MUST stay in sync with CloudRestoreManager.TpKeys.
        object TpKeys {
            const val ID   = "id"
            const val TS   = "ts"
            const val LAT  = "lat"
            const val LNG  = "lng"
            const val HR   = "hr"
            const val DIST = "dist"
            const val ALT  = "alt"
        }
    }

    fun isBackupEnabled(): Boolean = authManager.isGoogleLinked()

    // ── Profile ─────────────────────────────────────────────────────────

    /**
     * Returns true on success, false on any non-cancellation failure. External
     * callers can safely ignore the return value; [performFullBackup] uses it
     * to decide whether to stamp the backup as complete.
     */
    suspend fun syncProfile(): Boolean {
        if (!isBackupEnabled()) return false
        return try {
            withTimeout(TIMEOUT_MS) {
                val ref = backupRef() ?: return@withTimeout false
                val uid = authManager.getCurrentUid() ?: return@withTimeout false

                // Snapshot live partner UIDs so they can be re-established after a
                // UID change (e.g. fresh install wipes Firebase auth state).
                val partnerUids = try {
                    db.reference.child("users/$uid/partners").get().await()
                        .children.mapNotNull { it.key }
                        .associateWith { true }
                } catch (e: Exception) {
                    emptyMap<String, Boolean>()
                }

                val data = mapOf(
                    "maxHr"               to userProfileRepo.getMaxHr(),
                    "age"                 to userProfileRepo.getAge(),
                    "weight"              to userProfileRepo.getWeight(),
                    "weightUnit"          to userProfileRepo.getWeightUnit(),
                    "distanceUnit"        to userProfileRepo.getDistanceUnit(),
                    "partnerNudgesEnabled" to userProfileRepo.isPartnerNudgesEnabled(),
                    "partnerUids"         to partnerUids,
                )
                ref.child("profile").setValue(data).await()
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "syncProfile failed", e)
            false
        }
    }

    // ── Settings ────────────────────────────────────────────────────────

    suspend fun syncSettings(): Boolean {
        if (!isBackupEnabled()) return false
        return try {
            withTimeout(TIMEOUT_MS) {
                val ref = backupRef() ?: return@withTimeout false
                val audio = audioSettingsRepo.getAudioSettings()
                val data = mapOf(
                    "earconVolume"          to audio.earconVolume,
                    "voiceVolume"           to audio.voiceVolume,
                    "voiceVerbosity"        to audio.voiceVerbosity.name,
                    "enableVibration"       to audio.enableVibration,
                    "enableHalfwayReminder" to audio.enableHalfwayReminder,
                    "enableKmSplits"        to audio.enableKmSplits,
                    "enableWorkoutComplete" to audio.enableWorkoutComplete,
                    "enableInZoneConfirm"   to audio.enableInZoneConfirm,
                    "autoPauseEnabled"      to autoPauseRepo.isAutoPauseEnabled(),
                    "themeMode"             to themePrefsRepo.getThemeMode().name,
                )
                ref.child("settings").setValue(data).await()
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "syncSettings failed", e)
            false
        }
    }

    // ── Adaptive Profile ────────────────────────────────────────────────

    suspend fun syncAdaptiveProfile(): Boolean {
        if (!isBackupEnabled()) return false
        return try {
            withTimeout(TIMEOUT_MS) {
                val ref = backupRef() ?: return@withTimeout false
                val profile = adaptiveProfileRepo.getProfile()
                // Convert Map<Int, PaceHrBucket> keys to strings for Firebase
                val bucketsMap = profile.paceHrBuckets.map { (k, v) ->
                    k.toString() to mapOf("avgHr" to v.avgHr, "sampleCount" to v.sampleCount)
                }.toMap()
                val data = mapOf(
                    "longTermHrTrimBpm"    to profile.longTermHrTrimBpm,
                    "responseLagSec"       to profile.responseLagSec,
                    "paceHrBuckets"        to bucketsMap,
                    "totalSessions"        to profile.totalSessions,
                    "ctl"                  to profile.ctl,
                    "atl"                  to profile.atl,
                    "hrMax"                to profile.hrMax,
                    "hrMaxIsCalibrated"    to profile.hrMaxIsCalibrated,
                    "hrMaxCalibratedAtMs"  to profile.hrMaxCalibratedAtMs,
                    "hrRest"               to profile.hrRest,
                    "lastTuningDirection"  to profile.lastTuningDirection?.name,
                )
                ref.child("adaptive").setValue(data).await()
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "syncAdaptiveProfile failed", e)
            false
        }
    }

    // ── Workout + TrackPoints + Metrics (single atomic write) ───────────

    suspend fun syncWorkout(
        workout: WorkoutEntity,
        trackPoints: List<TrackPointEntity>,
        metrics: WorkoutMetricsEntity?,
    ): Boolean {
        if (!isBackupEnabled()) return false
        return try {
            withTimeout(TIMEOUT_MS) {
                val ref = backupRef() ?: return@withTimeout false
                val id = workout.id.toString()

                val updates = mutableMapOf<String, Any?>()

                updates["workouts/$id"] = mapOf(
                    "id"                  to workout.id,
                    "startTime"           to workout.startTime,
                    "endTime"             to workout.endTime,
                    "totalDistanceMeters" to workout.totalDistanceMeters,
                    "mode"                to workout.mode,
                    "targetConfig"        to workout.targetConfig,
                    "isSimulated"         to workout.isSimulated,
                )

                // Abbreviated keys reduce payload size per track point
                val pointsList = trackPoints.map { tp ->
                    mapOf(
                        TpKeys.ID   to tp.id,
                        TpKeys.TS   to tp.timestamp,
                        TpKeys.LAT  to tp.latitude,
                        TpKeys.LNG  to tp.longitude,
                        TpKeys.HR   to tp.heartRate,
                        TpKeys.DIST to tp.distanceMeters,
                        TpKeys.ALT  to tp.altitudeMeters,
                    )
                }
                updates["trackPoints/$id"] = pointsList

                if (metrics != null) {
                    updates["metrics/$id"] = mapOf(
                        "workoutId"             to metrics.workoutId,
                        "recordedAtMs"          to metrics.recordedAtMs,
                        "avgPaceMinPerKm"        to metrics.avgPaceMinPerKm,
                        "avgHr"                 to metrics.avgHr,
                        "hrAtSixMinPerKm"        to metrics.hrAtSixMinPerKm,
                        "settleDownSec"          to metrics.settleDownSec,
                        "settleUpSec"            to metrics.settleUpSec,
                        "longTermHrTrimBpm"      to metrics.longTermHrTrimBpm,
                        "responseLagSec"         to metrics.responseLagSec,
                        "efficiencyFactor"       to metrics.efficiencyFactor,
                        "aerobicDecoupling"      to metrics.aerobicDecoupling,
                        "efFirstHalf"            to metrics.efFirstHalf,
                        "efSecondHalf"           to metrics.efSecondHalf,
                        "heartbeatsPerKm"        to metrics.heartbeatsPerKm,
                        "paceAtRefHrMinPerKm"    to metrics.paceAtRefHrMinPerKm,
                        "hrr1Bpm"                to metrics.hrr1Bpm,
                        "trimpScore"             to metrics.trimpScore,
                        "trimpReliable"          to metrics.trimpReliable,
                        "environmentAffected"    to metrics.environmentAffected,
                    )
                }

                ref.updateChildren(updates).await()
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "syncWorkout failed for id=${workout.id}", e)
            false
        }
    }

    // ── Bootcamp ────────────────────────────────────────────────────────

    suspend fun syncBootcampEnrollment(enrollment: BootcampEnrollmentEntity): Boolean {
        if (!isBackupEnabled()) return false
        return try {
            withTimeout(TIMEOUT_MS) {
                val ref = backupRef() ?: return@withTimeout false
                val data = mapOf(
                    "id"                          to enrollment.id,
                    "goalType"                    to enrollment.goalType,
                    "targetMinutesPerRun"         to enrollment.targetMinutesPerRun,
                    "runsPerWeek"                 to enrollment.runsPerWeek,
                    "preferredDays"               to BootcampEnrollmentEntity.serializeDayPreferences(enrollment.preferredDays),
                    "startDate"                   to enrollment.startDate,
                    "currentPhaseIndex"           to enrollment.currentPhaseIndex,
                    "currentWeekInPhase"          to enrollment.currentWeekInPhase,
                    "status"                      to enrollment.status,
                    "tierIndex"                   to enrollment.tierIndex,
                    "tierPromptSnoozedUntilMs"    to enrollment.tierPromptSnoozedUntilMs,
                    "tierPromptDismissCount"      to enrollment.tierPromptDismissCount,
                    "illnessPromptSnoozedUntilMs" to enrollment.illnessPromptSnoozedUntilMs,
                    "pausedAtMs"                  to enrollment.pausedAtMs,
                    "targetFinishingTimeMinutes"  to enrollment.targetFinishingTimeMinutes,
                    "lastTierChangeWeek"          to enrollment.lastTierChangeWeek,
                )
                ref.child("bootcamp/enrollment").setValue(data).await()
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "syncBootcampEnrollment failed", e)
            false
        }
    }

    suspend fun syncBootcampSession(session: BootcampSessionEntity): Boolean {
        if (!isBackupEnabled()) return false
        return try {
            withTimeout(TIMEOUT_MS) {
                val ref = backupRef() ?: return@withTimeout false
                val id = session.id.toString()
                val data = mapOf(
                    "id"                to session.id,
                    "enrollmentId"      to session.enrollmentId,
                    "weekNumber"        to session.weekNumber,
                    "dayOfWeek"         to session.dayOfWeek,
                    "sessionType"       to session.sessionType,
                    "targetMinutes"     to session.targetMinutes,
                    "presetId"          to session.presetId,
                    "status"            to session.status,
                    "completedWorkoutId" to session.completedWorkoutId,
                    "presetIndex"       to session.presetIndex,
                    "completedAtMs"     to session.completedAtMs,
                )
                ref.child("bootcamp/sessions/$id").setValue(data).await()
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "syncBootcampSession failed for id=${session.id}", e)
            false
        }
    }

    // ── Achievement ─────────────────────────────────────────────────────

    suspend fun syncAchievement(achievement: AchievementEntity): Boolean {
        if (!isBackupEnabled()) return false
        return try {
            withTimeout(TIMEOUT_MS) {
                val ref = backupRef() ?: return@withTimeout false
                val id = achievement.id.toString()
                val data = mapOf(
                    "id"               to achievement.id,
                    "type"             to achievement.type,
                    "milestone"        to achievement.milestone,
                    "goal"             to achievement.goal,
                    "tier"             to achievement.tier,
                    "prestigeLevel"    to achievement.prestigeLevel,
                    "earnedAtMs"       to achievement.earnedAtMs,
                    "triggerWorkoutId" to achievement.triggerWorkoutId,
                    "shown"            to achievement.shown,
                )
                ref.child("achievements/$id").setValue(data).await()
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "syncAchievement failed for id=${achievement.id}", e)
            false
        }
    }

    // ── Full Backup ─────────────────────────────────────────────────────
    //
    // Uses a single outer timeout (2 minutes) instead of relying on N individual
    // 10-second timeouts. Writes a "backupComplete" marker only AFTER all data
    // has been written — so a partial backup is never mistaken for a complete one.

    suspend fun performFullBackup(
        workouts: List<WorkoutEntity>,
        trackPointsByWorkout: Map<Long, List<TrackPointEntity>>,
        metrics: List<WorkoutMetricsEntity>,
        enrollment: BootcampEnrollmentEntity?,
        sessions: List<BootcampSessionEntity>,
        achievements: List<AchievementEntity>,
    ) {
        if (!isBackupEnabled()) return

        var failureCount = 0
        try {
            withTimeout(FULL_BACKUP_TIMEOUT_MS) {
                if (!syncProfile()) failureCount++
                if (!syncSettings()) failureCount++
                if (!syncAdaptiveProfile()) failureCount++

                val metricsById = metrics.associateBy { it.workoutId }
                for (workout in workouts) {
                    val points = trackPointsByWorkout[workout.id] ?: emptyList()
                    if (!syncWorkout(workout, points, metricsById[workout.id])) failureCount++
                }

                if (enrollment != null) {
                    if (!syncBootcampEnrollment(enrollment)) failureCount++
                    for (session in sessions) {
                        if (!syncBootcampSession(session)) failureCount++
                    }
                }

                for (achievement in achievements) {
                    if (!syncAchievement(achievement)) failureCount++
                }

                // Only stamp the backup as complete if EVERY individual sync succeeded.
                // Per-entity sync functions swallow their own exceptions (by design, so
                // incremental callers aren't disrupted), so the outer catch below only
                // covers the overall timeout and unexpected errors. We rely on the
                // failureCount guard here to stop a partial backup from advertising as
                // complete via hasCloudBackup() → backupComplete=true.
                if (failureCount == 0) {
                    stampComplete()
                } else {
                    Log.w(TAG, "performFullBackup: $failureCount sub-sync(s) failed; NOT stamping backupComplete")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "performFullBackup failed", e)
        }

        Log.d(TAG, "Full backup done: ${workouts.size} workouts, ${sessions.size} sessions, ${achievements.size} achievements, $failureCount failures")
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Writes the backup manifest (version, completeness flag, timestamp).
     * Called once at the end of a full backup — NOT after every individual sync.
     * Individual syncs write their data only; this stamp is the "all done" signal.
     */
    internal suspend fun stampComplete() {
        val ref = backupRef() ?: return
        ref.updateChildren(
            mapOf(
                "version"        to BACKUP_VERSION,
                "backupComplete" to true,
                "lastSyncMs"     to System.currentTimeMillis(),
            )
        ).await()
    }

    private fun backupRef() =
        authManager.getCurrentUid()?.let { db.reference.child("users/$it/backup") }
}
