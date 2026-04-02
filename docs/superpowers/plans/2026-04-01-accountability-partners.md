# Accountability Partners Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add lightweight accountability partner pairing (1–3 partners) with Firebase RTDB sync, push notifications via Cloud Functions, and UI in AccountScreen + HomeScreen.

**Architecture:** Firebase RTDB stores per-user activity summaries and partner links. Anonymous Firebase Auth provides UIDs for security rules. FCM push notifications are triggered by a Cloud Function on activity updates. Client reads partner data via real-time RTDB listeners exposed as Kotlin Flows.

**Tech Stack:** Firebase RTDB, Firebase Auth (Anonymous), Firebase Cloud Messaging, Firebase Cloud Functions (TypeScript), Hilt DI, Jetpack Compose, Kotlin Coroutines/Flow

**Design Spec:** `docs/superpowers/specs/2026-04-01-accountability-partners-design.md`

---

## File Structure

### New Files

| File | Responsibility |
|------|---------------|
| `app/src/main/java/com/hrcoach/data/firebase/FirebaseAuthManager.kt` | Anonymous auth sign-in, UID mapping to UserProfileRepository |
| `app/src/main/java/com/hrcoach/data/firebase/FcmTokenManager.kt` | FCM token retrieval, refresh, RTDB sync |
| `app/src/main/java/com/hrcoach/data/firebase/FirebasePartnerRepository.kt` | RTDB CRUD: activity sync, invite codes, partner list, real-time listeners |
| `app/src/main/java/com/hrcoach/domain/model/PartnerActivity.kt` | Data class for partner state |
| `app/src/main/java/com/hrcoach/service/PartnerActivityNotificationService.kt` | `FirebaseMessagingService` subclass for FCM |
| `app/src/main/java/com/hrcoach/ui/account/PartnerSection.kt` | Partner list composable, add partner bottom sheet, empty state |
| `app/src/main/java/com/hrcoach/ui/home/PartnerNudgeBanner.kt` | HomeScreen nudge banner composable |
| `app/src/test/java/com/hrcoach/domain/model/PartnerActivityTest.kt` | Unit tests for PartnerActivity |
| `app/src/test/java/com/hrcoach/data/firebase/InviteCodeGeneratorTest.kt` | Unit tests for invite code generation |
| `app/src/test/java/com/hrcoach/ui/home/NudgeBannerVisibilityTest.kt` | Unit tests for nudge banner visibility rules |
| `functions/index.ts` | Cloud Function: FCM push on activity update |
| `functions/package.json` | Cloud Functions Node dependencies |
| `firebase.json` | Firebase project config |
| `database.rules.json` | RTDB security rules |

### Modified Files

| File | Change |
|------|--------|
| `app/build.gradle.kts` | Add Firebase RTDB, Auth, Messaging dependencies |
| `app/src/main/AndroidManifest.xml` | FCM service declaration |
| `app/src/main/java/com/hrcoach/di/AppModule.kt` | Provide Firebase managers and repository |
| `app/src/main/java/com/hrcoach/data/repository/UserProfileRepository.kt` | Add partner nudges pref + Firebase UID setter |
| `app/src/main/java/com/hrcoach/ui/account/AccountViewModel.kt` | Partner list state, add/remove methods |
| `app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt` | Insert PartnerSection |
| `app/src/main/java/com/hrcoach/ui/home/HomeViewModel.kt` | Partner nudge state |
| `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt` | Insert PartnerNudgeBanner |
| `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt` | Sync activity to RTDB after workout |

---

## Task 1: Firebase Dependencies & Project Config

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `firebase.json`
- Create: `database.rules.json`

- [ ] **Step 1: Add Firebase dependencies to build.gradle.kts**

In `app/build.gradle.kts`, add inside the `dependencies {}` block after the existing `implementation(platform("com.google.firebase:firebase-bom:33.8.0"))` and `implementation("com.google.firebase:firebase-analytics")` lines:

```kotlin
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-messaging")
```

- [ ] **Step 2: Create firebase.json**

```json
{
  "database": {
    "rules": "database.rules.json"
  },
  "functions": {
    "source": "functions"
  }
}
```

- [ ] **Step 3: Create database.rules.json**

```json
{
  "rules": {
    "users": {
      "$uid": {
        ".read": "auth.uid === $uid || root.child('users').child(auth.uid).child('partners').child($uid).exists()",
        ".write": "auth.uid === $uid",
        "partners": {
          ".validate": "newData.numChildren() <= 3",
          "$partnerId": {
            ".write": "auth.uid === $uid || auth.uid === $partnerId",
            ".validate": "$partnerId !== auth.uid"
          }
        }
      }
    },
    "invites": {
      "$code": {
        ".read": true,
        ".write": "!data.exists() || data.child('userId').val() === auth.uid"
      }
    }
  }
}
```

- [ ] **Step 4: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (Firebase dependencies resolve via existing BOM)

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts firebase.json database.rules.json
git commit -m "feat(partners): add Firebase RTDB, Auth, Messaging dependencies and security rules"
```

---

## Task 2: PartnerActivity Data Class + Tests

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/model/PartnerActivity.kt`
- Create: `app/src/test/java/com/hrcoach/domain/model/PartnerActivityTest.kt`

- [ ] **Step 1: Write failing test for PartnerActivity**

