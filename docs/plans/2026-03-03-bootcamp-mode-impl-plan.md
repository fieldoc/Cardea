# Bootcamp Mode Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add an adaptive, multi-week coaching program ("Bootcamp") that prescribes distance and HR profiles based on user goals, fitness level, and schedule — with science-backed periodization and gap-aware re-entry.

**Architecture:** Phase Engine (Base -> Build -> Peak -> Taper) that selects sessions from the existing PresetLibrary. Two new Room entities (BootcampEnrollment, BootcampSession) link into existing WorkoutEntity. A Gap Advisor adjusts re-entry based on days since last run and measured fitness. UI integrates via a hero card on HomeScreen and a first-option mode in SetupScreen.

**Tech Stack:** Kotlin, Room (migration v3->v4), Hilt DI, Jetpack Compose, JUnit 4

**Design doc:** `docs/plans/2026-03-03-bootcamp-mode-design.md`

---

## Phase 1: Data Model & Repository

### Task 1: BootcampGoal enum and domain models

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/model/BootcampGoal.kt`
- Test: `app/src/test/java/com/hrcoach/domain/model/BootcampGoalTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.hrcoach.domain.model

import org.junit.Assert.*
import org.junit.Test

class BootcampGoalTest {

    @Test
    fun `each goal has correct tier`() {
        assertEquals(1, BootcampGoal.CARDIO_HEALTH.tier)
        assertEquals(2, BootcampGoal.RACE_5K_10K.tier)
        assertEquals(3, BootcampGoal.HALF_MARATHON.tier)
        assertEquals(4, BootcampGoal.MARATHON.tier)
    }

    @Test
    fun `each goal has correct suggested min minutes`() {
        assertEquals(20, BootcampGoal.CARDIO_HEALTH.suggestedMinMinutes)
        assertEquals(25, BootcampGoal.RACE_5K_10K.suggestedMinMinutes)
        assertEquals(30, BootcampGoal.HALF_MARATHON.suggestedMinMinutes)
        assertEquals(45, BootcampGoal.MARATHON.suggestedMinMinutes)
    }

    @Test
    fun `each goal has correct warn-below minutes`() {
        assertEquals(15, BootcampGoal.CARDIO_HEALTH.warnBelowMinutes)
        assertEquals(20, BootcampGoal.RACE_5K_10K.warnBelowMinutes)
        assertEquals(25, BootcampGoal.HALF_MARATHON.warnBelowMinutes)
        assertEquals(30, BootcampGoal.MARATHON.warnBelowMinutes)
    }

    @Test
    fun `cardio health has no peak or taper phase`() {
        val phases = BootcampGoal.CARDIO_HEALTH.phaseArc
        assertTrue(phases.any { it == TrainingPhase.BASE })
        assertTrue(phases.any { it == TrainingPhase.BUILD })
        assertFalse(phases.any { it == TrainingPhase.PEAK })
        assertFalse(phases.any { it == TrainingPhase.TAPER })
    }

