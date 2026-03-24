# Account Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Firebase Auth, profile claim flow, 24 Canvas-drawn emblem avatars, and per-user data isolation to replace the current disposable profile system.

**Architecture:** Soft onboarding — app works without auth, profile "claimed" after first workout via Firebase Auth (email/password or Google Sign-In). Room DB tables gain `userId` columns; SharedPreferences switch to per-UID files. 24 athletic emblem icons replace Unicode symbols.

**Tech Stack:** Firebase Auth, Firebase BOM 33.8.0 (already present), Room (v15→v16 migration), Hilt, Jetpack Compose Canvas, Google Play Services Auth

**Spec:** `docs/superpowers/specs/2026-03-23-account-upgrade-design.md`

---

### Task 1: Add Firebase Auth Dependencies

**Files:**
- Modify: `app/build.gradle.kts:97-99` (Firebase dependencies section)

- [ ] **Step 1: Add firebase-auth and play-services-auth dependencies**

In `app/build.gradle.kts`, add to the dependencies block after the existing Firebase analytics line:

```kotlin
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
```

- [ ] **Step 2: Sync and verify build**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "deps: add firebase-auth and play-services-auth"
```

---

### Task 2: Create AuthRepository

**Files:**
- Create: `app/src/main/java/com/hrcoach/data/repository/AuthRepository.kt`
- Test: `app/src/test/java/com/hrcoach/data/repository/AuthRepositoryTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.hrcoach.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AuthRepositoryTest {

    private val mockAuth = mockk<FirebaseAuth>(relaxed = true)
    private lateinit var repo: AuthRepository

    @Before
    fun setup() {
        every { mockAuth.currentUser } returns null
        repo = AuthRepository(mockAuth)
    }

    @Test
    fun `currentUserId returns null when not authenticated`() {
        assertNull(repo.currentUserId)
    }

    @Test
    fun `currentUserId returns uid when authenticated`() {
        val mockUser = mockk<FirebaseUser>()
        every { mockUser.uid } returns "test-uid-123"
        every { mockAuth.currentUser } returns mockUser
        repo = AuthRepository(mockAuth)
        assertEquals("test-uid-123", repo.currentUserId)
    }

    @Test
    fun `isAuthenticated returns false when no user`() {
        assertFalse(repo.isAuthenticated())
    }

    @Test
    fun `isAuthenticated returns true when user present`() {
        val mockUser = mockk<FirebaseUser>()
        every { mockAuth.currentUser } returns mockUser
        repo = AuthRepository(mockAuth)
        assertTrue(repo.isAuthenticated())
    }

    @Test
    fun `effectiveUserId returns empty string when not authenticated`() {
        assertEquals("", repo.effectiveUserId)
    }

    @Test
    fun `effectiveUserId returns uid when authenticated`() {
        val mockUser = mockk<FirebaseUser>()
        every { mockUser.uid } returns "uid-abc"
        every { mockAuth.currentUser } returns mockUser
        repo = AuthRepository(mockAuth)
        assertEquals("uid-abc", repo.effectiveUserId)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.hrcoach.data.repository.AuthRepositoryTest" --info`
Expected: FAIL — class not found

- [ ] **Step 3: Add mockk dependency if not present**

Check `app/build.gradle.kts` for `mockk`. If missing, add:
```kotlin
    testImplementation("io.mockk:mockk:1.13.13")
```

- [ ] **Step 4: Implement AuthRepository**

Create `app/src/main/java/com/hrcoach/data/repository/AuthRepository.kt`:

```kotlin
package com.hrcoach.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) {
    private val _currentUser = MutableStateFlow(firebaseAuth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser

    val currentUserId: String?
        get() = firebaseAuth.currentUser?.uid

    /** Returns UID if authenticated, empty string if not (for Room userId columns). */
    val effectiveUserId: String
        get() = currentUserId ?: ""

    fun isAuthenticated(): Boolean = firebaseAuth.currentUser != null

    init {
        firebaseAuth.addAuthStateListener { auth ->
            _currentUser.value = auth.currentUser
        }
    }

    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> =
        runCatching {
            firebaseAuth.signInWithEmailAndPassword(email, password).await().user!!
        }

    suspend fun signUpWithEmail(email: String, password: String): Result<FirebaseUser> =
        runCatching {
            firebaseAuth.createUserWithEmailAndPassword(email, password).await().user!!
        }

    suspend fun signInWithGoogleCredential(idToken: String): Result<FirebaseUser> =
        runCatching {
            val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
            firebaseAuth.signInWithCredential(credential).await().user!!
        }

    fun signOut() {
        firebaseAuth.signOut()
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew.bat testDebugUnitTest --tests "com.hrcoach.data.repository.AuthRepositoryTest" --info`
Expected: ALL PASS

- [ ] **Step 6: Provide FirebaseAuth via Hilt**

In `app/src/main/java/com/hrcoach/di/AppModule.kt`, add at the end of the `AppModule` object (before closing brace, around line 73):

```kotlin
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()
```

Add import: `import com.google.firebase.auth.FirebaseAuth`

- [ ] **Step 7: Build to verify DI wiring**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/repository/AuthRepository.kt \
       app/src/test/java/com/hrcoach/data/repository/AuthRepositoryTest.kt \
       app/src/main/java/com/hrcoach/di/AppModule.kt \
       app/build.gradle.kts
git commit -m "feat(auth): add AuthRepository with Firebase Auth wrapper"
```

---

### Task 3: Room DB Migration — Add userId Columns

**Files:**
- Modify: `app/src/main/java/com/hrcoach/data/db/WorkoutEntity.kt:5-13`
- Modify: `app/src/main/java/com/hrcoach/data/db/BootcampEnrollmentEntity.kt:7-24`
- Modify: `app/src/main/java/com/hrcoach/data/db/AchievementEntity.kt:14-25`
- Modify: `app/src/main/java/com/hrcoach/data/db/AppDatabase.kt:8-16` (version + migration)
- Modify: `app/src/main/java/com/hrcoach/di/AppModule.kt:23-53` (add migration to builder)

- [ ] **Step 1: Add userId field to WorkoutEntity**

In `WorkoutEntity.kt`, add after the `targetConfig` field:
```kotlin
    @ColumnInfo(defaultValue = "") val userId: String = ""
```

- [ ] **Step 2: Add userId field to BootcampEnrollmentEntity**

In `BootcampEnrollmentEntity.kt`, add after the `targetFinishingTimeMinutes` field:
```kotlin
    @ColumnInfo(defaultValue = "") val userId: String = ""
```

- [ ] **Step 3: Add userId field to AchievementEntity**

In `AchievementEntity.kt`, add after the `shown` field:
```kotlin
    @ColumnInfo(defaultValue = "") val userId: String = ""
```

- [ ] **Step 4: Add MIGRATION_15_16 in AppDatabase**

In `AppDatabase.kt`, update the `@Database` annotation version to `16`, add `MIGRATION_15_16` at the top of the companion object (before `MIGRATION_14_15`):

```kotlin
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workouts ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE bootcamp_enrollments ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE achievements ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
            }
        }