```kotlin
package com.hrcoach.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartnerActivityTest {

    @Test
    fun `create partner activity with all fields`() {
        val partner = PartnerActivity(
            userId = "abc-123",
            displayName = "Sarah",
            emblemId = "bolt",
            currentStreak = 12,
            weeklyRunCount = 3,
            lastRunDate = "2026-04-01",
            lastRunDurationMin = 30,
            lastRunPhase = "Base building",
        )
        assertEquals("abc-123", partner.userId)
        assertEquals("Sarah", partner.displayName)
        assertEquals("bolt", partner.emblemId)
        assertEquals(12, partner.currentStreak)
        assertEquals(3, partner.weeklyRunCount)
        assertEquals("2026-04-01", partner.lastRunDate)
        assertEquals(30, partner.lastRunDurationMin)
        assertEquals("Base building", partner.lastRunPhase)
    }

    @Test
    fun `nullable fields default to null`() {
        val partner = PartnerActivity(
            userId = "abc-123",
            displayName = "Mike",
            emblemId = "pulse",
            currentStreak = 0,
            weeklyRunCount = 0,
            lastRunDate = null,
            lastRunDurationMin = null,
            lastRunPhase = null,
        )
        assertNull(partner.lastRunDate)
        assertNull(partner.lastRunDurationMin)
        assertNull(partner.lastRunPhase)
    }

    @Test
    fun `ranToday returns true when lastRunDate is today`() {
        val today = java.time.LocalDate.now().toString()
        val partner = PartnerActivity(
            userId = "abc-123",
            displayName = "Sarah",
            emblemId = "bolt",
            currentStreak = 1,
            weeklyRunCount = 1,
            lastRunDate = today,
            lastRunDurationMin = 30,
            lastRunPhase = "Base building",
        )
        assertTrue(partner.ranToday())
    }

    @Test
    fun `ranToday returns false when lastRunDate is yesterday`() {
        val yesterday = java.time.LocalDate.now().minusDays(1).toString()
        val partner = PartnerActivity(
            userId = "abc-123",
            displayName = "Sarah",
            emblemId = "bolt",
            currentStreak = 1,
            weeklyRunCount = 1,
            lastRunDate = yesterday,
            lastRunDurationMin = null,
            lastRunPhase = null,
        )
        assertTrue(!partner.ranToday())
    }

    @Test
    fun `isRecentlyActive returns true within 48 hours`() {
        val yesterday = java.time.LocalDate.now().minusDays(1).toString()
        val partner = PartnerActivity(
            userId = "abc-123",
            displayName = "Sarah",
            emblemId = "bolt",
            currentStreak = 1,
            weeklyRunCount = 1,
            lastRunDate = yesterday,
            lastRunDurationMin = null,
            lastRunPhase = null,
        )
        assertTrue(partner.isRecentlyActive())
    }

    @Test
    fun `isRecentlyActive returns false after 3 days`() {
        val threeDaysAgo = java.time.LocalDate.now().minusDays(3).toString()
        val partner = PartnerActivity(
            userId = "abc-123",
            displayName = "Sarah",
            emblemId = "bolt",
            currentStreak = 0,
            weeklyRunCount = 0,
            lastRunDate = threeDaysAgo,
            lastRunDurationMin = null,
            lastRunPhase = null,
        )
        assertTrue(!partner.isRecentlyActive())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.model.PartnerActivityTest"`
Expected: FAIL — `PartnerActivity` class not found

- [ ] **Step 3: Implement PartnerActivity**

```kotlin
package com.hrcoach.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class PartnerActivity(
    val userId: String,
    val displayName: String,
    val emblemId: String,
    val currentStreak: Int,
    val weeklyRunCount: Int,
    val lastRunDate: String?,
    val lastRunDurationMin: Int?,
    val lastRunPhase: String?,
) {
    fun ranToday(): Boolean {
        val date = lastRunDate ?: return false
        return LocalDate.parse(date) == LocalDate.now()
    }

    fun isRecentlyActive(): Boolean {
        val date = lastRunDate ?: return false
        val daysSince = ChronoUnit.DAYS.between(LocalDate.parse(date), LocalDate.now())
        return daysSince <= 2
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.model.PartnerActivityTest"`
Expected: All 6 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/model/PartnerActivity.kt app/src/test/java/com/hrcoach/domain/model/PartnerActivityTest.kt
git commit -m "feat(partners): add PartnerActivity data class with helper methods"
```

---

## Task 3: Invite Code Generator + Tests

**Files:**
- Create: `app/src/test/java/com/hrcoach/data/firebase/InviteCodeGeneratorTest.kt`

The invite code generator will be a top-level function in `FirebasePartnerRepository.kt` (created in Task 5), but we test it standalone first.

- [ ] **Step 1: Write failing test for invite code generation**

```kotlin
package com.hrcoach.data.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteCodeGeneratorTest {

    @Test
    fun `generated code is 6 characters`() {
        val code = generateInviteCode()
        assertEquals(6, code.length)
    }

    @Test
    fun `generated code contains only allowed characters`() {
        val allowed = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        repeat(100) {
            val code = generateInviteCode()
            code.forEach { char ->
                assertTrue(
                    "Unexpected character '$char' in code '$code'",
                    char in allowed
                )
            }
        }
    }

    @Test
    fun `excluded ambiguous characters are never present`() {
        val ambiguous = setOf('0', 'O', '1', 'I', 'L')
        repeat(200) {
            val code = generateInviteCode()
            code.forEach { char ->
                assertTrue(
                    "Ambiguous character '$char' found in code '$code'",
                    char !in ambiguous
                )
            }
        }
    }

    @Test
    fun `two generated codes are different`() {
        val codes = (1..50).map { generateInviteCode() }.toSet()
        assertTrue("Expected at least 40 unique codes out of 50", codes.size >= 40)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.data.firebase.InviteCodeGeneratorTest"`
Expected: FAIL — `generateInviteCode` not found

- [ ] **Step 3: Implement generateInviteCode**

Create `app/src/main/java/com/hrcoach/data/firebase/InviteCodeGenerator.kt`:

```kotlin
package com.hrcoach.data.firebase

import kotlin.random.Random

private val ALLOWED_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

fun generateInviteCode(): String {
    return (1..6)
        .map { ALLOWED_CHARS[Random.nextInt(ALLOWED_CHARS.length)] }
        .joinToString("")
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.data.firebase.InviteCodeGeneratorTest"`
Expected: All 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/firebase/InviteCodeGenerator.kt app/src/test/java/com/hrcoach/data/firebase/InviteCodeGeneratorTest.kt
git commit -m "feat(partners): add invite code generator with ambiguous char exclusion"
```

---

## Task 4: FirebaseAuthManager

**Files:**
- Create: `app/src/main/java/com/hrcoach/data/firebase/FirebaseAuthManager.kt`
- Modify: `app/src/main/java/com/hrcoach/data/repository/UserProfileRepository.kt`

- [ ] **Step 1: Add Firebase UID setter to UserProfileRepository**

In `UserProfileRepository.kt`, add after the existing `getUserId()` method (after line 67):

```kotlin
    @Synchronized
    fun setUserId(id: String) {
        prefs.edit().putString(PREF_USER_ID, id).apply()
    }

    @Synchronized
    fun isPartnerNudgesEnabled(): Boolean {
        return prefs.getBoolean(PREF_PARTNER_NUDGES_ENABLED, true)
    }

    @Synchronized
    fun setPartnerNudgesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_PARTNER_NUDGES_ENABLED, enabled).apply()
    }
