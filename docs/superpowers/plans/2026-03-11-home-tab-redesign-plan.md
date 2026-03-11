# Home Tab Redesign — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the three equal-weight GlassCards on the Home tab with an Ambient Banner hero card (next Bootcamp session) and three compact stat chips below it.

**Architecture:** Extend `BootcampDao` with a new query, compute `sessionStreak` as a pure function in a new `HomeSessionStreak.kt`, wire new fields into `HomeUiState`/`HomeViewModel`, then redesign `HomeScreen.kt`.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, StateFlow

**Spec:** `docs/superpowers/specs/2026-03-11-home-tab-redesign-design.md`

---

## Task 1: BootcampDao — getNextSession query

**Files:**
- Modify: `app/src/main/java/com/hrcoach/data/db/BootcampDao.kt`
- Modify: `app/src/main/java/com/hrcoach/data/repository/BootcampRepository.kt`

The existing `getNextScheduledSession` only returns `STATUS_SCHEDULED`. We need a new query that also returns `STATUS_DEFERRED` sessions (user deferred today's session, it is still their next session to do).

- [ ] **Step 1: Add DAO method**

In `BootcampDao.kt`, add after `getNextScheduledSession`:

```kotlin
@Query("""
    SELECT * FROM bootcamp_sessions
    WHERE enrollmentId = :enrollmentId
      AND status IN ('SCHEDULED', 'DEFERRED')
    ORDER BY weekNumber, dayOfWeek
    LIMIT 1
""")
suspend fun getNextSession(enrollmentId: Long): BootcampSessionEntity?
```

- [ ] **Step 2: Expose via repository**

In `BootcampRepository.kt`, add after `getNextScheduledSession`:

```kotlin
suspend fun getNextSession(enrollmentId: Long): BootcampSessionEntity? =
    bootcampDao.getNextSession(enrollmentId)
```

- [ ] **Step 3: Build and confirm no compile errors**

```
.\gradlew.bat :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/db/BootcampDao.kt
git add app/src/main/java/com/hrcoach/data/repository/BootcampRepository.kt
git commit -m "feat(dao): add getNextSession query (SCHEDULED + DEFERRED)"
```

---

## Task 2: Streak computation + HomeViewModel wiring

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/home/HomeSessionStreak.kt`
- Create: `app/src/test/java/com/hrcoach/ui/home/HomeSessionStreakTest.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeViewModel.kt`

The streak is computed from the full list of sessions for the active enrollment. The logic lives as a pure function so it is unit-testable without Hilt or coroutines.

**Streak algorithm (walking backward through sessions ordered by weekNumber DESC, dayOfWeek DESC):**
1. `COMPLETED` → streak continues
2. `SKIPPED` → stop (streak broken)
3. `SCHEDULED` and session date < today → stop (effectively missed)
4. `SCHEDULED` and session date >= today → skip (future session, not yet relevant)
5. `DEFERRED` → skip (rescheduling in progress, does not break streak)

**Calendar date formula** (matches existing `BootcampNotificationManager.reminderTimeForSession`):
```kotlin
val startDate = Instant.ofEpochMilli(enrollmentStartMs).atZone(ZoneId.systemDefault()).toLocalDate()
val sessionDate = startDate.plusDays(((weekNumber - 1L) * 7L) + (dayOfWeek - 1L))
```
`enrollmentStartMs` is the Monday midnight of week 1 (the first day of the program).

- [ ] **Step 1: Create `HomeSessionStreak.kt`**

```kotlin
package com.hrcoach.ui.home

import com.hrcoach.data.db.BootcampSessionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Computes the no-misses streak: consecutive completed Bootcamp sessions,
 * walking backward, stopping at the first skipped or effectively-missed session.
 *
 * @param sessions All sessions for the enrollment, any order (function sorts them).
 * @param enrollmentStartMs Epoch ms of the Monday that begins week 1 of the program.
 * @param today  LocalDate to compare against (injectable for testing).
 */
fun computeSessionStreak(
    sessions: List<BootcampSessionEntity>,
    enrollmentStartMs: Long,
    today: LocalDate = LocalDate.now()
): Int {
    val zone = ZoneId.systemDefault()
    val startDate = Instant.ofEpochMilli(enrollmentStartMs).atZone(zone).toLocalDate()

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
                if (sessionDate.isBefore(today)) return streak // effectively missed
                // future session — ignore and continue
            }
            BootcampSessionEntity.STATUS_DEFERRED -> { /* skip, does not break streak */ }
        }
    }
    return streak
}
```

- [ ] **Step 2: Create `HomeSessionStreakTest.kt`**

```kotlin
package com.hrcoach.ui.home