```

- [ ] **Step 5: Add migration to Room builder in AppModule**

In `AppModule.kt` `provideDatabase()`, add `.addMigrations(AppDatabase.MIGRATION_15_16)` in the builder chain (alongside the existing migrations).

- [ ] **Step 6: Build to verify schema**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/db/WorkoutEntity.kt \
       app/src/main/java/com/hrcoach/data/db/BootcampEnrollmentEntity.kt \
       app/src/main/java/com/hrcoach/data/db/AchievementEntity.kt \
       app/src/main/java/com/hrcoach/data/db/AppDatabase.kt \
       app/src/main/java/com/hrcoach/di/AppModule.kt
git commit -m "feat(db): add userId columns to workouts, enrollments, achievements (v15→v16)"
```

---

### Task 4: Add userId Filtering to DAOs + Update All Callers

> **Important:** This task updates DAOs AND fixes all callers in one commit. Do not commit until the build is green.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/data/db/WorkoutDao.kt:8-33`
- Modify: `app/src/main/java/com/hrcoach/data/db/BootcampDao.kt`
- Modify: `app/src/main/java/com/hrcoach/data/db/AchievementDao.kt`
- Modify: `app/src/main/java/com/hrcoach/data/repository/WorkoutRepository.kt`
- Modify: `app/src/main/java/com/hrcoach/data/repository/BootcampRepository.kt:10-13`

- [ ] **Step 1: Update WorkoutDao queries**

Add `userId` parameter to query methods. `insert`/`update` don't need changes (entity carries userId). Update each `@Query`:

```kotlin
@Dao
interface WorkoutDao {
    @Insert
    suspend fun insert(workout: WorkoutEntity): Long

    @Update
    suspend fun update(workout: WorkoutEntity)