```

Also add the constant in the companion object:

```kotlin
        private const val PREF_PARTNER_NUDGES_ENABLED = "partner_nudges_enabled"
```

- [ ] **Step 2: Implement FirebaseAuthManager**

```kotlin
package com.hrcoach.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.hrcoach.data.repository.UserProfileRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthManager @Inject constructor(
    private val auth: FirebaseAuth,
    private val userProfileRepository: UserProfileRepository,
) {
    suspend fun ensureSignedIn(): String {
        val currentUser = auth.currentUser
        if (currentUser != null) return currentUser.uid

        val result = auth.signInAnonymously().await()
        val uid = result.user?.uid ?: throw IllegalStateException("Anonymous auth returned null UID")
        userProfileRepository.setUserId(uid)
        return uid
    }

    fun getCurrentUid(): String? = auth.currentUser?.uid
}
```

- [ ] **Step 3: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/firebase/FirebaseAuthManager.kt app/src/main/java/com/hrcoach/data/repository/UserProfileRepository.kt
git commit -m "feat(partners): add FirebaseAuthManager for anonymous auth + UserProfile nudges pref"
```

---

## Task 5: FcmTokenManager + Notification Service

**Files:**
- Create: `app/src/main/java/com/hrcoach/data/firebase/FcmTokenManager.kt`
- Create: `app/src/main/java/com/hrcoach/service/PartnerActivityNotificationService.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Implement FcmTokenManager**

```kotlin
package com.hrcoach.data.firebase

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FcmTokenManager @Inject constructor(
    private val database: FirebaseDatabase,
    private val messaging: FirebaseMessaging,
    private val authManager: FirebaseAuthManager,
) {
    suspend fun refreshToken() {
        val uid = authManager.ensureSignedIn()
        val token = messaging.token.await()
        database.reference.child("users").child(uid).child("fcmToken").setValue(token).await()
        Log.d("FcmTokenManager", "Token synced for $uid")
    }
}
```

- [ ] **Step 2: Implement PartnerActivityNotificationService**

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
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.hrcoach.data.firebase.FcmTokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PartnerActivityNotificationService : FirebaseMessagingService() {

    @Inject lateinit var fcmTokenManager: FcmTokenManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch { fcmTokenManager.refreshToken() }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val notification = message.notification ?: return

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Partner Activity",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notifications when your accountability partners complete a run"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "partner_activity"
    }
}
```

- [ ] **Step 3: Add FCM service to AndroidManifest.xml**

After the existing `WorkoutForegroundService` `<service>` declaration (after line 60), add:

```xml
        <service
            android:name=".service.PartnerActivityNotificationService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>
```