    @Test
    fun `marathon has all four phases`() {
        val phases = BootcampGoal.MARATHON.phaseArc
        assertEquals(
            listOf(TrainingPhase.BASE, TrainingPhase.BUILD, TrainingPhase.PEAK, TrainingPhase.TAPER),
            phases
        )
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.model.BootcampGoalTest" --info 2>&1 | tail -20`
Expected: FAIL — class not found

**Step 3: Write minimal implementation**

```kotlin
package com.hrcoach.domain.model

enum class TrainingPhase(val weeksRange: IntRange) {
    BASE(3..6),
    BUILD(4..6),
    PEAK(2..3),
    TAPER(1..2)
}

enum class BootcampGoal(
    val tier: Int,
    val suggestedMinMinutes: Int,
    val warnBelowMinutes: Int,
    val neverPrescribeBelowMinutes: Int,
    val phaseArc: List<TrainingPhase>
) {
    CARDIO_HEALTH(
        tier = 1,
        suggestedMinMinutes = 20,
        warnBelowMinutes = 15,
        neverPrescribeBelowMinutes = 10,
        phaseArc = listOf(TrainingPhase.BASE, TrainingPhase.BUILD)
    ),
    RACE_5K_10K(
        tier = 2,
        suggestedMinMinutes = 25,
        warnBelowMinutes = 20,
        neverPrescribeBelowMinutes = 15,
        phaseArc = listOf(TrainingPhase.BASE, TrainingPhase.BUILD, TrainingPhase.PEAK, TrainingPhase.TAPER)
    ),
    HALF_MARATHON(
        tier = 3,
        suggestedMinMinutes = 30,
        warnBelowMinutes = 25,
        neverPrescribeBelowMinutes = 20,
        phaseArc = listOf(TrainingPhase.BASE, TrainingPhase.BUILD, TrainingPhase.PEAK, TrainingPhase.TAPER)
    ),
    MARATHON(
        tier = 4,
        suggestedMinMinutes = 45,
        warnBelowMinutes = 30,
        neverPrescribeBelowMinutes = 20,
        phaseArc = listOf(TrainingPhase.BASE, TrainingPhase.BUILD, TrainingPhase.PEAK, TrainingPhase.TAPER)
    )
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.model.BootcampGoalTest" --info 2>&1 | tail -20`
Expected: PASS (5 tests)

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/model/BootcampGoal.kt app/src/test/java/com/hrcoach/domain/model/BootcampGoalTest.kt
git commit -m "feat(bootcamp): add BootcampGoal enum and TrainingPhase model"
```

---

### Task 2: Room entities — BootcampEnrollmentEntity and BootcampSessionEntity

**Files:**
- Create: `app/src/main/java/com/hrcoach/data/db/BootcampEnrollmentEntity.kt`
- Create: `app/src/main/java/com/hrcoach/data/db/BootcampSessionEntity.kt`
- Test: `app/src/test/java/com/hrcoach/data/db/BootcampEntityTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.hrcoach.data.db

import org.junit.Assert.*
import org.junit.Test

class BootcampEntityTest {

    @Test
    fun `enrollment entity has correct defaults`() {
        val enrollment = BootcampEnrollmentEntity(
            goalType = "MARATHON",
            targetMinutesPerRun = 45,
            runsPerWeek = 4,
            preferredDays = "[1,3,5,6]",
            startDate = 1000L
        )
        assertEquals(0L, enrollment.id)
        assertEquals("ACTIVE", enrollment.status)
        assertEquals(0, enrollment.currentPhaseIndex)
        assertEquals(0, enrollment.currentWeekInPhase)
    }

    @Test
    fun `session entity has correct defaults`() {
        val session = BootcampSessionEntity(
            enrollmentId = 1L,
            weekNumber = 1,
            dayOfWeek = 2,
            sessionType = "EASY",
            targetMinutes = 30
        )
        assertEquals(0L, session.id)
        assertEquals("SCHEDULED", session.status)
        assertNull(session.presetId)
        assertNull(session.completedWorkoutId)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.data.db.BootcampEntityTest" --info 2>&1 | tail -20`
Expected: FAIL — class not found

**Step 3: Write minimal implementation**

```kotlin
// BootcampEnrollmentEntity.kt
package com.hrcoach.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bootcamp_enrollments")
data class BootcampEnrollmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalType: String,
    val targetMinutesPerRun: Int,
    val runsPerWeek: Int,
    val preferredDays: String,
    val startDate: Long,
    val currentPhaseIndex: Int = 0,
    val currentWeekInPhase: Int = 0,
    val status: String = "ACTIVE"
)
```

```kotlin
// BootcampSessionEntity.kt
package com.hrcoach.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bootcamp_sessions",
    foreignKeys = [
        ForeignKey(
            entity = BootcampEnrollmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["enrollmentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("enrollmentId")]
)
data class BootcampSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val enrollmentId: Long,
    val weekNumber: Int,
    val dayOfWeek: Int,
    val sessionType: String,
    val targetMinutes: Int,
    val presetId: String? = null,
    val status: String = "SCHEDULED",
    val completedWorkoutId: Long? = null
)
```

**Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.data.db.BootcampEntityTest" --info 2>&1 | tail -20`
Expected: PASS (2 tests)

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/db/BootcampEnrollmentEntity.kt app/src/main/java/com/hrcoach/data/db/BootcampSessionEntity.kt app/src/test/java/com/hrcoach/data/db/BootcampEntityTest.kt
git commit -m "feat(bootcamp): add Room entities for enrollment and sessions"
```

---

### Task 3: BootcampDao, Room migration v3->v4, and DI wiring

**Files:**
- Create: `app/src/main/java/com/hrcoach/data/db/BootcampDao.kt`
- Modify: `app/src/main/java/com/hrcoach/data/db/AppDatabase.kt` (add entities, bump version, migration)
- Modify: `app/src/main/java/com/hrcoach/di/AppModule.kt` (provide BootcampDao)

**Step 1: Create the DAO**

```kotlin
package com.hrcoach.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BootcampDao {
    @Insert
    suspend fun insertEnrollment(enrollment: BootcampEnrollmentEntity): Long

    @Update
    suspend fun updateEnrollment(enrollment: BootcampEnrollmentEntity)

    @Query("SELECT * FROM bootcamp_enrollments WHERE status = 'ACTIVE' LIMIT 1")
    fun getActiveEnrollment(): Flow<BootcampEnrollmentEntity?>

    @Query("SELECT * FROM bootcamp_enrollments WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveEnrollmentOnce(): BootcampEnrollmentEntity?

    @Query("SELECT * FROM bootcamp_enrollments WHERE id = :id")
    suspend fun getEnrollment(id: Long): BootcampEnrollmentEntity?

    @Insert
    suspend fun insertSession(session: BootcampSessionEntity): Long

    @Insert
    suspend fun insertSessions(sessions: List<BootcampSessionEntity>)

    @Update
    suspend fun updateSession(session: BootcampSessionEntity)

    @Query("SELECT * FROM bootcamp_sessions WHERE enrollmentId = :enrollmentId ORDER BY weekNumber, dayOfWeek")
    fun getSessionsForEnrollment(enrollmentId: Long): Flow<List<BootcampSessionEntity>>

    @Query("SELECT * FROM bootcamp_sessions WHERE enrollmentId = :enrollmentId AND weekNumber = :week ORDER BY dayOfWeek")
    suspend fun getSessionsForWeek(enrollmentId: Long, week: Int): List<BootcampSessionEntity>

    @Query("SELECT * FROM bootcamp_sessions WHERE enrollmentId = :enrollmentId AND status = 'SCHEDULED' ORDER BY weekNumber, dayOfWeek LIMIT 1")
    suspend fun getNextScheduledSession(enrollmentId: Long): BootcampSessionEntity?

    @Query("SELECT * FROM bootcamp_sessions WHERE enrollmentId = :enrollmentId AND status = 'COMPLETED' ORDER BY weekNumber DESC, dayOfWeek DESC LIMIT 1")
    suspend fun getLastCompletedSession(enrollmentId: Long): BootcampSessionEntity?

    @Query("DELETE FROM bootcamp_sessions WHERE enrollmentId = :enrollmentId AND weekNumber > :weekNumber")
    suspend fun deleteSessionsAfterWeek(enrollmentId: Long, weekNumber: Int)
}
```

**Step 2: Modify AppDatabase**

In `app/src/main/java/com/hrcoach/data/db/AppDatabase.kt`:
- Add `BootcampEnrollmentEntity::class, BootcampSessionEntity::class` to `entities` array (line 9)
- Bump `version` to `4` (line 10)
- Add migration companion val:

```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS bootcamp_enrollments (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                goalType TEXT NOT NULL,
                targetMinutesPerRun INTEGER NOT NULL,
                runsPerWeek INTEGER NOT NULL,
                preferredDays TEXT NOT NULL,
                startDate INTEGER NOT NULL,
                currentPhaseIndex INTEGER NOT NULL DEFAULT 0,
                currentWeekInPhase INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'ACTIVE'
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS bootcamp_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                enrollmentId INTEGER NOT NULL,
                weekNumber INTEGER NOT NULL,
                dayOfWeek INTEGER NOT NULL,
                sessionType TEXT NOT NULL,
                targetMinutes INTEGER NOT NULL,
                presetId TEXT,
                status TEXT NOT NULL DEFAULT 'SCHEDULED',
                completedWorkoutId INTEGER,
                FOREIGN KEY (enrollmentId) REFERENCES bootcamp_enrollments(id) ON DELETE CASCADE
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bootcamp_sessions_enrollmentId ON bootcamp_sessions(enrollmentId)")
    }
}
```

- Add `abstract fun bootcampDao(): BootcampDao`
- Add `MIGRATION_3_4` to `.addMigrations()` call

**Step 3: Modify AppModule**

In `app/src/main/java/com/hrcoach/di/AppModule.kt`, add:

```kotlin
@Provides
@Singleton
fun provideBootcampDao(db: AppDatabase): BootcampDao = db.bootcampDao()
```

**Step 4: Verify build compiles**

Run: `./gradlew assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/db/BootcampDao.kt app/src/main/java/com/hrcoach/data/db/AppDatabase.kt app/src/main/java/com/hrcoach/di/AppModule.kt
git commit -m "feat(bootcamp): add BootcampDao, Room migration v3->v4, DI wiring"
```

---

### Task 4: BootcampRepository

**Files:**
- Create: `app/src/main/java/com/hrcoach/data/repository/BootcampRepository.kt`
- Test: `app/src/test/java/com/hrcoach/data/repository/BootcampRepositoryTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.hrcoach.data.repository

import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.domain.model.BootcampGoal
import org.junit.Assert.*
import org.junit.Test

class BootcampRepositoryTest {

    @Test
    fun `createEnrollment builds entity with correct fields`() {
        val entity = BootcampRepository.buildEnrollmentEntity(
            goal = BootcampGoal.MARATHON,
            targetMinutesPerRun = 45,
            runsPerWeek = 4,
            preferredDays = listOf(1, 3, 5, 6),
            startDate = 1709424000000L
        )
        assertEquals("MARATHON", entity.goalType)
        assertEquals(45, entity.targetMinutesPerRun)
        assertEquals(4, entity.runsPerWeek)
        assertEquals("[1,3,5,6]", entity.preferredDays)
        assertEquals("ACTIVE", entity.status)
        assertEquals(0, entity.currentPhaseIndex)
        assertEquals(0, entity.currentWeekInPhase)
    }

    @Test
    fun `buildSessionEntity creates session with correct defaults`() {
        val entity = BootcampRepository.buildSessionEntity(
            enrollmentId = 1L,
            weekNumber = 2,
            dayOfWeek = 3,
            sessionType = "TEMPO",
            targetMinutes = 30,
            presetId = "aerobic_tempo"
        )
        assertEquals(1L, entity.enrollmentId)
        assertEquals(2, entity.weekNumber)
        assertEquals(3, entity.dayOfWeek)
        assertEquals("TEMPO", entity.sessionType)
        assertEquals(30, entity.targetMinutes)
        assertEquals("aerobic_tempo", entity.presetId)
        assertEquals("SCHEDULED", entity.status)
        assertNull(entity.completedWorkoutId)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.data.repository.BootcampRepositoryTest" --info 2>&1 | tail -20`
Expected: FAIL — class not found

**Step 3: Write minimal implementation**

```kotlin
package com.hrcoach.data.repository

import com.hrcoach.data.db.BootcampDao
import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.domain.model.BootcampGoal
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BootcampRepository @Inject constructor(
    private val bootcampDao: BootcampDao
) {
    fun getActiveEnrollment(): Flow<BootcampEnrollmentEntity?> =
        bootcampDao.getActiveEnrollment()

    suspend fun getActiveEnrollmentOnce(): BootcampEnrollmentEntity? =
        bootcampDao.getActiveEnrollmentOnce()

    suspend fun createEnrollment(
        goal: BootcampGoal,
        targetMinutesPerRun: Int,
        runsPerWeek: Int,
        preferredDays: List<Int>,
        startDate: Long
    ): Long {
        val entity = buildEnrollmentEntity(goal, targetMinutesPerRun, runsPerWeek, preferredDays, startDate)
        return bootcampDao.insertEnrollment(entity)
    }

    suspend fun updateEnrollment(enrollment: BootcampEnrollmentEntity) =
        bootcampDao.updateEnrollment(enrollment)

    suspend fun pauseEnrollment(enrollmentId: Long) {
        val enrollment = bootcampDao.getEnrollment(enrollmentId) ?: return
        bootcampDao.updateEnrollment(enrollment.copy(status = "PAUSED"))
    }

    suspend fun insertSessions(sessions: List<BootcampSessionEntity>) =
        bootcampDao.insertSessions(sessions)

    fun getSessionsForEnrollment(enrollmentId: Long): Flow<List<BootcampSessionEntity>> =
        bootcampDao.getSessionsForEnrollment(enrollmentId)

    suspend fun getSessionsForWeek(enrollmentId: Long, week: Int): List<BootcampSessionEntity> =
        bootcampDao.getSessionsForWeek(enrollmentId, week)

    suspend fun getNextScheduledSession(enrollmentId: Long): BootcampSessionEntity? =
        bootcampDao.getNextScheduledSession(enrollmentId)

    suspend fun getLastCompletedSession(enrollmentId: Long): BootcampSessionEntity? =
        bootcampDao.getLastCompletedSession(enrollmentId)

    suspend fun markSessionCompleted(sessionId: Long, workoutId: Long) {
        val sessions = bootcampDao.getSessionsForEnrollment(sessionId)
        // Use updateSession directly
    }

    suspend fun updateSession(session: BootcampSessionEntity) =
        bootcampDao.updateSession(session)

    suspend fun deleteSessionsAfterWeek(enrollmentId: Long, weekNumber: Int) =
        bootcampDao.deleteSessionsAfterWeek(enrollmentId, weekNumber)

    companion object {
        fun buildEnrollmentEntity(
            goal: BootcampGoal,
            targetMinutesPerRun: Int,
            runsPerWeek: Int,
            preferredDays: List<Int>,
            startDate: Long
        ) = BootcampEnrollmentEntity(
            goalType = goal.name,
            targetMinutesPerRun = targetMinutesPerRun,
            runsPerWeek = runsPerWeek,
            preferredDays = preferredDays.joinToString(",", "[", "]"),
            startDate = startDate
        )

        fun buildSessionEntity(
            enrollmentId: Long,
            weekNumber: Int,
            dayOfWeek: Int,
            sessionType: String,
            targetMinutes: Int,
            presetId: String? = null
        ) = BootcampSessionEntity(
            enrollmentId = enrollmentId,
            weekNumber = weekNumber,
            dayOfWeek = dayOfWeek,
            sessionType = sessionType,
            targetMinutes = targetMinutes,
            presetId = presetId
        )
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.data.repository.BootcampRepositoryTest" --info 2>&1 | tail -20`
Expected: PASS (2 tests)

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/repository/BootcampRepository.kt app/src/test/java/com/hrcoach/data/repository/BootcampRepositoryTest.kt
git commit -m "feat(bootcamp): add BootcampRepository with entity builders"
```

---

## Phase 2: Core Engine

### Task 5: SessionType enum and session selection logic

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/bootcamp/SessionType.kt`
- Create: `app/src/main/java/com/hrcoach/domain/bootcamp/SessionSelector.kt`
- Test: `app/src/test/java/com/hrcoach/domain/bootcamp/SessionSelectorTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.hrcoach.domain.bootcamp

import com.hrcoach.domain.model.BootcampGoal
import com.hrcoach.domain.model.TrainingPhase
import org.junit.Assert.*
import org.junit.Test

class SessionSelectorTest {

    @Test
    fun `base phase for cardio health returns only easy and long sessions`() {
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.BASE,
            goal = BootcampGoal.CARDIO_HEALTH,
            runsPerWeek = 3,
            targetMinutes = 30
        )
        assertEquals(3, sessions.size)
        assertTrue(sessions.all { it.type == SessionType.EASY || it.type == SessionType.LONG })
    }

    @Test
    fun `build phase for marathon includes tempo session`() {
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.BUILD,
            goal = BootcampGoal.MARATHON,
            runsPerWeek = 4,
            targetMinutes = 45
        )
        assertEquals(4, sessions.size)
        assertTrue(sessions.any { it.type == SessionType.TEMPO })
        assertTrue(sessions.any { it.type == SessionType.LONG })
    }

    @Test
    fun `peak phase for marathon includes interval session`() {
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.PEAK,
            goal = BootcampGoal.MARATHON,
            runsPerWeek = 4,
            targetMinutes = 45
        )
        assertTrue(sessions.any { it.type == SessionType.INTERVAL })
    }

    @Test
    fun `taper phase reduces target minutes by 30 percent`() {
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.TAPER,
            goal = BootcampGoal.HALF_MARATHON,
            runsPerWeek = 3,
            targetMinutes = 40
        )
        assertTrue(sessions.all { it.minutes <= 28 }) // 40 * 0.7 = 28
    }

    @Test
    fun `cardio health never gets interval sessions in any phase`() {
        for (phase in BootcampGoal.CARDIO_HEALTH.phaseArc) {
            val sessions = SessionSelector.weekSessions(
                phase = phase,
                goal = BootcampGoal.CARDIO_HEALTH,
                runsPerWeek = 3,
                targetMinutes = 25
            )
            assertFalse(
                "Phase $phase should not have intervals for cardio health",
                sessions.any { it.type == SessionType.INTERVAL }
            )
        }
    }

    @Test
    fun `2 runs per week still includes one long on build phase`() {
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.BUILD,
            goal = BootcampGoal.RACE_5K_10K,
            runsPerWeek = 2,
            targetMinutes = 30
        )
        assertEquals(2, sessions.size)
        assertTrue(sessions.any { it.type == SessionType.LONG || it.type == SessionType.TEMPO })
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.SessionSelectorTest" --info 2>&1 | tail -20`
Expected: FAIL — class not found

**Step 3: Write minimal implementation**

```kotlin
// SessionType.kt
package com.hrcoach.domain.bootcamp

import com.hrcoach.domain.preset.PresetCategory

enum class SessionType(val presetCategory: PresetCategory?) {
    EASY(PresetCategory.BASE_AEROBIC),
    LONG(PresetCategory.BASE_AEROBIC),
    TEMPO(PresetCategory.THRESHOLD),
    INTERVAL(PresetCategory.INTERVAL),
    RACE_SIM(PresetCategory.RACE_PREP),
    DISCOVERY(null),
    CHECK_IN(null)
}

data class PlannedSession(
    val type: SessionType,
    val minutes: Int,
    val presetId: String? = null
)
```

```kotlin
// SessionSelector.kt
package com.hrcoach.domain.bootcamp

import com.hrcoach.domain.model.BootcampGoal
import com.hrcoach.domain.model.TrainingPhase

object SessionSelector {

    fun weekSessions(
        phase: TrainingPhase,
        goal: BootcampGoal,
        runsPerWeek: Int,
        targetMinutes: Int
    ): List<PlannedSession> {
        val effectiveMinutes = if (phase == TrainingPhase.TAPER) {
            (targetMinutes * 0.7f).toInt()
        } else {
            targetMinutes
        }
        val longMinutes = (effectiveMinutes * 1.3f).toInt().coerceAtMost(effectiveMinutes + 20)

        return when {
            goal.tier <= 1 -> baseAerobicWeek(phase, runsPerWeek, effectiveMinutes, longMinutes)
            else -> periodizedWeek(phase, goal, runsPerWeek, effectiveMinutes, longMinutes)
        }
    }

    private fun baseAerobicWeek(
        phase: TrainingPhase,
        runsPerWeek: Int,
        minutes: Int,
        longMinutes: Int
    ): List<PlannedSession> {
        val sessions = mutableListOf<PlannedSession>()
        val hasLong = runsPerWeek >= 3 && phase != TrainingPhase.BASE
        val easyCount = if (hasLong) runsPerWeek - 1 else runsPerWeek
        repeat(easyCount) {
            sessions.add(PlannedSession(SessionType.EASY, minutes, "zone2_base"))
        }
        if (hasLong) {
            sessions.add(PlannedSession(SessionType.LONG, longMinutes, "zone2_base"))
        }
        return sessions
    }

    private fun periodizedWeek(
        phase: TrainingPhase,
        goal: BootcampGoal,
        runsPerWeek: Int,
        minutes: Int,
        longMinutes: Int
    ): List<PlannedSession> {
        val sessions = mutableListOf<PlannedSession>()

        // Always have at least one easy run
        val qualitySessions = when (phase) {
            TrainingPhase.BASE -> 0
            TrainingPhase.BUILD -> 1
            TrainingPhase.PEAK -> if (goal.tier >= 3) 2 else 1
            TrainingPhase.TAPER -> 1
        }

        val hasLong = runsPerWeek >= 3 && phase != TrainingPhase.TAPER
        val easyCount = (runsPerWeek - qualitySessions - (if (hasLong) 1 else 0)).coerceAtLeast(1)

        // Easy runs
        repeat(easyCount) {
            sessions.add(PlannedSession(SessionType.EASY, minutes, "zone2_base"))
        }

        // Quality sessions based on phase
        when (phase) {
            TrainingPhase.BASE -> {} // No quality sessions
            TrainingPhase.BUILD -> {
                sessions.add(PlannedSession(SessionType.TEMPO, minutes, "aerobic_tempo"))
            }
            TrainingPhase.PEAK -> {
                if (goal.tier >= 4) {
                    sessions.add(PlannedSession(SessionType.INTERVAL, minutes, "norwegian_4x4"))
                    if (qualitySessions >= 2) {
                        sessions.add(PlannedSession(SessionType.TEMPO, minutes, "lactate_threshold"))
                    }
                } else if (goal.tier >= 3) {
                    sessions.add(PlannedSession(SessionType.TEMPO, minutes, "lactate_threshold"))
                    if (qualitySessions >= 2) {
                        sessions.add(PlannedSession(SessionType.INTERVAL, minutes, "norwegian_4x4"))
                    }
                } else {
                    sessions.add(PlannedSession(SessionType.TEMPO, minutes, "aerobic_tempo"))
                }
            }
            TrainingPhase.TAPER -> {
                sessions.add(PlannedSession(SessionType.TEMPO, (minutes * 0.8f).toInt(), "aerobic_tempo"))
            }
        }

        // Long run
        if (hasLong) {
            val longPreset = if (phase == TrainingPhase.PEAK && goal.tier >= 3) null else "zone2_base"
            val longType = if (phase == TrainingPhase.PEAK && goal.tier >= 3) SessionType.RACE_SIM else SessionType.LONG
            sessions.add(PlannedSession(longType, longMinutes, longPreset))
        }

        return sessions
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.SessionSelectorTest" --info 2>&1 | tail -20`
Expected: PASS (6 tests)

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/bootcamp/SessionType.kt app/src/main/java/com/hrcoach/domain/bootcamp/SessionSelector.kt app/src/test/java/com/hrcoach/domain/bootcamp/SessionSelectorTest.kt
git commit -m "feat(bootcamp): add SessionSelector with phase-based session planning"
```

---

### Task 6: GapAdvisor — gap detection and re-entry strategy

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/bootcamp/GapAdvisor.kt`
- Test: `app/src/test/java/com/hrcoach/domain/bootcamp/GapAdvisorTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.hrcoach.domain.bootcamp

import org.junit.Assert.*
import org.junit.Test

class GapAdvisorTest {

    @Test
    fun `0-3 day gap returns ON_TRACK`() {
        assertEquals(GapStrategy.ON_TRACK, GapAdvisor.assess(daysSinceLastRun = 0))
        assertEquals(GapStrategy.ON_TRACK, GapAdvisor.assess(daysSinceLastRun = 1))
        assertEquals(GapStrategy.ON_TRACK, GapAdvisor.assess(daysSinceLastRun = 3))
    }

    @Test
    fun `4-7 day gap returns MINOR_SLIP`() {
        assertEquals(GapStrategy.MINOR_SLIP, GapAdvisor.assess(daysSinceLastRun = 4))
        assertEquals(GapStrategy.MINOR_SLIP, GapAdvisor.assess(daysSinceLastRun = 7))
    }

    @Test
    fun `8-14 day gap returns SHORT_BREAK`() {
        assertEquals(GapStrategy.SHORT_BREAK, GapAdvisor.assess(daysSinceLastRun = 8))
        assertEquals(GapStrategy.SHORT_BREAK, GapAdvisor.assess(daysSinceLastRun = 14))
    }

    @Test
    fun `15-28 day gap returns MEANINGFUL_BREAK`() {
        assertEquals(GapStrategy.MEANINGFUL_BREAK, GapAdvisor.assess(daysSinceLastRun = 15))
        assertEquals(GapStrategy.MEANINGFUL_BREAK, GapAdvisor.assess(daysSinceLastRun = 28))
    }

    @Test
    fun `29-60 day gap returns EXTENDED_BREAK`() {
        assertEquals(GapStrategy.EXTENDED_BREAK, GapAdvisor.assess(daysSinceLastRun = 29))
        assertEquals(GapStrategy.EXTENDED_BREAK, GapAdvisor.assess(daysSinceLastRun = 60))
    }

    @Test
    fun `61-120 day gap returns LONG_ABSENCE`() {
        assertEquals(GapStrategy.LONG_ABSENCE, GapAdvisor.assess(daysSinceLastRun = 61))
        assertEquals(GapStrategy.LONG_ABSENCE, GapAdvisor.assess(daysSinceLastRun = 120))
    }

    @Test
    fun `120+ day gap returns FULL_RESET`() {
        assertEquals(GapStrategy.FULL_RESET, GapAdvisor.assess(daysSinceLastRun = 121))
        assertEquals(GapStrategy.FULL_RESET, GapAdvisor.assess(daysSinceLastRun = 365))
    }

    @Test
    fun `ON_TRACK does not rewind phases`() {
        val action = GapAdvisor.action(GapStrategy.ON_TRACK, currentPhaseIndex = 1, currentWeekInPhase = 3)
        assertEquals(1, action.phaseIndex)
        assertEquals(3, action.weekInPhase)
        assertFalse(action.insertReturnSession)
        assertFalse(action.requiresCalibration)
    }

    @Test
    fun `MEANINGFUL_BREAK rewinds 1 week and inserts return session`() {
        val action = GapAdvisor.action(GapStrategy.MEANINGFUL_BREAK, currentPhaseIndex = 1, currentWeekInPhase = 3)
        assertEquals(1, action.phaseIndex)
        assertEquals(2, action.weekInPhase)
        assertTrue(action.insertReturnSession)
        assertFalse(action.requiresCalibration)
    }

    @Test
    fun `MEANINGFUL_BREAK at week 0 stays at week 0`() {
        val action = GapAdvisor.action(GapStrategy.MEANINGFUL_BREAK, currentPhaseIndex = 1, currentWeekInPhase = 0)
        assertEquals(1, action.phaseIndex)
        assertEquals(0, action.weekInPhase)
        assertTrue(action.insertReturnSession)
    }

    @Test
    fun `EXTENDED_BREAK rewinds to start of current phase`() {
        val action = GapAdvisor.action(GapStrategy.EXTENDED_BREAK, currentPhaseIndex = 2, currentWeekInPhase = 4)
        assertEquals(2, action.phaseIndex)
        assertEquals(0, action.weekInPhase)
        assertTrue(action.insertReturnSession)
        assertFalse(action.requiresCalibration)
    }

    @Test
    fun `LONG_ABSENCE rewinds to base phase and requires calibration`() {
        val action = GapAdvisor.action(GapStrategy.LONG_ABSENCE, currentPhaseIndex = 2, currentWeekInPhase = 3)
        assertEquals(0, action.phaseIndex)
        assertEquals(0, action.weekInPhase)
        assertTrue(action.insertReturnSession)
        assertTrue(action.requiresCalibration)
    }

    @Test
    fun `FULL_RESET rewinds to base phase and requires calibration`() {
        val action = GapAdvisor.action(GapStrategy.FULL_RESET, currentPhaseIndex = 3, currentWeekInPhase = 1)
        assertEquals(0, action.phaseIndex)
        assertEquals(0, action.weekInPhase)
        assertTrue(action.insertReturnSession)
        assertTrue(action.requiresCalibration)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.GapAdvisorTest" --info 2>&1 | tail -20`
Expected: FAIL — class not found

**Step 3: Write minimal implementation**

```kotlin
package com.hrcoach.domain.bootcamp

enum class GapStrategy {
    ON_TRACK,
    MINOR_SLIP,
    SHORT_BREAK,
    MEANINGFUL_BREAK,
    EXTENDED_BREAK,
    LONG_ABSENCE,
    FULL_RESET
}

data class GapAction(
    val phaseIndex: Int,
    val weekInPhase: Int,
    val insertReturnSession: Boolean,
    val requiresCalibration: Boolean,
    val welcomeMessage: String? = null
)

object GapAdvisor {

    fun assess(daysSinceLastRun: Int): GapStrategy = when {
        daysSinceLastRun <= 3 -> GapStrategy.ON_TRACK
        daysSinceLastRun <= 7 -> GapStrategy.MINOR_SLIP
        daysSinceLastRun <= 14 -> GapStrategy.SHORT_BREAK
        daysSinceLastRun <= 28 -> GapStrategy.MEANINGFUL_BREAK
        daysSinceLastRun <= 60 -> GapStrategy.EXTENDED_BREAK
        daysSinceLastRun <= 120 -> GapStrategy.LONG_ABSENCE
        else -> GapStrategy.FULL_RESET
    }

    fun action(
        strategy: GapStrategy,
        currentPhaseIndex: Int,
        currentWeekInPhase: Int
    ): GapAction = when (strategy) {
        GapStrategy.ON_TRACK -> GapAction(
            phaseIndex = currentPhaseIndex,
            weekInPhase = currentWeekInPhase,
            insertReturnSession = false,
            requiresCalibration = false
        )
        GapStrategy.MINOR_SLIP -> GapAction(
            phaseIndex = currentPhaseIndex,
            weekInPhase = currentWeekInPhase,
            insertReturnSession = false,
            requiresCalibration = false
        )
        GapStrategy.SHORT_BREAK -> GapAction(
            phaseIndex = currentPhaseIndex,
            weekInPhase = currentWeekInPhase,
            insertReturnSession = true,
            requiresCalibration = false,
            welcomeMessage = "Welcome back — here's an easy one to get moving again."
        )
        GapStrategy.MEANINGFUL_BREAK -> GapAction(
            phaseIndex = currentPhaseIndex,
            weekInPhase = (currentWeekInPhase - 1).coerceAtLeast(0),
            insertReturnSession = true,
            requiresCalibration = false,
            welcomeMessage = "Welcome back — let's ease in."
        )
        GapStrategy.EXTENDED_BREAK -> GapAction(
            phaseIndex = currentPhaseIndex,
            weekInPhase = 0,
            insertReturnSession = true,
            requiresCalibration = false,
            welcomeMessage = "Welcome back after a break — we've rewound to the start of this phase."
        )
        GapStrategy.LONG_ABSENCE -> GapAction(
            phaseIndex = 0,
            weekInPhase = 0,
            insertReturnSession = true,
            requiresCalibration = true,
            welcomeMessage = "Welcome back — your first run will help us gauge where you are."
        )
        GapStrategy.FULL_RESET -> GapAction(
            phaseIndex = 0,
            weekInPhase = 0,
            insertReturnSession = true,
            requiresCalibration = true,
            welcomeMessage = "Welcome back — let's start fresh with a Discovery Run."
        )
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.GapAdvisorTest" --info 2>&1 | tail -20`
Expected: PASS (12 tests)

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/bootcamp/GapAdvisor.kt app/src/test/java/com/hrcoach/domain/bootcamp/GapAdvisorTest.kt
git commit -m "feat(bootcamp): add GapAdvisor with 7-tier gap detection and re-entry logic"
```

---

### Task 7: PhaseEngine — orchestrator that plans weeks and advances phases

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/bootcamp/PhaseEngine.kt`
- Test: `app/src/test/java/com/hrcoach/domain/bootcamp/PhaseEngineTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.hrcoach.domain.bootcamp

import com.hrcoach.domain.model.BootcampGoal
import com.hrcoach.domain.model.TrainingPhase
import org.junit.Assert.*
import org.junit.Test

class PhaseEngineTest {

    @Test
    fun `currentPhase returns correct phase from arc`() {
        val engine = PhaseEngine(goal = BootcampGoal.MARATHON, phaseIndex = 0, weekInPhase = 0)
        assertEquals(TrainingPhase.BASE, engine.currentPhase)
    }

    @Test
    fun `currentPhase at index 2 for marathon returns PEAK`() {
        val engine = PhaseEngine(goal = BootcampGoal.MARATHON, phaseIndex = 2, weekInPhase = 0)
        assertEquals(TrainingPhase.PEAK, engine.currentPhase)
    }

    @Test
    fun `totalWeeks sums midpoints of all phase ranges`() {
        val engine = PhaseEngine(goal = BootcampGoal.RACE_5K_10K, phaseIndex = 0, weekInPhase = 0)
        // BASE(3..6)=4 + BUILD(4..6)=5 + PEAK(2..3)=2 + TAPER(1..2)=1 = 12
        assertEquals(12, engine.totalWeeks)
    }

    @Test
    fun `absoluteWeek computes correctly`() {
        // Phase 0 (BASE, midpoint 4 weeks), week 3 of 4 -> absolute week 4
        val engine = PhaseEngine(goal = BootcampGoal.MARATHON, phaseIndex = 0, weekInPhase = 3)
        assertEquals(4, engine.absoluteWeek)
    }

    @Test
    fun `planCurrentWeek delegates to SessionSelector`() {
        val engine = PhaseEngine(
            goal = BootcampGoal.CARDIO_HEALTH,
            phaseIndex = 0,
            weekInPhase = 0,
            runsPerWeek = 3,
            targetMinutes = 25
        )
        val sessions = engine.planCurrentWeek()
        assertEquals(3, sessions.size)
        assertTrue(sessions.all { it.type == SessionType.EASY || it.type == SessionType.LONG })
    }

    @Test
    fun `shouldAdvancePhase returns true when week exceeds phase midpoint`() {
        val engine = PhaseEngine(
            goal = BootcampGoal.MARATHON,
            phaseIndex = 0,  // BASE, midpoint = 4
            weekInPhase = 4
        )
        assertTrue(engine.shouldAdvancePhase())
    }

    @Test
    fun `shouldAdvancePhase returns false when in middle of phase`() {
        val engine = PhaseEngine(
            goal = BootcampGoal.MARATHON,
            phaseIndex = 0,  // BASE, midpoint = 4
            weekInPhase = 2
        )
        assertFalse(engine.shouldAdvancePhase())
    }

    @Test
    fun `advancePhase increments phase index and resets week`() {
        val engine = PhaseEngine(goal = BootcampGoal.MARATHON, phaseIndex = 0, weekInPhase = 4)
        val next = engine.advancePhase()
        assertEquals(1, next.phaseIndex)
        assertEquals(0, next.weekInPhase)
    }

    @Test
    fun `advancePhase at final phase wraps for cycling goals`() {
        // Cardio health has 2 phases (BASE, BUILD). At index 1, it should wrap to 0.
        val engine = PhaseEngine(goal = BootcampGoal.CARDIO_HEALTH, phaseIndex = 1, weekInPhase = 5)
        val next = engine.advancePhase()
        assertEquals(0, next.phaseIndex)
        assertEquals(0, next.weekInPhase)
    }

    @Test
    fun `isRecoveryWeek triggers on 2-on-1-off pattern`() {
        // Week 2 (0-indexed) of every phase is a recovery week (3rd week = back-off)
        val engine = PhaseEngine(goal = BootcampGoal.MARATHON, phaseIndex = 1, weekInPhase = 2)
        assertTrue(engine.isRecoveryWeek)
    }

    @Test
    fun `isRecoveryWeek is false for buildup weeks`() {
        val engine = PhaseEngine(goal = BootcampGoal.MARATHON, phaseIndex = 1, weekInPhase = 0)
        assertFalse(engine.isRecoveryWeek)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.PhaseEngineTest" --info 2>&1 | tail -20`
Expected: FAIL — class not found

**Step 3: Write minimal implementation**

```kotlin
package com.hrcoach.domain.bootcamp

import com.hrcoach.domain.model.BootcampGoal
import com.hrcoach.domain.model.TrainingPhase

data class PhaseEngine(
    val goal: BootcampGoal,
    val phaseIndex: Int,
    val weekInPhase: Int,
    val runsPerWeek: Int = 3,
    val targetMinutes: Int = 30
) {
    val currentPhase: TrainingPhase
        get() = goal.phaseArc[phaseIndex.coerceIn(goal.phaseArc.indices)]

    val totalWeeks: Int
        get() = goal.phaseArc.sumOf { phaseMidpointWeeks(it) }

    val absoluteWeek: Int
        get() {
            var sum = 0
            for (i in 0 until phaseIndex) {
                sum += phaseMidpointWeeks(goal.phaseArc[i])
            }
            return sum + weekInPhase + 1
        }

    val isRecoveryWeek: Boolean
        get() = weekInPhase > 0 && (weekInPhase + 1) % 3 == 0

    fun planCurrentWeek(): List<PlannedSession> {
        val effectiveMinutes = if (isRecoveryWeek) {
            (targetMinutes * 0.8f).toInt()
        } else {
            targetMinutes
        }
        return SessionSelector.weekSessions(
            phase = currentPhase,
            goal = goal,
            runsPerWeek = runsPerWeek,
            targetMinutes = effectiveMinutes
        )
    }

    fun shouldAdvancePhase(): Boolean =
        weekInPhase >= phaseMidpointWeeks(currentPhase)

    fun advancePhase(): PhaseEngine {
        val nextIndex = if (phaseIndex + 1 >= goal.phaseArc.size) 0 else phaseIndex + 1
        return copy(phaseIndex = nextIndex, weekInPhase = 0)
    }

    companion object {
        fun phaseMidpointWeeks(phase: TrainingPhase): Int {
            val range = phase.weeksRange
            return (range.first + range.last) / 2
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.PhaseEngineTest" --info 2>&1 | tail -20`
Expected: PASS (10 tests)

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/bootcamp/PhaseEngine.kt app/src/test/java/com/hrcoach/domain/bootcamp/PhaseEngineTest.kt
git commit -m "feat(bootcamp): add PhaseEngine orchestrator with phase advancement and recovery weeks"
```

---

### Task 8: FitnessEvaluator — uses pace-HR data to assess current fitness

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/bootcamp/FitnessEvaluator.kt`
- Test: `app/src/test/java/com/hrcoach/domain/bootcamp/FitnessEvaluatorTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.hrcoach.domain.bootcamp

import com.hrcoach.domain.model.AdaptiveProfile
import com.hrcoach.domain.model.PaceHrBucket
import com.hrcoach.domain.model.WorkoutAdaptiveMetrics
import org.junit.Assert.*
import org.junit.Test

class FitnessEvaluatorTest {

    @Test
    fun `no profile data returns UNKNOWN`() {
        val level = FitnessEvaluator.assess(AdaptiveProfile(), emptyList())
        assertEquals(FitnessLevel.UNKNOWN, level)
    }

    @Test
    fun `fewer than 3 sessions returns UNKNOWN`() {
        val profile = AdaptiveProfile(totalSessions = 2)
        val level = FitnessEvaluator.assess(profile, emptyList())
        assertEquals(FitnessLevel.UNKNOWN, level)
    }

    @Test
    fun `high efficiency factor indicates good fitness`() {
        val profile = AdaptiveProfile(
            totalSessions = 5,
            paceHrBuckets = mapOf(24 to PaceHrBucket(140f, 10))
        )
        val metrics = listOf(
            buildMetrics(efficiencyFactor = 1.2f, aerobicDecoupling = 3f)
        )
        val level = FitnessEvaluator.assess(profile, metrics)
        assertTrue(level == FitnessLevel.INTERMEDIATE || level == FitnessLevel.ADVANCED)
    }

    @Test
    fun `low efficiency factor indicates beginner fitness`() {
        val profile = AdaptiveProfile(
            totalSessions = 3,
            paceHrBuckets = mapOf(32 to PaceHrBucket(170f, 5))
        )
        val metrics = listOf(
            buildMetrics(efficiencyFactor = 0.7f, aerobicDecoupling = 12f)
        )
        val level = FitnessEvaluator.assess(profile, metrics)
        assertEquals(FitnessLevel.BEGINNER, level)
    }

    @Test
    fun `hasEnoughDataForBootcamp returns true with 3+ sessions`() {
        val profile = AdaptiveProfile(totalSessions = 3)
        assertTrue(FitnessEvaluator.hasEnoughData(profile))
    }

    @Test
    fun `hasEnoughDataForBootcamp returns false with fewer than 3 sessions`() {
        val profile = AdaptiveProfile(totalSessions = 2)
        assertFalse(FitnessEvaluator.hasEnoughData(profile))
    }

    private fun buildMetrics(
        efficiencyFactor: Float = 1.0f,
        aerobicDecoupling: Float = 5f
    ) = WorkoutAdaptiveMetrics(
        workoutId = 1L,
        recordedAtMs = System.currentTimeMillis(),
        efficiencyFactor = efficiencyFactor,
        aerobicDecoupling = aerobicDecoupling,
        avgPaceMinPerKm = 6f,
        avgHr = 150f,
        hrAtSixMinPerKm = 150f,
        settleDownSec = 20f,
        settleUpSec = 15f,
        longTermHrTrimBpm = 0f,
        responseLagSec = 25f,
        efFirstHalf = efficiencyFactor,
        efSecondHalf = efficiencyFactor * 0.95f,
        heartbeatsPerKm = 900f,
        paceAtRefHrMinPerKm = 6f
    )
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.FitnessEvaluatorTest" --info 2>&1 | tail -20`
Expected: FAIL — class not found

**Step 3: Write minimal implementation**

```kotlin
package com.hrcoach.domain.bootcamp

import com.hrcoach.domain.model.AdaptiveProfile
import com.hrcoach.domain.model.WorkoutAdaptiveMetrics

enum class FitnessLevel {
    UNKNOWN, BEGINNER, INTERMEDIATE, ADVANCED
}

object FitnessEvaluator {

    private const val MIN_SESSIONS = 3

    fun hasEnoughData(profile: AdaptiveProfile): Boolean =
        profile.totalSessions >= MIN_SESSIONS

    fun assess(
        profile: AdaptiveProfile,
        recentMetrics: List<WorkoutAdaptiveMetrics>
    ): FitnessLevel {
        if (profile.totalSessions < MIN_SESSIONS) return FitnessLevel.UNKNOWN

        val avgEf = recentMetrics.mapNotNull { it.efficiencyFactor }.average().toFloat()
        val avgDecoupling = recentMetrics.mapNotNull { it.aerobicDecoupling }.average().toFloat()

        if (avgEf.isNaN() || avgDecoupling.isNaN()) return FitnessLevel.UNKNOWN

        return when {
            avgEf >= 1.1f && avgDecoupling <= 5f -> FitnessLevel.ADVANCED
            avgEf >= 0.9f && avgDecoupling <= 8f -> FitnessLevel.INTERMEDIATE
            else -> FitnessLevel.BEGINNER
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.FitnessEvaluatorTest" --info 2>&1 | tail -20`
Expected: PASS (6 tests)

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/bootcamp/FitnessEvaluator.kt app/src/test/java/com/hrcoach/domain/bootcamp/FitnessEvaluatorTest.kt
git commit -m "feat(bootcamp): add FitnessEvaluator using efficiency factor and aerobic decoupling"
```

---

## Phase 3: ViewModel & Onboarding

### Task 9: BootcampViewModel — onboarding, dashboard state, and session management

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt`
- Create: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampUiState.kt`

This task does NOT include tests — it is a Hilt ViewModel that coordinates repository + engine calls. UI ViewModels in this codebase are not unit tested (they depend on Android lifecycle). The engine logic they delegate to IS tested (Tasks 5-8).

**Step 1: Create BootcampUiState**

```kotlin
package com.hrcoach.ui.bootcamp

import com.hrcoach.domain.bootcamp.FitnessLevel
import com.hrcoach.domain.bootcamp.PlannedSession
import com.hrcoach.domain.model.BootcampGoal
import com.hrcoach.domain.model.TrainingPhase

data class BootcampUiState(
    val isLoading: Boolean = true,
    val hasActiveEnrollment: Boolean = false,
    // Enrollment details
    val goal: BootcampGoal? = null,
    val currentPhase: TrainingPhase? = null,
    val absoluteWeek: Int = 0,
    val totalWeeks: Int = 0,
    val weekInPhase: Int = 0,
    val isRecoveryWeek: Boolean = false,
    // Next session
    val nextSession: PlannedSession? = null,
    val nextSessionDayLabel: String? = null,
    // Week view
    val currentWeekSessions: List<SessionUiItem> = emptyList(),
    // Onboarding
    val showOnboarding: Boolean = false,
    val onboardingGoal: BootcampGoal? = null,
    val onboardingMinutes: Int = 30,
    val onboardingRunsPerWeek: Int = 3,
    val onboardingTimeWarning: String? = null,
    // Gap return
    val welcomeBackMessage: String? = null,
    val needsCalibration: Boolean = false,
    // Fitness
    val fitnessLevel: FitnessLevel = FitnessLevel.UNKNOWN
)

data class SessionUiItem(
    val dayLabel: String,
    val typeName: String,
    val minutes: Int,
    val isCompleted: Boolean,
    val isToday: Boolean,
    val sessionId: Long? = null
)
```

**Step 2: Create BootcampViewModel**

```kotlin
package com.hrcoach.ui.bootcamp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.repository.AdaptiveProfileRepository
import com.hrcoach.data.repository.BootcampRepository
import com.hrcoach.data.repository.UserProfileRepository
import com.hrcoach.data.repository.WorkoutMetricsRepository
import com.hrcoach.domain.bootcamp.*
import com.hrcoach.domain.model.BootcampGoal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class BootcampViewModel @Inject constructor(
    private val bootcampRepository: BootcampRepository,
    private val adaptiveProfileRepository: AdaptiveProfileRepository,
    private val userProfileRepository: UserProfileRepository,
    private val workoutMetricsRepository: WorkoutMetricsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BootcampUiState())
    val uiState: StateFlow<BootcampUiState> = _uiState.asStateFlow()

    init {
        loadBootcampState()
    }

    private fun loadBootcampState() {
        viewModelScope.launch {
            bootcampRepository.getActiveEnrollment().collect { enrollment ->
                if (enrollment == null) {
                    _uiState.value = BootcampUiState(isLoading = false, hasActiveEnrollment = false)
                } else {
                    refreshFromEnrollment(enrollment)
                }
            }
        }
    }

    private suspend fun refreshFromEnrollment(enrollment: BootcampEnrollmentEntity) {
        val goal = BootcampGoal.valueOf(enrollment.goalType)
        val profile = adaptiveProfileRepository.getProfile()
        val fitnessLevel = FitnessEvaluator.assess(profile, emptyList())

        // Gap check
        val lastSession = bootcampRepository.getLastCompletedSession(enrollment.id)
        val daysSinceLastRun = if (lastSession?.completedWorkoutId != null) {
            // Approximate from session data — exact timestamp from workout entity
            val now = System.currentTimeMillis()
            val lastDate = enrollment.startDate + TimeUnit.DAYS.toMillis(
                ((lastSession.weekNumber - 1) * 7 + lastSession.dayOfWeek).toLong()
            )
            ((now - lastDate) / TimeUnit.DAYS.toMillis(1)).toInt()
        } else {
            0
        }

        val gapStrategy = GapAdvisor.assess(daysSinceLastRun)
        val gapAction = GapAdvisor.action(gapStrategy, enrollment.currentPhaseIndex, enrollment.currentWeekInPhase)

        // Apply gap action if needed
        val effectivePhaseIndex = gapAction.phaseIndex
        val effectiveWeekInPhase = gapAction.weekInPhase
        if (effectivePhaseIndex != enrollment.currentPhaseIndex || effectiveWeekInPhase != enrollment.currentWeekInPhase) {
            bootcampRepository.updateEnrollment(
                enrollment.copy(
                    currentPhaseIndex = effectivePhaseIndex,
                    currentWeekInPhase = effectiveWeekInPhase
                )
            )
        }

        val engine = PhaseEngine(
            goal = goal,
            phaseIndex = effectivePhaseIndex,
            weekInPhase = effectiveWeekInPhase,
            runsPerWeek = enrollment.runsPerWeek,
            targetMinutes = enrollment.targetMinutesPerRun
        )

        val weekSessions = engine.planCurrentWeek()
        val today = LocalDate.now().dayOfWeek.value
        val preferredDays = parsePreferredDays(enrollment.preferredDays)

        val sessionItems = weekSessions.mapIndexed { index, session ->
            val dayOfWeek = preferredDays.getOrElse(index) { index + 1 }
            SessionUiItem(
                dayLabel = DayOfWeek.of(dayOfWeek).getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                typeName = session.type.name.lowercase().replaceFirstChar { it.uppercase() },
                minutes = session.minutes,
                isCompleted = false,
                isToday = dayOfWeek == today
            )
        }

        _uiState.value = BootcampUiState(
            isLoading = false,
            hasActiveEnrollment = true,
            goal = goal,
            currentPhase = engine.currentPhase,
            absoluteWeek = engine.absoluteWeek,
            totalWeeks = engine.totalWeeks,
            weekInPhase = effectiveWeekInPhase,
            isRecoveryWeek = engine.isRecoveryWeek,
            nextSession = weekSessions.firstOrNull(),
            currentWeekSessions = sessionItems,
            welcomeBackMessage = gapAction.welcomeMessage,
            needsCalibration = gapAction.requiresCalibration,
            fitnessLevel = fitnessLevel
        )
    }

    // --- Onboarding ---

    fun startOnboarding() {
        _uiState.update { it.copy(showOnboarding = true) }
    }

    fun setOnboardingGoal(goal: BootcampGoal) {
        val warning = if (_uiState.value.onboardingMinutes < goal.warnBelowMinutes) {
            "${goal.name.replace('_', ' ')} training typically needs at least ${goal.suggestedMinMinutes} min per session."
        } else null
        _uiState.update { it.copy(onboardingGoal = goal, onboardingTimeWarning = warning) }
    }

    fun setOnboardingMinutes(minutes: Int) {
        val goal = _uiState.value.onboardingGoal
        val warning = if (goal != null && minutes < goal.warnBelowMinutes) {
            "${goal.name.replace('_', ' ')} training typically needs at least ${goal.suggestedMinMinutes} min per session."
        } else null
        _uiState.update { it.copy(onboardingMinutes = minutes, onboardingTimeWarning = warning) }
    }

    fun setOnboardingRunsPerWeek(runs: Int) {
        _uiState.update { it.copy(onboardingRunsPerWeek = runs) }
    }

    fun completeOnboarding(preferredDays: List<Int>) {
        val state = _uiState.value
        val goal = state.onboardingGoal ?: return
        viewModelScope.launch {
            bootcampRepository.createEnrollment(
                goal = goal,
                targetMinutesPerRun = state.onboardingMinutes,
                runsPerWeek = state.onboardingRunsPerWeek,
                preferredDays = preferredDays,
                startDate = System.currentTimeMillis()
            )
            _uiState.update { it.copy(showOnboarding = false) }
        }
    }

    fun pauseBootcamp() {
        viewModelScope.launch {
            val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return@launch
            bootcampRepository.pauseEnrollment(enrollment.id)
        }
    }

    fun dismissWelcomeBack() {
        _uiState.update { it.copy(welcomeBackMessage = null) }
    }

    private fun parsePreferredDays(json: String): List<Int> =
        json.removeSurrounding("[", "]").split(",").mapNotNull { it.trim().toIntOrNull() }
}
```

**Step 3: Verify build compiles**

Run: `./gradlew assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampUiState.kt app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt
git commit -m "feat(bootcamp): add BootcampViewModel with onboarding and gap-aware state"
```

---

## Phase 4: UI Integration

### Task 10: BootcampScreen — dashboard + onboarding flow

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt`

This is a Compose screen using `GlassCard`, `CardeaButton`, and Cardea design tokens. Build three states: onboarding flow (3 screens), active bootcamp dashboard (phase + week sessions), and welcome-back message.

**Context:** Reference existing screens for patterns:
- `HomeScreen.kt` — card layout, `GlassCard` usage, `CardeaButton` CTA
- `SetupScreen.kt` — preset selection cards, slider patterns
- `ui/theme/Color.kt` — all Cardea color tokens
- `ui/components/GlassCard.kt` — card composable

**Step 1: Create the screen composable**

This is a large UI file. Write the composable following existing patterns in `HomeScreen.kt`. Key sections:
1. Onboarding: Goal selection (4 `GlassCard`s), time slider, frequency selector, preferred days
2. Dashboard: Phase header, week session list, next session CTA
3. Welcome-back dialog when `welcomeBackMessage != null`

Use `hiltViewModel()` to obtain `BootcampViewModel`. Wire `onStartWorkout: (configJson: String) -> Unit` callback for launching a workout from a planned session.

**Step 2: Verify build compiles**

Run: `./gradlew assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt
git commit -m "feat(bootcamp): add BootcampScreen with onboarding and dashboard UI"
```

---

### Task 11: NavGraph integration — add bootcamp route

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt`

**Step 1: Add route constant**

In `Routes` object (line ~87), add:
```kotlin
const val BOOTCAMP = "bootcamp"
```

**Step 2: Add composable block**

Inside the `NavHost` (after the last `composable` block, around line 428), add:
```kotlin
composable(route = Routes.BOOTCAMP) {
    BootcampScreen(
        onStartWorkout = { configJson ->
            startWorkoutService(context, configJson, null)
        },
        onBack = { navController.popBackStack() }
    )
}
```

**Step 3: Verify build compiles**

Run: `./gradlew assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt
git commit -m "feat(bootcamp): add bootcamp route to NavGraph"
```

---

### Task 12: HomeScreen integration — hero card and dashboard card

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeViewModel.kt`

**Step 1: Add bootcamp state to HomeViewModel**

In `HomeViewModel`, inject `BootcampRepository` and `AdaptiveProfileRepository`. Add to `HomeUiState`:
```kotlin
val bootcampActive: Boolean = false,
val bootcampPhaseLabel: String? = null,
val bootcampWeekLabel: String? = null,
val bootcampNextSessionLabel: String? = null,
val showBootcampHero: Boolean = false // true if no enrollment and hero not dismissed
```

Add SharedPreferences check for hero-card-dismissed flag.

**Step 2: Add hero card to HomeScreen**

Insert between the "Last Run" card and "Start Run" button (~line 168). When `state.showBootcampHero` is true, show a full-width `GlassCard` with gradient border, title "Introducing Bootcamp", and a "Start my program" `CardeaButton`. The `[x]` dismiss button saves the dismissed flag.

When `state.bootcampActive` is true, show a compact `GlassCard` with phase label, week number, and a "Start Run" CTA that navigates to `Routes.BOOTCAMP`.

**Step 3: Wire callbacks**

Add `onGoToBootcamp: () -> Unit` to `HomeScreen` parameters. Wire the CTA buttons to call it.

**Step 4: Verify build compiles**

Run: `./gradlew assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt app/src/main/java/com/hrcoach/ui/home/HomeViewModel.kt
git commit -m "feat(bootcamp): add hero card and dashboard card to HomeScreen"
```

---

### Task 13: SetupScreen integration — bootcamp as first mode option

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/setup/SetupViewModel.kt`

**Step 1: Add bootcamp state to SetupViewModel**

Inject `BootcampRepository`. Add:
```kotlin
val hasActiveBootcamp: StateFlow<Boolean>
```

Derive from `bootcampRepository.getActiveEnrollment().map { it != null }`.

**Step 2: Add bootcamp card to SetupScreen**

At the top of the mode/preset selection area, add a `GlassCard` with gradient border. Two variants:
- **Active enrollment:** "Continue Bootcamp — Week 3 of 8, Base Building. Tap to start today's session." Tapping navigates to `Routes.BOOTCAMP`.
- **No enrollment:** "Try Bootcamp — Build fitness with a coached program." Tapping navigates to `Routes.BOOTCAMP` which will start onboarding.

Add `onGoToBootcamp: () -> Unit` parameter to `SetupScreen`.

**Step 3: Verify build compiles**

Run: `./gradlew assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt app/src/main/java/com/hrcoach/ui/setup/SetupViewModel.kt
git commit -m "feat(bootcamp): add bootcamp card to SetupScreen mode selection"
```

---

### Task 14: Workout completion linkage — mark bootcamp sessions complete

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt`

**Step 1: Add session completion logic to BootcampViewModel**

```kotlin
fun onWorkoutCompleted(workoutId: Long) {
    viewModelScope.launch {
        val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return@launch
        val nextSession = bootcampRepository.getNextScheduledSession(enrollment.id) ?: return@launch
        bootcampRepository.updateSession(
            nextSession.copy(status = "COMPLETED", completedWorkoutId = workoutId)
        )
        // Check if week is complete and advance
        val weekSessions = bootcampRepository.getSessionsForWeek(enrollment.id, nextSession.weekNumber)
        if (weekSessions.all { it.status == "COMPLETED" }) {
            val goal = BootcampGoal.valueOf(enrollment.goalType)
            val engine = PhaseEngine(
                goal = goal,
                phaseIndex = enrollment.currentPhaseIndex,
                weekInPhase = enrollment.currentWeekInPhase,
                runsPerWeek = enrollment.runsPerWeek,
                targetMinutes = enrollment.targetMinutesPerRun
            )
            val nextEngine = if (engine.shouldAdvancePhase()) engine.advancePhase()
                else engine.copy(weekInPhase = engine.weekInPhase + 1)
            bootcampRepository.updateEnrollment(
                enrollment.copy(
                    currentPhaseIndex = nextEngine.phaseIndex,
                    currentWeekInPhase = nextEngine.weekInPhase
                )
            )
        }
    }
}
```

**Step 2: Wire into NavGraph**

In the PostRunSummary `composable` block, after workout completion, check if there's an active bootcamp enrollment and call `onWorkoutCompleted`. The `onDone` callback for bootcamp sessions should navigate back to `Routes.BOOTCAMP` instead of `Routes.HOME`.

**Step 3: Verify build compiles**

Run: `./gradlew assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt
git commit -m "feat(bootcamp): link workout completion to bootcamp session tracking"
```

---

## Phase 5: Discovery Run

### Task 15: Discovery Run preset — calibration workout disguised as first run

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/preset/PresetLibrary.kt`
- Test: `app/src/test/java/com/hrcoach/domain/preset/PresetLibraryTest.kt`

**Step 1: Write the failing test**

Add to `PresetLibraryTest`:
```kotlin
@Test
fun `discovery_run preset exists and builds valid config`() {
    val preset = PresetLibrary.ALL.firstOrNull { it.id == "discovery_run" }
    assertNotNull("discovery_run preset should exist", preset)
    val config = preset!!.buildConfig(180)
    assertEquals(WorkoutMode.DISTANCE_PROFILE, config.mode)
    assertTrue("Should have 3+ segments (easy-harder-easy)", config.segments.size >= 3)
    // First segment is easy (Zone 2 ~65% of 180 = 117)
    assertTrue(config.segments.first().targetHr <= 130)
    // Middle segment is harder
    assertTrue(config.segments[1].targetHr > config.segments.first().targetHr)
    // Last segment returns to easy
    assertTrue(config.segments.last().targetHr <= 130)
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.preset.PresetLibraryTest" --info 2>&1 | tail -20`
Expected: FAIL — no preset with id "discovery_run"

**Step 3: Add discovery run preset**

In `PresetLibrary.kt`, add a `discoveryRun()` private function and add it to `ALL`:

```kotlin
private fun discoveryRun() = WorkoutPreset(
    id = "discovery_run",
    name = "Discovery Run",
    subtitle = "Find your rhythm",
    description = "A relaxed run that helps us learn your pace and heart rate patterns.",
    category = PresetCategory.BASE_AEROBIC,
    durationLabel = "20-25 min",
    intensityLabel = "Easy → Moderate → Easy",
    buildConfig = { maxHr ->
        val zone2 = (maxHr * 0.65f).toInt()
        val zone3 = (maxHr * 0.75f).toInt()
        WorkoutConfig(
            mode = WorkoutMode.DISTANCE_PROFILE,
            segments = listOf(
                HrSegment(durationSeconds = 480, targetHr = zone2, label = "Warm up — easy pace"),
                HrSegment(durationSeconds = 420, targetHr = zone3, label = "Pick it up a little"),
                HrSegment(durationSeconds = 300, targetHr = (maxHr * 0.80f).toInt(), label = "Steady push"),
                HrSegment(durationSeconds = 300, targetHr = zone2, label = "Cool down — easy")
            ),
            bufferBpm = 8,
            alertDelaySec = 20,
            presetId = "discovery_run"
        )
    }
)
```

**Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.preset.PresetLibraryTest" --info 2>&1 | tail -20`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/preset/PresetLibrary.kt app/src/test/java/com/hrcoach/domain/preset/PresetLibraryTest.kt
git commit -m "feat(bootcamp): add Discovery Run preset for new-user calibration"
```

---

## Phase 6: Notifications (future)

### Task 16: Notification scheduling — placeholder for push reminders

**Note:** Android notifications require `NotificationChannel`, `PendingIntent`, and `AlarmManager` or `WorkManager` for scheduling. This is a significant Android-specific implementation. This task is a PLACEHOLDER — create the notification channel and basic infrastructure. The full scheduling logic can be a follow-up.

**Files:**
- Create: `app/src/main/java/com/hrcoach/service/BootcampNotificationManager.kt`

**Step 1: Create notification channel and basic manager**

```kotlin
package com.hrcoach.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BootcampNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "bootcamp_reminders"
        const val CHANNEL_NAME = "Bootcamp Reminders"
    }

    fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Gentle reminders for your Bootcamp training schedule"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
```

**Step 2: Verify build compiles**

Run: `./gradlew assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/BootcampNotificationManager.kt
git commit -m "feat(bootcamp): add notification channel for bootcamp reminders (placeholder)"
```

---

## Summary

| Phase | Tasks | Description |
|---|---|---|
| 1: Data Model | 1-4 | BootcampGoal enum, Room entities, migration, DAO, repository |
| 2: Core Engine | 5-8 | SessionSelector, GapAdvisor, PhaseEngine, FitnessEvaluator |
| 3: ViewModel | 9 | BootcampViewModel with onboarding + gap-aware dashboard state |
| 4: UI Integration | 10-14 | BootcampScreen, NavGraph, HomeScreen card, SetupScreen card, completion linkage |
| 5: Discovery Run | 15 | Calibration workout preset |
| 6: Notifications | 16 | Notification channel placeholder |

**Test commands:**
- All bootcamp tests: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.*" --info`
- All tests: `./gradlew test`
- Build check: `./gradlew assembleDebug`

**Dependencies:** Tasks 1-4 are sequential (each builds on the last). Tasks 5-8 can run in parallel after Task 1. Task 9 depends on Tasks 4 + 5-8. Tasks 10-14 depend on Task 9. Task 15 is independent. Task 16 is independent.
