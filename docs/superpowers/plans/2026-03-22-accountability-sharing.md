# Accountability Sharing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add mutual accountability sharing — partner pairing via invite codes, push notifications on run completion, and a partner hero card on the home screen.

**Architecture:** Firebase Anonymous Auth for identity, Firestore for data, FCM for push, Cloud Functions for server logic. All business logic (streaks, week strip state) computed on-device. Server is a dumb relay. New `data/firebase/` package for all Firebase interactions, new `ui/partner/` package for partner UI.

**Tech Stack:** Firebase Auth, Firestore, FCM, Cloud Functions (Node.js/TypeScript), Jetpack Compose, Hilt, Google Maps SDK (for route display)

**Spec:** `docs/superpowers/specs/2026-03-22-accountability-sharing-design.md`

---

## File Structure

### New files

| File | Responsibility |
|------|---------------|
| `app/build.gradle.kts` | Add Firebase BOM + auth/firestore/messaging/functions deps |
| `build.gradle.kts` | Add google-services plugin |
| `app/google-services.json` | Firebase project config (manual setup) |
| `app/src/main/java/com/hrcoach/data/firebase/FirebaseAuthManager.kt` | Anonymous auth init, UID access, Google linking |
| `app/src/main/java/com/hrcoach/data/firebase/PartnerRepository.kt` | Firestore CRUD: user doc, pairing, run completions, partner listener |
| `app/src/main/java/com/hrcoach/data/firebase/RunCompletionPayload.kt` | Data class for the run completion Firestore document |
| `app/src/main/java/com/hrcoach/service/CardeaMessagingService.kt` | `FirebaseMessagingService` — token refresh + notification display |
| `app/src/main/java/com/hrcoach/domain/sharing/PartnerStreakCalculator.kt` | Compute sharing-specific streak from bootcamp sessions or workout history |
| `app/src/main/java/com/hrcoach/domain/sharing/WeekStripState.kt` | Compute week strip day states from `RunCompletionPayload` list |
| `app/src/main/java/com/hrcoach/ui/partner/PartnerHeroCard.kt` | Partner hero card composable (header + week track + phase) |
| `app/src/main/java/com/hrcoach/ui/partner/WeekProgressTrack.kt` | Canvas-drawn week strip with connected track line and day nodes |
| `app/src/main/java/com/hrcoach/ui/partner/PartnerDetailScreen.kt` | Route map + distance + streak + phase (read-only) |
| `app/src/main/java/com/hrcoach/ui/partner/PartnerDetailViewModel.kt` | Loads run completion from Firestore, exposes UI state |
| `app/src/main/java/com/hrcoach/ui/account/PartnerSection.kt` | Account screen GlassCard: invite/join/disconnect |
| `app/src/main/java/com/hrcoach/di/FirebaseModule.kt` | Hilt module: provides FirebaseAuth, FirebaseFirestore, FirebaseFunctions |
| `functions/src/index.ts` | Cloud Functions: createInvite, acceptInvite, onRunCompleted, cleanupOldCompletions |
| `functions/package.json` | Node project for Cloud Functions |
| `firebase-hosting/public/pair.html` | Deep link redirect page (serves App Links or Play Store fallback) |
| `firebase-hosting/public/.well-known/assetlinks.json` | Android App Links domain verification |
| `firebase-hosting/firebase.json` | Firebase Hosting config with rewrite for `/pair/*` |
| **Tests** | |
| `app/src/test/.../domain/sharing/PartnerStreakCalculatorTest.kt` | Streak edge cases: bootcamp deferred, free runner, midnight boundary |
| `app/src/test/.../domain/sharing/WeekStripStateTest.kt` | Week strip rendering: deferred, bonus, free runner, empty week |
| `app/src/test/.../data/firebase/RunCompletionPayloadTest.kt` | Payload composition from workout + bootcamp data |

### Modified files

| File | Changes |
|------|---------|
| `app/src/main/AndroidManifest.xml` | Add `CardeaMessagingService`, `POST_NOTIFICATIONS` permission, deep link intent filter |
| `app/src/main/java/com/hrcoach/HrCoachApp.kt` | Init Firebase Auth on app start |
| `app/src/main/java/com/hrcoach/MainActivity.kt` | Handle deep link intent (`EXTRA_PAIR_CODE`) |
| `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt` | Post `RunCompletionPayload` to Firestore after workout save |
| `app/src/main/java/com/hrcoach/ui/home/HomeViewModel.kt` | Add partner state to `HomeUiState`, observe Firestore partner data |
| `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt` | Render `PartnerHeroCard` when paired (state 3/4) |
| `app/src/main/java/com/hrcoach/ui/account/AccountViewModel.kt` | Add pairing methods + partner state to `AccountUiState` |
| `app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt` | Add `PartnerSection` composable |
| `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt` | Add `partnerDetail/{completionId}` route |

---

## Task 1: Firebase Project Setup & Dependencies

**Files:**
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/hrcoach/di/FirebaseModule.kt`

- [ ] **Step 1: Add google-services plugin to root `build.gradle.kts`**

```kotlin
// Add after existing plugins
id("com.google.gms.google-services") version "4.4.2" apply false
```

- [ ] **Step 2: Add Firebase BOM and libraries to `app/build.gradle.kts`**

Add plugin:
```kotlin
id("com.google.gms.google-services")
```

Add dependencies:
```kotlin
// Firebase
implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
implementation("com.google.firebase:firebase-auth-ktx")
implementation("com.google.firebase:firebase-firestore-ktx")
implementation("com.google.firebase:firebase-messaging-ktx")
implementation("com.google.firebase:firebase-functions-ktx")
```

- [ ] **Step 3: Create `FirebaseModule.kt` Hilt module**

```kotlin
package com.hrcoach.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFunctions(): FirebaseFunctions = FirebaseFunctions.getInstance()
}
```

- [ ] **Step 4: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (will fail if `google-services.json` is missing — that's a manual step documented below)

> **Manual step:** The developer must create a Firebase project in the Firebase Console, enable Anonymous Auth and Firestore, download `google-services.json`, and place it in `app/`. This cannot be automated.

- [ ] **Step 5: Commit**

```bash
git add build.gradle.kts app/build.gradle.kts app/src/main/java/com/hrcoach/di/FirebaseModule.kt
git commit -m "feat(sharing): add Firebase dependencies and Hilt module"
```

---

## Task 2: Firebase Auth Manager & App Init

**Files:**
- Create: `app/src/main/java/com/hrcoach/data/firebase/FirebaseAuthManager.kt`
- Modify: `app/src/main/java/com/hrcoach/HrCoachApp.kt`

- [ ] **Step 1: Create `FirebaseAuthManager.kt`**

```kotlin
package com.hrcoach.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthManager @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    /** Current Firebase UID, or null if not yet signed in. */
    val uid: String? get() = auth.currentUser?.uid

    /**
     * Sign in anonymously if not already signed in.
     * Writes/updates FCM token to Firestore user doc.
     */
    suspend fun ensureSignedIn() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
        val currentUid = uid ?: return
        val token = FirebaseMessaging.getInstance().token.await()
        firestore.collection("users").document(currentUid)
            .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
            .await()
    }

    /** Update FCM token in Firestore (called from MessagingService.onNewToken). */
    suspend fun updateFcmToken(token: String) {
        val currentUid = uid ?: return
        firestore.collection("users").document(currentUid)
            .update("fcmToken", token)
            .await()
    }
}
```

- [ ] **Step 2: Init Firebase Auth in `HrCoachApp.kt`**

Add to `HrCoachApp` class body — inject and trigger sign-in on app creation:

```kotlin
@Inject lateinit var firebaseAuthManager: FirebaseAuthManager