- [ ] **Step 4: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/firebase/FcmTokenManager.kt app/src/main/java/com/hrcoach/service/PartnerActivityNotificationService.kt app/src/main/AndroidManifest.xml
git commit -m "feat(partners): add FcmTokenManager and PartnerActivityNotificationService"
```

---

## Task 6: FirebasePartnerRepository

**Files:**
- Create: `app/src/main/java/com/hrcoach/data/firebase/FirebasePartnerRepository.kt`

- [ ] **Step 1: Implement FirebasePartnerRepository**

```kotlin
package com.hrcoach.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.hrcoach.data.repository.UserProfileRepository
import com.hrcoach.domain.model.PartnerActivity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebasePartnerRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val authManager: FirebaseAuthManager,
    private val userProfileRepository: UserProfileRepository,
) {
    private val usersRef get() = database.reference.child("users")
    private val invitesRef get() = database.reference.child("invites")

    suspend fun syncProfile() {
        val uid = authManager.ensureSignedIn()
        val userRef = usersRef.child(uid)
        userRef.child("displayName").setValue(userProfileRepository.getDisplayName()).await()
        userRef.child("emblemId").setValue(userProfileRepository.getEmblemId()).await()
    }

    suspend fun syncWorkoutActivity(
        currentStreak: Int,
        weeklyRunCount: Int,
        lastRunDurationMin: Int,
        lastRunPhase: String,
    ) {
        val uid = authManager.ensureSignedIn()
        val activityRef = usersRef.child(uid).child("activity")
        val updates = mapOf(
            "currentStreak" to currentStreak,
            "weeklyRunCount" to weeklyRunCount,
            "lastRunDate" to java.time.LocalDate.now().toString(),
            "lastRunDurationMin" to lastRunDurationMin,
            "lastRunPhase" to lastRunPhase,
        )
        activityRef.updateChildren(updates).await()
    }

    suspend fun createInviteCode(): String {
        val uid = authManager.ensureSignedIn()
        val code = generateInviteCode()
        val now = System.currentTimeMillis()
        val data = mapOf(
            "userId" to uid,
            "displayName" to userProfileRepository.getDisplayName(),
            "emblemId" to userProfileRepository.getEmblemId(),
            "createdAt" to now,
            "expiresAt" to now + 24 * 60 * 60 * 1000,
        )
        invitesRef.child(code).setValue(data).await()
        return code
    }

    suspend fun redeemInviteCode(code: String): PartnerActivity? {
        val snapshot = invitesRef.child(code).get().await()
        if (!snapshot.exists()) return null

        val expiresAt = snapshot.child("expiresAt").getValue(Long::class.java) ?: return null
        if (System.currentTimeMillis() > expiresAt) return null

        val partnerId = snapshot.child("userId").getValue(String::class.java) ?: return null
        val partnerName = snapshot.child("displayName").getValue(String::class.java) ?: "Runner"
        val partnerEmblem = snapshot.child("emblemId").getValue(String::class.java) ?: "pulse"

        val myUid = authManager.ensureSignedIn()
        if (partnerId == myUid) return null

        // Bidirectional partner link
        usersRef.child(myUid).child("partners").child(partnerId).setValue(true).await()
        usersRef.child(partnerId).child("partners").child(myUid).setValue(true).await()

        // Delete consumed invite
        invitesRef.child(code).removeValue().await()

        return PartnerActivity(
            userId = partnerId,
            displayName = partnerName,
            emblemId = partnerEmblem,
            currentStreak = 0,
            weeklyRunCount = 0,
            lastRunDate = null,
            lastRunDurationMin = null,
            lastRunPhase = null,
        )
    }

    suspend fun removePartner(partnerId: String) {
        val myUid = authManager.ensureSignedIn()
        usersRef.child(myUid).child("partners").child(partnerId).removeValue().await()
        usersRef.child(partnerId).child("partners").child(myUid).removeValue().await()
    }

    fun observePartners(): Flow<List<PartnerActivity>> = callbackFlow {
        val uid = authManager.getCurrentUid() ?: run {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val partnersRef = usersRef.child(uid).child("partners")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val partnerIds = snapshot.children.mapNotNull { it.key }
                if (partnerIds.isEmpty()) {
                    trySend(emptyList())
                    return
                }
                // Attach listeners to each partner's activity
                val partners = mutableListOf<PartnerActivity>()
                var loaded = 0
                for (pid in partnerIds) {
                    usersRef.child(pid).get().addOnSuccessListener { partnerSnap ->
                        val activity = partnerSnap.toPartnerActivity(pid)
                        if (activity != null) partners.add(activity)
                        loaded++
                        if (loaded == partnerIds.size) {
                            trySend(partners.sortedByDescending { it.lastRunDate })
                        }
                    }.addOnFailureListener {
                        loaded++
                        if (loaded == partnerIds.size) {
                            trySend(partners.sortedByDescending { it.lastRunDate })
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(emptyList())
            }
        }

        partnersRef.addValueEventListener(listener)
        awaitClose { partnersRef.removeEventListener(listener) }
    }

    fun getPartnerCount(): Flow<Int> = callbackFlow {
        val uid = authManager.getCurrentUid() ?: run {
            trySend(0)
            close()
            return@callbackFlow
        }
        val partnersRef = usersRef.child(uid).child("partners")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.childrenCount.toInt())
            }
            override fun onCancelled(error: DatabaseError) {
                trySend(0)
            }
        }
        partnersRef.addValueEventListener(listener)
        awaitClose { partnersRef.removeEventListener(listener) }
    }

    private fun DataSnapshot.toPartnerActivity(userId: String): PartnerActivity? {
        if (!exists()) return null
        val displayName = child("displayName").getValue(String::class.java) ?: return null
        val emblemId = child("emblemId").getValue(String::class.java) ?: "pulse"
        val activity = child("activity")
        return PartnerActivity(
            userId = userId,
            displayName = displayName,
            emblemId = emblemId,
            currentStreak = activity.child("currentStreak").getValue(Int::class.java) ?: 0,
            weeklyRunCount = activity.child("weeklyRunCount").getValue(Int::class.java) ?: 0,
            lastRunDate = activity.child("lastRunDate").getValue(String::class.java),
            lastRunDurationMin = activity.child("lastRunDurationMin").getValue(Int::class.java),
            lastRunPhase = activity.child("lastRunPhase").getValue(String::class.java),
        )
    }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/firebase/FirebasePartnerRepository.kt
