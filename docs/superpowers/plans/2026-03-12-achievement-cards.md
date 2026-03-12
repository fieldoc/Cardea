# Achievement Cards Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add celebratory achievement cards that surface on post-run summary and live in a Profile gallery, with 3-level prestige scaling proportionate to difficulty across 5 milestone categories.

**Architecture:** ViewModel-triggered evaluation via `AchievementEvaluator` called from `PostRunSummaryViewModel.load()`, following the `BootcampSessionCompleter` pattern. Room entity + DAO for persistence. Shared `AchievementCard` composable rendered on post-run and Profile screens.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, StateFlow

**Spec:** `docs/superpowers/specs/2026-03-12-achievement-cards-design.md`

---

## File Map

### New files (under `app/src/main/java/com/hrcoach/`)
| File | Responsibility |
|------|---------------|
| `data/db/AchievementEntity.kt` | Room entity, `AchievementType` enum |
| `data/db/AchievementDao.kt` | All achievement queries |
| `domain/achievement/MilestoneDefinitions.kt` | Threshold constants + prestige mappings |
| `domain/achievement/StreakCalculator.kt` | Extracted streak + weekly goal streak logic |
| `domain/achievement/AchievementEvaluator.kt` | Post-workout + explicit milestone evaluation |
| `ui/components/AchievementCard.kt` | Shared card composable (full + compact modes) |
| `ui/account/AchievementGallery.kt` | Profile gallery section composable |

### New test files (under `app/src/test/java/com/hrcoach/`)
| File | Tests |
|------|-------|
| `domain/achievement/StreakCalculatorTest.kt` | Session streak + weekly goal streak logic |
| `domain/achievement/AchievementEvaluatorTest.kt` | All 5 milestone evaluation paths |
| `domain/achievement/MilestoneDefinitionsTest.kt` | Prestige mapping correctness |

### Modified files
| File | Change |
|------|--------|
| `data/db/AppDatabase.kt` (line 12) | Add `AchievementEntity::class` to entities, bump version 12→13, add `MIGRATION_12_13`, add `achievementDao()` |
| `di/AppModule.kt` (line 42, 66) | Register migration, provide `AchievementDao` |
| `ui/home/HomeSessionStreak.kt` | Delegate to `StreakCalculator.computeSessionStreak()` |
| `ui/theme/Color.kt` (after line 49) | Add `AchievementSlate`, `AchievementSky`, `AchievementGold` tokens |
| `data/db/WorkoutDao.kt` | Add `sumAllDistanceKm()` query |
| `ui/postrun/PostRunSummaryViewModel.kt` (line 47, after 144) | Add achievements to UiState, inject evaluator + BootcampRepository + AchievementDao, call evaluator in `load()`, mark shown in `onCleared()` |
| `ui/postrun/PostRunSummaryScreen.kt` (after line 272) | Render achievement cards section |
| `ui/bootcamp/BootcampViewModel.kt` (line 464, after tier update; line 584, after graduation) | Call `evaluateTierGraduation()` on tier-up; call `evaluateBootcampGraduation()` on graduation |
| `ui/account/AccountScreen.kt` (after line 133) | Add achievements gallery section |

---

## Chunk 1: Data Layer + Milestone Definitions

### Task 1: AchievementEntity + AchievementType enum

**Files:**
- Create: `app/src/main/java/com/hrcoach/data/db/AchievementEntity.kt`

- [ ] **Step 1: Create AchievementEntity and AchievementType**

```kotlin
package com.hrcoach.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AchievementType {
    TIER_GRADUATION,
    DISTANCE_MILESTONE,
    STREAK_MILESTONE,
    BOOTCAMP_GRADUATION,
    WEEKLY_GOAL_STREAK
}

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val milestone: String,
    val goal: String? = null,
    val tier: Int? = null,
    val prestigeLevel: Int,
    val earnedAtMs: Long,
    val triggerWorkoutId: Long? = null,
    @ColumnInfo(defaultValue = "0") val shown: Boolean = false
)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/db/AchievementEntity.kt
git commit -m "feat(achievement): add AchievementEntity and AchievementType enum"
```

---

### Task 2: AchievementDao

**Files:**
- Create: `app/src/main/java/com/hrcoach/data/db/AchievementDao.kt`

- [ ] **Step 1: Create AchievementDao**

```kotlin
package com.hrcoach.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AchievementDao {

    @Insert
    suspend fun insert(achievement: AchievementEntity)

    @Query("SELECT * FROM achievements WHERE shown = 0 ORDER BY earnedAtMs DESC")
    suspend fun getUnshownAchievements(): List<AchievementEntity>

    @Query("SELECT * FROM achievements ORDER BY earnedAtMs DESC")
    fun getAllAchievements(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements WHERE type = :type ORDER BY earnedAtMs DESC")
    fun getAchievementsByType(type: String): Flow<List<AchievementEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM achievements WHERE type = :type AND milestone = :milestone)")
    suspend fun hasAchievement(type: String, milestone: String): Boolean

    @Query("UPDATE achievements SET shown = 1 WHERE id IN (:ids)")
    suspend fun markShown(ids: List<Long>)
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/db/AchievementDao.kt
git commit -m "feat(achievement): add AchievementDao with all queries"
```

---

### Task 3: Register entity + DAO in AppDatabase and AppModule

**Files:**
- Modify: `app/src/main/java/com/hrcoach/data/db/AppDatabase.kt`
- Modify: `app/src/main/java/com/hrcoach/di/AppModule.kt`

- [ ] **Step 1: Add AchievementEntity to AppDatabase entities array (line 12)**

Add `AchievementEntity::class` to the `entities` list in the `@Database` annotation.

- [ ] **Step 2: Bump version from 12 to 13**

Change `version = 12` to `version = 13` in the `@Database` annotation.

- [ ] **Step 3: Add MIGRATION_12_13**

Add inside the `companion object`, following the existing migration pattern (e.g., `MIGRATION_11_12`):