    @Query("SELECT * FROM workouts WHERE userId = :userId ORDER BY startTime DESC")
    fun getAllWorkouts(userId: String): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE userId = :userId ORDER BY startTime DESC")
    suspend fun getAllWorkoutsOnce(userId: String): List<WorkoutEntity>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getById(id: Long): WorkoutEntity?

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COALESCE(SUM(totalDistanceMeters), 0) / 1000.0 FROM workouts WHERE userId = :userId")
    suspend fun sumAllDistanceKm(userId: String): Double

    @Query("SELECT * FROM workouts WHERE endTime = 0")
    suspend fun getOrphanedWorkouts(): List<WorkoutEntity>

    @Query("SELECT COUNT(*) FROM workouts WHERE userId = :userId")
    suspend fun countWorkouts(userId: String): Int

    @Query("UPDATE workouts SET userId = :userId WHERE userId = ''")
    suspend fun claimOrphanedWorkouts(userId: String)
}
```

`getById`/`deleteById` stay without userId (accessed via known ID from userId-filtered list). Added `countWorkouts` and `claimOrphanedWorkouts`.

- [ ] **Step 2: Update BootcampDao queries**

Add `userId` to `getActiveEnrollment`, `getActiveEnrollmentOnce`, and add orphan claim:

```kotlin
    @Query("SELECT * FROM bootcamp_enrollments WHERE status = 'ACTIVE' AND userId = :userId ORDER BY id DESC LIMIT 1")
    fun getActiveEnrollment(userId: String): Flow<BootcampEnrollmentEntity?>

    @Query("SELECT * FROM bootcamp_enrollments WHERE status = 'ACTIVE' AND userId = :userId ORDER BY id DESC LIMIT 1")
    suspend fun getActiveEnrollmentOnce(userId: String): BootcampEnrollmentEntity?

    @Query("UPDATE bootcamp_enrollments SET userId = :userId WHERE userId = ''")
    suspend fun claimOrphanedEnrollments(userId: String)
```

- [ ] **Step 3: Update AchievementDao queries**

Add `userId` to all queries + orphan claim:

```kotlin
    @Query("SELECT * FROM achievements WHERE shown = 0 AND userId = :userId ORDER BY earnedAtMs DESC")
    fun getUnshownAchievements(userId: String): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements WHERE userId = :userId ORDER BY earnedAtMs DESC")
    fun getAllAchievements(userId: String): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements WHERE type = :type AND userId = :userId ORDER BY earnedAtMs DESC")
    fun getAchievementsByType(type: String, userId: String): Flow<List<AchievementEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM achievements WHERE type = :type AND milestone = :milestone AND userId = :userId)")
    suspend fun hasAchievement(type: String, milestone: String, userId: String): Boolean

    @Query("UPDATE achievements SET userId = :userId WHERE userId = ''")
    suspend fun claimOrphanedAchievements(userId: String)
