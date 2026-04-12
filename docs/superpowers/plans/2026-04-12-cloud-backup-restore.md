# Cloud Backup & Restore Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users link a Google account to preserve all training data across device changes — profile, partner connections, workouts (with GPS routes), bootcamp progress, adaptive model, achievements, and settings.

**Architecture:** Extend the existing anonymous Firebase Auth with Google credential linking. A new `CloudBackupManager` singleton handles incremental writes to RTDB on data changes and a one-time bulk restore on new-device sign-in. All backup data lives under `/users/{uid}/backup/` to separate it from the existing partner-visible data.

**Tech Stack:** Firebase Auth (Google Sign-In linking), Firebase RTDB, Credential Manager API (Android), Hilt DI, Room DAOs.

---

### Task 1: Add Google Sign-In dependencies

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add Credential Manager and Google Sign-In deps**

Add these to the `dependencies` block in `app/build.gradle.kts`, near the existing Firebase deps:

```kotlin
// Google Sign-In via Credential Manager
implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
implementation("androidx.credentials:credentials:1.5.0-rc01")
implementation("androidx.credentials:credentials-play-services-auth:1.5.0-rc01")
```

- [ ] **Step 2: Sync and verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build: add Credential Manager + Google Sign-In dependencies"
```

---

### Task 2: Extend FirebaseAuthManager with Google linking and sign-in

**Files:**
- Modify: `app/src/main/java/com/hrcoach/data/firebase/FirebaseAuthManager.kt`

- [ ] **Step 1: Write the updated FirebaseAuthManager**

Replace the entire file with:

```kotlin
package com.hrcoach.data.firebase

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.hrcoach.data.repository.UserProfileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthManager @Inject constructor(
    private val auth: FirebaseAuth,
    private val userProfileRepository: UserProfileRepository,
    @ApplicationContext private val context: Context,
) {
    companion object {
        // Web client ID from google-services.json → oauth_client with type 3
        const val WEB_CLIENT_ID = "648498089498-dj2f8rnh4dsam9v2qfml0jke3nqm7uag.apps.googleusercontent.com"
    }

    suspend fun ensureSignedIn(): String {
        val currentUser = auth.currentUser
        if (currentUser != null) return currentUser.uid

        val authResult = runCatching {
            withTimeout(10_000) { auth.signInAnonymously().await() }
        }.getOrElse { e ->
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            throw Exception("Authentication timed out. Please check your connection.")
        }

        val uid = authResult.user?.uid ?: throw IllegalStateException("Anonymous auth returned null UID")
        userProfileRepository.setUserId(uid)
        return uid
    }

    fun getCurrentUid(): String? = auth.currentUser?.uid

    fun isGoogleLinked(): Boolean {
        return auth.currentUser?.providerData?.any {
            it.providerId == GoogleAuthProvider.PROVIDER_ID
        } == true
    }

    fun getLinkedEmail(): String? {
        return auth.currentUser?.providerData?.firstOrNull {
            it.providerId == GoogleAuthProvider.PROVIDER_ID
        }?.email
    }

    /**
     * Link Google credential to existing anonymous account.
     * UID stays the same — partner connections are preserved.
     */
    suspend fun linkGoogleAccount(): String {
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = credentialManager.getCredential(context, request)
        val credential = result.credential
        require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected credential type"
        }
        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)

        val currentUser = auth.currentUser ?: throw IllegalStateException("No current user to link")
        withTimeout(15_000) {
            currentUser.linkWithCredential(firebaseCredential).await()
        }
        return currentUser.uid
    }

    /**
     * Sign in with Google on a new device. Returns the existing UID
     * if the Google account was previously linked.
     */
    suspend fun signInWithGoogle(): String {
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = credentialManager.getCredential(context, request)
        val credential = result.credential
        require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected credential type"
        }
        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)

        val authResult = withTimeout(15_000) {
            auth.signInWithCredential(firebaseCredential).await()
        }
        val uid = authResult.user?.uid ?: throw IllegalStateException("Google sign-in returned null UID")
        userProfileRepository.setUserId(uid)
        return uid
    }

    suspend fun signOut() {
        val credentialManager = CredentialManager.create(context)
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
        auth.signOut()
    }
}
```

NOTE: The `WEB_CLIENT_ID` must match the `oauth_client` with `client_type: 3` from `app/google-services.json`. Verify this value before building. Read `app/google-services.json` and find the `oauth_client` array entry where `"client_type": 3` — use its `client_id`.

- [ ] **Step 2: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/firebase/FirebaseAuthManager.kt
git commit -m "feat(auth): add Google Sign-In linking and sign-in to FirebaseAuthManager"
```

---

### Task 3: Create CloudBackupManager — serialization and write methods

**Files:**
- Create: `app/src/main/java/com/hrcoach/data/firebase/CloudBackupManager.kt`

- [ ] **Step 1: Create CloudBackupManager**