```kotlin
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `achievements` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `type` TEXT NOT NULL,
                `milestone` TEXT NOT NULL,
                `goal` TEXT,
                `tier` INTEGER,
                `prestigeLevel` INTEGER NOT NULL,
                `earnedAtMs` INTEGER NOT NULL,
                `triggerWorkoutId` INTEGER,
                `shown` INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }
}
```

- [ ] **Step 4: Add abstract DAO function (after line 177)**

```kotlin
abstract fun achievementDao(): AchievementDao
```

- [ ] **Step 5: Register migration in AppModule (line 42)**

Add `AppDatabase.MIGRATION_12_13` to the `.addMigrations(...)` call in `provideDatabase()`.

- [ ] **Step 6: Provide AchievementDao in AppModule (after line 66)**

```kotlin
@Provides
@Singleton
fun provideAchievementDao(db: AppDatabase): AchievementDao = db.achievementDao()
```

- [ ] **Step 7: Build to verify compilation**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/db/AppDatabase.kt app/src/main/java/com/hrcoach/di/AppModule.kt
git commit -m "feat(achievement): register AchievementEntity in database, add migration 12->13"
```

---

### Task 4: MilestoneDefinitions

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/achievement/MilestoneDefinitions.kt`
- Create: `app/src/test/java/com/hrcoach/domain/achievement/MilestoneDefinitionsTest.kt`

- [ ] **Step 1: Write tests for milestone definitions**

```kotlin
package com.hrcoach.domain.achievement

import org.junit.Assert.assertEquals
import org.junit.Test

class MilestoneDefinitionsTest {

    @Test
    fun `distance milestones are ordered ascending`() {
        val thresholds = MilestoneDefinitions.DISTANCE_THRESHOLDS.map { it.first }
        assertEquals(thresholds, thresholds.sorted())
    }

    @Test
    fun `streak milestones are ordered ascending`() {
        val thresholds = MilestoneDefinitions.STREAK_THRESHOLDS.map { it.first }
        assertEquals(thresholds, thresholds.sorted())
    }

    @Test
    fun `weekly goal milestones are ordered ascending`() {
        val thresholds = MilestoneDefinitions.WEEKLY_GOAL_THRESHOLDS.map { it.first }
        assertEquals(thresholds, thresholds.sorted())
    }

    @Test
    fun `distance prestige 50km is 1, 250km is 2, 1000km is 3`() {
        val map = MilestoneDefinitions.DISTANCE_THRESHOLDS.toMap()
        assertEquals(1, map[50.0])
        assertEquals(2, map[250.0])
        assertEquals(3, map[1000.0])
    }

    @Test
    fun `tier graduation prestige is 2 for tier 1 and 3 for tier 2`() {
        assertEquals(2, MilestoneDefinitions.tierGraduationPrestige(1))
        assertEquals(3, MilestoneDefinitions.tierGraduationPrestige(2))
    }

    @Test
    fun `bootcamp graduation is always prestige 3`() {
        assertEquals(3, MilestoneDefinitions.BOOTCAMP_GRADUATION_PRESTIGE)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.domain.achievement.MilestoneDefinitionsTest"`
Expected: FAIL — class not found

- [ ] **Step 3: Implement MilestoneDefinitions**

```kotlin
package com.hrcoach.domain.achievement

object MilestoneDefinitions {

    /** Pair<thresholdKm, prestigeLevel> — ordered ascending */
    val DISTANCE_THRESHOLDS: List<Pair<Double, Int>> = listOf(
        50.0 to 1,
        100.0 to 1,
        250.0 to 2,
        500.0 to 2,
        1000.0 to 3,
        2500.0 to 3
    )

    /** Pair<sessionCount, prestigeLevel> — ordered ascending */
    val STREAK_THRESHOLDS: List<Pair<Int, Int>> = listOf(
        5 to 1,
        10 to 1,
        20 to 2,
        50 to 2,
        100 to 3
    )

    /** Pair<weekCount, prestigeLevel> — ordered ascending */
    val WEEKLY_GOAL_THRESHOLDS: List<Pair<Int, Int>> = listOf(
        4 to 1,
        8 to 2,
        12 to 2,
        24 to 3
    )

    const val BOOTCAMP_GRADUATION_PRESTIGE = 3

    fun tierGraduationPrestige(newTierIndex: Int): Int = when (newTierIndex) {
        1 -> 2  // Beginner -> Intermediate
        2 -> 3  // Intermediate -> Advanced
        else -> 1
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.domain.achievement.MilestoneDefinitionsTest"`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/achievement/MilestoneDefinitions.kt app/src/test/java/com/hrcoach/domain/achievement/MilestoneDefinitionsTest.kt
git commit -m "feat(achievement): add MilestoneDefinitions with threshold constants and prestige mappings"
```

---

## Chunk 2: Domain Logic — Streak Calculator + Achievement Evaluator

### Task 5: Extract StreakCalculator from HomeSessionStreak

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/achievement/StreakCalculator.kt`
- Create: `app/src/test/java/com/hrcoach/domain/achievement/StreakCalculatorTest.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeSessionStreak.kt`

- [ ] **Step 1: Write tests for StreakCalculator**

Port the logic from `HomeSessionStreak.computeSessionStreak()` (lines 17-48 of `HomeSessionStreak.kt`). Tests should cover:

```kotlin
package com.hrcoach.domain.achievement

import com.hrcoach.data.db.BootcampSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StreakCalculatorTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 3, 12)
    private val enrollmentStartMs = LocalDate.of(2026, 1, 1)
        .atStartOfDay(zone).toInstant().toEpochMilli()

    private fun session(
        week: Int, day: Int,
        status: String = BootcampSessionEntity.STATUS_COMPLETED,
        completedAtMs: Long? = null
    ) = BootcampSessionEntity(
        id = 0,
        enrollmentId = 1,
        weekNumber = week,
        dayOfWeek = day,
        sessionType = "EASY",
        targetMinutes = 30,
        status = status,
        completedWorkoutId = if (status == BootcampSessionEntity.STATUS_COMPLETED) 1L else null,
        completedAtMs = completedAtMs
    )

    @Test
    fun `all completed sessions count as streak`() {
        val sessions = listOf(
            session(1, 1), session(1, 3), session(1, 5),
            session(2, 1), session(2, 3)
        )
        assertEquals(5, StreakCalculator.computeSessionStreak(sessions, enrollmentStartMs, today, zone))
    }

    @Test
    fun `skipped session breaks streak`() {
        val sessions = listOf(
            session(1, 1), session(1, 3, BootcampSessionEntity.STATUS_SKIPPED),
            session(1, 5), session(2, 1)
        )
        // Walking backward: w2d1=COMPLETED, w1d5=COMPLETED, w1d3=SKIPPED -> streak=2
        assertEquals(2, StreakCalculator.computeSessionStreak(sessions, enrollmentStartMs, today, zone))
    }

    @Test
    fun `deferred sessions are skipped, do not break streak`() {
        val sessions = listOf(
            session(1, 1), session(1, 3, BootcampSessionEntity.STATUS_DEFERRED),
            session(1, 5), session(2, 1)
        )
        // w2d1=COMPLETED, w1d5=COMPLETED, w1d3=DEFERRED(skip), w1d1=COMPLETED -> streak=3
        assertEquals(3, StreakCalculator.computeSessionStreak(sessions, enrollmentStartMs, today, zone))
    }

    @Test
    fun `empty sessions returns 0`() {
        assertEquals(0, StreakCalculator.computeSessionStreak(emptyList(), enrollmentStartMs, today, zone))
    }

    // --- Weekly Goal Streak tests ---

    @Test
    fun `weekly goal streak counts consecutive weeks at target`() {
        // 3 runs per week for weeks 1-4, runsPerWeek=3
        val sessions = (1..4).flatMap { w ->
            listOf(session(w, 1), session(w, 3), session(w, 5))
        }
        assertEquals(4, StreakCalculator.computeWeeklyGoalStreak(
            sessions, runsPerWeek = 3, enrollmentStartMs = enrollmentStartMs,
            today = LocalDate.of(2026, 2, 2), zone = zone // week 5 is current, so 4 complete weeks
        ))
    }

    @Test
    fun `weekly goal streak breaks at first short week`() {
        // Weeks 3,4 hit target (3 each), week 2 only has 2
        val sessions = listOf(
            session(2, 1), session(2, 3), // only 2 in week 2
            session(3, 1), session(3, 3), session(3, 5),
            session(4, 1), session(4, 3), session(4, 5)
        )
        assertEquals(2, StreakCalculator.computeWeeklyGoalStreak(
            sessions, runsPerWeek = 3, enrollmentStartMs = enrollmentStartMs,
            today = LocalDate.of(2026, 2, 2), zone = zone
        ))
    }

    @Test
    fun `weekly goal streak excludes current incomplete week`() {
        // Only week 1, and today is still in week 1
        val sessions = listOf(session(1, 1), session(1, 3), session(1, 5))
        assertEquals(0, StreakCalculator.computeWeeklyGoalStreak(
            sessions, runsPerWeek = 3, enrollmentStartMs = enrollmentStartMs,
            today = LocalDate.of(2026, 1, 5), zone = zone // Monday of week 1
        ))
    }

    @Test
    fun `deferred sessions do not count toward weekly goal`() {
        val sessions = listOf(
            session(1, 1), session(1, 3), session(1, 5, BootcampSessionEntity.STATUS_DEFERRED)
        )
        // Only 2 completed in week 1
        assertEquals(0, StreakCalculator.computeWeeklyGoalStreak(
            sessions, runsPerWeek = 3, enrollmentStartMs = enrollmentStartMs,
            today = LocalDate.of(2026, 1, 12), zone = zone
        ))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.domain.achievement.StreakCalculatorTest"`
Expected: FAIL — class not found

- [ ] **Step 3: Implement StreakCalculator**

Extract the core logic from `HomeSessionStreak.computeSessionStreak()` and add the new weekly goal streak algorithm:

```kotlin
package com.hrcoach.domain.achievement

import com.hrcoach.data.db.BootcampSessionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.IsoFields

object StreakCalculator {

    /**
     * Count consecutive completed sessions walking backward.
     * DEFERRED sessions are skipped (don't break streak).
     * SKIPPED or past SCHEDULED sessions break the streak.
     */
    fun computeSessionStreak(
        sessions: List<BootcampSessionEntity>,
        enrollmentStartMs: Long,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Int {
        val sorted = sessions.sortedWith(
            compareByDescending<BootcampSessionEntity> { it.weekNumber }
                .thenByDescending { it.dayOfWeek }
        )
        var streak = 0
        for (s in sorted) {
            when (s.status) {
                BootcampSessionEntity.STATUS_COMPLETED -> streak++
                BootcampSessionEntity.STATUS_DEFERRED -> continue
                BootcampSessionEntity.STATUS_SKIPPED -> break
                BootcampSessionEntity.STATUS_SCHEDULED -> {
                    // Only past scheduled sessions break the streak.
                    // Future scheduled sessions are not yet missed — skip them.
                    val sessionDate = Instant.ofEpochMilli(enrollmentStartMs)
                        .atZone(zone).toLocalDate()
                        .plusWeeks((s.weekNumber - 1).toLong())
                        .with(java.time.DayOfWeek.of(s.dayOfWeek))
                    if (sessionDate.isBefore(today)) break else continue
                }
                else -> break
            }
        }
        return streak
    }

    /**
     * Count consecutive completed ISO weeks where completed session count >= runsPerWeek.
     * Walks backward from the most recently completed week (excludes current incomplete week).
     * DEFERRED sessions do not count as completions.
     */
    fun computeWeeklyGoalStreak(
        sessions: List<BootcampSessionEntity>,
        runsPerWeek: Int,
        enrollmentStartMs: Long,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Int {
        if (sessions.isEmpty() || runsPerWeek <= 0) return 0

        // Group completed sessions by week number
        val completedByWeek = sessions
            .filter { it.status == BootcampSessionEntity.STATUS_COMPLETED }
            .groupBy { it.weekNumber }
            .mapValues { (_, v) -> v.size }

        // Find the range of weeks to check
        val maxWeek = sessions.maxOf { it.weekNumber }
        val enrollmentStart = Instant.ofEpochMilli(enrollmentStartMs)
            .atZone(zone).toLocalDate()
        val firstWeek = sessions.minOf { it.weekNumber }

        // Determine current week number to exclude it
        // Current week = maxWeek if there are SCHEDULED sessions in it, otherwise maxWeek is complete
        val hasScheduledInMaxWeek = sessions.any {
            it.weekNumber == maxWeek && it.status == BootcampSessionEntity.STATUS_SCHEDULED
        }
        val lastCompleteWeek = if (hasScheduledInMaxWeek) maxWeek - 1 else maxWeek

        var streak = 0
        for (week in lastCompleteWeek downTo firstWeek) {
            val completed = completedByWeek[week] ?: 0
            if (completed >= runsPerWeek) {
                streak++
            } else {
                break
            }
        }
        return streak
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.domain.achievement.StreakCalculatorTest"`
Expected: ALL PASS