override fun onCreate() {
    super.onCreate()
    // Fire-and-forget anonymous sign-in + FCM token sync
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        runCatching { firebaseAuthManager.ensureSignedIn() }
    }
}
```

Note: `HrCoachApp` is `@HiltAndroidApp` so `@Inject` fields are populated automatically.

- [ ] **Step 3: Verify build compiles**

Run: `./gradlew assembleDebug`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/firebase/FirebaseAuthManager.kt app/src/main/java/com/hrcoach/HrCoachApp.kt
git commit -m "feat(sharing): add FirebaseAuthManager with anonymous sign-in and FCM token sync"
```

---

## Task 3: FCM Messaging Service

**Files:**
- Create: `app/src/main/java/com/hrcoach/service/CardeaMessagingService.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create `CardeaMessagingService.kt`**

```kotlin
package com.hrcoach.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.hrcoach.MainActivity
import com.hrcoach.R
import com.hrcoach.data.firebase.FirebaseAuthManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CardeaMessagingService : FirebaseMessagingService() {

    @Inject lateinit var authManager: FirebaseAuthManager

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { authManager.updateFcmToken(token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.data["title"] ?: "Cardea"
        val body = message.data["body"] ?: return
        val completionId = message.data["completionId"]

        val channelId = "partner_runs"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Partner Runs",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (completionId != null) {
                putExtra("EXTRA_COMPLETION_ID", completionId)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(System.currentTimeMillis().toInt(), notification)
    }
}
```

- [ ] **Step 2: Register service in `AndroidManifest.xml`**

Add inside `<application>`:
```xml
<service
    android:name=".service.CardeaMessagingService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
```

Add permission (before `<application>`):
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

- [ ] **Step 3: Verify build compiles**

Run: `./gradlew assembleDebug`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/CardeaMessagingService.kt app/src/main/AndroidManifest.xml
git commit -m "feat(sharing): add FCM messaging service for partner notifications"
```

---

## Task 4: Partner Repository & Data Layer

**Files:**
- Create: `app/src/main/java/com/hrcoach/data/firebase/RunCompletionPayload.kt`
- Create: `app/src/main/java/com/hrcoach/data/firebase/PartnerRepository.kt`
- Test: `app/src/test/.../data/firebase/RunCompletionPayloadTest.kt`

- [ ] **Step 1: Write test for `RunCompletionPayload` construction**

```kotlin
package com.hrcoach.data.firebase

import org.junit.Assert.*
import org.junit.Test

class RunCompletionPayloadTest {

    @Test
    fun `payload from bootcamp run includes phase and session label`() {
        val payload = RunCompletionPayload(
            userId = "uid1",
            timestamp = 1000L,
            distanceMeters = 3200.0,
            routePolyline = "encodedPoly",
            streakCount = 5,
            programPhase = "Week 3 · BUILD",
            sessionLabel = "Day 2 of 4",
            wasScheduled = true,
            originalScheduledWeekDay = null,
            weekDay = 3 // Wednesday
        )
        assertEquals("Week 3 · BUILD", payload.programPhase)
        assertTrue(payload.wasScheduled)
        assertNull(payload.originalScheduledWeekDay)
    }

    @Test
    fun `payload from free run has null phase and session label`() {
        val payload = RunCompletionPayload(
            userId = "uid1",
            timestamp = 2000L,
            distanceMeters = 5000.0,
            routePolyline = "poly2",
            streakCount = 3,
            programPhase = null,
            sessionLabel = null,
            wasScheduled = false,
            originalScheduledWeekDay = null,
            weekDay = 6 // Saturday
        )
        assertNull(payload.programPhase)
        assertFalse(payload.wasScheduled)
    }

    @Test
    fun `makeup run carries originalScheduledWeekDay`() {
        val payload = RunCompletionPayload(
            userId = "uid1",
            timestamp = 3000L,
            distanceMeters = 4000.0,
            routePolyline = "poly3",
            streakCount = 2,
            programPhase = "Week 2 · BASE",
            sessionLabel = "Day 1 of 3",
            wasScheduled = true,
            originalScheduledWeekDay = 2, // was scheduled Tuesday
            weekDay = 3 // ran on Wednesday
        )
        assertEquals(2, payload.originalScheduledWeekDay)
        assertEquals(3, payload.weekDay)
    }

    @Test
    fun `toMap produces correct Firestore map`() {
        val payload = RunCompletionPayload(
            userId = "uid1",
            timestamp = 1000L,
            distanceMeters = 3200.0,
            routePolyline = "poly",
            streakCount = 5,
            programPhase = "Week 3 · BUILD",
            sessionLabel = "Day 2 of 4",
            wasScheduled = true,
            originalScheduledWeekDay = null,
            weekDay = 3
        )
        val map = payload.toMap()
        assertEquals("uid1", map["userId"])
        assertEquals(1000L, map["timestamp"])
        assertEquals(3200.0, map["distanceMeters"])
        assertEquals(5, map["streakCount"])
        assertEquals(3, map["weekDay"])
        assertFalse(map.containsKey("originalScheduledWeekDay")) // null fields omitted
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.data.firebase.RunCompletionPayloadTest"`
Expected: FAIL — class does not exist

- [ ] **Step 3: Create `RunCompletionPayload.kt`**

```kotlin
package com.hrcoach.data.firebase

data class RunCompletionPayload(
    val userId: String,
    val timestamp: Long,
    val distanceMeters: Double,
    val routePolyline: String,
    val streakCount: Int,
    val programPhase: String?,
    val sessionLabel: String?,
    val wasScheduled: Boolean,
    val originalScheduledWeekDay: Int?, // 1=Mon..7=Sun; non-null for makeup runs
    val weekDay: Int // 1=Mon..7=Sun (ISO 8601)
) {
    /** Convert to Firestore-compatible map, omitting null fields. */
    fun toMap(): Map<String, Any> = buildMap {
        put("userId", userId)
        put("timestamp", timestamp)
        put("distanceMeters", distanceMeters)
        put("routePolyline", routePolyline)
        put("streakCount", streakCount)
        programPhase?.let { put("programPhase", it) }
        sessionLabel?.let { put("sessionLabel", it) }
        put("wasScheduled", wasScheduled)
        originalScheduledWeekDay?.let { put("originalScheduledWeekDay", it) }
        put("weekDay", weekDay)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.data.firebase.RunCompletionPayloadTest"`
Expected: PASS (all 4 tests)

- [ ] **Step 5: Create `PartnerRepository.kt`**

```kotlin
package com.hrcoach.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class PartnerInfo(
    val uid: String,
    val displayName: String,
    val avatarSymbol: String
)

@Singleton
class PartnerRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val authManager: FirebaseAuthManager
) {
    /** Update display name and avatar in Firestore user doc. */
    suspend fun syncUserProfile(displayName: String, avatarSymbol: String) {
        val uid = authManager.uid ?: return
        firestore.collection("users").document(uid)
            .set(
                mapOf("displayName" to displayName, "avatarSymbol" to avatarSymbol),
                SetOptions.merge()
            ).await()
    }

    /** Generate an invite code via Cloud Function. */
    suspend fun createInvite(): String {
        val uid = authManager.uid ?: throw IllegalStateException("Not signed in")
        val result = functions.getHttpsCallable("createInvite")
            .call(mapOf("uid" to uid))
            .await()
        @Suppress("UNCHECKED_CAST")
        val data = result.data as Map<String, Any>
        return data["code"] as String
    }

    /** Accept an invite code via Cloud Function. */
    suspend fun acceptInvite(code: String) {
        val uid = authManager.uid ?: throw IllegalStateException("Not signed in")
        functions.getHttpsCallable("acceptInvite")
            .call(mapOf("uid" to uid, "code" to code))
            .await()
    }

    /** Disconnect from current partner via Firestore transaction. */
    suspend fun disconnect() {
        val uid = authManager.uid ?: return
        val userDoc = firestore.collection("users").document(uid)
        firestore.runTransaction { tx ->
            val snapshot = tx.get(userDoc)
            val partnerId = snapshot.getString("partnerId") ?: return@runTransaction
            tx.update(userDoc, "partnerId", null)
            tx.update(firestore.collection("users").document(partnerId), "partnerId", null)
        }.await()
    }

    /** Observe partner ID changes in real time. Emits null when unpaired. */
    fun observePartnerId(): Flow<String?> = callbackFlow {
        val uid = authManager.uid
        if (uid == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val registration: ListenerRegistration = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getString("partnerId"))
            }
        awaitClose { registration.remove() }
    }

    /** Get partner info (display name, avatar). */
    suspend fun getPartnerInfo(partnerUid: String): PartnerInfo? {
        val doc = firestore.collection("users").document(partnerUid).get().await()
        if (!doc.exists()) return null
        return PartnerInfo(
            uid = partnerUid,
            displayName = doc.getString("displayName") ?: "Runner",
            avatarSymbol = doc.getString("avatarSymbol") ?: "\u2665"
        )
    }

    /** Post a run completion to Firestore. */
    suspend fun postRunCompletion(payload: RunCompletionPayload) {
        firestore.collection("runCompletions")
            .add(payload.toMap())
            .await()
    }

    /** Get partner's recent run completions (last 7 days). */
    suspend fun getPartnerCompletions(partnerUid: String, sinceDaysAgo: Int = 7): List<RunCompletionPayload> {
        val cutoff = System.currentTimeMillis() - (sinceDaysAgo * 86_400_000L)
        val docs = firestore.collection("runCompletions")
            .whereEqualTo("userId", partnerUid)
            .whereGreaterThan("timestamp", cutoff)
            .get()
            .await()
        return docs.map { doc ->
            RunCompletionPayload(
                userId = doc.getString("userId") ?: "",
                timestamp = doc.getLong("timestamp") ?: 0L,
                distanceMeters = doc.getDouble("distanceMeters") ?: 0.0,
                routePolyline = doc.getString("routePolyline") ?: "",
                streakCount = (doc.getLong("streakCount") ?: 0).toInt(),
                programPhase = doc.getString("programPhase"),
                sessionLabel = doc.getString("sessionLabel"),
                wasScheduled = doc.getBoolean("wasScheduled") ?: false,
                originalScheduledWeekDay = doc.getLong("originalScheduledWeekDay")?.toInt(),
                weekDay = (doc.getLong("weekDay") ?: 1).toInt()
            )
        }
    }
}
```

- [ ] **Step 6: Verify build compiles**

Run: `./gradlew assembleDebug`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/firebase/ app/src/test/java/com/hrcoach/data/firebase/
git commit -m "feat(sharing): add RunCompletionPayload, PartnerRepository, and data layer"
```

---

## Task 5: Partner Streak Calculator

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/sharing/PartnerStreakCalculator.kt`
- Test: `app/src/test/.../domain/sharing/PartnerStreakCalculatorTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.hrcoach.domain.sharing

import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.data.db.WorkoutEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PartnerStreakCalculatorTest {

    private val zone = ZoneId.of("UTC")

    // --- Bootcamp runner tests ---

    @Test
    fun `bootcamp streak counts consecutive completed sessions`() {
        val sessions = listOf(
            session(1, 1, BootcampSessionEntity.STATUS_COMPLETED),
            session(1, 3, BootcampSessionEntity.STATUS_COMPLETED),
            session(1, 5, BootcampSessionEntity.STATUS_COMPLETED)
        )
        val streak = PartnerStreakCalculator.computeBootcampStreak(sessions)
        assertEquals(3, streak)
    }

    @Test
    fun `deferred session does not break streak`() {
        val sessions = listOf(
            session(1, 1, BootcampSessionEntity.STATUS_COMPLETED),
            session(1, 3, BootcampSessionEntity.STATUS_DEFERRED),
            session(1, 4, BootcampSessionEntity.STATUS_COMPLETED) // makeup
        )
        val streak = PartnerStreakCalculator.computeBootcampStreak(sessions)
        assertEquals(2, streak)
    }

    @Test
    fun `missed session breaks streak`() {
        val sessions = listOf(
            session(1, 1, BootcampSessionEntity.STATUS_COMPLETED),
            session(1, 3, BootcampSessionEntity.STATUS_SCHEDULED), // missed (past)
            session(1, 5, BootcampSessionEntity.STATUS_COMPLETED)
        )
        val today = LocalDate.of(2026, 3, 22) // after all sessions
        val enrollStart = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val streak = PartnerStreakCalculator.computeBootcampStreak(
            sessions, enrollStart, today, zone
        )
        assertEquals(1, streak) // only last completed counts
    }

    // --- Free runner tests ---

    @Test
    fun `free runner streak counts consecutive days with runs`() {
        val workouts = listOf(
            workout(dayOffset = 0), // today
            workout(dayOffset = -1), // yesterday
            workout(dayOffset = -2) // day before
        )
        val today = LocalDate.of(2026, 3, 22)
        val streak = PartnerStreakCalculator.computeFreeRunnerStreak(workouts, today, zone)
        assertEquals(3, streak)
    }

    @Test
    fun `free runner streak breaks on gap day`() {
        val workouts = listOf(
            workout(dayOffset = 0), // today
            // gap: yesterday
            workout(dayOffset = -2)
        )
        val today = LocalDate.of(2026, 3, 22)
        val streak = PartnerStreakCalculator.computeFreeRunnerStreak(workouts, today, zone)
        assertEquals(1, streak)
    }

    @Test
    fun `free runner with no workouts has zero streak`() {
        val streak = PartnerStreakCalculator.computeFreeRunnerStreak(
            emptyList(), LocalDate.of(2026, 3, 22), zone
        )
        assertEquals(0, streak)
    }

    // --- Helpers ---

    private fun session(week: Int, day: Int, status: String) =
        BootcampSessionEntity(
            enrollmentId = 1L,
            weekNumber = week,
            dayOfWeek = day,
            sessionType = "EASY_RUN",
            targetMinutes = 30,
            status = status
        )

    private val baseDate = LocalDate.of(2026, 3, 22)

    private fun workout(dayOffset: Int): WorkoutEntity {
        val date = baseDate.plusDays(dayOffset.toLong())
        val ms = date.atStartOfDay(zone).toInstant().toEpochMilli() + 36_000_000 // 10am
        return WorkoutEntity(
            startTime = ms,
            endTime = ms + 1_800_000,
            totalDistanceMeters = 3000f,
            mode = "FREE_RUN"
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.sharing.PartnerStreakCalculatorTest"`
Expected: FAIL — class does not exist

- [ ] **Step 3: Implement `PartnerStreakCalculator.kt`**

```kotlin
package com.hrcoach.domain.sharing

import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.data.db.WorkoutEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object PartnerStreakCalculator {

    /**
     * Bootcamp streak: consecutive completed sessions walking backward.
     * Deferred sessions skip (don't break). Past SCHEDULED sessions break.
     */
    fun computeBootcampStreak(
        sessions: List<BootcampSessionEntity>,
        enrollmentStartMs: Long = 0L,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Int {
        val startDate = if (enrollmentStartMs > 0)
            Instant.ofEpochMilli(enrollmentStartMs).atZone(zone).toLocalDate()
        else today.minusDays(365) // fallback: treat all as past

        val sorted = sessions.sortedWith(
            compareByDescending<BootcampSessionEntity> { it.weekNumber }
                .thenByDescending { it.dayOfWeek }
        )

        var streak = 0
        for (session in sorted) {
            when (session.status) {
                BootcampSessionEntity.STATUS_COMPLETED -> streak++
                BootcampSessionEntity.STATUS_SKIPPED -> return streak
                BootcampSessionEntity.STATUS_SCHEDULED -> {
                    val sessionDate = startDate.plusDays(
                        ((session.weekNumber - 1L) * 7L) + (session.dayOfWeek - 1L)
                    )
                    if (sessionDate.isBefore(today)) return streak
                    // future — ignore
                }
                BootcampSessionEntity.STATUS_DEFERRED -> { /* skip, don't break */ }
            }
        }
        return streak
    }

    /**
     * Free runner streak: consecutive calendar days with at least one run,
     * counting backward from today.
     */
    fun computeFreeRunnerStreak(
        workouts: List<WorkoutEntity>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Int {
        if (workouts.isEmpty()) return 0

        val runDays = workouts.map { w ->
            Instant.ofEpochMilli(w.startTime).atZone(zone).toLocalDate()
        }.toSet()

        var streak = 0
        var day = today
        while (runDays.contains(day)) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.sharing.PartnerStreakCalculatorTest"`
Expected: PASS (all 6 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/sharing/ app/src/test/java/com/hrcoach/domain/sharing/
git commit -m "feat(sharing): add PartnerStreakCalculator with bootcamp and free runner modes"
```

---

## Task 6: Week Strip State Computer

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/sharing/WeekStripState.kt`
- Test: `app/src/test/.../domain/sharing/WeekStripStateTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.hrcoach.domain.sharing

import com.hrcoach.data.firebase.RunCompletionPayload
import org.junit.Assert.*
import org.junit.Test

class WeekStripStateTest {

    @Test
    fun `completed scheduled run shows as COMPLETED`() {
        val completions = listOf(
            payload(weekDay = 1, wasScheduled = true, originalScheduledWeekDay = null)
        )
        val states = WeekStripState.compute(completions, todayWeekDay = 5)
        assertEquals(DayState.COMPLETED, states[0]) // Monday
    }

    @Test
    fun `deferred run shows DEFERRED on original day and COMPLETED on actual day`() {
        val completions = listOf(
            payload(weekDay = 3, wasScheduled = true, originalScheduledWeekDay = 2) // Tue->Wed
        )
        val states = WeekStripState.compute(completions, todayWeekDay = 5)
        assertEquals(DayState.DEFERRED, states[1]) // Tuesday (original)
        assertEquals(DayState.COMPLETED, states[2]) // Wednesday (actual)
    }

    @Test
    fun `bonus run shows as BONUS`() {
        val completions = listOf(
            payload(weekDay = 6, wasScheduled = false, originalScheduledWeekDay = null)
        )
        val states = WeekStripState.compute(completions, todayWeekDay = 7)
        assertEquals(DayState.BONUS, states[5]) // Saturday
    }

    @Test
    fun `today with no run shows as TODAY`() {
        val states = WeekStripState.compute(emptyList(), todayWeekDay = 3)
        assertEquals(DayState.TODAY, states[2]) // Wednesday
    }

    @Test
    fun `past day with no run shows as REST`() {
        val states = WeekStripState.compute(emptyList(), todayWeekDay = 5)
        assertEquals(DayState.REST, states[0]) // Monday (past, no run)
    }

    @Test
    fun `future day shows as FUTURE`() {
        val states = WeekStripState.compute(emptyList(), todayWeekDay = 3)
        assertEquals(DayState.FUTURE, states[4]) // Friday (future)
    }

    private fun payload(
        weekDay: Int,
        wasScheduled: Boolean,
        originalScheduledWeekDay: Int?
    ) = RunCompletionPayload(
        userId = "u1",
        timestamp = System.currentTimeMillis(),
        distanceMeters = 3000.0,
        routePolyline = "",
        streakCount = 1,
        programPhase = null,
        sessionLabel = null,
        wasScheduled = wasScheduled,
        originalScheduledWeekDay = originalScheduledWeekDay,
        weekDay = weekDay
    )
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.sharing.WeekStripStateTest"`
Expected: FAIL

- [ ] **Step 3: Implement `WeekStripState.kt`**

```kotlin
package com.hrcoach.domain.sharing

import com.hrcoach.data.firebase.RunCompletionPayload

enum class DayState {
    COMPLETED, // scheduled run completed (green checkmark)
    DEFERRED,  // was scheduled here but deferred to another day (↷)
    BONUS,     // unscheduled run (cyan star)
    TODAY,     // today, no run yet (yellow pending)
    REST,      // past day, no activity (empty)
    FUTURE     // future day (empty, dim)
}

object WeekStripState {

    /**
     * Compute 7 day states (Mon=index 0 .. Sun=index 6) from partner's completions this week.
     * @param completions this week's RunCompletionPayloads for the partner
     * @param todayWeekDay 1=Mon..7=Sun (ISO 8601)
     */
    fun compute(
        completions: List<RunCompletionPayload>,
        todayWeekDay: Int
    ): List<DayState> {
        val states = MutableList(7) { index ->
            val dayNumber = index + 1 // 1-based
            when {
                dayNumber == todayWeekDay -> DayState.TODAY
                dayNumber < todayWeekDay -> DayState.REST
                else -> DayState.FUTURE
            }
        }

        // Mark deferred days first (from makeup runs)
        for (c in completions) {
            val origDay = c.originalScheduledWeekDay
            if (origDay != null && origDay in 1..7) {
                states[origDay - 1] = DayState.DEFERRED
            }
        }

        // Mark actual run days
        for (c in completions) {
            val dayIdx = c.weekDay - 1
            if (dayIdx !in 0..6) continue
            states[dayIdx] = if (c.wasScheduled) DayState.COMPLETED else DayState.BONUS
        }

        return states
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.sharing.WeekStripStateTest"`
Expected: PASS (all 6 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/sharing/WeekStripState.kt app/src/test/java/com/hrcoach/domain/sharing/WeekStripStateTest.kt
git commit -m "feat(sharing): add WeekStripState computer for partner card day states"
```

---

## Task 7: Run Completion Pipeline (WorkoutForegroundService Integration)

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`

- [ ] **Step 1: Add `PartnerRepository` and `BootcampDao` injection to `WorkoutForegroundService`**

Add fields alongside existing injections:
```kotlin
@Inject lateinit var partnerRepository: PartnerRepository
@Inject lateinit var bootcampDao: BootcampDao
```

Note: `BootcampDao` is already provided by `AppModule`. `PartnerRepository` is `@Singleton @Inject constructor` so Hilt can provide it automatically.

- [ ] **Step 2: Add run completion posting at the end of `stopWorkout`**

Insert after `WorkoutState.update { it.copy(completedWorkoutId = workoutId) }` (around line 712), before `cleanupManagers()`:

```kotlin
// --- Post run completion to Firestore for partner sharing ---
runCatching {
    val snapshot = WorkoutState.snapshot.value
    val trackPoints = repository.getTrackPoints(workoutId)
    val polyline = com.google.maps.android.PolyUtil.encode(
        trackPoints.mapNotNull { tp ->
            if (tp.latitude != null && tp.longitude != null)
                com.google.android.gms.maps.model.LatLng(tp.latitude, tp.longitude)
            else null
        }
    )

    // Determine bootcamp context
    val pendingSessionId = WorkoutState.pendingBootcampSessionId
    var programPhase: String? = null
    var sessionLabel: String? = null
    var wasScheduled = false
    var originalScheduledWeekDay: Int? = null

    if (pendingSessionId != null) {
        val session = bootcampDao.getSessionById(pendingSessionId)
        val enrollment = session?.let { bootcampDao.getEnrollmentById(it.enrollmentId) }
        if (session != null && enrollment != null) {
            wasScheduled = true
            programPhase = "Week ${session.weekNumber} · ${enrollment.currentPhase ?: "BASE"}"
            sessionLabel = session.sessionType.replace("_", " ")
            // If session was deferred (rescheduled), track original day
            // originalScheduledWeekDay is set if the session's dayOfWeek differs from today
            val todayDow = java.time.LocalDate.now().dayOfWeek.value
            if (session.dayOfWeek != todayDow) {
                originalScheduledWeekDay = session.dayOfWeek
            }
        }
    }

    // Compute streak
    val streakCount = if (wasScheduled) {
        val enrollment = bootcampDao.getActiveEnrollment()
        val sessions = enrollment?.let { bootcampDao.getSessionsForEnrollmentOnce(it.id) } ?: emptyList()
        PartnerStreakCalculator.computeBootcampStreak(
            sessions,
            enrollment?.startDateMs ?: 0L
        )
    } else {
        val allWorkouts = repository.getAllWorkoutsOnce()
        PartnerStreakCalculator.computeFreeRunnerStreak(allWorkouts)
    }

    val payload = RunCompletionPayload(
        userId = partnerRepository.authManager.uid ?: "",
        timestamp = System.currentTimeMillis(),
        distanceMeters = snapshot.distanceMeters.toDouble(),
        routePolyline = polyline,
        streakCount = streakCount,
        programPhase = programPhase,
        sessionLabel = sessionLabel,
        wasScheduled = wasScheduled,
        originalScheduledWeekDay = originalScheduledWeekDay,
        weekDay = java.time.LocalDate.now().dayOfWeek.value
    )
    partnerRepository.postRunCompletion(payload)
}.onFailure { e ->
    android.util.Log.e("WorkoutService", "Failed to post run completion", e)
}
```

- [ ] **Step 3: Add necessary imports**

Add to imports at top of file:
```kotlin
import com.hrcoach.data.firebase.PartnerRepository
import com.hrcoach.data.firebase.RunCompletionPayload
import com.hrcoach.domain.sharing.PartnerStreakCalculator
```

- [ ] **Step 4: Verify build compiles**

Run: `./gradlew assembleDebug`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt
git commit -m "feat(sharing): post RunCompletionPayload to Firestore after workout ends"
```

---

## Task 8: Partner Hero Card UI

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/partner/WeekProgressTrack.kt`
- Create: `app/src/main/java/com/hrcoach/ui/partner/PartnerHeroCard.kt`

- [ ] **Step 1: Create `WeekProgressTrack.kt`** — the Canvas-drawn connected track composable

This composable renders 7 day nodes connected by a track line. It takes a `List<DayState>` (from `WeekStripState.compute()`) and draws each node according to its state.

```kotlin
package com.hrcoach.ui.partner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.domain.sharing.DayState

private val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val completedColor = Color(0xFF4ADE80)
private val bonusColor = Color(0xFF00D1FF)
private val pendingColor = Color(0xFFFACC15)
private val trackBgColor = Color(0x10FFFFFF)
private val nodeRadius = 10f
private val glowRadius = 16f

@Composable
fun WeekProgressTrack(
    dayStates: List<DayState>,
    modifier: Modifier = Modifier
) {
    require(dayStates.size == 7) { "dayStates must have 7 elements" }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
        ) {
            val totalWidth = size.width
            val spacing = totalWidth / 6f
            val centerY = size.height / 2f

            // Background track line
            drawLine(
                color = trackBgColor,
                start = Offset(0f, centerY),
                end = Offset(totalWidth, centerY),
                strokeWidth = 2f
            )

            // Green progress line up to last completed day
            val lastCompletedIdx = dayStates.indexOfLast {
                it == DayState.COMPLETED || it == DayState.BONUS
            }
            if (lastCompletedIdx >= 0) {
                drawLine(
                    color = completedColor.copy(alpha = 0.6f),
                    start = Offset(0f, centerY),
                    end = Offset(lastCompletedIdx * spacing, centerY),
                    strokeWidth = 2f
                )
            }

            // Draw nodes
            for (i in 0..6) {
                val cx = i * spacing
                drawDayNode(dayStates[i], cx, centerY)
            }
        }

        Spacer(Modifier.height(4.dp))

        // Day labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            dayStates.forEachIndexed { i, state ->
                val color = when (state) {
                    DayState.COMPLETED -> completedColor.copy(alpha = 0.7f)
                    DayState.BONUS -> bonusColor.copy(alpha = 0.7f)
                    DayState.TODAY -> pendingColor.copy(alpha = 0.8f)
                    DayState.DEFERRED -> Color.White.copy(alpha = 0.2f)
                    else -> Color.White.copy(alpha = 0.35f)
                }
                Text(
                    text = dayLabels[i],
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = if (state == DayState.TODAY) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = color
                )
            }
        }
    }
}

private fun DrawScope.drawDayNode(state: DayState, cx: Float, cy: Float) {
    when (state) {
        DayState.COMPLETED -> {
            // Glow
            drawCircle(completedColor.copy(alpha = 0.2f), glowRadius, Offset(cx, cy))
            // Filled circle
            drawCircle(completedColor, nodeRadius, Offset(cx, cy))
            // Checkmark
            val path = Path().apply {
                moveTo(cx - 4f, cy)
                lineTo(cx - 1f, cy + 3f)
                lineTo(cx + 5f, cy - 3f)
            }
            drawPath(path, Color(0xFF0F1623), style = Stroke(width = 2f))
        }
        DayState.DEFERRED -> {
            drawCircle(
                Color.White.copy(alpha = 0.04f), nodeRadius, Offset(cx, cy)
            )
            drawCircle(
                Color.White.copy(alpha = 0.15f), nodeRadius, Offset(cx, cy),
                style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
            )
        }
        DayState.BONUS -> {
            drawCircle(bonusColor.copy(alpha = 0.15f), glowRadius, Offset(cx, cy))
            drawCircle(bonusColor.copy(alpha = 0.5f), nodeRadius, Offset(cx, cy), style = Stroke(2f))
            // Star (simplified as a smaller circle for Canvas — real star via drawPath)
            drawCircle(bonusColor, 4f, Offset(cx, cy))
        }
        DayState.TODAY -> {
            drawCircle(pendingColor.copy(alpha = 0.1f), glowRadius, Offset(cx, cy))
            drawCircle(pendingColor.copy(alpha = 0.5f), nodeRadius + 1f, Offset(cx, cy), style = Stroke(2f))
            drawCircle(pendingColor, 3f, Offset(cx, cy))
        }
        DayState.REST, DayState.FUTURE -> {
            drawCircle(Color.White.copy(alpha = 0.04f), nodeRadius, Offset(cx, cy))
            drawCircle(Color.White.copy(alpha = 0.06f), nodeRadius, Offset(cx, cy), style = Stroke(1f))
        }
    }
}
```

- [ ] **Step 2: Create `PartnerHeroCard.kt`**

```kotlin
package com.hrcoach.ui.partner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.domain.sharing.DayState
import com.hrcoach.ui.theme.CardeaTheme

data class PartnerCardState(
    val displayName: String = "Runner",
    val avatarSymbol: String = "\u2665",
    val statusText: String = "",
    val statusColor: Color = Color.White,
    val streakCount: Int = 0,
    val dayStates: List<DayState> = List(7) { DayState.REST },
    val programPhase: String? = null,
    val latestCompletionId: String? = null
)

@Composable
fun PartnerHeroCard(
    state: PartnerCardState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xEB0F1623),
                        Color(0xF2141C2A)
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column {
            // Header row: avatar + name/status + streak
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFFF5A5F), Color(0xFF5B5BFF))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.avatarSymbol,
                        fontSize = 20.sp
                    )
                }

                // Name + status
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.displayName,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = CardeaTheme.colors.textPrimary
                    )
                    Text(
                        text = state.statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = state.statusColor
                    )
                }

                // Streak badge
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0x1AFF5A5F),
                                    Color(0x1A00D1FF)
                                )
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${state.streakCount}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFFF5A5F), Color(0xFF00D1FF))
                            )
                        )
                    )
                    Text(
                        text = "STREAK",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Week progress track
            WeekProgressTrack(dayStates = state.dayStates)

            // Phase label
            if (state.programPhase != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = state.programPhase,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.3.sp),
                    color = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}
```

- [ ] **Step 3: Verify build compiles**

Run: `./gradlew assembleDebug`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/partner/
git commit -m "feat(sharing): add PartnerHeroCard and WeekProgressTrack composables"
```

---

## Task 9: Home Screen Integration

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt`

- [ ] **Step 1: Add partner fields to `HomeUiState`**

Add after existing fields:
```kotlin
val partnerState: PartnerCardState? = null, // null = not paired
```

Add import:
```kotlin
import com.hrcoach.ui.partner.PartnerCardState
```

- [ ] **Step 2: Add `PartnerRepository` to `HomeViewModel` and observe partner state**

Add injection:
```kotlin
private val partnerRepository: PartnerRepository
```

In the `init` block (or wherever the existing combine flow is), add a partner observation flow that:
1. Observes `partnerRepository.observePartnerId()`
2. When non-null, fetches `getPartnerInfo()` and `getPartnerCompletions()`
3. Computes `WeekStripState` and `PartnerCardState`
4. Updates `HomeUiState.partnerState`

The exact integration depends on the existing flow structure — the implementer should merge the partner flow into the existing `combine(...).flatMapLatest` pipeline.

Key computation:
```kotlin
val completions = partnerRepository.getPartnerCompletions(partnerId)
val todayDow = java.time.LocalDate.now().dayOfWeek.value
val dayStates = WeekStripState.compute(completions, todayDow)
val todayCompletion = completions.firstOrNull { it.weekDay == todayDow }
val statusText = if (todayCompletion != null) {
    "Ran today · %.1f km".format(todayCompletion.distanceMeters / 1000.0)
} else "Rest day"
val statusColor = if (todayCompletion != null) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.4f)
val latestPhase = completions.firstOrNull()?.programPhase
```

- [ ] **Step 3: Update `HomeScreen` to show `PartnerHeroCard` when paired**

In `HomeScreen`, replace the hero card section with a `when` block:

```kotlin
val uiState = viewModel.uiState.collectAsStateWithLifecycle()

// Hero card: partner wins if paired
if (uiState.partnerState != null) {
    PartnerHeroCard(
        state = uiState.partnerState,
        onClick = { /* navigate to partner detail */ }
    )
} else if (uiState.hasActiveBootcamp && uiState.nextSession != null) {
    BootcampHeroCard(/* existing params */)
} else {
    NoBootcampCard(/* existing params */)
}
```

- [ ] **Step 4: Verify build compiles**

Run: `./gradlew assembleDebug`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/home/
git commit -m "feat(sharing): integrate PartnerHeroCard into HomeScreen with progressive states"
```

---

## Task 10: Account Screen — Partner Section

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/account/PartnerSection.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt`

- [ ] **Step 1: Add partner state to `AccountUiState`**

Add fields:
```kotlin
val partnerName: String? = null,
val partnerAvatar: String? = null,
val isPaired: Boolean = false,
val inviteCode: String? = null,
val isGeneratingCode: Boolean = false,
val isJoining: Boolean = false,
val pairError: String? = null,
```

- [ ] **Step 2: Add pairing methods to `AccountViewModel`**

Inject `PartnerRepository`. Add methods:
- `generateInviteCode()` — calls `partnerRepository.createInvite()`, sets `inviteCode` in state
- `acceptInvite(code: String)` — calls `partnerRepository.acceptInvite(code)`, refreshes partner state
- `disconnect()` — calls `partnerRepository.disconnect()`
- Observe `partnerRepository.observePartnerId()` in `init` block

- [ ] **Step 3: Create `PartnerSection.kt` composable**

GlassCard with two states:
- **Unpaired:** "Invite Partner" (gradient button) + "Join Partner" (outline button). "Join" expands to show a 6-char code input with clipboard detection.
- **Paired:** Partner name + avatar row + "Disconnect Partner" text button (muted red).

The invite flow shows a dialog with the generated code and a "Share" button that fires an `Intent.ACTION_SEND`.

- [ ] **Step 4: Add `PartnerSection` to `AccountScreen`**

Insert after the profile card section, before settings.

- [ ] **Step 5: Verify build compiles**

Run: `./gradlew assembleDebug`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/account/
git commit -m "feat(sharing): add partner pairing section to AccountScreen"
```

---

## Task 11: Partner Detail Screen & Navigation

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/partner/PartnerDetailScreen.kt`
- Create: `app/src/main/java/com/hrcoach/ui/partner/PartnerDetailViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/hrcoach/MainActivity.kt`

- [ ] **Step 1: Create `PartnerDetailViewModel.kt`**

Loads a specific `RunCompletionPayload` from Firestore by document ID. Exposes `UiState` with: route polyline, distance, streak, phase, session label.

- [ ] **Step 2: Create `PartnerDetailScreen.kt`**

Read-only screen:
- Google Maps composable rendering the route polyline (reuse pattern from `HistoryDetailScreen`)
- Distance text
- Streak count
- Program phase + session label (collapsed for free runners)
- Back button in top bar

- [ ] **Step 3: Add route to `NavGraph.kt`**

Add composable route: `partnerDetail/{completionId}`

- [ ] **Step 4: Handle deep link intent in `MainActivity.kt`**

In `onCreate` and `onNewIntent`, check for:
- `EXTRA_PAIR_CODE` → navigate to `account` with code pre-filled
- `EXTRA_COMPLETION_ID` → navigate to `partnerDetail/{id}`

- [ ] **Step 5: Verify build compiles**

Run: `./gradlew assembleDebug`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/partner/ app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt app/src/main/java/com/hrcoach/MainActivity.kt
git commit -m "feat(sharing): add PartnerDetailScreen, navigation route, and deep link handling"
```

---

## Task 12: Cloud Functions (Firebase Server-Side)

**Files:**
- Create: `functions/package.json`
- Create: `functions/tsconfig.json`
- Create: `functions/src/index.ts`

- [ ] **Step 1: Create `functions/package.json`**

```json
{
  "name": "cardea-functions",
  "scripts": {
    "build": "tsc",
    "serve": "firebase emulators:start --only functions",
    "deploy": "firebase deploy --only functions"
  },
  "main": "lib/index.js",
  "dependencies": {
    "firebase-admin": "^12.0.0",
    "firebase-functions": "^5.0.0"
  },
  "devDependencies": {
    "typescript": "^5.4.0"
  }
}
```

- [ ] **Step 2: Create `functions/tsconfig.json`**

```json
{
  "compilerOptions": {
    "module": "commonjs",
    "noImplicitReturns": true,
    "outDir": "lib",
    "sourceMap": true,
    "strict": true,
    "target": "es2017"
  },
  "compileOnSave": true,
  "include": ["src"]
}
```

- [ ] **Step 3: Create `functions/src/index.ts`**

```typescript
import * as admin from "firebase-admin";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";

admin.initializeApp();
const db = admin.firestore();

// --- createInvite ---
export const createInvite = onCall(async (request) => {
  const uid = request.data.uid;
  if (!uid) throw new HttpsError("invalid-argument", "uid required");

  const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  for (let attempt = 0; attempt < 3; attempt++) {
    let code = "";
    for (let i = 0; i < 6; i++) {
      code += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    const docRef = db.collection("pairings").doc(code);
    const existing = await docRef.get();
    if (!existing.exists) {
      await docRef.set({
        creatorId: uid,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000),
      });
      return { code };
    }
  }
  throw new HttpsError("unavailable", "Could not generate unique code");
});

// --- acceptInvite ---
export const acceptInvite = onCall(async (request) => {
  const { uid, code } = request.data;
  if (!uid || !code) throw new HttpsError("invalid-argument", "uid and code required");

  const pairingRef = db.collection("pairings").doc(code);

  await db.runTransaction(async (tx) => {
    const pairing = await tx.get(pairingRef);
    if (!pairing.exists) throw new HttpsError("not-found", "Invalid code");

    const data = pairing.data()!;
    if (data.expiresAt.toDate() < new Date()) {
      throw new HttpsError("deadline-exceeded", "Code expired");
    }

    const creatorId = data.creatorId;
    if (creatorId === uid) {
      throw new HttpsError("invalid-argument", "Cannot pair with yourself");
    }

    const creatorRef = db.collection("users").doc(creatorId);
    const acceptorRef = db.collection("users").doc(uid);

    tx.update(creatorRef, { partnerId: uid });
    tx.update(acceptorRef, { partnerId: creatorId });
    tx.delete(pairingRef);
  });

  // Send FCM to both
  const [creatorDoc, acceptorDoc] = await Promise.all([
    db.collection("users").doc(request.data.uid).get(),
    // We need to look up the creator from the pairing — but we just deleted it.
    // Instead, read both users after the transaction.
    Promise.resolve(null),
  ]);
  // Re-read both user docs to get partner info + tokens
  const acceptorSnap = await db.collection("users").doc(uid).get();
  const partnerId = acceptorSnap.data()?.partnerId;
  if (partnerId) {
    const creatorSnap = await db.collection("users").doc(partnerId).get();
    const creatorToken = creatorSnap.data()?.fcmToken;
    const acceptorToken = acceptorSnap.data()?.fcmToken;
    const creatorName = creatorSnap.data()?.displayName ?? "Your partner";
    const acceptorName = acceptorSnap.data()?.displayName ?? "Your partner";

    const notifications = [];
    if (creatorToken) {
      notifications.push(
        admin.messaging().send({
          token: creatorToken,
          data: { title: "Cardea", body: `You're now connected with ${acceptorName}!` },
        })
      );
    }
    if (acceptorToken) {
      notifications.push(
        admin.messaging().send({
          token: acceptorToken,
          data: { title: "Cardea", body: `You're now connected with ${creatorName}!` },
        })
      );
    }
    await Promise.allSettled(notifications);
  }

  return { success: true };
});

// --- onRunCompleted ---
export const onRunCompleted = onDocumentCreated("runCompletions/{docId}", async (event) => {
  const data = event.data?.data();
  if (!data) return;

  const userId = data.userId;
  const userDoc = await db.collection("users").doc(userId).get();
  const partnerId = userDoc.data()?.partnerId;
  if (!partnerId) return;

  const partnerDoc = await db.collection("users").doc(partnerId).get();
  const partnerToken = partnerDoc.data()?.fcmToken;
  if (!partnerToken) return;

  const userName = userDoc.data()?.displayName ?? "Your partner";

  await admin.messaging().send({
    token: partnerToken,
    data: {
      title: "Cardea",
      body: `${userName} just finished their run!`,
      completionId: event.data?.id ?? "",
    },
  });
});

// --- cleanupOldCompletions (weekly) ---
export const cleanupOldCompletions = onSchedule("every sunday 02:00", async () => {
  const cutoff = Date.now() - 90 * 24 * 60 * 60 * 1000;
  const old = await db
    .collection("runCompletions")
    .where("timestamp", "<", cutoff)
    .limit(500)
    .get();

  const batch = db.batch();
  old.docs.forEach((doc) => batch.delete(doc.ref));
  await batch.commit();
});
```

- [ ] **Step 4: Commit**

```bash
git add functions/
git commit -m "feat(sharing): add Cloud Functions — createInvite, acceptInvite, onRunCompleted, cleanup"
```

> **Manual step:** Deploy with `cd functions && npm install && npm run build && firebase deploy --only functions`

---

## Task 13: Firebase Hosting & Deep Link Redirect Page

**Files:**
- Create: `firebase-hosting/public/pair.html`
- Create: `firebase-hosting/public/.well-known/assetlinks.json`
- Create: `firebase-hosting/firebase.json`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create `firebase.json` hosting config**

```json
{
  "hosting": {
    "public": "public",
    "rewrites": [
      {
        "source": "/pair/**",
        "destination": "/pair.html"
      }
    ]
  }
}
```

- [ ] **Step 2: Create `pair.html` redirect page**

```html
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>Cardea — Join Partner</title>
  <script>
    const path = window.location.pathname; // e.g. /pair/K7X2M9
    const code = path.split('/').pop();
    const intentUri = 'intent://pair/' + code +
      '#Intent;scheme=https;package=com.hrcoach;' +
      'S.EXTRA_PAIR_CODE=' + code + ';end';
    // Try App Link first, then fall back to Play Store
    window.location.replace(intentUri);
    setTimeout(function() {
      window.location.replace('https://play.google.com/store/apps/details?id=com.hrcoach');
    }, 1500);
  </script>
</head>
<body>
  <p>Redirecting to Cardea...</p>
  <p><a href="https://play.google.com/store/apps/details?id=com.hrcoach">
    Open in Play Store
  </a></p>
</body>
</html>
```

- [ ] **Step 3: Create `assetlinks.json` for App Links verification**

```json
[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "com.hrcoach",
    "sha256_cert_fingerprints": ["TODO:INSERT_SHA256_FROM_SIGNING_KEY"]
  }
}]
```

> **Manual step:** Replace the SHA-256 fingerprint with the actual signing key fingerprint from `./gradlew signingReport`.

- [ ] **Step 4: Add App Links intent filter to `AndroidManifest.xml`**

Add inside `<activity android:name=".MainActivity">`:
```xml
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="https" android:host="cardea.app" android:pathPrefix="/pair/" />
</intent-filter>
```

- [ ] **Step 5: Commit**

```bash
git add firebase-hosting/ app/src/main/AndroidManifest.xml
git commit -m "feat(sharing): add Firebase Hosting deep link page and Android App Links config"
```

> **Manual step:** Deploy with `cd firebase-hosting && firebase deploy --only hosting`. Domain `cardea.app` must be verified in Firebase Console and DNS pointed to Firebase Hosting.

---

## Task 14: Notification Permission Request & Google Account Linking

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/account/PartnerSection.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/data/firebase/FirebaseAuthManager.kt`

- [ ] **Step 1: Add notification permission request to `PartnerSection`**

When the user first pairs (either via `generateInviteCode()` or `acceptInvite()`), request `POST_NOTIFICATIONS` permission using `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`. Only request on Android 13+.

```kotlin
val notificationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { /* no-op — feature works without it */ }

// Call after successful pairing:
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
}
```

- [ ] **Step 2: Add "Enable Notifications" banner when permission denied**

In `PartnerSection`, when paired, check if `POST_NOTIFICATIONS` is denied. If so, show a subtle row:

```kotlin
val context = LocalContext.current
val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
} else true

if (isPaired && !hasNotificationPermission) {
    Row(/* subtle banner style */) {
        Text("Notifications disabled", style = ...)
        TextButton(onClick = {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            context.startActivity(intent)
        }) {
            Text("Enable")
        }
    }
}
```

- [ ] **Step 3: Add Google account linking to `FirebaseAuthManager`**

```kotlin
suspend fun linkGoogleAccount(idToken: String) {
    val credential = GoogleAuthProvider.getCredential(idToken, null)
    auth.currentUser?.linkWithCredential(credential)?.await()
}

val isLinkedToGoogle: Boolean
    get() = auth.currentUser?.providerData?.any { it.providerId == "google.com" } == true
```

- [ ] **Step 4: Add "Link Google Account" prompt to Account screen**

After successful pairing, if `!authManager.isLinkedToGoogle`, show a non-blocking card:

> "Link your Google account to keep your partnership if you switch phones."

With a "Link" button that triggers Google Sign-In flow and calls `linkGoogleAccount(idToken)`. Add a "Later" dismiss button. If linked, show a green checkmark: "Linked to Google".

This should be a separate `GlassCard` below the partner section.

- [ ] **Step 5: Add foreground resume partner refresh**

In `HomeViewModel`, add a `refreshPartnerData()` method called from `init` and from a lifecycle observer (`Lifecycle.Event.ON_RESUME`):

```kotlin
fun refreshPartnerData() {
    viewModelScope.launch {
        val partnerId = partnerRepository.observePartnerId().first() ?: return@launch
        val completions = partnerRepository.getPartnerCompletions(partnerId)
        // Update partnerState in uiState with fresh data
    }
}
```

The `HomeScreen` composable should call `refreshPartnerData` from a `LifecycleEventEffect(Lifecycle.Event.ON_RESUME)`.

- [ ] **Step 6: Verify build compiles**

Run: `./gradlew assembleDebug`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/hrcoach/
git commit -m "feat(sharing): add notification permission flow, Google linking, and foreground refresh"
```

---

## Task 15: Final Integration Test & Smoke Check

- [ ] **Step 1: Run all unit tests**

Run: `./gradlew testDebugUnitTest`
Expected: All existing + new tests pass

- [ ] **Step 2: Verify build compiles end-to-end**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Manual smoke test checklist** (on device/emulator with `google-services.json`)

1. Launch app → Firebase anonymous auth happens silently
2. Account screen → "Invite Partner" generates a code
3. Share code → deep link works from `cardea.app/pair/CODE` (if hosting deployed)
4. On second device: "Join Partner" with code → both see "Connected!" notification
5. POST_NOTIFICATIONS permission dialog appears on Android 13+
6. "Link Google Account" card appears after pairing
7. Run a workout → partner receives push notification
8. Home screen shows partner hero card with week strip
9. Tap card → opens partner detail with route map
10. Minimize and re-open app → partner card refreshes from Firestore

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "feat(sharing): accountability sharing — complete implementation"
```