```kotlin
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
import com.hrcoach.domain.model.AdaptiveProfile
import com.hrcoach.domain.model.AudioSettings
import com.hrcoach.domain.model.PaceHrBucket
import com.hrcoach.domain.model.VoiceVerbosity
import com.hrcoach.ui.theme.ThemeMode
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudBackupManager @Inject constructor(
    private val db: FirebaseDatabase,
    private val authManager: FirebaseAuthManager,
    private val userProfileRepo: UserProfileRepository,
    private val audioRepo: AudioSettingsRepository,
    private val themeRepo: ThemePreferencesRepository,
    private val autoPauseRepo: AutoPauseSettingsRepository,
    private val adaptiveRepo: AdaptiveProfileRepository,
    private val onboardingRepo: OnboardingRepository,
) {
    companion object {
        private const val TAG = "CloudBackupManager"
        private const val BACKUP_VERSION = 1
        private const val TIMEOUT_MS = 10_000L
    }

    fun isBackupEnabled(): Boolean = authManager.isGoogleLinked()

    private fun backupRef() = db.reference.child("users").child(authManager.getCurrentUid()!!).child("backup")

    // ── Profile & Settings ───────────────────────────────────────────────

    suspend fun syncProfile() {
        if (!isBackupEnabled()) return
        runCatching {
            val data = mapOf(
                "maxHr" to userProfileRepo.getMaxHr(),
                "age" to userProfileRepo.getAge(),
                "weight" to userProfileRepo.getWeight(),
                "weightUnit" to userProfileRepo.getWeightUnit(),
                "distanceUnit" to userProfileRepo.getDistanceUnit(),
                "partnerNudgesEnabled" to userProfileRepo.isPartnerNudgesEnabled(),
                "onboardingCompleted" to onboardingRepo.isOnboardingCompleted(),
            )
            withTimeout(TIMEOUT_MS) {
                backupRef().child("version").setValue(BACKUP_VERSION).await()
                backupRef().child("lastSyncMs").setValue(System.currentTimeMillis()).await()
                backupRef().child("profile").setValue(data).await()
            }
        }.onFailure { Log.w(TAG, "syncProfile failed", it) }
    }

    suspend fun syncSettings() {
        if (!isBackupEnabled()) return
        runCatching {
            val audio = audioRepo.getAudioSettings()
            val data = mapOf(
                "earconVolume" to audio.earconVolume,
                "voiceVolume" to audio.voiceVolume,
                "voiceVerbosity" to audio.voiceVerbosity.name,
                "enableVibration" to audio.enableVibration,
                "enableHalfwayReminder" to (audio.enableHalfwayReminder != false),
                "enableKmSplits" to (audio.enableKmSplits != false),
                "enableWorkoutComplete" to (audio.enableWorkoutComplete != false),
                "enableInZoneConfirm" to (audio.enableInZoneConfirm != false),
                "autoPauseEnabled" to autoPauseRepo.isAutoPauseEnabled(),
                "themeMode" to themeRepo.getThemeMode().name,
            )
            withTimeout(TIMEOUT_MS) {
                backupRef().child("settings").setValue(data).await()
            }
        }.onFailure { Log.w(TAG, "syncSettings failed", it) }
    }

    suspend fun syncAdaptiveProfile() {
        if (!isBackupEnabled()) return
        runCatching {
            val profile = adaptiveRepo.getProfile()
            val buckets = profile.paceHrBuckets.mapKeys { it.key.toString() }
                .mapValues { mapOf("avgHr" to it.value.avgHr, "sampleCount" to it.value.sampleCount) }
            val data = mapOf(
                "longTermHrTrimBpm" to profile.longTermHrTrimBpm,
                "responseLagSec" to profile.responseLagSec,
                "totalSessions" to profile.totalSessions,
                "ctl" to profile.ctl,
                "atl" to profile.atl,
                "hrMax" to profile.hrMax,
                "hrMaxIsCalibrated" to profile.hrMaxIsCalibrated,
                "hrRest" to profile.hrRest,
                "lastTuningDirection" to profile.lastTuningDirection?.name,
                "buckets" to buckets,
            )
            withTimeout(TIMEOUT_MS) {
                backupRef().child("adaptive").setValue(data).await()
            }
        }.onFailure { Log.w(TAG, "syncAdaptiveProfile failed", it) }
    }

    // ── Room entities ────────────────────────────────────────────────────

    suspend fun syncWorkout(workout: WorkoutEntity, trackPoints: List<TrackPointEntity>, metrics: WorkoutMetricsEntity?) {
        if (!isBackupEnabled()) return
        runCatching {
            val id = workout.id.toString()
            val workoutData = mapOf(
                "startTime" to workout.startTime,
                "endTime" to workout.endTime,
                "totalDistanceMeters" to workout.totalDistanceMeters,
                "mode" to workout.mode,
                "targetConfig" to workout.targetConfig,
                "isSimulated" to workout.isSimulated,
            )
            val pointsList = trackPoints.mapIndexed { index, pt ->
                index.toString() to mapOf(
                    "ts" to pt.timestamp,
                    "lat" to pt.latitude,
                    "lng" to pt.longitude,
                    "hr" to pt.heartRate,
                    "dist" to pt.distanceMeters,
                    "alt" to pt.altitudeMeters,
                )
            }.toMap()

            val updates = mutableMapOf<String, Any?>()
            updates["workouts/$id"] = workoutData
            if (pointsList.isNotEmpty()) {
                updates["trackPoints/$id"] = pointsList
            }
            if (metrics != null) {
                updates["metrics/$id"] = mapOf(
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
            updates["lastSyncMs"] = System.currentTimeMillis()

            withTimeout(TIMEOUT_MS) {
                backupRef().updateChildren(updates).await()
            }
        }.onFailure { Log.w(TAG, "syncWorkout failed for ${workout.id}", it) }
    }

    suspend fun syncBootcampEnrollment(enrollment: BootcampEnrollmentEntity) {
        if (!isBackupEnabled()) return
        runCatching {
            val data = mapOf(
                "id" to enrollment.id,
                "goalType" to enrollment.goalType,
                "targetMinutesPerRun" to enrollment.targetMinutesPerRun,
                "runsPerWeek" to enrollment.runsPerWeek,
                "preferredDays" to enrollment.preferredDays.joinToString(",") { "${it.day}:${it.preferred}" },
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
            withTimeout(TIMEOUT_MS) {
                backupRef().child("bootcamp").child("enrollment").setValue(data).await()
            }
        }.onFailure { Log.w(TAG, "syncBootcampEnrollment failed", it) }
    }

    suspend fun syncBootcampSession(session: BootcampSessionEntity) {
        if (!isBackupEnabled()) return
        runCatching {
            val data = mapOf(
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
            withTimeout(TIMEOUT_MS) {
                backupRef().child("bootcamp").child("sessions").child(session.id.toString()).setValue(data).await()
            }
        }.onFailure { Log.w(TAG, "syncBootcampSession failed for ${session.id}", it) }
    }

    suspend fun syncAchievement(achievement: AchievementEntity) {
        if (!isBackupEnabled()) return
        runCatching {
            val data = mapOf(
                "type" to achievement.type,
                "milestone" to achievement.milestone,
                "goal" to achievement.goal,
                "tier" to achievement.tier,
                "prestigeLevel" to achievement.prestigeLevel,
                "earnedAtMs" to achievement.earnedAtMs,
                "triggerWorkoutId" to achievement.triggerWorkoutId,
                "shown" to achievement.shown,
            )
            withTimeout(TIMEOUT_MS) {
                backupRef().child("achievements").child(achievement.id.toString()).setValue(data).await()
            }
        }.onFailure { Log.w(TAG, "syncAchievement failed for ${achievement.id}", it) }
    }

    // ── Full backup (all data at once) ───────────────────────────────────

    suspend fun performFullBackup(
        workouts: List<WorkoutEntity>,
        trackPointsByWorkout: Map<Long, List<TrackPointEntity>>,
        metrics: List<WorkoutMetricsEntity>,
        enrollment: BootcampEnrollmentEntity?,
        sessions: List<BootcampSessionEntity>,
        achievements: List<AchievementEntity>,
    ) {
        if (!isBackupEnabled()) return
        syncProfile()
        syncSettings()
        syncAdaptiveProfile()
        enrollment?.let { syncBootcampEnrollment(it) }
        sessions.forEach { syncBootcampSession(it) }
        workouts.forEach { workout ->
            val points = trackPointsByWorkout[workout.id] ?: emptyList()
            val m = metrics.find { it.workoutId == workout.id }
            syncWorkout(workout, points, m)
        }
        achievements.forEach { syncAchievement(it) }
        Log.i(TAG, "Full backup complete: ${workouts.size} workouts, ${sessions.size} sessions, ${achievements.size} achievements")
    }
}
```