- [ ] **Step 5: Update HomeSessionStreak to delegate to StreakCalculator**

In `app/src/main/java/com/hrcoach/ui/home/HomeSessionStreak.kt`, replace the body of `computeSessionStreak()` with a delegation call:

```kotlin
fun computeSessionStreak(
    sessions: List<BootcampSessionEntity>,
    enrollmentStartMs: Long,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault()
): Int = StreakCalculator.computeSessionStreak(sessions, enrollmentStartMs, today, zone)
```

Add the import: `import com.hrcoach.domain.achievement.StreakCalculator`

- [ ] **Step 6: Run existing streak tests to verify no regression**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.ui.bootcamp.*"`
Expected: ALL PASS (existing streak tests still work)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/achievement/StreakCalculator.kt app/src/test/java/com/hrcoach/domain/achievement/StreakCalculatorTest.kt app/src/main/java/com/hrcoach/ui/home/HomeSessionStreak.kt
git commit -m "feat(achievement): extract StreakCalculator with session streak + weekly goal streak"
```

---

### Task 6: AchievementEvaluator

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/achievement/AchievementEvaluator.kt`
- Create: `app/src/test/java/com/hrcoach/domain/achievement/AchievementEvaluatorTest.kt`

- [ ] **Step 1: Write tests for AchievementEvaluator using a FakeAchievementDao**

No mockito dependency exists in the project — use a simple fake DAO instead:

```kotlin
package com.hrcoach.domain.achievement

import com.hrcoach.data.db.AchievementDao
import com.hrcoach.data.db.AchievementEntity
import com.hrcoach.data.db.AchievementType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** In-memory fake for AchievementDao — avoids needing mockito dependency */
class FakeAchievementDao : AchievementDao {
    val inserted = mutableListOf<AchievementEntity>()

    override suspend fun insert(achievement: AchievementEntity) { inserted.add(achievement) }
    override suspend fun getUnshownAchievements(): List<AchievementEntity> = inserted.filter { !it.shown }
    override fun getAllAchievements(): Flow<List<AchievementEntity>> = flowOf(inserted.toList())
    override fun getAchievementsByType(type: String): Flow<List<AchievementEntity>> = flowOf(inserted.filter { it.type == type })
    override suspend fun hasAchievement(type: String, milestone: String): Boolean = inserted.any { it.type == type && it.milestone == milestone }
    override suspend fun markShown(ids: List<Long>) { /* no-op for tests */ }
}

class AchievementEvaluatorTest {

    private lateinit var dao: FakeAchievementDao
    private lateinit var evaluator: AchievementEvaluator

    @Before
    fun setup() {
        dao = FakeAchievementDao()
        evaluator = AchievementEvaluator(dao)
    }

    // --- Distance milestones ---

    @Test
    fun `evaluateDistance inserts achievement when threshold crossed`() = runTest {
        evaluator.evaluateDistance(totalKm = 105.0, workoutId = 42L)

        // Should award both 50km and 100km
        assertTrue(dao.inserted.any { it.milestone == "50km" && it.prestigeLevel == 1 })
        assertTrue(dao.inserted.any { it.milestone == "100km" && it.prestigeLevel == 1 })
        assertEquals(2, dao.inserted.size)
    }

    @Test
    fun `evaluateDistance skips already earned milestones`() = runTest {
        // Pre-seed 50km as already earned
        evaluator.evaluateDistance(totalKm = 55.0, workoutId = 1L) // earns 50km
        dao.inserted.clear() // reset, pretend it's a new workout

        // Manually re-add the 50km so hasAchievement returns true
        dao.inserted.add(AchievementEntity(type = AchievementType.DISTANCE_MILESTONE.name, milestone = "50km", prestigeLevel = 1, earnedAtMs = 0))

        evaluator.evaluateDistance(totalKm = 105.0, workoutId = 42L)

        // Should only insert 100km, not 50km again
        val newInserts = dao.inserted.filter { it.triggerWorkoutId == 42L }
        assertEquals(1, newInserts.size)
        assertEquals("100km", newInserts[0].milestone)
    }

    @Test
    fun `evaluateDistance does nothing below first threshold`() = runTest {
        evaluator.evaluateDistance(totalKm = 30.0, workoutId = 42L)
        assertTrue(dao.inserted.isEmpty())
    }

    @Test
    fun `evaluateDistance at exactly threshold value awards it`() = runTest {
        evaluator.evaluateDistance(totalKm = 50.0, workoutId = 42L)
        assertEquals(1, dao.inserted.size)
        assertEquals("50km", dao.inserted[0].milestone)
    }

    // --- Streak milestones ---

    @Test
    fun `evaluateStreak inserts when streak crosses threshold`() = runTest {
        evaluator.evaluateStreak(currentStreak = 12, workoutId = 42L)

        val milestones = dao.inserted.map { it.milestone }
        assertTrue("5_sessions" in milestones)
        assertTrue("10_sessions" in milestones)
        assertEquals(2, dao.inserted.size)
    }

    @Test
    fun `evaluateStreak does nothing when streak is 0`() = runTest {
        evaluator.evaluateStreak(currentStreak = 0, workoutId = 42L)
        assertTrue(dao.inserted.isEmpty())
    }

    // --- Tier graduation ---

    @Test
    fun `evaluateTierGraduation inserts with correct prestige and goal`() = runTest {
        evaluator.evaluateTierGraduation(newTierIndex = 1, goal = "HALF_MARATHON")

        assertEquals(1, dao.inserted.size)
        val a = dao.inserted[0]
        assertEquals(AchievementType.TIER_GRADUATION.name, a.type)
        assertEquals("tier_1_HALF_MARATHON", a.milestone)
        assertEquals("HALF_MARATHON", a.goal)
        assertEquals(1, a.tier)
        assertEquals(2, a.prestigeLevel)
    }

    @Test
    fun `evaluateTierGraduation allows same tier for different goals`() = runTest {
        evaluator.evaluateTierGraduation(newTierIndex = 1, goal = "HALF_MARATHON")
        evaluator.evaluateTierGraduation(newTierIndex = 1, goal = "MARATHON")

        assertEquals(2, dao.inserted.size)
        assertEquals("tier_1_HALF_MARATHON", dao.inserted[0].milestone)
        assertEquals("tier_1_MARATHON", dao.inserted[1].milestone)
    }

    // --- Bootcamp graduation ---

    @Test
    fun `evaluateBootcampGraduation inserts prestige 3 with goal`() = runTest {
        evaluator.evaluateBootcampGraduation(
            enrollmentId = 1L, goal = "MARATHON", tierIndex = 2
        )

        assertEquals(1, dao.inserted.size)
        val a = dao.inserted[0]
        assertEquals(AchievementType.BOOTCAMP_GRADUATION.name, a.type)
        assertEquals(3, a.prestigeLevel)
        assertEquals("MARATHON", a.goal)
    }

    // --- Weekly goal streak ---

    @Test
    fun `evaluateWeeklyGoalStreak inserts when threshold crossed`() = runTest {
        evaluator.evaluateWeeklyGoalStreak(consecutiveWeeks = 5, workoutId = 42L)

        assertEquals(1, dao.inserted.size)
        assertEquals("4_weeks", dao.inserted[0].milestone)
        assertEquals(1, dao.inserted[0].prestigeLevel)
    }

    @Test
    fun `evaluateWeeklyGoalStreak does nothing when weeks is 0`() = runTest {
        evaluator.evaluateWeeklyGoalStreak(consecutiveWeeks = 0, workoutId = 42L)
        assertTrue(dao.inserted.isEmpty())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.domain.achievement.AchievementEvaluatorTest"`
