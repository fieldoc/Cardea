package com.hrcoach.data.firebase

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CancellationException
import com.hrcoach.data.db.AchievementDao
import com.hrcoach.data.db.AchievementEntity
import androidx.room.withTransaction
import com.hrcoach.data.db.AppDatabase
import com.hrcoach.data.db.BootcampDao
import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.data.db.TrackPointDao
import com.hrcoach.data.db.TrackPointEntity
import com.hrcoach.data.db.WorkoutDao
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.data.db.WorkoutMetricsDao
import com.hrcoach.data.db.WorkoutMetricsEntity
import com.hrcoach.data.firebase.CloudBackupManager.Companion.BACKUP_VERSION
import com.hrcoach.data.firebase.CloudBackupManager.Companion.TpKeys
import com.hrcoach.data.repository.AdaptiveProfileRepository
import com.hrcoach.data.repository.AudioSettingsRepository
import com.hrcoach.data.repository.AutoPauseSettingsRepository
import com.hrcoach.data.repository.OnboardingRepository
import com.hrcoach.data.repository.ThemePreferencesRepository
import com.hrcoach.data.repository.UserProfileRepository
import com.hrcoach.domain.engine.TuningDirection
import com.hrcoach.domain.model.AdaptiveProfile
import com.hrcoach.domain.model.AudioSettings
import com.hrcoach.domain.model.PaceHrBucket
import com.hrcoach.domain.model.ThemeMode
import com.hrcoach.domain.model.VoiceVerbosity
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

data class RestoreResult(
    val workoutCount: Int,
    val sessionCount: Int,
    val achievementCount: Int,
)