git commit -m "feat(partners): add FirebasePartnerRepository with RTDB CRUD and real-time listeners"
```

---

## Task 7: Hilt DI Wiring

**Files:**
- Modify: `app/src/main/java/com/hrcoach/di/AppModule.kt`

- [ ] **Step 1: Add Firebase providers to AppModule**

Add these imports at the top of `AppModule.kt`:

```kotlin
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
```

Add these provider methods inside the `@Module` object, after existing providers:

```kotlin
    @Provides
    @Singleton
    fun provideFirebaseDatabase(): FirebaseDatabase {
        val db = FirebaseDatabase.getInstance()
        db.setPersistenceEnabled(true)
        return db
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()
```

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/di/AppModule.kt
git commit -m "feat(partners): wire Firebase Database, Auth, Messaging into Hilt DI"
```

---

## Task 8: AccountViewModel Partner State

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountViewModel.kt`

- [ ] **Step 1: Add partner state to AccountUiState**

In `AccountUiState` data class, add these fields:

```kotlin
    val partners: List<PartnerActivity> = emptyList(),
    val partnerCount: Int = 0,
    val partnerNudgesEnabled: Boolean = true,
```

Add the import:

```kotlin
import com.hrcoach.domain.model.PartnerActivity
```

- [ ] **Step 2: Inject FirebasePartnerRepository and add partner flows**

Add to constructor injection:

```kotlin
    private val partnerRepository: FirebasePartnerRepository,
    private val firebaseAuthManager: FirebaseAuthManager,
    private val fcmTokenManager: FcmTokenManager,
```

Add imports:

```kotlin
import com.hrcoach.data.firebase.FirebasePartnerRepository
import com.hrcoach.data.firebase.FirebaseAuthManager
import com.hrcoach.data.firebase.FcmTokenManager
```

Add partner-related state flows as private properties:

```kotlin
    private val _partners = partnerRepository.observePartners()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _partnerNudgesEnabled = MutableStateFlow(true)
```

- [ ] **Step 3: Include partners in the uiState combine chain**

Add `_partners` and `_partnerNudgesEnabled` to the existing `combine()` call that produces `uiState`. In the mapping lambda, add:

```kotlin
    partners = partners,
    partnerCount = partners.size,
    partnerNudgesEnabled = partnerNudgesEnabled,
```

- [ ] **Step 4: Add partner action methods**

```kotlin
    fun initFirebase() {
        viewModelScope.launch {
            firebaseAuthManager.ensureSignedIn()
            fcmTokenManager.refreshToken()
            partnerRepository.syncProfile()
        }
    }

    suspend fun createInviteCode(): String {
        return partnerRepository.createInviteCode()
    }

    suspend fun redeemInviteCode(code: String): PartnerActivity? {
        return partnerRepository.redeemInviteCode(code)
    }

    fun removePartner(partnerId: String) {
        viewModelScope.launch { partnerRepository.removePartner(partnerId) }
    }

    fun setPartnerNudgesEnabled(enabled: Boolean) {
        _partnerNudgesEnabled.value = enabled
        userProfileRepository.setPartnerNudgesEnabled(enabled)
    }
```

- [ ] **Step 5: Call initFirebase in init block**

In the existing `init {}` block, add at the end:

```kotlin
        _partnerNudgesEnabled.value = userProfileRepository.isPartnerNudgesEnabled()
        initFirebase()
```

- [ ] **Step 6: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/account/AccountViewModel.kt
git commit -m "feat(partners): add partner state and Firebase init to AccountViewModel"
```

---

## Task 9: PartnerSection UI (AccountScreen)

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/account/PartnerSection.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt`

- [ ] **Step 1: Implement PartnerSection composable**

```kotlin
package com.hrcoach.ui.account

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.domain.emblem.Emblem
import com.hrcoach.domain.model.PartnerActivity
import com.hrcoach.ui.components.EmblemIconWithRing
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.GradientRed
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientCyan
import kotlinx.coroutines.launch

@Composable
fun PartnerSection(
    partners: List<PartnerActivity>,
    partnerCount: Int,
    onAddPartner: () -> Unit,
    onRemovePartner: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Section header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("👥", fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Partners",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = CardeaTheme.colors.textPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "$partnerCount of 3",
                    fontSize = 12.sp,
                    color = CardeaTheme.colors.textTertiary,
                )
            }
            if (partnerCount < 3) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(GradientRed, GradientPink, GradientBlue, GradientCyan)
                            )
                        )
                        .clickable { onAddPartner() }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        "+ Add",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        }

        if (partners.isEmpty()) {
            // Empty state
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(24.dp),
            ) {
                Text(
                    "Add a partner to stay motivated together",
                    fontSize = 13.sp,
                    color = CardeaTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(GradientRed, GradientPink, GradientBlue, GradientCyan)
                            )
                        )
                        .clickable { onAddPartner() }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(
                        "+ Add Partner",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        } else {
            // Partner list
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(0.dp),
            ) {
                partners.forEachIndexed { index, partner ->
                    PartnerRow(
                        partner = partner,
                        onLongPress = { onRemovePartner(partner.userId) },
                    )
                    if (index < partners.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                            color = CardeaTheme.colors.glassBorder,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PartnerRow(
    partner: PartnerActivity,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = {}) // TODO: future detail view
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmblemIconWithRing(
            emblem = Emblem.fromId(partner.emblemId),
            size = 40.dp,
            ringWidth = 2.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                partner.displayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = CardeaTheme.colors.textPrimary,
            )
            Text(
                buildString {
                    append("${partner.weeklyRunCount} run${if (partner.weeklyRunCount != 1) "s" else ""}")
                    append(" · ")
                    append("${partner.currentStreak}-day streak 🔥")
                },
                fontSize = 12.sp,
                color = CardeaTheme.colors.textSecondary,
            )
        }
        if (partner.ranToday()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4ADE80)),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Ran today",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF4ADE80),
                )
            }
        } else if (partner.lastRunDate != null) {
            val daysAgo = java.time.temporal.ChronoUnit.DAYS.between(
                java.time.LocalDate.parse(partner.lastRunDate),
                java.time.LocalDate.now()
            ).toInt()
            Text(
                when (daysAgo) {
                    1 -> "Yesterday"
                    else -> "$daysAgo days ago"
                },
                fontSize = 11.sp,
                color = CardeaTheme.colors.textTertiary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPartnerBottomSheet(
    onDismiss: () -> Unit,
    onCreateCode: suspend () -> String,
    onRedeemCode: suspend (String) -> PartnerActivity?,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var inviteCode by remember { mutableStateOf<String?>(null) }
    var inputCode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var connectedPartner by remember { mutableStateOf<PartnerActivity?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111827),
        contentColor = CardeaTheme.colors.textPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (connectedPartner != null) {
                // Success state
                Text("🎉", fontSize = 48.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Partner Added!",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = CardeaTheme.colors.textPrimary,
                )
                Text(
                    "You're now connected",
                    fontSize = 13.sp,
                    color = CardeaTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(16.dp))

                // Partner preview
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EmblemIconWithRing(
                            emblem = Emblem.fromId(connectedPartner!!.emblemId),
                            size = 44.dp,
                            ringWidth = 2.dp,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                connectedPartner!!.displayName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Connected just now",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF4ADE80),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // What you'll see
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(14.dp),
                ) {
                    Text("What you'll see", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        "Their weekly run count",
                        "Their current streak",
                        "Gentle nudge when they run",
                    ).forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 3.dp),
                        ) {
                            Text("✓", fontSize = 12.sp, color = Color(0xFF4ADE80))
                            Spacer(Modifier.width(10.dp))
                            Text(item, fontSize = 13.sp, color = CardeaTheme.colors.textSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(GradientRed, GradientPink, GradientBlue, GradientCyan)
                            )
                        )
                        .clickable { onDismiss() }
                        .padding(14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Done", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Spacer(Modifier.height(24.dp))
                return@ModalBottomSheet
            }

            // Normal state: tabs
            Text(
                "Add Partner",
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                "Connect with a running buddy",
                fontSize = 13.sp,
                color = CardeaTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(16.dp))

            // Tab row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = CardeaTheme.colors.textPrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = GradientPink,
                    )
                },
                divider = {},
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            "Share my code",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            "Enter a code",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                )
            }

            Spacer(Modifier.height(20.dp))

            if (selectedTab == 0) {
                // Share my code
                if (inviteCode == null && !isLoading) {
                    scope.launch {
                        isLoading = true
                        inviteCode = onCreateCode()
                        isLoading = false
                    }
                }
                if (isLoading) {
                    Text("Generating code...", color = CardeaTheme.colors.textSecondary)
                } else if (inviteCode != null) {
                    Text(
                        inviteCode!!,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 8.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        style = androidx.compose.ui.text.TextStyle(
                            brush = Brush.linearGradient(
                                listOf(GradientRed, GradientPink, GradientBlue, GradientCyan)
                            )
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Expires in 24 hours",
                        fontSize = 12.sp,
                        color = CardeaTheme.colors.textTertiary,
                    )

                    Spacer(Modifier.height(20.dp))

                    // Share button
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        android.content.Intent.EXTRA_TEXT,
                                        "Join me on Cardea! Enter my partner code: ${inviteCode!!}"
                                    )
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share invite code"))
                            },
                        contentPadding = PaddingValues(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("📤", fontSize = 14.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Share invite code",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "Send this code to your partner.\nThey'll enter it in their Cardea app.",
                    fontSize = 12.sp,
                    color = CardeaTheme.colors.textTertiary,
                    textAlign = TextAlign.Center,
                )
            } else {
                // Enter a code
                Text(
                    "Enter your partner's 6-character code",
                    fontSize = 13.sp,
                    color = CardeaTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = inputCode,
                    onValueChange = { value ->
                        if (value.length <= 6 && value.all { it.isLetterOrDigit() }) {
                            inputCode = value.uppercase()
                            error = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. X7K2M9") },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 6.sp,
                        textAlign = TextAlign.Center,
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (inputCode.length == 6) {
                                scope.launch {
                                    isLoading = true
                                    val result = onRedeemCode(inputCode)
                                    isLoading = false
                                    if (result != null) {
                                        connectedPartner = result
                                    } else {
                                        error = "Code not found or expired"
                                    }
                                }
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GradientPink,
                        unfocusedBorderColor = CardeaTheme.colors.glassBorder,
                        cursorColor = GradientCyan,
                        focusedTextColor = CardeaTheme.colors.textPrimary,
                        unfocusedTextColor = CardeaTheme.colors.textPrimary,
                    ),
                    shape = RoundedCornerShape(14.dp),
                )

                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, fontSize = 12.sp, color = GradientRed)
                }

                Spacer(Modifier.height(16.dp))

                val canConnect = inputCode.length == 6 && !isLoading
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (canConnect) Brush.linearGradient(
                                listOf(GradientRed, GradientPink, GradientBlue, GradientCyan)
                            ) else Brush.linearGradient(
                                listOf(Color.Gray.copy(alpha = 0.3f), Color.Gray.copy(alpha = 0.3f))
                            )
                        )
                        .then(
                            if (canConnect) Modifier.clickable {
                                scope.launch {
                                    isLoading = true
                                    val result = onRedeemCode(inputCode)
                                    isLoading = false
                                    if (result != null) {
                                        connectedPartner = result
                                    } else {
                                        error = "Code not found or expired"
                                    }
                                }
                            } else Modifier
                        )
                        .padding(14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (isLoading) "Connecting..." else "Connect",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = if (canConnect) 1f else 0.4f),
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "Ask your partner to share their\ncode from their Cardea app.",
                    fontSize = 12.sp,
                    color = CardeaTheme.colors.textTertiary,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
```

- [ ] **Step 2: Insert PartnerSection into AccountScreen**

In `AccountScreen.kt`, after the profile hero card section and before achievements/other sections, add:

```kotlin
                var showAddPartnerSheet by remember { mutableStateOf(false) }

                Spacer(modifier = Modifier.height(16.dp))

                PartnerSection(
                    partners = state.partners,
                    partnerCount = state.partnerCount,
                    onAddPartner = { showAddPartnerSheet = true },
                    onRemovePartner = { viewModel.removePartner(it) },
                )

                if (showAddPartnerSheet) {
                    AddPartnerBottomSheet(
                        onDismiss = { showAddPartnerSheet = false },
                        onCreateCode = { viewModel.createInviteCode() },
                        onRedeemCode = { viewModel.redeemInviteCode(it) },
                    )
                }
```

Also add the partner nudges toggle in a settings section below the partners:

```kotlin
                Spacer(modifier = Modifier.height(12.dp))

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    SettingToggleRow(
                        icon = Icons.Default.Notifications,
                        title = "Partner nudges",
                        checked = state.partnerNudgesEnabled,
                        onCheckedChange = { viewModel.setPartnerNudgesEnabled(it) },
                    )
                }