Expected: FAIL — class not found

- [ ] **Step 3: Implement AchievementEvaluator**

```kotlin
package com.hrcoach.domain.achievement

import com.hrcoach.data.db.AchievementDao
import com.hrcoach.data.db.AchievementEntity
import com.hrcoach.data.db.AchievementType
import javax.inject.Inject

class AchievementEvaluator @Inject constructor(
    private val achievementDao: AchievementDao
) {

    suspend fun evaluateDistance(totalKm: Double, workoutId: Long) {
        for ((threshold, prestige) in MilestoneDefinitions.DISTANCE_THRESHOLDS) {
            if (totalKm < threshold) break
            val milestone = "${threshold.toInt()}km"
            if (!achievementDao.hasAchievement(AchievementType.DISTANCE_MILESTONE.name, milestone)) {
                achievementDao.insert(
                    AchievementEntity(
                        type = AchievementType.DISTANCE_MILESTONE.name,
                        milestone = milestone,
                        prestigeLevel = prestige,
                        earnedAtMs = System.currentTimeMillis(),
                        triggerWorkoutId = workoutId
                    )
                )
            }
        }
    }

    suspend fun evaluateStreak(currentStreak: Int, workoutId: Long) {
        for ((threshold, prestige) in MilestoneDefinitions.STREAK_THRESHOLDS) {
            if (currentStreak < threshold) break
            val milestone = "${threshold}_sessions"
            if (!achievementDao.hasAchievement(AchievementType.STREAK_MILESTONE.name, milestone)) {
                achievementDao.insert(
                    AchievementEntity(
                        type = AchievementType.STREAK_MILESTONE.name,
                        milestone = milestone,
                        prestigeLevel = prestige,
                        earnedAtMs = System.currentTimeMillis(),
                        triggerWorkoutId = workoutId
                    )
                )
            }
        }
    }

    suspend fun evaluateWeeklyGoalStreak(consecutiveWeeks: Int, workoutId: Long) {
        for ((threshold, prestige) in MilestoneDefinitions.WEEKLY_GOAL_THRESHOLDS) {
            if (consecutiveWeeks < threshold) break
            val milestone = "${threshold}_weeks"
            if (!achievementDao.hasAchievement(AchievementType.WEEKLY_GOAL_STREAK.name, milestone)) {
                achievementDao.insert(
                    AchievementEntity(
                        type = AchievementType.WEEKLY_GOAL_STREAK.name,
                        milestone = milestone,
                        prestigeLevel = prestige,
                        earnedAtMs = System.currentTimeMillis(),
                        triggerWorkoutId = workoutId
                    )
                )
            }
        }
    }

    suspend fun evaluateTierGraduation(newTierIndex: Int, goal: String) {
        val milestone = "tier_${newTierIndex}_$goal"
        if (!achievementDao.hasAchievement(AchievementType.TIER_GRADUATION.name, milestone)) {
            achievementDao.insert(
                AchievementEntity(
                    type = AchievementType.TIER_GRADUATION.name,
                    milestone = milestone,
                    goal = goal,
                    tier = newTierIndex,
                    prestigeLevel = MilestoneDefinitions.tierGraduationPrestige(newTierIndex),
                    earnedAtMs = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun evaluateBootcampGraduation(enrollmentId: Long, goal: String, tierIndex: Int) {
        val milestone = "graduated_${enrollmentId}"
        if (!achievementDao.hasAchievement(AchievementType.BOOTCAMP_GRADUATION.name, milestone)) {
            achievementDao.insert(
                AchievementEntity(
                    type = AchievementType.BOOTCAMP_GRADUATION.name,
                    milestone = milestone,
                    goal = goal,
                    tier = tierIndex,
                    prestigeLevel = MilestoneDefinitions.BOOTCAMP_GRADUATION_PRESTIGE,
                    earnedAtMs = System.currentTimeMillis()
                )
            )
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.domain.achievement.AchievementEvaluatorTest"`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/achievement/AchievementEvaluator.kt app/src/test/java/com/hrcoach/domain/achievement/AchievementEvaluatorTest.kt
git commit -m "feat(achievement): add AchievementEvaluator with all 5 milestone evaluation paths"
```

---

## Chunk 3: Integration — ViewModel Wiring + Distance Query

**Execution order:** Task 9 → Task 7 → Task 8 (Task 9 must complete before Task 7 because Task 7 calls `sumAllDistanceKm()`).

### Task 7: Wire AchievementEvaluator into PostRunSummaryViewModel

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryViewModel.kt`