import com.hrcoach.data.db.BootcampSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HomeSessionStreakTest {

    // enrollment starts 2026-01-05 (Monday of week 1)
    private val startMs = java.time.LocalDate.of(2026, 1, 5)
        .atStartOfDay(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli()

    private val today = LocalDate.of(2026, 1, 26) // Monday of week 4

    private fun session(week: Int, day: Int, status: String) = BootcampSessionEntity(
        enrollmentId = 1L,
        weekNumber = week,
        dayOfWeek = day,
        sessionType = "EASY_RUN",
        targetMinutes = 30,
        status = status
    )

    @Test fun `empty list returns 0`() {
        assertEquals(0, computeSessionStreak(emptyList(), startMs, today))
    }

    @Test fun `all completed returns count`() {
        val sessions = listOf(
            session(1, 1, BootcampSessionEntity.STATUS_COMPLETED),
            session(1, 3, BootcampSessionEntity.STATUS_COMPLETED),
            session(2, 1, BootcampSessionEntity.STATUS_COMPLETED),
        )
        assertEquals(3, computeSessionStreak(sessions, startMs, today))
    }

    @Test fun `skipped session stops streak`() {
        val sessions = listOf(
            session(1, 1, BootcampSessionEntity.STATUS_COMPLETED),
            session(1, 3, BootcampSessionEntity.STATUS_SKIPPED),
            session(2, 1, BootcampSessionEntity.STATUS_COMPLETED),
            session(2, 3, BootcampSessionEntity.STATUS_COMPLETED),
        )
        // walk backward: W2D3=COMPLETED(1), W2D1=COMPLETED(2), W1D3=SKIPPED → stop → 2
        assertEquals(2, computeSessionStreak(sessions, startMs, today))
    }

    @Test fun `past scheduled session counts as missed`() {
        // W1D1 = 2026-01-05 (past), never actioned (SCHEDULED)
        val sessions = listOf(
            session(1, 1, BootcampSessionEntity.STATUS_SCHEDULED), // past, effectively missed
            session(2, 1, BootcampSessionEntity.STATUS_COMPLETED),
            session(2, 3, BootcampSessionEntity.STATUS_COMPLETED),
        )
        // walk backward: W2D3=COMPLETED(1), W2D1=COMPLETED(2), W1D1=SCHEDULED+past → stop → 2
        assertEquals(2, computeSessionStreak(sessions, startMs, today))
    }

    @Test fun `future scheduled session is skipped`() {
        // today = 2026-01-26 (week 4 Monday). W4D3 = 2026-01-28 = future
        val sessions = listOf(
            session(4, 3, BootcampSessionEntity.STATUS_SCHEDULED), // future, skip
            session(3, 5, BootcampSessionEntity.STATUS_COMPLETED),
            session(3, 3, BootcampSessionEntity.STATUS_COMPLETED),
        )
        assertEquals(2, computeSessionStreak(sessions, startMs, today))
    }

    @Test fun `deferred session does not break streak`() {
        val sessions = listOf(
            session(3, 1, BootcampSessionEntity.STATUS_COMPLETED),
            session(2, 5, BootcampSessionEntity.STATUS_DEFERRED), // skipped in streak calc
            session(2, 3, BootcampSessionEntity.STATUS_COMPLETED),
            session(2, 1, BootcampSessionEntity.STATUS_COMPLETED),
        )
        // W3D1=COMPLETED(1), W2D5=DEFERRED(skip), W2D3=COMPLETED(2), W2D1=COMPLETED(3)
        assertEquals(3, computeSessionStreak(sessions, startMs, today))
    }

    @Test fun `no runs returns 0 streak`() {
        val sessions = listOf(
            session(1, 1, BootcampSessionEntity.STATUS_SCHEDULED), // past — effectively missed
        )
        assertEquals(0, computeSessionStreak(sessions, startMs, today))
    }
}
```

- [ ] **Step 3: Run tests — expect 6 passing**

```
.\gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.ui.home.HomeSessionStreakTest"
```
Expected: 6 tests, all PASS

- [ ] **Step 4: Update `HomeUiState` and `HomeViewModel`**

Replace the contents of `HomeViewModel.kt` with:

```kotlin
package com.hrcoach.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.data.repository.BootcampRepository
import com.hrcoach.data.repository.WorkoutRepository
import com.hrcoach.service.WorkoutState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class HomeUiState(
    val greeting: String = "Good morning",
    val lastWorkout: WorkoutEntity? = null,
    val workoutsThisWeek: Int = 0,
    val weeklyTarget: Int = 4,
    val isSessionRunning: Boolean = false,
    // Bootcamp hero
    val hasActiveBootcamp: Boolean = false,
    val nextSession: BootcampSessionEntity? = null,
    val currentWeekNumber: Int = 1,
    val sessionStreak: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val bootcampRepository: BootcampRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        workoutRepository.getAllWorkouts(),
        bootcampRepository.getActiveEnrollment(),
        WorkoutState.snapshot
    ) { workouts, enrollment, snapshot ->
        val zone = ZoneId.systemDefault()
        val now = Instant.now().atZone(zone)
        val weekStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val thisWeek = workouts.count { it.startTime >= weekStart }

        val greeting = when (now.hour) {
            in 0..11  -> "Good morning"
            in 12..17 -> "Good afternoon"
            else      -> "Good evening"
        }

        // Bootcamp is "active" only when enrollment status == ACTIVE
        val activeEnrollment = enrollment?.takeIf {
            it.status == BootcampEnrollmentEntity.STATUS_ACTIVE
        }

        val nextSession = activeEnrollment?.let {
            bootcampRepository.getNextSession(it.id)
        }

        val sessionStreak = if (activeEnrollment != null) {
            val allSessions = bootcampRepository.getSessionsForEnrollment(activeEnrollment.id)
            // getSessionsForEnrollment returns a Flow; we need a one-shot read.
            // Use getNextSession's DAO sibling to get all sessions once.
            // (see note below — we suspend-collect the one-shot variant)
            0 // placeholder replaced in step 5
        } else 0

        HomeUiState(
            greeting = greeting,
            lastWorkout = workouts.firstOrNull(),
            workoutsThisWeek = thisWeek,
            weeklyTarget = activeEnrollment?.runsPerWeek ?: 4,
            isSessionRunning = snapshot.isRunning,
            hasActiveBootcamp = activeEnrollment != null,
            nextSession = nextSession,
            currentWeekNumber = activeEnrollment?.let {
                (it.currentPhaseIndex * 4) + it.currentWeekInPhase + 1
            } ?: 1,
            sessionStreak = sessionStreak,
        )
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
```

> **Note on streak:** The `combine` lambda runs on every emission but cannot call `suspend` functions directly. The streak must be computed differently — see Step 5.

- [ ] **Step 5: Fix streak computation — use `getSessionsForEnrollmentOnce`**

The streak requires a one-shot read of all sessions. Add a new DAO + repository method:

In `BootcampDao.kt`:
```kotlin
@Query("SELECT * FROM bootcamp_sessions WHERE enrollmentId = :enrollmentId ORDER BY weekNumber, dayOfWeek")
suspend fun getSessionsForEnrollmentOnce(enrollmentId: Long): List<BootcampSessionEntity>
```

In `BootcampRepository.kt`:
```kotlin
suspend fun getSessionsForEnrollmentOnce(enrollmentId: Long): List<BootcampSessionEntity> =
    bootcampDao.getSessionsForEnrollmentOnce(enrollmentId)
```

Then in `HomeViewModel`, replace the `combine` body with a `flatMapLatest` + coroutine approach. Replace the entire `uiState` property with:

```kotlin
val uiState: StateFlow<HomeUiState> = combine(
    workoutRepository.getAllWorkouts(),
    bootcampRepository.getActiveEnrollment(),
    WorkoutState.snapshot
) { workouts, enrollment, snapshot ->
    Triple(workouts, enrollment, snapshot)
}
.flatMapLatest { (workouts, enrollment, snapshot) ->
    kotlinx.coroutines.flow.flow {
        val zone = ZoneId.systemDefault()
        val now = Instant.now().atZone(zone)
        val weekStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val thisWeek = workouts.count { it.startTime >= weekStart }

        val greeting = when (now.hour) {
            in 0..11  -> "Good morning"
            in 12..17 -> "Good afternoon"
            else      -> "Good evening"
        }

        val activeEnrollment = enrollment?.takeIf {
            it.status == BootcampEnrollmentEntity.STATUS_ACTIVE
        }

        val nextSession = activeEnrollment?.let {
            bootcampRepository.getNextSession(it.id)
        }

        val sessionStreak = if (activeEnrollment != null) {
            val allSessions = bootcampRepository.getSessionsForEnrollmentOnce(activeEnrollment.id)
            computeSessionStreak(allSessions, activeEnrollment.startDate)
        } else 0

        emit(HomeUiState(
            greeting = greeting,
            lastWorkout = workouts.firstOrNull(),
            workoutsThisWeek = thisWeek,
            weeklyTarget = activeEnrollment?.runsPerWeek ?: 4,
            isSessionRunning = snapshot.isRunning,
            hasActiveBootcamp = activeEnrollment != null,
            nextSession = nextSession,
            currentWeekNumber = activeEnrollment?.let {
                (it.currentPhaseIndex * 4) + it.currentWeekInPhase + 1
            } ?: 1,
            sessionStreak = sessionStreak,
        ))
    }
}
.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
```

Add imports:
```kotlin
import kotlinx.coroutines.flow.flatMapLatest
```

- [ ] **Step 6: Build**

```
.\gradlew.bat :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/db/BootcampDao.kt
git add app/src/main/java/com/hrcoach/data/repository/BootcampRepository.kt
git add app/src/main/java/com/hrcoach/ui/home/HomeSessionStreak.kt
git add app/src/test/java/com/hrcoach/ui/home/HomeSessionStreakTest.kt
git add app/src/main/java/com/hrcoach/ui/home/HomeViewModel.kt
git commit -m "feat(home): add sessionStreak + nextSession to HomeUiState and HomeViewModel"
```

---

## Task 3: HomeScreen — Ambient Banner hero + stat chips

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt`

Replace the three existing GlassCards with the new layout. Keep the header (greeting + BT icon + logo avatar) and the `ActiveSessionCard` banner. Remove `EfficiencyRing`.

### Composable structure

```
HomeScreen
  ActiveSessionCard (existing, unchanged)
  HomeHeader (existing Row, unchanged except remove "CARDEA" Text)
  BootcampHeroSection   ← new: hero or no-bootcamp depending on hasActiveBootcamp
  StatChipsRow          ← new: 3 chips
```

- [ ] **Step 1: Add zone pill color helper**

At the top of `HomeScreen.kt` (below imports), add:

```kotlin
private fun zonePillColors(sessionType: String): Pair<Color, Color> = when {
    sessionType.contains("Z2", ignoreCase = true) ||
    sessionType.contains("EASY", ignoreCase = true) ||
    sessionType.contains("AEROBIC", ignoreCase = true) ->
        Color(0xFF4D61FF).copy(alpha = 0.2f) to Color(0xFF7B8FFF)
    sessionType.contains("Z4", ignoreCase = true) ||
    sessionType.contains("TEMPO", ignoreCase = true) ||
    sessionType.contains("INTERVAL", ignoreCase = true) ->
        Color(0xFFFF4D5A).copy(alpha = 0.2f) to Color(0xFFFF7B84)
    else ->
        Color.White.copy(alpha = 0.07f) to Color.White.copy(alpha = 0.5f)
}
```

- [ ] **Step 2: Add `StatChip` composable**

```kotlin
@Composable
private fun StatChip(value: String, label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                color = Color.White.copy(alpha = 0.04f),
                shape = RoundedCornerShape(14.dp)
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.07f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    lineHeight = 20.sp
                ),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White.copy(alpha = 0.4f)
            )
        }
    }
}
```

- [ ] **Step 3: Add `StatChipsRow` composable**

```kotlin
@Composable
private fun StatChipsRow(state: HomeUiState, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.hasActiveBootcamp) {
            val lastKm = state.lastWorkout?.let { "%.1f".format(it.totalDistanceMeters / 1000f) } ?: "—"
            StatChip(
                value = "${state.workoutsThisWeek}/${state.weeklyTarget}",
                label = "GOAL",
                modifier = Modifier.weight(1f)
            )
            StatChip(
                value = if (state.lastWorkout != null) "$lastKm km" else "—",
                label = "LAST RUN",
                modifier = Modifier.weight(1f)
            )
            StatChip(
                value = state.sessionStreak.toString(),
                label = "NO MISSES",
                modifier = Modifier.weight(1f)
            )
        } else {
            val lastKm = state.lastWorkout?.let { "%.1f".format(it.totalDistanceMeters / 1000f) } ?: "—"
            val lastMin = state.lastWorkout?.let {
                val mins = ((it.endTime - it.startTime) / 60_000L).coerceAtLeast(0)
                if (mins == 0L) "< 1 min" else "$mins min"
            } ?: "—"
            StatChip(
                value = state.workoutsThisWeek.toString(),
                label = "THIS WEEK",
                modifier = Modifier.weight(1f)
            )
            StatChip(
                value = if (state.lastWorkout != null) "$lastKm km" else "—",
                label = "LAST RUN",
                modifier = Modifier.weight(1f)
            )
            StatChip(
                value = lastMin,
                label = "LAST RUN",
                modifier = Modifier.weight(1f)
            )
        }
    }
}
```

- [ ] **Step 4: Add `BootcampHeroCard` composable (with active session)**

```kotlin
@Composable
private fun BootcampHeroCard(
    session: BootcampSessionEntity,
    weekNumber: Int,
    onStartSession: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (pillBg, pillText) = zonePillColors(session.sessionType)
    val sessionLabel = session.sessionType
        .replace("_", " ")
        .split(" ")
        .joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color(0x33FF2DA6),  // rgba(255,45,166,0.20)
                        1f to Color(0x14E5FFFF)   // rgba(0,229,255,0.08)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        // Radial glow — top right
        Box(
            modifier = Modifier
                .size(120.dp)
                .offset(x = (-30).dp, y = (-30).dp)
                .align(Alignment.TopEnd)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x40FF2DA6), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )

        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "TODAY'S SESSION · WEEK $weekNumber",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                ),
                color = Color.White.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = sessionLabel,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    lineHeight = 24.sp
                ),
                color = Color.White
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${session.targetMinutes} min · ${session.sessionType.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(12.dp))
            // Zone pill
            Box(
                modifier = Modifier
                    .background(color = pillBg, shape = RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = session.sessionType.replace("_", " "),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = pillText
                )
            }
            Spacer(Modifier.height(16.dp))
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CardeaButton(
                    text = "Start Session",
                    onClick = onStartSession,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onDetails)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Details",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 5: Add `NoBootcampCard` composable**

```kotlin
@Composable
private fun NoBootcampCard(onSetupBootcamp: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(20.dp)
            )
            .background(
                color = Color.White.copy(alpha = 0.03f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "STRUCTURED TRAINING",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            ),
            color = Color.White.copy(alpha = 0.4f)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Start Bootcamp",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = Color.White
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Adaptive program — HR zones, life-aware scheduling",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.45f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        CardeaButton(
            text = "Set Up Bootcamp",
            onClick = onSetupBootcamp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
```

- [ ] **Step 6: Rewrite `HomeScreen` body**

Replace everything inside the `Column` (currently lines ~89–258 in `HomeScreen.kt`) with the new layout. Keep the existing `Column` wrapper and its modifiers. Remove `EfficiencyRing`. The new column content:

```kotlin
// Active session banner — unchanged
if (state.isSessionRunning) {
    ActiveSessionCard(onClick = onGoToWorkout)
}

// Header — remove "CARDEA" Text, keep greeting + icons
Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
) {
    Text(
        text = state.greeting,
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        color = Color.White
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(GlassHighlight)
                .clickable(onClick = onStartRun),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = "Sensor setup",
                tint = CardeaTextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(GlassHighlight)
                .clickable(onClick = onGoToAccount),
            contentAlignment = Alignment.Center
        ) {
            CardeaLogo(size = 28.dp, animate = false)
        }
    }
}

// Hero card or no-bootcamp card
if (state.hasActiveBootcamp && state.nextSession != null) {
    BootcampHeroCard(
        session = state.nextSession,
        weekNumber = state.currentWeekNumber,
        onStartSession = onGoToBootcamp,
        onDetails = onGoToBootcamp,
        modifier = Modifier.fillMaxWidth()
    )
} else {
    NoBootcampCard(
        onSetupBootcamp = onGoToBootcamp,
        modifier = Modifier.fillMaxWidth()
    )
}

// Stat chips
StatChipsRow(state = state)

Spacer(modifier = Modifier.height(8.dp))
```

- [ ] **Step 7: Update imports in `HomeScreen.kt`**

Add any new imports needed (RoundedCornerShape, border, offset, Brush.linearGradient if not present, TextAlign). Remove imports for removed composables (`EfficiencyRing` is private so no import; remove `StatItem` import if no longer used, and any SimpleDateFormat/Date imports if removed).

- [ ] **Step 8: Build**

```
.\gradlew.bat :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Run all unit tests**

```
.\gradlew.bat :app:testDebugUnitTest
```
Expected: All tests pass.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt
git commit -m "feat(home): ambient banner hero + stat chips, remove old GlassCards"
```

---

## Final verification

- [ ] Full build passes: `.\gradlew.bat :app:assembleDebug`
- [ ] All unit tests pass: `.\gradlew.bat :app:testDebugUnitTest`