```

- [ ] **Step 3: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/account/PartnerSection.kt app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt
git commit -m "feat(partners): add PartnerSection UI with compact list and add partner bottom sheet"
```

---

## Task 10: Nudge Banner Visibility Tests

**Files:**
- Create: `app/src/test/java/com/hrcoach/ui/home/NudgeBannerVisibilityTest.kt`

- [ ] **Step 1: Write tests for nudge banner visibility rules**

```kotlin
package com.hrcoach.ui.home

import com.hrcoach.domain.model.PartnerActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NudgeBannerVisibilityTest {

    private val today = LocalDate.now().toString()
    private val yesterday = LocalDate.now().minusDays(1).toString()
    private val threeDaysAgo = LocalDate.now().minusDays(3).toString()

    private fun partner(
        name: String = "Sarah",
        lastRunDate: String? = today,
        streak: Int = 5,
        weeklyRuns: Int = 2,
    ) = PartnerActivity(
        userId = "id-$name",
        displayName = name,
        emblemId = "bolt",
        currentStreak = streak,
        weeklyRunCount = weeklyRuns,
        lastRunDate = lastRunDate,
        lastRunDurationMin = 30,
        lastRunPhase = "Base building",
    )

    @Test
    fun `show nudge when partner ran today and user has not`() {
        val result = computeNudgeBanner(
            partners = listOf(partner(lastRunDate = today)),
            userRanToday = false,
        )
        assertTrue(result != null)
        assertTrue(result!!.text.contains("Sarah"))
    }

    @Test
    fun `hide nudge when user already ran today`() {
        val result = computeNudgeBanner(
            partners = listOf(partner(lastRunDate = today)),
            userRanToday = true,
        )
        assertNull(result)
    }

    @Test
    fun `hide nudge when no partners`() {
        val result = computeNudgeBanner(
            partners = emptyList(),
            userRanToday = false,
        )
        assertNull(result)
    }

    @Test
    fun `hide nudge when partner activity older than 48 hours`() {
        val result = computeNudgeBanner(
            partners = listOf(partner(lastRunDate = threeDaysAgo)),
            userRanToday = false,
        )
        assertNull(result)
    }

    @Test
    fun `show nudge for yesterday activity`() {
        val result = computeNudgeBanner(
            partners = listOf(partner(name = "Mike", lastRunDate = yesterday)),
            userRanToday = false,
        )
        assertTrue(result != null)
        assertTrue(result!!.text.contains("Mike"))
    }

    @Test
    fun `combined text when two partners ran today`() {
        val result = computeNudgeBanner(
            partners = listOf(
                partner(name = "Sarah", lastRunDate = today),
                partner(name = "Mike", lastRunDate = today),
            ),
            userRanToday = false,
        )
        assertTrue(result != null)
        assertTrue(result!!.text.contains("Sarah"))
        assertTrue(result!!.text.contains("Mike"))
    }

    @Test
    fun `most recent partner shown first`() {
        val result = computeNudgeBanner(
            partners = listOf(
                partner(name = "Mike", lastRunDate = yesterday),
                partner(name = "Sarah", lastRunDate = today),
            ),
            userRanToday = false,
        )
        assertTrue(result != null)
        assertTrue(result!!.text.startsWith("Sarah"))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.ui.home.NudgeBannerVisibilityTest"`