This task requires reading the current file at implementation time to identify exact insertion points. The key changes:

- [ ] **Step 1: Add `achievements` field to `PostRunSummaryUiState`**

At line 47 (before closing brace of `PostRunSummaryUiState`), add:

```kotlin
val achievements: List<AchievementEntity> = emptyList()
```

Add import: `import com.hrcoach.data.db.AchievementEntity`

- [ ] **Step 2: Inject AchievementEvaluator, AchievementDao, and BootcampRepository into ViewModel constructor**

Add to the `@Inject constructor` parameters (line 50-54):

```kotlin
private val achievementEvaluator: AchievementEvaluator,
private val achievementDao: AchievementDao,
private val bootcampRepository: BootcampRepository,
```

Add imports:
```kotlin
import com.hrcoach.domain.achievement.AchievementEvaluator
import com.hrcoach.domain.achievement.StreakCalculator
import com.hrcoach.data.db.AchievementDao
import com.hrcoach.data.repository.BootcampRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
```

- [ ] **Step 3: Add achievement evaluation call in `load()` method**

After the `BootcampSessionCompleter` block (around line 144), add achievement evaluation:

```kotlin
// Achievement evaluation — runs after workout is fully persisted
try {
    val workoutId = id
    // Distance milestone (uses WorkoutDao.sumAllDistanceKm() added in Task 9)
    val totalKm = workoutRepository.sumAllDistanceKm()
    achievementEvaluator.evaluateDistance(totalKm, workoutId)

    // Streak + weekly goal (only if bootcamp enrollment exists)
    bootcampRepository.getActiveEnrollmentOnce()?.let { enrollment ->
        val sessions = bootcampRepository.getSessionsForEnrollmentOnce(enrollment.id)
        val streak = StreakCalculator.computeSessionStreak(sessions, enrollment.startDate)
        achievementEvaluator.evaluateStreak(streak, workoutId)

        val weeklyStreak = StreakCalculator.computeWeeklyGoalStreak(
            sessions, enrollment.runsPerWeek, enrollment.startDate
        )
        achievementEvaluator.evaluateWeeklyGoalStreak(weeklyStreak, workoutId)
    }

    // Load unshown achievements for display
    val unshown = achievementDao.getUnshownAchievements()
    _uiState.update { it.copy(achievements = unshown) }
} catch (e: Exception) {
    // Achievement evaluation is non-critical — don't break post-run flow
}
```

**Important:** Task 9 (adding `sumAllDistanceKm` to WorkoutDao) must be completed BEFORE this task, otherwise `workoutRepository.sumAllDistanceKm()` won't compile.

- [ ] **Step 4: Add `onCleared()` to mark achievements shown**

```kotlin
override fun onCleared() {
    super.onCleared()
    val ids = _uiState.value.achievements.map { it.id }
    if (ids.isNotEmpty()) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                try { achievementDao.markShown(ids) } catch (_: Exception) {}
            }
        }
    }
}
```

Note: `NonCancellable` ensures the DB write completes even though `viewModelScope` is about to be cancelled. If it doesn't fire (process death), the cards show again next time — acceptable.

- [ ] **Step 5: Build to verify compilation**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryViewModel.kt
git commit -m "feat(achievement): wire AchievementEvaluator into PostRunSummaryViewModel"
```

---

### Task 8: Wire tier graduation + bootcamp graduation into BootcampViewModel

Both trigger points live in `BootcampViewModel`:
- **Tier graduation**: `acceptTierChange()` at line 446 — updates `tierIndex` on enrollment
- **Bootcamp graduation**: `graduateCurrentGoal()` at line 581 — calls `bootcampRepository.graduateEnrollment()`

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt`

- [ ] **Step 1: Inject AchievementEvaluator into BootcampViewModel constructor**

Add `private val achievementEvaluator: AchievementEvaluator` to the `@Inject constructor`.

Add import: `import com.hrcoach.domain.achievement.AchievementEvaluator`

- [ ] **Step 2: Wire tier graduation in acceptTierChange() (after line 464)**

After the `bootcampRepository.updateEnrollment(...)` call at line 458-464, add:

```kotlin
// Award tier graduation achievement
achievementEvaluator.evaluateTierGraduation(
    newTierIndex = updatedTierIndex,
    goal = enrollment.goalType
)
```

The full `acceptTierChange()` should now look like:
```kotlin
fun acceptTierChange(direction: TierPromptDirection) {
    if (direction == TierPromptDirection.NONE) return
    viewModelScope.launch {
        val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return@launch
        val delta = when (direction) {
            TierPromptDirection.UP -> 1
            TierPromptDirection.DOWN -> -1
            TierPromptDirection.NONE -> 0
        }
        val updatedTierIndex = (enrollment.tierIndex + delta)
            .coerceIn(TierCtlRanges.minTierIndex, TierCtlRanges.maxTierIndex)

        bootcampRepository.updateEnrollment(
            enrollment.copy(
                tierIndex = updatedTierIndex,
                tierPromptDismissCount = 0,
                tierPromptSnoozedUntilMs = 0L
            )
        )
        // Award tier graduation achievement (only fires for UP direction due to prestige mapping)
        if (direction == TierPromptDirection.UP) {
            achievementEvaluator.evaluateTierGraduation(
                newTierIndex = updatedTierIndex,
                goal = enrollment.goalType
            )
        }
        _uiState.update {
            it.copy(
                tierPromptDirection = TierPromptDirection.NONE,
                tierPromptEvidence = null
            )
        }
    }
}
```