- [ ] **Step 2: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/firebase/CloudBackupManager.kt
git commit -m "feat(backup): create CloudBackupManager with incremental RTDB sync"
```

---

### Task 4: Create CloudRestoreManager — read and populate local stores

**Files:**
- Create: `app/src/main/java/com/hrcoach/data/firebase/CloudRestoreManager.kt`

- [ ] **Step 1: Create CloudRestoreManager**

```kotlin
package com.hrcoach.data.firebase

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.hrcoach.data.db.AchievementDao
import com.hrcoach.data.db.AchievementEntity
import com.hrcoach.data.db.BootcampDao
import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.data.db.TrackPointDao
import com.hrcoach.data.db.TrackPointEntity
import com.hrcoach.data.db.WorkoutDao
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.data.db.WorkoutMetricsDao
import com.hrcoach.data.db.WorkoutMetricsEntity
import com.hrcoach.data.repository.AdaptiveProfileRepository
import com.hrcoach.data.repository.AudioSettingsRepository
import com.hrcoach.data.repository.AutoPauseSettingsRepository
import com.hrcoach.data.repository.OnboardingRepository
import com.hrcoach.data.repository.ThemePreferencesRepository
import com.hrcoach.data.repository.UserProfileRepository
import com.hrcoach.domain.model.AdaptiveProfile
import com.hrcoach.domain.model.AudioSettings
import com.hrcoach.domain.model.PaceHrBucket
import com.hrcoach.domain.model.VoiceVerbosity
import com.hrcoach.domain.engine.TuningDirection
import com.hrcoach.ui.theme.ThemeMode
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
    private val audioRepo: AudioSettingsRepository,
    private val themeRepo: ThemePreferencesRepository,
    private val autoPauseRepo: AutoPauseSettingsRepository,
    private val adaptiveRepo: AdaptiveProfileRepository,
    private val onboardingRepo: OnboardingRepository,
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

    suspend fun hasCloudBackup(): Boolean {
        val uid = authManager.getCurrentUid() ?: return false
        return runCatching {
            withTimeout(TIMEOUT_MS) {
                val snap = db.reference.child("users").child(uid).child("backup").child("version").get().await()
                snap.exists()
            }
        }.getOrDefault(false)
    }

    suspend fun needsRestore(): Boolean {
        if (!authManager.isGoogleLinked()) return false
        val localCount = workoutDao.getWorkoutCount()
        return localCount == 0 && hasCloudBackup()
    }

    suspend fun restore(): RestoreResult {
        val uid = authManager.getCurrentUid() ?: throw IllegalStateException("Not signed in")
        val backupSnap = withTimeout(TIMEOUT_MS) {
            db.reference.child("users").child(uid).child("backup").get().await()
        }
        if (!backupSnap.exists()) throw IllegalStateException("No backup found")

        restoreProfile(backupSnap.child("profile"))
        restoreSettings(backupSnap.child("settings"))
        restoreAdaptive(backupSnap.child("adaptive"))

        val workoutCount = restoreWorkouts(backupSnap.child("workouts"), backupSnap.child("trackPoints"), backupSnap.child("metrics"))
        val sessionCount = restoreBootcamp(backupSnap.child("bootcamp"))
        val achievementCount = restoreAchievements(backupSnap.child("achievements"))

        onboardingRepo.setOnboardingCompleted()
        Log.i(TAG, "Restore complete: $workoutCount workouts, $sessionCount sessions, $achievementCount achievements")
        return RestoreResult(workoutCount, sessionCount, achievementCount)
    }

    private fun restoreProfile(snap: DataSnapshot) {
        if (!snap.exists()) return
        snap.child("maxHr").getValue(Int::class.java)?.let {
            runCatching { userProfileRepo.setMaxHr(it) }
        }
        snap.child("age").getValue(Int::class.java)?.let {
            runCatching { userProfileRepo.setAge(it) }
        }
        snap.child("weight").getValue(Int::class.java)?.let {
            runCatching { userProfileRepo.setWeight(it) }
        }
        snap.child("weightUnit").getValue(String::class.java)?.let {
            runCatching { userProfileRepo.setWeightUnit(it) }
        }
        snap.child("distanceUnit").getValue(String::class.java)?.let {
            runCatching { userProfileRepo.setDistanceUnit(it) }
        }
        snap.child("partnerNudgesEnabled").getValue(Boolean::class.java)?.let {
            userProfileRepo.setPartnerNudgesEnabled(it)
        }
    }

    private fun restoreSettings(snap: DataSnapshot) {
        if (!snap.exists()) return
        val audio = AudioSettings(
            earconVolume = snap.child("earconVolume").getValue(Int::class.java) ?: 80,
            voiceVolume = snap.child("voiceVolume").getValue(Int::class.java) ?: 80,
            voiceVerbosity = snap.child("voiceVerbosity").getValue(String::class.java)?.let {
                runCatching { VoiceVerbosity.valueOf(it) }.getOrNull()
            } ?: VoiceVerbosity.MINIMAL,
            enableVibration = snap.child("enableVibration").getValue(Boolean::class.java) ?: true,
            enableHalfwayReminder = snap.child("enableHalfwayReminder").getValue(Boolean::class.java),
            enableKmSplits = snap.child("enableKmSplits").getValue(Boolean::class.java),
            enableWorkoutComplete = snap.child("enableWorkoutComplete").getValue(Boolean::class.java),
            enableInZoneConfirm = snap.child("enableInZoneConfirm").getValue(Boolean::class.java),
        )
        audioRepo.saveAudioSettings(audio)
        snap.child("autoPauseEnabled").getValue(Boolean::class.java)?.let {
            autoPauseRepo.setAutoPauseEnabled(it)
        }
        snap.child("themeMode").getValue(String::class.java)?.let {
            runCatching { themeRepo.setThemeMode(ThemeMode.valueOf(it)) }
        }
    }

    private fun restoreAdaptive(snap: DataSnapshot) {
        if (!snap.exists()) return
        val buckets = mutableMapOf<Int, PaceHrBucket>()
        snap.child("buckets").children.forEach { child ->
            val key = child.key?.toIntOrNull() ?: return@forEach
            val avgHr = child.child("avgHr").getValue(Float::class.java) ?: return@forEach
            val count = child.child("sampleCount").getValue(Int::class.java) ?: return@forEach
            buckets[key] = PaceHrBucket(avgHr, count)
        }
        val profile = AdaptiveProfile(
            longTermHrTrimBpm = snap.child("longTermHrTrimBpm").getValue(Float::class.java) ?: 0f,
            responseLagSec = snap.child("responseLagSec").getValue(Float::class.java) ?: 25f,
            totalSessions = snap.child("totalSessions").getValue(Int::class.java) ?: 0,
            ctl = snap.child("ctl").getValue(Float::class.java) ?: 0f,
            atl = snap.child("atl").getValue(Float::class.java) ?: 0f,
            hrMax = snap.child("hrMax").getValue(Int::class.java),
            hrMaxIsCalibrated = snap.child("hrMaxIsCalibrated").getValue(Boolean::class.java) ?: false,
            hrRest = snap.child("hrRest").getValue(Float::class.java),
            lastTuningDirection = snap.child("lastTuningDirection").getValue(String::class.java)?.let {
                runCatching { TuningDirection.valueOf(it) }.getOrNull()
            },
            paceHrBuckets = buckets,
        )
        adaptiveRepo.saveProfile(profile)
    }

    private suspend fun restoreWorkouts(workoutSnap: DataSnapshot, trackSnap: DataSnapshot, metricsSnap: DataSnapshot): Int {
        if (!workoutSnap.exists()) return 0
        var count = 0
        workoutSnap.children.forEach { child ->
            val id = child.key?.toLongOrNull() ?: return@forEach
            val workout = WorkoutEntity(
                id = id,
                startTime = child.child("startTime").getValue(Long::class.java) ?: return@forEach,
                endTime = child.child("endTime").getValue(Long::class.java) ?: 0L,
                totalDistanceMeters = child.child("totalDistanceMeters").getValue(Float::class.java) ?: 0f,
                mode = child.child("mode").getValue(String::class.java) ?: return@forEach,
                targetConfig = child.child("targetConfig").getValue(String::class.java) ?: "{}",
                isSimulated = child.child("isSimulated").getValue(Boolean::class.java) ?: false,
            )
            workoutDao.insert(workout)
            count++

            // Track points
            trackSnap.child(id.toString()).children.forEach { ptChild ->
                val pt = TrackPointEntity(
                    workoutId = id,
                    timestamp = ptChild.child("ts").getValue(Long::class.java) ?: return@forEach,
                    latitude = ptChild.child("lat").getValue(Double::class.java) ?: return@forEach,
                    longitude = ptChild.child("lng").getValue(Double::class.java) ?: return@forEach,
                    heartRate = ptChild.child("hr").getValue(Int::class.java) ?: 0,
                    distanceMeters = ptChild.child("dist").getValue(Float::class.java) ?: 0f,
                    altitudeMeters = ptChild.child("alt").getValue(Double::class.java),
                )
                trackPointDao.insert(pt)
            }

            // Metrics
            val mChild = metricsSnap.child(id.toString())
            if (mChild.exists()) {
                val metrics = WorkoutMetricsEntity(
                    workoutId = id,
                    recordedAtMs = mChild.child("recordedAtMs").getValue(Long::class.java) ?: System.currentTimeMillis(),
                    avgPaceMinPerKm = mChild.child("avgPaceMinPerKm").getValue(Float::class.java),
                    avgHr = mChild.child("avgHr").getValue(Float::class.java),
                    hrAtSixMinPerKm = mChild.child("hrAtSixMinPerKm").getValue(Float::class.java),
                    settleDownSec = mChild.child("settleDownSec").getValue(Float::class.java),
                    settleUpSec = mChild.child("settleUpSec").getValue(Float::class.java),
                    longTermHrTrimBpm = mChild.child("longTermHrTrimBpm").getValue(Float::class.java) ?: 0f,
                    responseLagSec = mChild.child("responseLagSec").getValue(Float::class.java) ?: 25f,
                    efficiencyFactor = mChild.child("efficiencyFactor").getValue(Float::class.java),
                    aerobicDecoupling = mChild.child("aerobicDecoupling").getValue(Float::class.java),
                    efFirstHalf = mChild.child("efFirstHalf").getValue(Float::class.java),
                    efSecondHalf = mChild.child("efSecondHalf").getValue(Float::class.java),
                    heartbeatsPerKm = mChild.child("heartbeatsPerKm").getValue(Float::class.java),
                    paceAtRefHrMinPerKm = mChild.child("paceAtRefHrMinPerKm").getValue(Float::class.java),
                    hrr1Bpm = mChild.child("hrr1Bpm").getValue(Float::class.java),
                    trimpScore = mChild.child("trimpScore").getValue(Float::class.java),
                    trimpReliable = mChild.child("trimpReliable").getValue(Boolean::class.java) ?: true,
                    environmentAffected = mChild.child("environmentAffected").getValue(Boolean::class.java) ?: false,
                )
                workoutMetricsDao.upsert(metrics)
            }
        }
        return count
    }

    private suspend fun restoreBootcamp(snap: DataSnapshot): Int {
        if (!snap.exists()) return 0
        val enrollSnap = snap.child("enrollment")
        if (!enrollSnap.exists()) return 0

        val preferredDaysStr = enrollSnap.child("preferredDays").getValue(String::class.java) ?: ""
        val dayPrefs = BootcampEnrollmentEntity.parseDayPreferences(preferredDaysStr)

        val enrollment = BootcampEnrollmentEntity(
            id = enrollSnap.child("id").getValue(Long::class.java) ?: 0L,
            goalType = enrollSnap.child("goalType").getValue(String::class.java) ?: return 0,
            targetMinutesPerRun = enrollSnap.child("targetMinutesPerRun").getValue(Int::class.java) ?: 30,
            runsPerWeek = enrollSnap.child("runsPerWeek").getValue(Int::class.java) ?: 3,
            preferredDays = dayPrefs,
            startDate = enrollSnap.child("startDate").getValue(Long::class.java) ?: return 0,
            currentPhaseIndex = enrollSnap.child("currentPhaseIndex").getValue(Int::class.java) ?: 0,
            currentWeekInPhase = enrollSnap.child("currentWeekInPhase").getValue(Int::class.java) ?: 0,
            status = enrollSnap.child("status").getValue(String::class.java) ?: BootcampEnrollmentEntity.STATUS_ACTIVE,
            tierIndex = enrollSnap.child("tierIndex").getValue(Int::class.java) ?: 0,
            tierPromptSnoozedUntilMs = enrollSnap.child("tierPromptSnoozedUntilMs").getValue(Long::class.java) ?: 0,
            tierPromptDismissCount = enrollSnap.child("tierPromptDismissCount").getValue(Int::class.java) ?: 0,
            illnessPromptSnoozedUntilMs = enrollSnap.child("illnessPromptSnoozedUntilMs").getValue(Long::class.java) ?: 0,
            pausedAtMs = enrollSnap.child("pausedAtMs").getValue(Long::class.java) ?: 0,
            targetFinishingTimeMinutes = enrollSnap.child("targetFinishingTimeMinutes").getValue(Int::class.java),
        )
        bootcampDao.insertEnrollment(enrollment)

        var sessionCount = 0
        snap.child("sessions").children.forEach { sChild ->
            val session = BootcampSessionEntity(
                id = sChild.key?.toLongOrNull() ?: return@forEach,
                enrollmentId = sChild.child("enrollmentId").getValue(Long::class.java) ?: enrollment.id,
                weekNumber = sChild.child("weekNumber").getValue(Int::class.java) ?: return@forEach,
                dayOfWeek = sChild.child("dayOfWeek").getValue(Int::class.java) ?: return@forEach,
                sessionType = sChild.child("sessionType").getValue(String::class.java) ?: return@forEach,
                targetMinutes = sChild.child("targetMinutes").getValue(Int::class.java) ?: 30,
                presetId = sChild.child("presetId").getValue(String::class.java),
                status = sChild.child("status").getValue(String::class.java) ?: BootcampSessionEntity.STATUS_SCHEDULED,
                completedWorkoutId = sChild.child("completedWorkoutId").getValue(Long::class.java),
                presetIndex = sChild.child("presetIndex").getValue(Int::class.java),
                completedAtMs = sChild.child("completedAtMs").getValue(Long::class.java),
            )
            bootcampDao.insertSession(session)
            sessionCount++
        }
        return sessionCount
    }

    private suspend fun restoreAchievements(snap: DataSnapshot): Int {
        if (!snap.exists()) return 0
        var count = 0
        snap.children.forEach { child ->
            val achievement = AchievementEntity(
                id = child.key?.toLongOrNull() ?: return@forEach,
                type = child.child("type").getValue(String::class.java) ?: return@forEach,
                milestone = child.child("milestone").getValue(String::class.java) ?: return@forEach,
                goal = child.child("goal").getValue(String::class.java),
                tier = child.child("tier").getValue(Int::class.java),
                prestigeLevel = child.child("prestigeLevel").getValue(Int::class.java) ?: 0,
                earnedAtMs = child.child("earnedAtMs").getValue(Long::class.java) ?: return@forEach,
                triggerWorkoutId = child.child("triggerWorkoutId").getValue(Long::class.java),
                shown = child.child("shown").getValue(Boolean::class.java) ?: false,
            )
            achievementDao.insert(achievement)
            count++
        }
        return count
    }
}
```

- [ ] **Step 2: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. If `BootcampEnrollmentEntity.parseDayPreferences` doesn't exist as a static method, check if it's a companion function or a top-level function and adjust the call accordingly.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/firebase/CloudRestoreManager.kt
git commit -m "feat(backup): create CloudRestoreManager with full RTDB restore"
```