```

- [ ] **Step 4: Update WorkoutRepository**

Add `AuthRepository` injection and pass `effectiveUserId` to all DAO calls:

```kotlin
@Singleton
class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val trackPointDao: TrackPointDao,
    private val authRepository: AuthRepository
) {
```

Every call to `workoutDao.getAllWorkouts()` becomes `workoutDao.getAllWorkouts(authRepository.effectiveUserId)`, etc. For `insert`, set userId on entity: `workout.copy(userId = authRepository.effectiveUserId)`.

- [ ] **Step 5: Update BootcampRepository**

Add `AuthRepository` injection:

```kotlin
@Singleton
class BootcampRepository @Inject constructor(
    private val bootcampDao: BootcampDao,
    private val authRepository: AuthRepository
) {
```

Update `getActiveEnrollment()`/`getActiveEnrollmentOnce()` to pass userId. Set userId on entities at insert time.

- [ ] **Step 6: Fix all remaining callers**

Search for compilation errors from DAO signature changes. Common callers:
- ViewModels injecting DAOs directly (especially `AchievementDao`) — inject `AuthRepository`, pass `effectiveUserId`
- `WorkoutForegroundService` — inject `AuthRepository`, pass userId when creating `WorkoutEntity`
- Any ViewModel calling `workoutDao.getAllWorkouts()` or achievement queries

Fix each caller by injecting `AuthRepository` and passing userId.

- [ ] **Step 7: Build and test**

Run: `./gradlew.bat assembleDebug && ./gradlew.bat testDebugUnitTest`
Expected: BUILD SUCCESSFUL, tests PASS (some tests may need userId params in setup)

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/db/WorkoutDao.kt \
       app/src/main/java/com/hrcoach/data/db/BootcampDao.kt \
       app/src/main/java/com/hrcoach/data/db/AchievementDao.kt \
       app/src/main/java/com/hrcoach/data/repository/WorkoutRepository.kt \
       app/src/main/java/com/hrcoach/data/repository/BootcampRepository.kt
git add -u  # catch all other caller fixes
git commit -m "feat(data): add userId filtering to DAOs and scope all repositories/callers"
```

### Task 5: Per-UID SharedPreferences for All Settings Repositories

**Files:**
- Modify: `app/src/main/java/com/hrcoach/data/repository/UserProfileRepository.kt`
- Modify: `app/src/main/java/com/hrcoach/data/repository/AudioSettingsRepository.kt`
- Modify: `app/src/main/java/com/hrcoach/data/repository/AutoPauseSettingsRepository.kt`
- Modify: `app/src/main/java/com/hrcoach/data/repository/MapsSettingsRepository.kt`
- Modify: `app/src/main/java/com/hrcoach/data/repository/AdaptiveProfileRepository.kt`

- [ ] **Step 1: Update UserProfileRepository**

Inject `AuthRepository`. Change from a single prefs file to per-UID prefs files. Add emblem migration, claim fields, name cooldown:

```kotlin
@Singleton
class UserProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository
) {
    companion object {
        private const val PREFS_PREFIX = "user_profile_"
        private const val LEGACY_PREFS_NAME = "hr_coach_user_profile"
        private const val PREF_MAX_HR = "max_hr"
        private const val PREF_DISPLAY_NAME = "display_name"
        private const val PREF_AVATAR_EMBLEM_ID = "avatar_emblem_id"
        private const val PREF_BIO = "bio"
        private const val PREF_IS_CLAIMED = "is_profile_claimed"
        private const val PREF_CLAIMED_AT_MS = "profile_claimed_at_ms"
        private const val PREF_LAST_NAME_CHANGE_MS = "last_name_change_ms"
        private const val UNSET = -1
        private const val DEFAULT_NAME = "Runner"
        private const val DEFAULT_EMBLEM = "pulse"
        private const val NAME_CHANGE_COOLDOWN_MS = 30L * 24 * 60 * 60 * 1000 // 30 days

        private val UNICODE_TO_EMBLEM = mapOf(
            "\u2665" to "heart",
            "\u2605" to "nova",
            "\u26A1" to "bolt",
            "\u25C6" to "diamond",
            "\u25B2" to "ascent",
            "\u25CF" to "ripple",
            "\u2726" to "compass",
            "\u2666" to "prism",
            "\u2191" to "flame",
            "\u221E" to "infinity"
        )
    }

    private fun prefs(): SharedPreferences {
        val uid = authRepository.effectiveUserId.ifEmpty { "anonymous" }
        return context.getSharedPreferences("$PREFS_PREFIX$uid", Context.MODE_PRIVATE)
    }
    // ... all getters/setters use prefs() instead of a stored `prefs` field
}
```

Add new methods: `getBio()`, `setBio()`, `getAvatarEmblemId()` (with Unicode migration), `setAvatarEmblemId()`, `isProfileClaimed()`, `claimProfile()`, `canChangeName()`, `daysUntilNameChange()`.

Remove `getUserId()` method.

> **Important (no caching):** Since `effectiveUserId` changes on sign-in/out and `UserProfileRepository` is `@Singleton`, it must NOT cache SharedPreferences values in memory. Every getter must read from `prefs()` directly. The `prefs()` function dynamically resolves the correct per-UID file.

- [ ] **Step 2: Add legacy prefs migration**

In each repository, add a migration helper that runs on first `prefs()` call for a UID. If the per-UID file is empty and the legacy (unscoped) file has data, copy all keys:

```kotlin
private fun prefs(): SharedPreferences {
    val uid = authRepository.effectiveUserId.ifEmpty { "anonymous" }
    val userPrefs = context.getSharedPreferences("$PREFS_PREFIX$uid", Context.MODE_PRIVATE)
    if (userPrefs.all.isEmpty()) {
        val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        if (legacy.all.isNotEmpty()) {
            userPrefs.edit().apply {
                legacy.all.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Float -> putFloat(key, value)
                    }
                }
                apply()
            }
        }
    }
    return userPrefs
}
```

This ensures existing users don't lose their display name, maxHr, audio preferences, or adaptive profile data on upgrade.

- [ ] **Step 3: Update AudioSettingsRepository**

Inject `AuthRepository`, switch to per-UID prefs file with same pattern:
```kotlin
private fun prefs(): SharedPreferences {
    val uid = authRepository.effectiveUserId.ifEmpty { "anonymous" }
    return context.getSharedPreferences("audio_settings_$uid", Context.MODE_PRIVATE)
}
```

- [ ] **Step 3: Update AutoPauseSettingsRepository**

Same pattern — inject `AuthRepository`, per-UID prefs.

- [ ] **Step 4: Update MapsSettingsRepository**

Same pattern — inject `AuthRepository`, per-UID prefs.

- [ ] **Step 5: Update AdaptiveProfileRepository**

Same pattern — inject `AuthRepository`, per-UID prefs. This is critical for data isolation.

- [ ] **Step 6: Fix all callers of getUserId()**

Search for `getUserId()` calls. Replace with `authRepository.currentUserId` or `authRepository.effectiveUserId` as appropriate.

- [ ] **Step 7: Build and test**

Run: `./gradlew.bat assembleDebug && ./gradlew.bat testDebugUnitTest`
Expected: BUILD SUCCESSFUL, tests PASS

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat(data): switch all settings repositories to per-UID SharedPreferences"
```

---

### Task 6: Room Migration Test

**Files:**
- Create: `app/src/androidTest/java/com/hrcoach/data/db/MigrationTest.kt`

> **Note:** This is an `androidTest` requiring an emulator/device. Write and compile the test here; run it when a device is available.

- [ ] **Step 1: Write migration test**

```kotlin
package com.hrcoach.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate15To16_addsUserIdColumns() {
        val db = helper.createDatabase("test_db", 15).apply {
            execSQL("INSERT INTO workouts (startTime, endTime, totalDistanceMeters, mode, targetConfig) VALUES (1000, 2000, 5000.0, 'FREE_RUN', '{}')")
            close()
        }
        val migratedDb = helper.runMigrationsAndValidate("test_db", 16, true, AppDatabase.MIGRATION_15_16)
        val cursor = migratedDb.query("SELECT userId FROM workouts")
        assertTrue(cursor.moveToFirst())
        assertEquals("", cursor.getString(0))
        cursor.close()
        migratedDb.close()
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew.bat assembleDebugAndroidTest`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run if emulator available**

Run: `./gradlew.bat connectedAndroidTest --tests "com.hrcoach.data.db.MigrationTest"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/java/com/hrcoach/data/db/MigrationTest.kt
git commit -m "test: add Room migration test for v15->v16 userId columns"
```

---

### Task 7: Emblem Registry and EmblemIcon Composable

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/components/EmblemIcon.kt`
- Test: `app/src/test/java/com/hrcoach/ui/components/EmblemRegistryTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.hrcoach.ui.components

import org.junit.Assert.*
import org.junit.Test

class EmblemRegistryTest {

    @Test
    fun `registry contains exactly 24 emblems`() {
        assertEquals(24, EmblemRegistry.allIds.size)
    }

    @Test
    fun `all expected emblem IDs are present`() {
        val expected = listOf(
            "pulse", "bolt", "summit", "flame", "compass", "shield",
            "ascent", "crown", "orbit", "infinity", "diamond", "nova",
            "heart", "wave", "spiral", "trident", "comet", "prism",
            "ripple", "crescent", "wings", "helix", "focus", "laurel"
        )
        expected.forEach { id ->
            assertTrue("Missing emblem: $id", EmblemRegistry.allIds.contains(id))
        }
    }

    @Test
    fun `each emblem has a display name`() {
        EmblemRegistry.allIds.forEach { id ->
            assertNotNull("Missing display name for: $id", EmblemRegistry.displayName(id))
            assertTrue(EmblemRegistry.displayName(id)!!.isNotBlank())
        }
    }

    @Test
    fun `unknown emblem ID returns null display name`() {
        assertNull(EmblemRegistry.displayName("nonexistent"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.hrcoach.ui.components.EmblemRegistryTest" --info`
Expected: FAIL

- [ ] **Step 3: Implement EmblemRegistry and EmblemIcon**

Create `app/src/main/java/com/hrcoach/ui/components/EmblemIcon.kt`.

This file contains:
1. `EmblemRegistry` object — maps emblem IDs to draw lambdas and display names
2. `EmblemIcon` composable — renders an emblem at a given size with Cardea gradient
3. `EmblemIconWithRing` composable — wraps `EmblemIcon` in the gradient ring circle (same visual pattern as current `ProfileHeroCard`)

The `EmblemRegistry` stores `Map<String, EmblemDef>` where:
```kotlin
data class EmblemDef(
    val displayName: String,
    val draw: DrawScope.(size: Size, brush: Brush) -> Unit
)
```

Each of the 24 emblems gets a draw lambda using `drawPath`, `drawLine`, `drawCircle`, `drawArc` etc. — translating the SVG paths from the brainstorm mockups to `DrawScope` calls.

The Compose Canvas code for each emblem should:
- Accept a `Brush` parameter (always `CardeaGradient`)
- Scale to the provided `Size`
- Use `Path()` objects for complex shapes

This is the largest single file in the plan (~400-500 lines). Keep all 24 draw lambdas in the registry object for discoverability.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat testDebugUnitTest --tests "com.hrcoach.ui.components.EmblemRegistryTest" --info`
Expected: ALL PASS

- [ ] **Step 5: Build**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/components/EmblemIcon.kt \
       app/src/test/java/com/hrcoach/ui/components/EmblemRegistryTest.kt
git commit -m "feat(ui): add EmblemRegistry with 24 Canvas-drawn athletic emblems"
```

---

### Task 8: EmblemPicker Composable

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/components/EmblemPicker.kt`

- [ ] **Step 1: Implement EmblemPicker**

```kotlin
package com.hrcoach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaTheme

@Composable
fun EmblemPicker(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        items(EmblemRegistry.allIds) { emblemId ->
            val isSelected = emblemId == selected
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) CardeaGradient
                        else Brush.linearGradient(
                            listOf(
                                CardeaTheme.colors.textTertiary.copy(alpha = 0.3f),
                                CardeaTheme.colors.textTertiary.copy(alpha = 0.1f)
                            )
                        )
                    )
                    .clickable { onSelect(emblemId) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(CardeaTheme.colors.bgPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    EmblemIcon(
                        emblemId = emblemId,
                        size = 28.dp,
                        tinted = isSelected
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/components/EmblemPicker.kt
git commit -m "feat(ui): add EmblemPicker 4x6 grid composable"
```

---

### Task 9: AuthScreen and AuthViewModel

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/auth/AuthScreen.kt`
- Create: `app/src/main/java/com/hrcoach/ui/auth/AuthViewModel.kt`

- [ ] **Step 1: Create AuthViewModel**

```kotlin
package com.hrcoach.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrcoach.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isSignUp: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state

    fun setEmail(email: String) = _state.update { it.copy(email = email, error = null) }
    fun setPassword(password: String) = _state.update { it.copy(password = password, error = null) }
    fun toggleSignUp() = _state.update { it.copy(isSignUp = !it.isSignUp, error = null) }

    fun submit() {
        val s = _state.value
        if (s.email.isBlank() || s.password.isBlank()) {
            _state.update { it.copy(error = "Email and password required") }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = if (s.isSignUp) {
                authRepository.signUpWithEmail(s.email.trim(), s.password)
            } else {
                authRepository.signInWithEmail(s.email.trim(), s.password)
            }
            result.fold(
                onSuccess = { _state.update { it.copy(isLoading = false) } },
                onFailure = { e ->
                    _state.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Authentication failed") }
                }
            )
        }
    }
}
```

- [ ] **Step 2: Add Google Sign-In support to AuthViewModel**

Add `signInWithGoogle(idToken: String)` method to `AuthViewModel`:

```kotlin
fun signInWithGoogle(idToken: String) {
    _state.update { it.copy(isLoading = true, error = null) }
    viewModelScope.launch {
        authRepository.signInWithGoogleCredential(idToken).fold(
            onSuccess = { _state.update { it.copy(isLoading = false) } },
            onFailure = { e ->
                _state.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Google sign-in failed") }
            }
        )
    }
}
```

- [ ] **Step 3: Create AuthScreen composable**

Create `app/src/main/java/com/hrcoach/ui/auth/AuthScreen.kt` with:
- Cardea-themed dark background
- Email + password `OutlinedTextField` fields
- "Sign In" / "Create Account" toggle
- Google Sign-In button using `CredentialManager` API (or `GoogleSignInClient` + `ActivityResultLauncher` via `rememberLauncherForActivityResult`). On success, extract `idToken` and call `viewModel.signInWithGoogle(idToken)`.
- Error display
- Gradient CTA button
- Loading state
- `onAuthSuccess: () -> Unit` callback triggered when `AuthRepository.currentUser` becomes non-null

Uses the Cardea design system: `CardeaTheme.colors`, `GlassCard`, `GradientSaveButton` pattern.

> **Google Sign-In wiring:** Requires a `web_client_id` from `google-services.json` (already in project for Firebase). Use `GoogleSignInOptions.Builder(DEFAULT_SIGN_IN).requestIdToken(webClientId).requestEmail().build()` to configure the sign-in client. The `idToken` from the sign-in result is passed to `AuthRepository.signInWithGoogleCredential()`.

- [ ] **Step 3: Build to verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/auth/AuthScreen.kt \
       app/src/main/java/com/hrcoach/ui/auth/AuthViewModel.kt
git commit -m "feat(ui): add AuthScreen with email/password sign-in and sign-up"
```

---

### Task 10: Navigation — Add auth Route

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt:107-614`

- [ ] **Step 1: Add auth route to NavGraph**

In `HrCoachNavGraph`, add a new `composable("auth")` route that renders `AuthScreen`. On successful auth (observe `AuthRepository.currentUser` becoming non-null), navigate back or to `home`.

```kotlin
composable("auth") {
    AuthScreen(
        onAuthSuccess = {
            navController.popBackStack()
        }
    )
}
```

No changes to start destination — splash still routes to home.

- [ ] **Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt
git commit -m "feat(nav): add auth route to NavGraph (not start destination)"
```

---

### Task 11: Upgrade AccountScreen and AccountViewModel

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt:74-496`
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountViewModel.kt:24-43`

- [ ] **Step 1: Update AccountUiState**

Add new fields:
```kotlin
data class AccountUiState(
    val displayName: String = "Runner",
    val avatarEmblemId: String = "pulse",  // was avatarSymbol
    val bio: String = "",
    val isProfileClaimed: Boolean = false,
    val memberSinceMs: Long = 0,
    val nameChangeDaysRemaining: Int = 0,
    val canChangeName: Boolean = true,
    val runnerLevel: String = "Beginner",
    val isAuthenticated: Boolean = false,
    val userEmail: String? = null,
    val isWorkoutActive: Boolean = false,
    val totalWorkouts: Int = 0,
    // ... keep all existing settings fields unchanged
)
```

- [ ] **Step 2: Update AccountViewModel**

- Inject `AuthRepository`
- Replace `_avatarSymbol` with `_avatarEmblemId`
- Add bio, claim state, runner level computation
- Add `signOut()` method
- Compute runner level from `workoutDao.countWorkouts(userId)`
- Read `isAuthenticated`, `userEmail` from `AuthRepository`
- Access `WorkoutState` (singleton object) to expose `isWorkoutActive` in `AccountUiState` — used to disable sign-out button during active workout

- [ ] **Step 3: Update ProfileHeroCard**

Replace Unicode `Text` with `EmblemIconWithRing`:
```kotlin
EmblemIconWithRing(
    emblemId = avatarEmblemId,
    ringSize = 56.dp,
    iconSize = 28.dp
)
```

Add bio display, runner level badge, "Member since" text.

- [ ] **Step 4: Update ProfileEditBottomSheet**

- Replace `AVATAR_SYMBOLS` 5x2 grid with `EmblemPicker` (4x6 grid)
- Add bio field (`OutlinedTextField`, max 40 chars)
- Name field: disable when `!canChangeName`, show "Editable in X days"
- Delete `AVATAR_SYMBOLS` constant

- [ ] **Step 5: Add Auth section to AccountScreen**

At the bottom of the scrollable content (before "About" text), add:
- If authenticated: show email, "Sign Out" button (disabled during active workout)
- If not authenticated: show "Sign in to claim your profile" card with gradient CTA → navigate to `auth` route

- [ ] **Step 6: Build and test**

Run: `./gradlew.bat assembleDebug && ./gradlew.bat testDebugUnitTest`
Expected: BUILD SUCCESSFUL, tests PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt \
       app/src/main/java/com/hrcoach/ui/account/AccountViewModel.kt
git commit -m "feat(ui): upgrade AccountScreen with emblems, bio, auth section, runner level"
```

---

### Task 12: Profile Claim Sheet

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/account/ProfileClaimSheet.kt`

- [ ] **Step 1: Implement ProfileClaimSheet**

A `ModalBottomSheet` that appears after first workout. Contains:
- Display name field (3-20 chars)
- EmblemPicker (4x6 grid)
- Bio field (optional, max 40 chars)
- If not authenticated: email/password fields + Google Sign-In button inline
- "Claim Profile" gradient CTA
- On claim: authenticates (if needed), saves profile, sets `isProfileClaimed = true`

Uses existing Cardea components: `GlassCard`, `OutlinedTextField` with Cardea colors, gradient button.

- [ ] **Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/account/ProfileClaimSheet.kt
git commit -m "feat(ui): add ProfileClaimSheet for post-first-workout profile creation"
```

---

### Task 13: Service Layer — Attach userId to Workouts + Claim Trigger

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`

- [ ] **Step 1: Inject AuthRepository into WorkoutForegroundService**

Add `@Inject lateinit var authRepository: AuthRepository` to the service.

- [ ] **Step 2: Set userId on workout creation**

In `startWorkout()` (or wherever `WorkoutEntity` is constructed), set `userId = authRepository.effectiveUserId`.

- [ ] **Step 3: Add claim flow trigger**

In `stopWorkout()`, after saving the workout, check `userProfileRepository.isProfileClaimed()`. If false, set a flag (e.g., `WorkoutState.showClaimPrompt = true`) that the post-run summary screen reads to show `ProfileClaimSheet`.

- [ ] **Step 4: Build and test**

Run: `./gradlew.bat assembleDebug && ./gradlew.bat testDebugUnitTest`
Expected: BUILD SUCCESSFUL, tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt
git commit -m "feat(service): attach userId to workouts, trigger claim flow after first workout"
```

---

### Task 14: Orphan Data Claim Flow

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountViewModel.kt`

- [ ] **Step 1: Add orphan data detection**

In `AccountViewModel.init` or on auth state change, check for orphaned data:
```kotlin
viewModelScope.launch {
    if (authRepository.isAuthenticated()) {
        val orphanCount = workoutDao.countWorkouts("")
        if (orphanCount > 0) {
            _showOrphanClaimDialog.value = true
        }
    }
}
```

- [ ] **Step 2: Add claim/dismiss methods**

```kotlin
fun claimOrphanedData() {
    viewModelScope.launch {
        val uid = authRepository.effectiveUserId
        workoutDao.claimOrphanedWorkouts(uid)
        bootcampDao.claimOrphanedEnrollments(uid)
        achievementDao.claimOrphanedAchievements(uid)
        _showOrphanClaimDialog.value = false
    }
}

fun dismissOrphanClaim() {
    _showOrphanClaimDialog.value = false
}
```

- [ ] **Step 3: Add dialog to AccountScreen**

Show an `AlertDialog` when `showOrphanClaimDialog` is true:
"We found existing workout data on this device. Claim it as yours?"
[Claim] [Skip]

- [ ] **Step 4: Build and test**

Run: `./gradlew.bat assembleDebug && ./gradlew.bat testDebugUnitTest`
Expected: BUILD SUCCESSFUL, tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/account/AccountViewModel.kt \
       app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt
git commit -m "feat(account): add orphan data claim flow for existing workout data"
```

---

### Task 15: Final Integration — Wire Post-Run Claim + Sign-Out

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt` (post-run route)
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutState.kt`

- [ ] **Step 1: Add showClaimPrompt to WorkoutState**

Add `val showClaimPrompt: MutableStateFlow<Boolean> = MutableStateFlow(false)` to `WorkoutState`.

- [ ] **Step 2: Wire post-run summary to show ProfileClaimSheet**

In the post-run summary composable (route `postrun/{workoutId}`), observe `WorkoutState.showClaimPrompt`. If true, show `ProfileClaimSheet`. On claim complete, set it back to false.

- [ ] **Step 3: Wire sign-out in AccountScreen**

Ensure the sign-out button:
1. Is disabled when `WorkoutState.isRunning`
2. Shows confirmation dialog
3. On confirm: calls `authRepository.signOut()`, resets `WorkoutState`, navigates to `home` with backstack cleared: `navController.navigate("home") { popUpTo(0) { inclusive = true } }`

- [ ] **Step 4: Full build and all tests**

Run: `./gradlew.bat assembleDebug && ./gradlew.bat testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests PASS

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: wire post-run claim prompt and sign-out flow"
```

---

### Task 16: Final Verification and Cleanup

- [ ] **Step 1: Full test suite**

Run: `./gradlew.bat testDebugUnitTest`
Expected: ALL PASS

- [ ] **Step 2: Full build**

Run: `./gradlew.bat clean assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Lint check**

Run: `./gradlew.bat lint`
Expected: No new errors

- [ ] **Step 4: Verify no getUserId() references remain**

Search for `getUserId` in the codebase. Should only appear in the deprecated `PREF_USER_ID` constant (kept for backward compat) and nowhere else.

- [ ] **Step 5: Verify AVATAR_SYMBOLS is deleted**

Search for `AVATAR_SYMBOLS` — should have zero results.

- [ ] **Step 6: Final commit if any cleanup needed**

```bash
git add -A
git commit -m "chore: final cleanup for account upgrade feature"
```