Expected: FAIL — `computeNudgeBanner` not found

- [ ] **Step 3: Implement computeNudgeBanner**

This will go in `PartnerNudgeBanner.kt` (created in next task), but we create the logic function now. Create `app/src/main/java/com/hrcoach/ui/home/NudgeBannerLogic.kt`:

```kotlin
package com.hrcoach.ui.home

import com.hrcoach.domain.model.PartnerActivity

data class NudgeBannerState(
    val text: String,
    val subtitle: String?,
    val partners: List<PartnerActivity>,
)

fun computeNudgeBanner(
    partners: List<PartnerActivity>,
    userRanToday: Boolean,
): NudgeBannerState? {
    if (partners.isEmpty() || userRanToday) return null

    val recentPartners = partners
        .filter { it.isRecentlyActive() }
        .sortedByDescending { it.lastRunDate }

    if (recentPartners.isEmpty()) return null

    val todayRunners = recentPartners.filter { it.ranToday() }

    val text: String
    val subtitle: String?

    when {
        todayRunners.size >= 2 -> {
            text = "${todayRunners[0].displayName} and ${todayRunners[1].displayName} both ran today"
            subtitle = "Your turn?"
        }
        todayRunners.size == 1 -> {
            text = "${todayRunners[0].displayName} just finished a run"
            val others = recentPartners.filter { !it.ranToday() }
            subtitle = if (others.isNotEmpty()) {
                "${others[0].displayName} ran yesterday · Both keeping it up!"
            } else {
                "Your turn?"
            }
        }
        else -> {
            text = "${recentPartners[0].displayName} ran yesterday"
            subtitle = "Your turn?"
        }
    }

    return NudgeBannerState(
        text = text,
        subtitle = subtitle,
        partners = recentPartners,
    )
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.ui.home.NudgeBannerVisibilityTest"`
Expected: All 7 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/home/NudgeBannerLogic.kt app/src/test/java/com/hrcoach/ui/home/NudgeBannerVisibilityTest.kt
git commit -m "feat(partners): add nudge banner visibility logic with tests"
```

---

## Task 11: PartnerNudgeBanner Composable + HomeScreen Integration

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/home/PartnerNudgeBanner.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt`

- [ ] **Step 1: Implement PartnerNudgeBanner composable**

```kotlin
package com.hrcoach.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.domain.emblem.Emblem
import com.hrcoach.domain.model.PartnerActivity
import com.hrcoach.ui.components.EmblemIconWithRing
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.GradientRed
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientCyan

private val NudgeGreen = Color(0xFF4ADE80)
private val NudgeGreenDim = Color(0x0F4ADE80)
private val NudgeCyanDim = Color(0x0A00D1FF)

@Composable
fun PartnerNudgeBanner(
    state: NudgeBannerState,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(NudgeGreenDim, NudgeCyanDim),
                )
            )
            .drawBehind {
                // Green left accent border
                drawRect(
                    color = NudgeGreen,
                    topLeft = Offset.Zero,
                    size = Size(3.dp.toPx(), size.height),
                )
            }
            .clickable { onTap() }
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Stacked partner avatars
        Box(modifier = Modifier.size(if (state.partners.size > 1) 44.dp else 32.dp)) {
            state.partners.take(2).forEachIndexed { index, partner ->
                EmblemIconWithRing(
                    emblem = Emblem.fromId(partner.emblemId),
                    size = 32.dp,
                    ringWidth = 2.dp,
                    modifier = Modifier.offset(x = (index * 12).dp),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildAnnotatedString {
                    // Find partner name in text and color it green
                    val text = state.text
                    val firstPartner = state.partners.firstOrNull()
                    if (firstPartner != null && text.contains(firstPartner.displayName)) {
                        val idx = text.indexOf(firstPartner.displayName)
                        append(text.substring(0, idx))
                        withStyle(SpanStyle(color = NudgeGreen, fontWeight = FontWeight.Bold)) {
                            append(firstPartner.displayName)
                        }
                        append(text.substring(idx + firstPartner.displayName.length))
                    } else {
                        append(text)
                    }
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = CardeaTheme.colors.textPrimary,
            )
            if (state.subtitle != null) {
                Text(
                    state.subtitle,
                    fontSize = 11.sp,
                    color = CardeaTheme.colors.textSecondary,
                )
            }
        }

        Text(
            "Go →",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            style = androidx.compose.ui.text.TextStyle(
                brush = Brush.linearGradient(
                    listOf(GradientRed, GradientPink, GradientBlue, GradientCyan)
                )
            ),
        )
    }
}
```