---

### Task 5: Wire backup calls into existing data-write sites

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountViewModel.kt`

- [ ] **Step 1: Add CloudBackupManager injection to WorkoutForegroundService**

In `WorkoutForegroundService.kt`, add the injection field near the other `@Inject lateinit var` declarations:

```kotlin
@Inject lateinit var cloudBackupManager: CloudBackupManager
```

Add the import:
```kotlin
import com.hrcoach.data.firebase.CloudBackupManager
```

- [ ] **Step 2: Add backup call after workout save in stopWorkout()**

In the `stopJob` function, after the existing `partnerRepository.syncWorkoutActivity(...)` block (around where the workout is fully saved), add:

```kotlin
// Cloud backup — sync workout + adaptive profile
runCatching {
    val savedWorkout = repository.getById(workoutId)
    if (savedWorkout != null) {
        val points = trackPointDao.getPointsForWorkout(workoutId)
        val metricsEntity = workoutMetricsRepository.getByWorkoutId(workoutId)
        cloudBackupManager.syncWorkout(savedWorkout, points, metricsEntity)
        cloudBackupManager.syncAdaptiveProfile()
    }
}.onFailure { Log.w("WorkoutService", "Cloud backup failed", it) }
```

Add the import for `Log` if not already present.

- [ ] **Step 3: Add backup calls in AccountViewModel**

In `AccountViewModel.kt`, inject `CloudBackupManager`:

```kotlin
private val cloudBackupManager: CloudBackupManager,
```

Add to constructor params after `fcmTokenManager`. Add import:
```kotlin
import com.hrcoach.data.firebase.CloudBackupManager
```

In `saveProfile()`, add backup call:

```kotlin
fun saveProfile() {
    userProfileRepo.setDisplayName(_displayName.value)
    userProfileRepo.setEmblemId(_emblemId.value)
    viewModelScope.launch { cloudBackupManager.syncProfile() }
}
```

In `saveMaxHr()`, after the existing `adaptiveProfileRepo.saveProfile(...)` call, add:

```kotlin
viewModelScope.launch { cloudBackupManager.syncProfile() }
```

Add audio settings backup — in `saveAudioSettings()` (the existing private method), add:

```kotlin
viewModelScope.launch { cloudBackupManager.syncSettings() }
```

In `setDistanceUnit()`, add after the existing `userProfileRepo.setDistanceUnit(...)`:

```kotlin
viewModelScope.launch { cloudBackupManager.syncProfile() }
```

In `setAutoPauseEnabled()`, add after the existing `autoPauseRepo.setAutoPauseEnabled(...)`:

```kotlin
viewModelScope.launch { cloudBackupManager.syncSettings() }
```

- [ ] **Step 4: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt app/src/main/java/com/hrcoach/ui/account/AccountViewModel.kt
git commit -m "feat(backup): wire CloudBackupManager into workout save and settings changes"
```