- [ ] **Step 3: Wire bootcamp graduation in graduateCurrentGoal() (after line 584)**

After `bootcampRepository.graduateEnrollment(enrollment.id)`, add:

```kotlin
fun graduateCurrentGoal() {
    viewModelScope.launch {
        val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return@launch
        bootcampRepository.graduateEnrollment(enrollment.id)
        // Award bootcamp graduation achievement
        achievementEvaluator.evaluateBootcampGraduation(
            enrollmentId = enrollment.id,
            goal = enrollment.goalType,
            tierIndex = enrollment.tierIndex
        )
    }
}
```

- [ ] **Step 4: Build to verify compilation**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt
git commit -m "feat(achievement): wire tier graduation + bootcamp graduation into BootcampViewModel"
```

---

### Task 9: Add sumAllDistanceKm query

**IMPORTANT:** This task MUST be completed before Task 7, because Task 7 calls `workoutRepository.sumAllDistanceKm()`.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/data/db/WorkoutDao.kt`
- Modify: `app/src/main/java/com/hrcoach/data/repository/WorkoutRepository.kt`

- [ ] **Step 1: Add the query to WorkoutDao**

In `app/src/main/java/com/hrcoach/data/db/WorkoutDao.kt`, add:

```kotlin
@Query("SELECT COALESCE(SUM(totalDistanceMeters), 0) / 1000.0 FROM workouts")
suspend fun sumAllDistanceKm(): Double
```

- [ ] **Step 2: Expose through WorkoutRepository**

In `app/src/main/java/com/hrcoach/data/repository/WorkoutRepository.kt`, add:

```kotlin
suspend fun sumAllDistanceKm(): Double = workoutDao.sumAllDistanceKm()
```

Note: Check if `WorkoutRepository` already has a `workoutDao` field. If it uses a different name, adapt accordingly.

- [ ] **Step 3: Build to verify**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/db/WorkoutDao.kt app/src/main/java/com/hrcoach/data/repository/WorkoutRepository.kt
git commit -m "feat(achievement): add sumAllDistanceKm query for distance milestones"
```

---

## Chunk 4: UI — Achievement Card + Color Tokens

### Task 10: Add achievement color tokens to Color.kt

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/theme/Color.kt`

- [ ] **Step 1: Add color tokens after existing zone colors (after line 49)**

```kotlin
// Achievement prestige colors
val AchievementSlate = Color(0xFF94A3B8)
val AchievementSlateBorder = Color(0x2694A3B8)    // 15% opacity
val AchievementSlateBg = Color(0x1494A3B8)        // 8% opacity

val AchievementSky = Color(0xFF7DD3FC)
val AchievementSkyBorder = Color(0x407DD3FC)       // 25% opacity
val AchievementSkyBg = Color(0x147DD3FC)           // 8% opacity

val AchievementGold = Color(0xFFFACC15)
val AchievementGoldBorder = Color(0x4DFACC15)      // 30% opacity
val AchievementGoldBg = Color(0x1FFACC15)          // 12% opacity
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/theme/Color.kt
git commit -m "feat(achievement): add prestige color tokens (slate, sky, gold)"
```

---

### Task 11: AchievementCard composable

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/components/AchievementCard.kt`

- [ ] **Step 1: Create AchievementCard composable**

```kotlin
package com.hrcoach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.data.db.AchievementEntity
import com.hrcoach.data.db.AchievementType
import com.hrcoach.ui.theme.*

@Composable
fun AchievementCard(
    achievement: AchievementEntity,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (accentColor, borderColor, bgColor) = prestigeColors(achievement.prestigeLevel)
    val shape = RoundedCornerShape(if (compact) 12.dp else 16.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(bgColor, bgColor.copy(alpha = bgColor.alpha * 0.25f))
                )
            )
            .border(1.dp, borderColor, shape)
            .padding(if (compact) 12.dp else 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = achievementIcon(achievement),
                fontSize = if (compact) 24.sp else 32.sp
            )
            Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
            Text(
                text = achievementTitle(achievement),
                color = CardeaTextPrimary,
                fontSize = if (compact) 13.sp else 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            if (achievement.goal != null) {
                Text(
                    text = goalDisplayName(achievement.goal),
                    color = CardeaTextTertiary,
                    fontSize = if (compact) 11.sp else 13.sp
                )
            } else {
                Text(
                    text = categoryLabel(achievement.type),
                    color = CardeaTextTertiary,
                    fontSize = if (compact) 11.sp else 13.sp
                )
            }
            Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
            PrestigeDots(level = achievement.prestigeLevel, color = accentColor, compact = compact)
        }
    }
}

@Composable
private fun PrestigeDots(level: Int, color: Color, compact: Boolean) {
    val size = if (compact) 6.dp else 8.dp
    val gap = if (compact) 3.dp else 4.dp
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        repeat(3) { i ->
            Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(if (i < level) color else color.copy(alpha = 0.2f))
            )
        }
    }
}

private fun prestigeColors(level: Int): Triple<Color, Color, Color> = when (level) {
    1 -> Triple(AchievementSlate, AchievementSlateBorder, AchievementSlateBg)
    2 -> Triple(AchievementSky, AchievementSkyBorder, AchievementSkyBg)
    3 -> Triple(AchievementGold, AchievementGoldBorder, AchievementGoldBg)
    else -> Triple(AchievementSlate, AchievementSlateBorder, AchievementSlateBg)
}