@Singleton
class CloudRestoreManager @Inject constructor(
    private val db: FirebaseDatabase,
    private val authManager: FirebaseAuthManager,
    private val userProfileRepo: UserProfileRepository,
    private val audioSettingsRepo: AudioSettingsRepository,
    private val themePrefsRepo: ThemePreferencesRepository,
    private val autoPauseRepo: AutoPauseSettingsRepository,
    private val adaptiveProfileRepo: AdaptiveProfileRepository,
    private val onboardingRepo: OnboardingRepository,
    private val roomDb: AppDatabase,
    private val workoutDao: WorkoutDao,
    private val trackPointDao: TrackPointDao,
    private val workoutMetricsDao: WorkoutMetricsDao,
    private val bootcampDao: BootcampDao,
    private val achievementDao: AchievementDao,
) {
    companion object {
        private const val TAG = "CloudRestoreManager"
        private const val TIMEOUT_MS = 30_000L
    }

    /** Checks if a complete cloud backup exists for the current user. */
    suspend fun hasCloudBackup(): Boolean {
        val uid = authManager.getCurrentUid() ?: return false
        return runCatching {
            withTimeout(TIMEOUT_MS) {
                val snap = db.reference
                    .child("users/$uid/backup")
                    .get()
                    .await()
                // Require the explicit "backupComplete" stamp. A bare "version" node
                // is not sufficient — it can be left behind by a partial or aborted
                // backup and would cause a fresh install to restore a truncated snapshot.
                snap.child("backupComplete").getValue(Boolean::class.java) == true
            }
        }.getOrElse { false }
    }

    /**
     * Returns true when Google is linked, local DB has no real workouts (simulated
     * excluded), onboarding is not complete, and a cloud backup exists.
     * Used to trigger auto-restore on a new device.
     */
    suspend fun needsRestore(): Boolean {
        if (!authManager.isGoogleLinked()) return false
        if (onboardingRepo.isOnboardingCompleted()) return false
        val localCount = workoutDao.getWorkoutCount()   // counts non-simulated only (C1 fix)
        if (localCount > 0) return false
        return hasCloudBackup()
    }

    /**
     * Reads the entire backup snapshot and populates all local stores.
     *
     * - Rejects backups from a newer schema version than this client supports (I4).
     * - SharedPrefs restores (profile, settings, adaptive) happen before the Room
     *   transaction; on catastrophic Room failure those changes are already committed,
     *   but workout history is lost in that scenario regardless.
     * - Room inserts are wrapped in a transaction — partial entity failure rolls back
     *   all DB changes cleanly.
     * - Returns a [RestoreResult] with counts, or throws on unrecoverable failure.
     */
    suspend fun restore(): RestoreResult {
        val uid = authManager.getCurrentUid()
            ?: throw IllegalStateException("Must be signed in to restore")

        val snapshot = withTimeout(TIMEOUT_MS) {
            db.reference.child("users/$uid/backup").get().await()
        }

        if (!snapshot.exists()) throw IllegalStateException("No backup found")

        // I4: Reject backups from a schema version this client can't read.
        val backupVersion = snapshot.child("version").getValue(Int::class.java) ?: 0
        if (backupVersion > BACKUP_VERSION) {
            throw IllegalStateException(
                "Backup was created by a newer version of Cardea (schema v$backupVersion). " +
                "Please update the app and try again."
            )
        }

        // SharedPrefs-based restores (outside Room transaction)
        restoreProfile(snapshot.child("profile"))
        restoreSettings(snapshot.child("settings"))
        restoreAdaptiveProfile(snapshot.child("adaptive"))
        restorePartnerLinks(snapshot.child("profile"))

        // Room inserts — atomic transaction so partial failure rolls back cleanly
        data class Counts(val workouts: Int, val sessions: Int, val achievements: Int)
        val counts = roomDb.withTransaction {
            val (w, restoredIds) = restoreWorkouts(snapshot.child("workouts"))
            restoreTrackPoints(snapshot.child("trackPoints"), restoredIds)
            restoreMetrics(snapshot.child("metrics"))
            val s = restoreBootcamp(snapshot.child("bootcamp"), restoredIds)
            val a = restoreAchievements(snapshot.child("achievements"))
            Counts(w, s, a)
        }

        // Mark onboarding complete after successful restore
        runCatching { onboardingRepo.setOnboardingCompleted() }
            .onFailure { Log.w(TAG, "Failed to mark onboarding complete", it) }

        Log.d(TAG, "Restore complete: ${counts.workouts} workouts, ${counts.sessions} sessions, ${counts.achievements} achievements")
        return RestoreResult(counts.workouts, counts.sessions, counts.achievements)
    }

    // ── Profile ─────────────────────────────────────────────────────────

    /**
     * Re-establishes bidirectional partner links after a UID change (e.g. fresh install
     * wiped Firebase auth state). Reads partner UIDs backed up in [syncProfile] and
     * writes mutual links via [FirebaseDatabase.updateChildren].
     *
     * Firebase security rules allow this because `auth.uid === $partnerId` is satisfied
     * when the current user writes to another user's `partners/<currentUid>` node.
     * No-op when the profile snapshot has no partnerUids node.
     */
    private suspend fun restorePartnerLinks(profileSnap: DataSnapshot) {
        if (!profileSnap.exists()) return
        val uid = authManager.getCurrentUid() ?: return
        try {
            val partnerUids = profileSnap.child("partnerUids").children.mapNotNull { it.key }
            if (partnerUids.isEmpty()) return
            val updates = mutableMapOf<String, Any?>()
            for (partnerUid in partnerUids) {
                updates["users/$uid/partners/$partnerUid"] = true
                updates["users/$partnerUid/partners/$uid"] = true
            }
            withTimeout(10_000) { db.reference.updateChildren(updates).await() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "restorePartnerLinks failed", e)
        }
    }

    private fun restoreProfile(snap: DataSnapshot) {
        if (!snap.exists()) return

        runCatching {
            snap.child("maxHr").getValue(Int::class.java)?.let { maxHr ->
                // C3: hrMax must stay in sync across both stores (dual-store invariant).
                userProfileRepo.setMaxHr(maxHr)
                // restoreAdaptiveProfile() runs next and will overwrite if its node exists;
                // this ensures AdaptiveProfileRepository.hrMax is set even when "adaptive"
                // node is absent from the backup (e.g. user never completed a session).
                val existing = adaptiveProfileRepo.getProfile()
                adaptiveProfileRepo.saveProfile(existing.copy(hrMax = maxHr))
            }
        }.onFailure { Log.w(TAG, "restoreProfile: maxHr failed", it) }

        runCatching {
            snap.child("age").getValue(Int::class.java)?.let { userProfileRepo.setAge(it) }
        }.onFailure { Log.w(TAG, "restoreProfile: age failed", it) }

        runCatching {
            snap.child("weight").getValue(Int::class.java)?.let { userProfileRepo.setWeight(it) }
        }.onFailure { Log.w(TAG, "restoreProfile: weight failed", it) }

        runCatching {
            snap.child("weightUnit").getValue(String::class.java)?.let { userProfileRepo.setWeightUnit(it) }
        }.onFailure { Log.w(TAG, "restoreProfile: weightUnit failed", it) }

        runCatching {
            snap.child("distanceUnit").getValue(String::class.java)?.let { userProfileRepo.setDistanceUnit(it) }
        }.onFailure { Log.w(TAG, "restoreProfile: distanceUnit failed", it) }

        runCatching {
            snap.child("partnerNudgesEnabled").getValue(Boolean::class.java)?.let {
                userProfileRepo.setPartnerNudgesEnabled(it)
            }
        }.onFailure { Log.w(TAG, "restoreProfile: partnerNudgesEnabled failed", it) }
    }

    // ── Settings ────────────────────────────────────────────────────────

    private fun restoreSettings(snap: DataSnapshot) {
        if (!snap.exists()) return

        runCatching {
            val audio = AudioSettings(
                earconVolume          = snap.child("earconVolume").getValue(Int::class.java) ?: 80,
                voiceVolume           = snap.child("voiceVolume").getValue(Int::class.java) ?: 80,
                voiceVerbosity        = snap.child("voiceVerbosity").getValue(String::class.java)?.let {
                    runCatching { VoiceVerbosity.valueOf(it) }.getOrNull()
                } ?: VoiceVerbosity.MINIMAL,
                enableVibration       = snap.child("enableVibration").getValue(Boolean::class.java) ?: true,
                enableHalfwayReminder = snap.child("enableHalfwayReminder").getValue(Boolean::class.java),
                enableKmSplits        = snap.child("enableKmSplits").getValue(Boolean::class.java),
                enableWorkoutComplete = snap.child("enableWorkoutComplete").getValue(Boolean::class.java),
                enableInZoneConfirm   = snap.child("enableInZoneConfirm").getValue(Boolean::class.java),
            )
            audioSettingsRepo.saveAudioSettings(audio)
        }.onFailure { Log.w(TAG, "restoreSettings: audio failed", it) }

        runCatching {
            snap.child("autoPauseEnabled").getValue(Boolean::class.java)?.let {
                autoPauseRepo.setAutoPauseEnabled(it)
            }
        }.onFailure { Log.w(TAG, "restoreSettings: autoPause failed", it) }

        runCatching {
            snap.child("themeMode").getValue(String::class.java)?.let {
                val mode = runCatching { ThemeMode.valueOf(it) }.getOrElse { ThemeMode.SYSTEM }
                themePrefsRepo.setThemeMode(mode)
            }
        }.onFailure { Log.w(TAG, "restoreSettings: themeMode failed", it) }
    }

    // ── Adaptive Profile ────────────────────────────────────────────────

    private fun restoreAdaptiveProfile(snap: DataSnapshot) {
        if (!snap.exists()) return
        runCatching {
            val bucketsSnap = snap.child("paceHrBuckets")
            val buckets = mutableMapOf<Int, PaceHrBucket>()
            for (entry in bucketsSnap.children) {
                val key = entry.key?.toIntOrNull() ?: continue
                val avgHr       = entry.child("avgHr").getValue(Float::class.java) ?: 0f
                val sampleCount = entry.child("sampleCount").getValue(Int::class.java) ?: 0
                buckets[key] = PaceHrBucket(avgHr, sampleCount)
            }

            val tuningStr = snap.child("lastTuningDirection").getValue(String::class.java)
            val tuning = tuningStr?.let { runCatching { TuningDirection.valueOf(it) }.getOrNull() }

            val profile = AdaptiveProfile(
                longTermHrTrimBpm  = snap.child("longTermHrTrimBpm").getValue(Float::class.java) ?: 0f,
                responseLagSec     = snap.child("responseLagSec").getValue(Float::class.java) ?: 25f,
                paceHrBuckets      = buckets,
                totalSessions      = snap.child("totalSessions").getValue(Int::class.java) ?: 0,
                ctl                = snap.child("ctl").getValue(Float::class.java) ?: 0f,
                atl                = snap.child("atl").getValue(Float::class.java) ?: 0f,
                hrMax              = snap.child("hrMax").getValue(Int::class.java),
                hrMaxIsCalibrated  = snap.child("hrMaxIsCalibrated").getValue(Boolean::class.java) ?: false,
                hrMaxCalibratedAtMs = snap.child("hrMaxCalibratedAtMs").getValue(Long::class.java),
                hrRest             = snap.child("hrRest").getValue(Float::class.java),
                lastTuningDirection = tuning,
            )
            adaptiveProfileRepo.saveProfile(profile)
        }.onFailure { Log.w(TAG, "restoreAdaptiveProfile failed", it) }
    }

    // ── Workouts ────────────────────────────────────────────────────────
    //
    // Returns (count, set of successfully restored workout IDs) so that
    // restoreTrackPoints and restoreBootcamp can skip FK-broken entries.

    private suspend fun restoreWorkouts(snap: DataSnapshot): Pair<Int, Set<Long>> {
        if (!snap.exists()) return 0 to emptySet()
        var count = 0
        val restoredIds = mutableSetOf<Long>()
        for (child in snap.children) {
            runCatching {
                val id = child.child("id").getValue(Long::class.java) ?: 0L
                // I6: A workout with id=0 in Firebase indicates a corrupted backup entry.
                // Skip it — its track points (written under key "0") would FK-violate anyway.
                if (id == 0L) {
                    Log.w(TAG, "restoreWorkout: skipping entry with id=0 (corrupted backup)")
                    return@runCatching
                }
                val entity = WorkoutEntity(
                    id                   = id,
                    startTime            = child.child("startTime").getValue(Long::class.java) ?: 0L,
                    endTime              = child.child("endTime").getValue(Long::class.java) ?: 0L,
                    totalDistanceMeters  = child.child("totalDistanceMeters").getValue(Float::class.java) ?: 0f,
                    mode                 = child.child("mode").getValue(String::class.java) ?: "FREE_RUN",
                    targetConfig         = child.child("targetConfig").getValue(String::class.java) ?: "{}",
                    isSimulated           = child.child("isSimulated").getValue(Boolean::class.java) ?: false,
                    activeDurationSeconds = child.child("activeDurationSeconds").getValue(Long::class.java) ?: 0L,
                )
                workoutDao.upsert(entity)
                restoredIds.add(id)
                count++
            }.onFailure { Log.w(TAG, "restoreWorkout failed for key=${child.key}", it) }
        }
        return count to restoredIds
    }

    // ── Track Points ────────────────────────────────────────────────────

    private suspend fun restoreTrackPoints(snap: DataSnapshot, restoredWorkoutIds: Set<Long>) {
        if (!snap.exists()) return
        for (workoutSnap in snap.children) {
            val workoutId = workoutSnap.key?.toLongOrNull() ?: continue
            // I8 (track points): skip if the parent workout wasn't restored — FK would fail.
            if (workoutId !in restoredWorkoutIds) {
                Log.w(TAG, "restoreTrackPoints: skipping workout $workoutId (not in restored set)")
                continue
            }
            for (pointSnap in workoutSnap.children) {
                runCatching {
                    val entity = TrackPointEntity(
                        id             = pointSnap.child(TpKeys.ID).getValue(Long::class.java) ?: 0L,
                        workoutId      = workoutId,
                        timestamp      = pointSnap.child(TpKeys.TS).getValue(Long::class.java) ?: 0L,
                        latitude       = pointSnap.child(TpKeys.LAT).getValue(Double::class.java) ?: 0.0,
                        longitude      = pointSnap.child(TpKeys.LNG).getValue(Double::class.java) ?: 0.0,
                        heartRate      = pointSnap.child(TpKeys.HR).getValue(Int::class.java) ?: 0,
                        distanceMeters = pointSnap.child(TpKeys.DIST).getValue(Float::class.java) ?: 0f,
                        altitudeMeters = pointSnap.child(TpKeys.ALT).getValue(Double::class.java),
                    )
                    trackPointDao.upsert(entity)
                }.onFailure { Log.w(TAG, "restoreTrackPoint failed for workout=$workoutId", it) }
            }
        }
    }

    // ── Metrics ─────────────────────────────────────────────────────────

    private suspend fun restoreMetrics(snap: DataSnapshot) {
        if (!snap.exists()) return
        for (child in snap.children) {
            val workoutId = child.child("workoutId").getValue(Long::class.java) ?: continue
            runCatching {
                val entity = WorkoutMetricsEntity(
                    workoutId            = workoutId,
                    recordedAtMs         = child.child("recordedAtMs").getValue(Long::class.java) ?: 0L,
                    avgPaceMinPerKm      = child.child("avgPaceMinPerKm").getValue(Float::class.java),
                    avgHr                = child.child("avgHr").getValue(Float::class.java),
                    hrAtSixMinPerKm      = child.child("hrAtSixMinPerKm").getValue(Float::class.java),
                    settleDownSec        = child.child("settleDownSec").getValue(Float::class.java),
                    settleUpSec          = child.child("settleUpSec").getValue(Float::class.java),
                    longTermHrTrimBpm    = child.child("longTermHrTrimBpm").getValue(Float::class.java) ?: 0f,
                    responseLagSec       = child.child("responseLagSec").getValue(Float::class.java) ?: 25f,
                    efficiencyFactor     = child.child("efficiencyFactor").getValue(Float::class.java),
                    aerobicDecoupling    = child.child("aerobicDecoupling").getValue(Float::class.java),
                    efFirstHalf          = child.child("efFirstHalf").getValue(Float::class.java),
                    efSecondHalf         = child.child("efSecondHalf").getValue(Float::class.java),
                    heartbeatsPerKm      = child.child("heartbeatsPerKm").getValue(Float::class.java),
                    paceAtRefHrMinPerKm  = child.child("paceAtRefHrMinPerKm").getValue(Float::class.java),
                    hrr1Bpm              = child.child("hrr1Bpm").getValue(Float::class.java),
                    trimpScore           = child.child("trimpScore").getValue(Float::class.java),
                    trimpReliable        = child.child("trimpReliable").getValue(Boolean::class.java) ?: true,
                    environmentAffected  = child.child("environmentAffected").getValue(Boolean::class.java) ?: false,
                )
                workoutMetricsDao.upsert(entity)
            }.onFailure { Log.w(TAG, "restoreMetrics failed for key=${child.key}", it) }
        }
    }

    // ── Bootcamp ────────────────────────────────────────────────────────

    private suspend fun restoreBootcamp(snap: DataSnapshot, restoredWorkoutIds: Set<Long>): Int {
        if (!snap.exists()) return 0

        // Enrollment — if this fails, skip sessions entirely (they'd all FK-violate)
        val enrollmentSnap = snap.child("enrollment")
        var enrollmentRestored = false
        if (enrollmentSnap.exists()) {
            // I2: Capture enrollment failure explicitly so we can abort session restore.
            val enrollmentResult = runCatching {
                val preferredDaysRaw = enrollmentSnap.child("preferredDays").getValue(String::class.java) ?: ""
                val enrollment = BootcampEnrollmentEntity(
                    id                          = enrollmentSnap.child("id").getValue(Long::class.java) ?: 0L,
                    goalType                    = enrollmentSnap.child("goalType").getValue(String::class.java) ?: "",
                    targetMinutesPerRun         = enrollmentSnap.child("targetMinutesPerRun").getValue(Int::class.java) ?: 30,
                    runsPerWeek                 = enrollmentSnap.child("runsPerWeek").getValue(Int::class.java) ?: 3,
                    preferredDays               = BootcampEnrollmentEntity.parseDayPreferences(preferredDaysRaw),
                    startDate                   = enrollmentSnap.child("startDate").getValue(Long::class.java) ?: 0L,
                    currentPhaseIndex           = enrollmentSnap.child("currentPhaseIndex").getValue(Int::class.java) ?: 0,
                    currentWeekInPhase          = enrollmentSnap.child("currentWeekInPhase").getValue(Int::class.java) ?: 0,
                    status                      = enrollmentSnap.child("status").getValue(String::class.java) ?: BootcampEnrollmentEntity.STATUS_ACTIVE,
                    tierIndex                   = enrollmentSnap.child("tierIndex").getValue(Int::class.java) ?: 0,
                    tierPromptSnoozedUntilMs    = enrollmentSnap.child("tierPromptSnoozedUntilMs").getValue(Long::class.java) ?: 0,
                    tierPromptDismissCount      = enrollmentSnap.child("tierPromptDismissCount").getValue(Int::class.java) ?: 0,
                    illnessPromptSnoozedUntilMs = enrollmentSnap.child("illnessPromptSnoozedUntilMs").getValue(Long::class.java) ?: 0,
                    pausedAtMs                  = enrollmentSnap.child("pausedAtMs").getValue(Long::class.java) ?: 0,
                    targetFinishingTimeMinutes  = enrollmentSnap.child("targetFinishingTimeMinutes").getValue(Int::class.java),
                    lastTierChangeWeek          = enrollmentSnap.child("lastTierChangeWeek").getValue(Long::class.java)?.toInt(),
                )
                bootcampDao.upsertEnrollment(enrollment)
            }
            if (enrollmentResult.isSuccess) {
                enrollmentRestored = true
            } else {
                Log.w(TAG, "restoreBootcamp: enrollment failed — skipping sessions to avoid FK violation", enrollmentResult.exceptionOrNull())
            }
        }

        if (!enrollmentRestored) return 0

        // Sessions
        val sessionsSnap = snap.child("sessions")
        var sessionCount = 0
        for (child in sessionsSnap.children) {
            runCatching {
                val completedWorkoutId = child.child("completedWorkoutId").getValue(Long::class.java)
                // I8: If the linked workout wasn't restored, null out the FK rather than
                // failing the session insert. Session status is preserved; history link is lost.
                val safeCompletedWorkoutId = if (completedWorkoutId != null && completedWorkoutId !in restoredWorkoutIds) {
                    Log.w(TAG, "restoreBootcamp: session ${child.key} completedWorkoutId=$completedWorkoutId not in restored set — clearing FK")
                    null
                } else {
                    completedWorkoutId
                }
                val session = BootcampSessionEntity(
                    id                = child.child("id").getValue(Long::class.java) ?: 0L,
                    enrollmentId      = child.child("enrollmentId").getValue(Long::class.java) ?: 0L,
                    weekNumber        = child.child("weekNumber").getValue(Int::class.java) ?: 0,
                    dayOfWeek         = child.child("dayOfWeek").getValue(Int::class.java) ?: 0,
                    sessionType       = child.child("sessionType").getValue(String::class.java) ?: "",
                    targetMinutes     = child.child("targetMinutes").getValue(Int::class.java) ?: 30,
                    presetId          = child.child("presetId").getValue(String::class.java),
                    status            = child.child("status").getValue(String::class.java) ?: BootcampSessionEntity.STATUS_SCHEDULED,
                    completedWorkoutId = safeCompletedWorkoutId,
                    presetIndex       = child.child("presetIndex").getValue(Int::class.java),
                    completedAtMs     = child.child("completedAtMs").getValue(Long::class.java),
                )
                bootcampDao.upsertSession(session)
                sessionCount++
            }.onFailure { Log.w(TAG, "restoreBootcamp: session failed for key=${child.key}", it) }
        }
        return sessionCount
    }

    // ── Achievements ────────────────────────────────────────────────────

    private suspend fun restoreAchievements(snap: DataSnapshot): Int {
        if (!snap.exists()) return 0
        var count = 0
        for (child in snap.children) {
            runCatching {
                val entity = AchievementEntity(
                    id               = child.child("id").getValue(Long::class.java) ?: 0L,
                    type             = child.child("type").getValue(String::class.java) ?: "",
                    milestone        = child.child("milestone").getValue(String::class.java) ?: "",
                    goal             = child.child("goal").getValue(String::class.java),
                    tier             = child.child("tier").getValue(Int::class.java),
                    prestigeLevel    = child.child("prestigeLevel").getValue(Int::class.java) ?: 0,
                    earnedAtMs       = child.child("earnedAtMs").getValue(Long::class.java) ?: 0L,
                    triggerWorkoutId = child.child("triggerWorkoutId").getValue(Long::class.java),
                    shown            = child.child("shown").getValue(Boolean::class.java) ?: false,
                )
                achievementDao.upsert(entity)
                count++
            }.onFailure { Log.w(TAG, "restoreAchievement failed for key=${child.key}", it) }
        }
        return count
    }
}