---

### Task 6: Wire backup into bootcamp and achievement writes

**Files:**
- Modify: Files that insert/update bootcamp enrollments, sessions, and achievements. Find the exact call sites.

- [ ] **Step 1: Find bootcamp write call sites**

Search for `insertEnrollment`, `updateSession`, `insertSession`, `updateEnrollment` calls outside of DAOs. The main sites will be in `BootcampViewModel` and `BootcampSessionCompleter`. Inject `CloudBackupManager` into each and add backup calls after each write:

For `BootcampViewModel` (or wherever enrollment is created):
```kotlin
@Inject constructor(
    ...
    private val cloudBackupManager: CloudBackupManager,
)
```

After `bootcampDao.insertEnrollment(enrollment)`:
```kotlin
viewModelScope.launch { cloudBackupManager.syncBootcampEnrollment(enrollment) }
```

After `bootcampDao.insertSessions(sessions)`:
```kotlin
viewModelScope.launch { sessions.forEach { cloudBackupManager.syncBootcampSession(it) } }
```

For `BootcampSessionCompleter` (or wherever session status is updated after workout completion):
After session status update to COMPLETED:
```kotlin
cloudBackupManager.syncBootcampSession(updatedSession)
```

- [ ] **Step 2: Find achievement write call sites**

Search for `achievementDao.insert(` calls. Inject `CloudBackupManager` into the class that inserts achievements and add:

```kotlin
cloudBackupManager.syncAchievement(achievement)
```

after each insert.

- [ ] **Step 3: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(backup): wire backup into bootcamp and achievement write sites"
```

---

### Task 7: Add Google Account UI to AccountScreen

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt`

- [ ] **Step 1: Add Google account state and methods to AccountViewModel**

Add to `AccountUiState`:
```kotlin
val isGoogleLinked: Boolean = false,
val linkedEmail: String? = null,
val lastSyncMs: Long? = null,
val isLinking: Boolean = false,
val linkError: String? = null,
val isRestoring: Boolean = false,
val restoreResult: RestoreResult? = null,
```

Add imports:
```kotlin
import com.hrcoach.data.firebase.CloudRestoreManager
import com.hrcoach.data.firebase.RestoreResult
```

Add `CloudRestoreManager` to constructor injection:
```kotlin
private val cloudRestoreManager: CloudRestoreManager,
```

Add state flows:
```kotlin
private val _isGoogleLinked = MutableStateFlow(false)
private val _linkedEmail = MutableStateFlow<String?>(null)
private val _isLinking = MutableStateFlow(false)
private val _linkError = MutableStateFlow<String?>(null)
private val _isRestoring = MutableStateFlow(false)
private val _restoreResult = MutableStateFlow<RestoreResult?>(null)
```