- [ ] **Step 2: Add partner nudge state to HomeUiState**

In `HomeViewModel.kt`, add to `HomeUiState`:

```kotlin
    val nudgeBanner: NudgeBannerState? = null,
```

- [ ] **Step 3: Inject FirebasePartnerRepository into HomeViewModel**

Add constructor parameter:

```kotlin
    private val partnerRepository: FirebasePartnerRepository,
```

Add import:

```kotlin
import com.hrcoach.data.firebase.FirebasePartnerRepository
```

In the reactive pipeline that produces `uiState`, combine `partnerRepository.observePartners()` and compute the nudge banner:

```kotlin
    val nudgeBanner = computeNudgeBanner(
        partners = partners,
        userRanToday = /* true if user has a workout completed today */,
    )
```

Add `nudgeBanner = nudgeBanner` to the `HomeUiState(...)` construction.

- [ ] **Step 4: Insert PartnerNudgeBanner into HomeScreen**

In `HomeScreen.kt`, after the hero section and before the CTA row, add:

```kotlin
                if (state.nudgeBanner != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    PartnerNudgeBanner(
                        state = state.nudgeBanner!!,
                        onTap = onGoToBootcamp,
                    )
                }
```

- [ ] **Step 5: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/home/PartnerNudgeBanner.kt app/src/main/java/com/hrcoach/ui/home/HomeViewModel.kt app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt
git commit -m "feat(partners): add PartnerNudgeBanner composable and HomeScreen integration"
```

---

## Task 12: Activity Sync in WorkoutForegroundService

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`

- [ ] **Step 1: Inject FirebasePartnerRepository into WorkoutForegroundService**

The service is `@AndroidEntryPoint`, so add a field injection:

```kotlin
    @Inject lateinit var partnerRepository: FirebasePartnerRepository
```

Add import:

```kotlin
import com.hrcoach.data.firebase.FirebasePartnerRepository
```

- [ ] **Step 2: Add activity sync after workout completion**

In the workout completion flow, after metrics are saved and before simulation cleanup (approximately after line 734 in the current file), add:

```kotlin
                    // Sync activity to Firebase for accountability partners
                    try {
                        val weeklyCount = workoutRepository.getWorkoutsCompletedThisWeek()
                        val streak = sessionStreak  // Already computed above
                        val durationMin = ((clock.now() - workoutStartMs) / 60_000L).toInt()
                        val phase = WorkoutState.snapshot.value.pendingBootcampSessionId?.let {
                            "Bootcamp"
                        } ?: "Free run"
                        partnerRepository.syncWorkoutActivity(
                            currentStreak = streak,
                            weeklyRunCount = weeklyCount,
                            lastRunDurationMin = durationMin,
                            lastRunPhase = phase,
                        )
                    } catch (e: Exception) {
                        android.util.Log.w("WorkoutService", "Failed to sync partner activity", e)
                    }
```

Note: The exact variable names for streak and weekly count depend on what's already computed at the insertion point. The implementing agent should read the surrounding code to wire the correct values.

- [ ] **Step 3: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt
git commit -m "feat(partners): sync workout activity to Firebase RTDB on completion"
```

---

## Task 13: Cloud Functions

**Files:**
- Create: `functions/package.json`
- Create: `functions/index.ts`

- [ ] **Step 1: Create functions/package.json**

```json
{
  "name": "cardea-functions",
  "description": "Cardea HR Coach Cloud Functions",
  "scripts": {
    "build": "tsc",
    "serve": "npm run build && firebase emulators:start --only functions",
    "deploy": "firebase deploy --only functions"
  },
  "engines": {
    "node": "18"
  },
  "main": "lib/index.js",
  "dependencies": {
    "firebase-admin": "^12.0.0",
    "firebase-functions": "^5.0.0"
  },
  "devDependencies": {
    "typescript": "^5.3.0"
  }
}
```

- [ ] **Step 2: Create functions/index.ts**

```typescript
import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

admin.initializeApp();

export const onPartnerRun = functions.database
  .ref("/users/{userId}/activity/lastRunDate")
  .onWrite(async (change, context) => {
    const newDate = change.after.val();
    if (!newDate) return;

    const userId = context.params.userId;
    const userSnap = await admin.database().ref(`/users/${userId}`).once("value");
    const userData = userSnap.val();
    if (!userData) return;

    const partnerIds = Object.keys(userData.partners || {});
    if (partnerIds.length === 0) return;

    const displayName = userData.displayName || "Your partner";

    const sendPromises = partnerIds.map(async (partnerId) => {
      const partnerSnap = await admin.database().ref(`/users/${partnerId}`).once("value");
      const partnerData = partnerSnap.val();
      if (!partnerData?.fcmToken) return;

      return admin.messaging().send({
        token: partnerData.fcmToken,
        notification: {
          title: `${displayName} just finished a run`,
          body: "Your turn? 💪",
        },
        android: {
          notification: {
            channelId: "partner_activity",
            icon: "ic_notification",
          },
        },
      });
    });

    await Promise.allSettled(sendPromises);
  });
```

- [ ] **Step 3: Commit**

```bash
git add functions/package.json functions/index.ts firebase.json
git commit -m "feat(partners): add Cloud Function for FCM push on partner activity"
```

---

## Task 14: Run All Tests + Final Build Verification

- [ ] **Step 1: Run all unit tests**

Run: `./gradlew testDebugUnitTest`
Expected: All tests PASS (including new PartnerActivityTest, InviteCodeGeneratorTest, NudgeBannerVisibilityTest)

- [ ] **Step 2: Run full debug build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run lint check**

Run: `./gradlew lint`
Expected: No new errors introduced

- [ ] **Step 4: Final commit if any fixups needed**

```bash
git add -A
git commit -m "fix(partners): address lint and test issues"
```