private fun achievementIcon(a: AchievementEntity): String = when (a.type) {
    AchievementType.TIER_GRADUATION.name -> goalIcon(a.goal)
    AchievementType.BOOTCAMP_GRADUATION.name -> "\uD83C\uDF93" // graduation cap
    AchievementType.DISTANCE_MILESTONE.name -> "\uD83C\uDFC3" // runner
    AchievementType.STREAK_MILESTONE.name -> "\uD83D\uDD25"   // flame
    AchievementType.WEEKLY_GOAL_STREAK.name -> "\uD83C\uDFAF" // target
    else -> "\uD83C\uDFC6" // trophy
}

private fun goalIcon(goal: String?): String = when (goal) {
    "CARDIO_HEALTH" -> "\u2764\uFE0F"     // heart
    "RACE_5K_10K" -> "\uD83D\uDC5F"       // shoe
    "HALF_MARATHON" -> "\uD83D\uDEE3\uFE0F" // road
    "MARATHON" -> "\uD83C\uDFC5"           // medal
    else -> "\uD83C\uDFC6"                 // trophy
}

private fun achievementTitle(a: AchievementEntity): String = when (a.type) {
    AchievementType.TIER_GRADUATION.name -> when (a.tier) {
        1 -> "Intermediate"
        2 -> "Advanced"
        else -> "Tier Up"
    }
    AchievementType.BOOTCAMP_GRADUATION.name -> "Program Complete"
    AchievementType.DISTANCE_MILESTONE.name -> a.milestone.replace("km", " km")
    AchievementType.STREAK_MILESTONE.name -> a.milestone
        .replace("_sessions", " Sessions")
        .replaceFirstChar { it.uppercase() }
    AchievementType.WEEKLY_GOAL_STREAK.name -> a.milestone
        .replace("_weeks", " Weeks On Target")
        .replaceFirstChar { it.uppercase() }
    else -> a.milestone
}

private fun goalDisplayName(goal: String?): String = when (goal) {
    "CARDIO_HEALTH" -> "Cardio Health"
    "RACE_5K_10K" -> "5K / 10K"
    "HALF_MARATHON" -> "Half Marathon"
    "MARATHON" -> "Marathon"
    else -> ""
}

private fun categoryLabel(type: String): String = when (type) {
    AchievementType.DISTANCE_MILESTONE.name -> "Distance"
    AchievementType.STREAK_MILESTONE.name -> "No Misses"
    AchievementType.WEEKLY_GOAL_STREAK.name -> "Weekly Goal"
    else -> ""
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/components/AchievementCard.kt
git commit -m "feat(achievement): add AchievementCard composable with prestige visual treatment"
```

---

## Chunk 5: UI Integration — Post-Run + Profile Gallery

### Task 12: Add achievement cards to PostRunSummaryScreen

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt`

- [ ] **Step 1: Add achievement section after bootcamp context card (after line 272)**

```kotlin
// Achievement cards
if (uiState.achievements.isNotEmpty()) {
    Spacer(Modifier.height(16.dp))
    Text(
        text = "ACHIEVEMENT UNLOCKED",
        color = AchievementGold,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 3.sp,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(12.dp))
    uiState.achievements.forEach { achievement ->
        AchievementCard(
            achievement = achievement,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
    }
}
```

Add imports:
```kotlin
import com.hrcoach.ui.components.AchievementCard
import com.hrcoach.ui.theme.AchievementGold
```

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt
git commit -m "feat(achievement): render achievement cards on post-run summary"
```

---

### Task 13: Add achievement gallery to Profile/Account screen

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/account/AchievementGallery.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountViewModel.kt`

- [ ] **Step 1: Add achievements to AccountUiState**

In `AccountViewModel.kt`, add to `AccountUiState`:

```kotlin
val achievements: List<AchievementEntity> = emptyList()
```

- [ ] **Step 2: Inject AchievementDao and load achievements in AccountViewModel**

Add `private val achievementDao: AchievementDao` to the `@Inject constructor`.

In the `init` block or state flow composition, collect from `achievementDao.getAllAchievements()` and include in the combined state.

- [ ] **Step 3: Create AchievementGallery composable**

```kotlin
package com.hrcoach.ui.account

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.data.db.AchievementEntity
import com.hrcoach.ui.components.AchievementCard
import com.hrcoach.ui.theme.CardeaTextPrimary
import com.hrcoach.ui.theme.CardeaTextSecondary

@Composable
fun AchievementGallery(
    achievements: List<AchievementEntity>,
    modifier: Modifier = Modifier
) {
    if (achievements.isEmpty()) return

    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Achievements",
                color = CardeaTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${achievements.size} earned",
                color = CardeaTextSecondary,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(12.dp))
        // Non-lazy 2-column grid — safe inside a scrollable Column parent.
        // LazyVerticalGrid inside a scrollable Column is fragile; this is simpler.
        achievements.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { achievement ->
                    AchievementCard(
                        achievement = achievement,
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Fill remaining space if odd number of items
                if (rowItems.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
```

- [ ] **Step 4: Add AchievementGallery to AccountScreen**

In `AccountScreen.kt`, insert after the ProfileHeroCard section (around line 133) and before the Configuration SectionLabel:

```kotlin
val achievements = uiState.achievements
if (achievements.isNotEmpty()) {
    Spacer(Modifier.height(16.dp))
    AchievementGallery(achievements = achievements)
    Spacer(Modifier.height(16.dp))
}
```

- [ ] **Step 5: Build to verify compilation**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/account/AchievementGallery.kt app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt app/src/main/java/com/hrcoach/ui/account/AccountViewModel.kt
git commit -m "feat(achievement): add achievement gallery to Profile screen"
```

---

## Chunk 6: Final Verification

### Task 14: Full build + test pass

- [ ] **Step 1: Run all unit tests**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: ALL PASS

- [ ] **Step 2: Run full debug build**

Run: `./gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Verify no regressions in existing tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.domain.engine.ZoneEngineTest"`
Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.SessionReschedulerTest"`
Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.util.FormattersTest"`
Expected: ALL PASS

- [ ] **Step 4: Final commit if any fixups were needed**

```bash
git add -A
git commit -m "fix(achievement): address build/test issues from integration"
```