In `init {}`, after `initFirebase()`, add:
```kotlin
_isGoogleLinked.value = firebaseAuthManager.isGoogleLinked()
_linkedEmail.value = firebaseAuthManager.getLinkedEmail()
```

Add these to the `uiState` combine chain (add another `.combine()`):
```kotlin
.combine(
    combine(_isGoogleLinked, _linkedEmail, _isLinking, _linkError, _isRestoring, _restoreResult) { values ->
        @Suppress("UNCHECKED_CAST")
        values.toList()
    }
) { base, parts ->
    base.copy(
        isGoogleLinked = parts[0] as Boolean,
        linkedEmail = parts[1] as String?,
        isLinking = parts[2] as Boolean,
        linkError = parts[3] as String?,
        isRestoring = parts[4] as Boolean,
        restoreResult = parts[5] as RestoreResult?,
    )
}
```

Add methods:
```kotlin
fun linkGoogleAccount() {
    viewModelScope.launch {
        _isLinking.value = true
        _linkError.value = null
        runCatching {
            firebaseAuthManager.linkGoogleAccount()
            _isGoogleLinked.value = true
            _linkedEmail.value = firebaseAuthManager.getLinkedEmail()
            // Trigger full backup now that Google is linked
            performFullBackup()
        }.onFailure { e ->
            _linkError.value = when {
                e.message?.contains("CREDENTIAL_ALREADY_IN_USE") == true ->
                    "This Google account is already linked to another profile."
                else -> "Failed to link: ${e.message}"
            }
        }
        _isLinking.value = false
    }
}

fun restoreFromCloud() {
    viewModelScope.launch {
        _isRestoring.value = true
        runCatching {
            val result = cloudRestoreManager.restore()
            _restoreResult.value = result
        }.onFailure { e ->
            _linkError.value = "Restore failed: ${e.message}"
        }
        _isRestoring.value = false
    }
}

fun clearRestoreResult() {
    _restoreResult.value = null
}

private suspend fun performFullBackup() {
    val workouts = workoutRepo.getAllWorkoutsOnce()
    val trackPointsByWorkout = workouts.associate { w ->
        w.id to trackPointDao.getPointsForWorkout(w.id)
    }
    val metrics = workouts.mapNotNull { workoutMetricsRepository.getByWorkoutId(it.id) }
    val enrollment = bootcampRepository.getActiveEnrollmentOnce()
    val sessions = enrollment?.let { bootcampRepository.getSessionsForEnrollmentOnce(it.id) } ?: emptyList()
    val achievements = achievementDao.getAllAchievementsOnce()
    cloudBackupManager.performFullBackup(workouts, trackPointsByWorkout, metrics, enrollment, sessions, achievements)
}
```

NOTE: `getAllWorkoutsOnce()`, `getPointsForWorkout()`, `getByWorkoutId()`, `getAllAchievementsOnce()` — verify these DAO methods exist. If `getAllAchievementsOnce` doesn't exist, add a `@Query("SELECT * FROM achievements") suspend fun getAllAchievementsOnce(): List<AchievementEntity>` to `AchievementDao`. Similarly ensure `WorkoutRepository` exposes `getAllWorkoutsOnce()` — it may be on `WorkoutDao` directly.

Also add these injections to the constructor if not already present: `trackPointDao: TrackPointDao`, `workoutMetricsRepository: WorkoutMetricsRepository` (or `workoutMetricsDao`), `bootcampRepository: BootcampRepository` (or `bootcampDao`). Check what's already injected vs what needs adding.

- [ ] **Step 2: Add Cloud Backup section to AccountScreen**

In `AccountScreen.kt`, above the partner section, add a new `CloudBackupSection` composable:

```kotlin
@Composable
private fun CloudBackupSection(
    state: AccountUiState,
    onLinkGoogle: () -> Unit,
    onRestore: () -> Unit,
    onDismissRestore: () -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Favorite, // cloud icon not in Default set, use a placeholder
                    contentDescription = "Cloud Backup",
                    tint = CardeaTheme.colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Cloud Backup",
                    style = MaterialTheme.typography.titleSmall,
                    color = CardeaTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(12.dp))

            if (state.isGoogleLinked) {
                Text(
                    text = state.linkedEmail ?: "Google account linked",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CardeaTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Your training data is backed up",
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTheme.colors.textTertiary,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onRestore,
                    enabled = !state.isRestoring,
                    border = BorderStroke(1.dp, CardeaTheme.colors.glassBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (state.isRestoring) "Restoring..." else "Restore from cloud",
                        color = CardeaTheme.colors.textSecondary,
                    )
                }
            } else {
                Text(
                    text = "Link a Google account to back up your workouts, bootcamp progress, and partner connections.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTheme.colors.textTertiary,
                )
                Spacer(Modifier.height(12.dp))
                CardeaButton(
                    text = if (state.isLinking) "Linking..." else "Link Google Account",
                    onClick = onLinkGoogle,
                    enabled = !state.isLinking,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.linkError?.let { error ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTheme.colors.zoneRed,
                )
            }

            state.restoreResult?.let { result ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Restored ${result.workoutCount} workouts, ${result.sessionCount} sessions, ${result.achievementCount} achievements",
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTheme.colors.zoneGreen,
                )
            }
        }
    }
}
```

Add the composable to the AccountScreen column, before the partner section:

```kotlin
CloudBackupSection(
    state = state,
    onLinkGoogle = viewModel::linkGoogleAccount,
    onRestore = viewModel::restoreFromCloud,
    onDismissRestore = viewModel::clearRestoreResult,
)
Spacer(Modifier.height(16.dp))
```

Add necessary imports: `BorderStroke`, `OutlinedButton`, `CardeaButton`, etc.

- [ ] **Step 3: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. Fix any missing imports or DAO method issues.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(backup): add Cloud Backup section to Account screen with Google linking"
```

---

### Task 8: Add auto-restore check on app launch

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt` (or the splash/onboarding ViewModel)

- [ ] **Step 1: Add restore check in OnboardingSplashViewModel**

The splash screen already checks `onboardingRepository.isOnboardingCompleted()`. After a Google sign-in on a new device, the local onboarding flag will be false, but cloud backup exists. Add a restore check:

In `OnboardingSplashViewModel` (or wherever the splash → onboarding decision is made), inject `CloudRestoreManager` and add:

```kotlin
@Inject constructor(
    val onboardingRepository: OnboardingRepository,
    private val cloudRestoreManager: CloudRestoreManager,
)
```

Add a method:
```kotlin
suspend fun checkAndRestore(): Boolean {
    return runCatching {
        if (cloudRestoreManager.needsRestore()) {
            cloudRestoreManager.restore()
            true
        } else false
    }.getOrDefault(false)
}
```

The splash screen can call this and if it returns `true`, skip to the home screen. If `false`, proceed with normal onboarding check.

- [ ] **Step 2: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat(backup): add auto-restore check on app launch"
```

---

### Task 9: Update Firebase RTDB security rules

**Files:**
- Modify: `database.rules.json` (or wherever RTDB rules live)

- [ ] **Step 1: Find and update security rules**

Check if `database.rules.json` exists in the project root. If not, check `firebase.json` for the rules file path. Update the rules to include the backup subtree:

```json
{
  "rules": {
    "users": {
      "$uid": {
        ".read": "auth.uid === $uid",
        ".write": "auth.uid === $uid",
        "partners": {
          "$partnerId": {
            ".read": "auth != null",
            ".write": "auth.uid === $uid || auth.uid === $partnerId"
          }
        },
        "backup": {
          ".read": "auth.uid === $uid",
          ".write": "auth.uid === $uid"
        }
      }
    },
    "invites": {
      "$code": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    }
  }
}
```

The backup node only needs owner read/write — no partner access needed.

- [ ] **Step 2: Deploy rules**

Run: `firebase deploy --only database --project cardea-1c8fc`
Expected: Successful deployment. If no `.firebaserc` exists, always pass `--project`.

- [ ] **Step 3: Commit**

```bash
git add database.rules.json
git commit -m "feat(backup): add RTDB security rules for backup subtree"
```

---

### Task 10: Fix profile dismiss bug and final integration

**Files:**
- Verify: `app/src/main/java/com/hrcoach/ui/account/AccountViewModel.kt` (discardProfileChanges already added)
- Verify: `app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt` (dismiss handler already wired)

- [ ] **Step 1: Verify the profile dismiss fix is in place**

Check that `AccountViewModel` has:
```kotlin
fun discardProfileChanges() {
    _displayName.value = userProfileRepo.getDisplayName()
    _emblemId.value = userProfileRepo.getEmblemId()
}
```

And `AccountScreen` calls it in the sheet's `onDismiss`.

- [ ] **Step 2: Full build and manual test plan**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

Manual test checklist:
1. Install on device
2. Open Account screen → verify "Link Google Account" button appears
3. Tap Link → Google sign-in flow → verify email appears + "backed up" status
4. Complete a workout → verify RTDB `/users/{uid}/backup/workouts/` has data
5. Uninstall app
6. Reinstall → at splash, the auto-restore should detect backup and restore
7. Verify workouts, bootcamp, settings are restored

- [ ] **Step 3: Final commit**

```bash
git add -A
git commit -m "feat(backup): cloud backup & restore with Google Sign-In account linking

Adds Google account linking via Credential Manager to preserve training
data across device changes. Includes:
- Incremental backup to Firebase RTDB on data changes
- Full backup on initial Google link
- Auto-restore on new device sign-in
- Cloud Backup section in Account screen
- Profile dismiss bug fix (discard changes on sheet dismiss)"
```
