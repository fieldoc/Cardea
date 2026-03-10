# Rescheduling & Blackout Days — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add proactive session rescheduling, blackout days in the day picker, and smart enrollment start-date defaulting.

**Architecture:** Extend `DaySelectionLevel` with BLACKOUT (long-press only in the UI), add DEFERRED session status, build a pure `SessionRescheduler` domain object, and wire a reschedule bottom sheet into the active bootcamp dashboard.

**Tech Stack:** Kotlin, Jetpack Compose, Room v11, Hilt, WorkManager, java.time

---

### Task 1: Add BLACKOUT to DaySelectionLevel + fix validation + DB migration

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/bootcamp/DayPreference.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampSettingsUiState.kt`
- Modify: `app/src/main/java/com/hrcoach/data/db/AppDatabase.kt`
- Modify: `app/src/main/java/com/hrcoach/di/AppModule.kt`
- Create: `app/src/test/java/com/hrcoach/domain/bootcamp/DayPreferenceTest.kt`

**Step 1: Write the failing test**

Create `app/src/test/java/com/hrcoach/domain/bootcamp/DayPreferenceTest.kt`:

```kotlin
package com.hrcoach.domain.bootcamp

import com.hrcoach.data.db.BootcampEnrollmentEntity
import org.junit.Assert.*
import org.junit.Test

class DayPreferenceTest {
    @Test fun blackout_serializes_and_roundtrips() {
        val prefs = listOf(
            DayPreference(1, DaySelectionLevel.AVAILABLE),
            DayPreference(3, DaySelectionLevel.LONG_RUN_BIAS),
            DayPreference(5, DaySelectionLevel.BLACKOUT)
        )
        val encoded = BootcampEnrollmentEntity.serializeDayPreferences(prefs)
        val decoded = BootcampEnrollmentEntity.parseDayPreferences(encoded)
        assertEquals(prefs, decoded)
    }

    @Test fun blackout_not_in_tap_cycle() {
        // BLACKOUT is set by long press only — tap cycle should never produce it
        assertEquals(DaySelectionLevel.AVAILABLE, DaySelectionLevel.NONE.next())
        assertEquals(DaySelectionLevel.LONG_RUN_BIAS, DaySelectionLevel.AVAILABLE.next())
        assertEquals(DaySelectionLevel.NONE, DaySelectionLevel.LONG_RUN_BIAS.next())
    }
}
```

**Step 2: Run to confirm fails**
```
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.DayPreferenceTest" 2>&1 | tail -20
```
Expected: FAIL — `DaySelectionLevel.BLACKOUT` does not exist yet.

**Step 3: Add BLACKOUT to DaySelectionLevel**

In `DayPreference.kt`, change the enum so BLACKOUT exists but is NOT in the `next()` cycle:

```kotlin
enum class DaySelectionLevel {
    NONE,
    AVAILABLE,
    LONG_RUN_BIAS,
    BLACKOUT;

    fun next(): DaySelectionLevel = when (this) {
        NONE -> AVAILABLE
        AVAILABLE -> LONG_RUN_BIAS
        LONG_RUN_BIAS -> NONE
        BLACKOUT -> NONE   // safety: tapping a blackout day clears it
    }
}
```

**Step 4: Fix validation in BootcampSettingsUiState.kt**

`preferredDaysValidationError` currently counts all entries in `editPreferredDays`. BLACKOUT days will now live in that list, so count only run days:

```kotlin
val preferredDaysValidationError: String?
    get() {
        val runDays = editPreferredDays.count {
            it.level == DaySelectionLevel.AVAILABLE || it.level == DaySelectionLevel.LONG_RUN_BIAS
        }
        return if (runDays == editRunsPerWeek) null
        else "Select exactly $editRunsPerWeek days \u00b7 $runDays selected"
    }
```

Also fix `hasPreferredDayChanges` — BLACKOUT days must be compared too (no change needed, it already compares the full list).

**Step 5: Bump DB to version 11 in AppDatabase.kt**

- Change `version = 10` → `version = 11`
- Add before the `MIGRATION_9_10` declaration:

```kotlin
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // No schema change. BLACKOUT is a new DaySelectionLevel value
        // stored in the existing preferredDays TEXT column.
    }
}
```

**Step 6: Add MIGRATION_10_11 to AppModule.kt**

Find the `databaseBuilder` call in `AppModule.kt` and add `.addMigrations(AppDatabase.MIGRATION_10_11)` alongside the existing migrations.

**Step 7: Run tests**
```
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.DayPreferenceTest" 2>&1 | tail -20
```
Expected: PASS

**Step 8: Commit**
```
git add app/src/main/java/com/hrcoach/domain/bootcamp/DayPreference.kt \
        app/src/main/java/com/hrcoach/ui/bootcamp/BootcampSettingsUiState.kt \
        app/src/main/java/com/hrcoach/data/db/AppDatabase.kt \
        app/src/main/java/com/hrcoach/di/AppModule.kt \
        app/src/test/java/com/hrcoach/domain/bootcamp/DayPreferenceTest.kt
git commit -m "feat(bootcamp): add BLACKOUT day level, DB v11, fix run-day validation count"
```

---

### Task 2: Add STATUS_DEFERRED + fix missed-session detection + notification skip

**Files:**
- Modify: `app/src/main/java/com/hrcoach/data/db/BootcampSessionEntity.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt` (~line 182)
- Modify: `app/src/main/java/com/hrcoach/service/BootcampNotificationManager.kt` (~line 52)
- Modify: `app/src/main/java/com/hrcoach/data/repository/BootcampRepository.kt` (~line 161)

**Step 1: Add STATUS_DEFERRED to BootcampSessionEntity**

In the `companion object`, add after `STATUS_SKIPPED`:
```kotlin
const val STATUS_DEFERRED = "DEFERRED"
```

**Step 2: Fix missedSession in BootcampViewModel.kt (~line 182)**

Change:
```kotlin
val missedSession = scheduledSessions.any {
    it.dayOfWeek < today &&
        it.status != BootcampSessionEntity.STATUS_COMPLETED &&
        it.status != BootcampSessionEntity.STATUS_SKIPPED
}
```
To:
```kotlin
val missedSession = scheduledSessions.any {
    it.dayOfWeek < today &&
        it.status != BootcampSessionEntity.STATUS_COMPLETED &&
        it.status != BootcampSessionEntity.STATUS_SKIPPED &&
        it.status != BootcampSessionEntity.STATUS_DEFERRED
}
```

**Step 3: Skip DEFERRED in BootcampNotificationManager.kt (~line 52)**

At the top of the `sessions.forEach` lambda in `scheduleWeekReminders`, add:
```kotlin
sessions.forEach { session ->
    if (session.status == BootcampSessionEntity.STATUS_DEFERRED) return@forEach
    val reminderAt = reminderTimeForSession(startDateMs = startDateMs, session = session)
    ...
```

**Step 4: Include DEFERRED in reslotting candidate pool (BootcampRepository.kt ~line 161)**

`computeReslottedDays` currently only reslots `STATUS_SCHEDULED` sessions. DEFERRED sessions occupy a day slot and should not be double-booked. Change filter to:
```kotlin
val scheduledSessions = sessions.filter {
    it.status == BootcampSessionEntity.STATUS_SCHEDULED ||
    it.status == BootcampSessionEntity.STATUS_DEFERRED
}
```

**Step 5: Compile check**
```
./gradlew assembleDebug 2>&1 | grep -E "^.*error:" | head -10
```

**Step 6: Commit**
```
git add app/src/main/java/com/hrcoach/data/db/BootcampSessionEntity.kt \
        app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt \
        app/src/main/java/com/hrcoach/service/BootcampNotificationManager.kt \
        app/src/main/java/com/hrcoach/data/repository/BootcampRepository.kt
git commit -m "feat(bootcamp): DEFERRED status, suppress nag notifications, fix missed detection"
```

---

### Task 3: Fix enrollment start date — default to first upcoming preferred day

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/bootcamp/EnrollmentStartDate.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt` (~line 288)
- Create: `app/src/test/java/com/hrcoach/domain/bootcamp/EnrollmentStartDateTest.kt`

**Step 1: Write the failing test**

Create `app/src/test/java/com/hrcoach/domain/bootcamp/EnrollmentStartDateTest.kt`:

```kotlin
package com.hrcoach.domain.bootcamp

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class EnrollmentStartDateTest {

    @Test fun picks_next_preferred_day_in_same_week() {
        // preferred Mon(1), Wed(3), Sat(6). Today is Mon(1). Next preferred = Wed(3).
        val result = firstPreferredDayAfter(listOf(1, 3, 6), LocalDate.of(2026, 3, 9))
        assertEquals(LocalDate.of(2026, 3, 11), result) // Wednesday
    }

    @Test fun wraps_to_next_week_when_no_days_remain() {
        // preferred Mon(1) only. Today is Wednesday(3). Wraps to next Monday.
        val result = firstPreferredDayAfter(listOf(1), LocalDate.of(2026, 3, 11))
        assertEquals(LocalDate.of(2026, 3, 16), result)
    }

    @Test fun never_returns_today() {
        // preferred Mon(1), Wed(3). Today is Monday(1). Returns Wed(3), not today.
        val result = firstPreferredDayAfter(listOf(1, 3), LocalDate.of(2026, 3, 9))
        assertEquals(LocalDate.of(2026, 3, 11), result)
    }
}
```

**Step 2: Run to confirm fails**
```
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.EnrollmentStartDateTest" 2>&1 | tail -20
```

**Step 3: Implement EnrollmentStartDate.kt**

Create `app/src/main/java/com/hrcoach/domain/bootcamp/EnrollmentStartDate.kt`:

```kotlin
package com.hrcoach.domain.bootcamp

import java.time.LocalDate
import java.time.ZoneId

/**
 * Returns the first day in [preferredDayNumbers] (1=Mon … 7=Sun) that is
 * strictly after [today]. Wraps to the following week if needed.
 */
fun firstPreferredDayAfter(preferredDayNumbers: List<Int>, today: LocalDate): LocalDate {
    val sorted = preferredDayNumbers.sorted()
    val todayDow = today.dayOfWeek.value          // 1=Mon, 7=Sun
    val thisWeek = sorted.firstOrNull { it > todayDow }
    if (thisWeek != null) {
        return today.plusDays((thisWeek - todayDow).toLong())
    }
    // Wrap: first preferred day next week
    val nextWeekFirst = sorted.first()
    return today.plusDays((7 - todayDow + nextWeekFirst).toLong())
}

/** Returns the epoch-millis of midnight (local) for the first upcoming preferred day. */
fun firstPreferredDayAfterMs(preferredDayNumbers: List<Int>): Long {
    val today = LocalDate.now(ZoneId.systemDefault())
    return firstPreferredDayAfter(preferredDayNumbers, today)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}
```

**Step 4: Wire into completeOnboarding() in BootcampViewModel.kt (~line 288)**

Change:
```kotlin
val startDate = System.currentTimeMillis()
```
To:
```kotlin
val startDate = firstPreferredDayAfterMs(preferredDays)
```

Add import at top of BootcampViewModel.kt:
```kotlin
import com.hrcoach.domain.bootcamp.firstPreferredDayAfterMs
```

**Step 5: Run tests**
```
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.EnrollmentStartDateTest" 2>&1 | tail -20
```
Expected: PASS

**Step 6: Commit**
```
git add app/src/main/java/com/hrcoach/domain/bootcamp/EnrollmentStartDate.kt \
        app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt \
        app/src/test/java/com/hrcoach/domain/bootcamp/EnrollmentStartDateTest.kt
git commit -m "fix(bootcamp): default enrollment start to first upcoming preferred day"
```

---

### Task 4: Fix star badge clipping on circular day buttons

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampSettingsScreen.kt` (lines 484–493)

**Background:** The 40dp day button is clipped to `CircleShape`. The star icon at `Alignment.TopEnd` sits at the bounding-box corner, partially outside the circle's edge. Adding `offset(x = (-3).dp, y = 3.dp)` pulls it inward toward the circle's visible area.

**Step 1: Update the star icon modifier in DayChipRow**

Change lines 484–493 from:
```kotlin
if (isLongRun) {
    Icon(
        imageVector = Icons.Default.Star,
        contentDescription = "Long Run Bias",
        tint = Color.White,
        modifier = Modifier
            .size(10.dp)
            .align(Alignment.TopEnd)
    )
}
```
To:
```kotlin
if (isLongRun) {
    Icon(
        imageVector = Icons.Default.Star,
        contentDescription = "Long Run Bias",
        tint = Color.White,
        modifier = Modifier
            .size(10.dp)
            .align(Alignment.TopEnd)
            .offset(x = (-3).dp, y = 3.dp)
    )
}
```

**Step 2: Commit**
```
git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampSettingsScreen.kt
git commit -m "fix(ui): pull star badge inside circular day button bounds"
```

---

### Task 5: Day picker — BLACKOUT UI (long press + visual states + legend strip)

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampSettingsScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampSettingsViewModel.kt`

**Step 1: Add `toggleBlackoutDay` to BootcampSettingsViewModel**

After the `cycleDayPreference` function (~line 149), add:

```kotlin
fun toggleBlackoutDay(day: Int) {
    _uiState.update { state ->
        val current = state.editPreferredDays.toMutableList()
        val existing = current.indexOfFirst { it.day == day }
        if (existing != -1 && current[existing].level == DaySelectionLevel.BLACKOUT) {
            current.removeAt(existing)   // long press on BLACKOUT → clear it
        } else {
            if (existing != -1) current.removeAt(existing)
            current.add(DayPreference(day, DaySelectionLevel.BLACKOUT))
        }
        state.copy(editPreferredDays = current.sortedBy { it.day }, saveError = null)
    }
}
```

**Step 2: Add required imports to BootcampSettingsScreen.kt**

```kotlin
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material.icons.filled.Close
```

**Step 3: Replace the DayChipRow composable entirely** (lines 447–497 in BootcampSettingsScreen.kt)

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayChipRow(
    selectedDays: List<DayPreference>,
    onToggle: (Int) -> Unit,
    onLongPress: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    // Legend strip — shows all four states as tiny labeled chips
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DayLegendChip("open",     DaySelectionLevel.NONE)
        DayLegendChip("run",      DaySelectionLevel.AVAILABLE)
        DayLegendChip("long",     DaySelectionLevel.LONG_RUN_BIAS)
        DayLegendChip("blocked",  DaySelectionLevel.BLACKOUT)
    }

    // Day buttons
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        (1..7).forEach { day ->
            val preference = selectedDays.find { it.day == day }
            val level = preference?.level ?: DaySelectionLevel.NONE
            val isBlackout  = level == DaySelectionLevel.BLACKOUT
            val isSelected  = level == DaySelectionLevel.AVAILABLE || level == DaySelectionLevel.LONG_RUN_BIAS
            val isLongRun   = level == DaySelectionLevel.LONG_RUN_BIAS

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isBlackout -> Brush.linearGradient(listOf(Color(0xFF1C1F26), Color(0xFF1C1F26)))
                            isSelected -> CardeaGradient
                            else       -> Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = when {
                            isBlackout -> Color(0xFF3D2020)
                            isSelected -> Color.Transparent
                            else       -> GlassBorder
                        },
                        shape = CircleShape
                    )
                    .combinedClickable(
                        onClick = { onToggle(day) },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress(day)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = DayLetter[day - 1],
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isBlackout) Color(0xFF8B3A3A) else Color.White
                )
                if (isLongRun) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Long run bias",
                        tint = Color.White,
                        modifier = Modifier
                            .size(10.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = (-3).dp, y = 3.dp)
                    )
                }
                if (isBlackout) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Blocked",
                        tint = Color(0xFF8B3A3A),
                        modifier = Modifier
                            .size(10.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = (-3).dp, y = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayLegendChip(label: String, level: DaySelectionLevel) {
    val isBlackout = level == DaySelectionLevel.BLACKOUT
    val isSelected = level == DaySelectionLevel.AVAILABLE || level == DaySelectionLevel.LONG_RUN_BIAS
    val isLongRun  = level == DaySelectionLevel.LONG_RUN_BIAS

    Box(
        modifier = Modifier
            .height(22.dp)
            .clip(CircleShape)
            .background(
                when {
                    isBlackout -> Brush.linearGradient(listOf(Color(0xFF1C1F26), Color(0xFF1C1F26)))
                    isSelected -> CardeaGradient
                    else       -> Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                }
            )
            .border(1.dp, if (isSelected) Color.Transparent else GlassBorder, CircleShape)
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = if (isBlackout) Color(0xFF8B3A3A) else Color.White
            )
            if (isLongRun)  Icon(Icons.Default.Star,  null, tint = Color.White,           modifier = Modifier.size(7.dp))
            if (isBlackout) Icon(Icons.Default.Close, null, tint = Color(0xFF8B3A3A),      modifier = Modifier.size(7.dp))
        }
    }
}
```

**Step 4: Update DayChipRow call site** in BootcampSettingsScreen (~line 306)

```kotlin
DayChipRow(
    selectedDays = state.editPreferredDays,
    onToggle = viewModel::cycleDayPreference,
    onLongPress = viewModel::toggleBlackoutDay
)
```

**Step 5: Remove the prose hint text** (the "Tap once to select, twice to prioritize..." Text block just above the `DayChipRow` call, approx lines 299–304). The legend strip replaces it.

**Step 6: Add missing `sp` import** if not present:
```kotlin
import androidx.compose.ui.unit.sp
```

**Step 7: Compile check**
```
./gradlew assembleDebug 2>&1 | grep -E "^.*error:" | head -20
```

**Step 8: Commit**
```
git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampSettingsScreen.kt \
        app/src/main/java/com/hrcoach/ui/bootcamp/BootcampSettingsViewModel.kt
git commit -m "feat(ui): blackout day picker - long press, visual states, legend strip"
```

---

### Task 6: Build SessionRescheduler domain object

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/bootcamp/SessionRescheduler.kt`
- Create: `app/src/test/java/com/hrcoach/domain/bootcamp/SessionReschedulerTest.kt`

**Step 1: Write failing tests**

Create `app/src/test/java/com/hrcoach/domain/bootcamp/SessionReschedulerTest.kt`:

```kotlin
package com.hrcoach.domain.bootcamp

import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity
import org.junit.Assert.*
import org.junit.Test

class SessionReschedulerTest {

    private fun enrollment(preferredDays: String = "1:AVAILABLE,3:AVAILABLE,6:AVAILABLE") =
        BootcampEnrollmentEntity(
            id = 1L, goalType = "CARDIO_HEALTH", targetMinutesPerRun = 30,
            runsPerWeek = 3, preferredDays = preferredDays, startDate = 0L
        )

    private fun session(day: Int, type: String = "EASY", status: String = "SCHEDULED") =
        BootcampSessionEntity(
            id = day.toLong(), enrollmentId = 1L, weekNumber = 1,
            dayOfWeek = day, sessionType = type, targetMinutes = 30, status = status
        )

    @Test fun moves_to_next_available_preferred_day() {
        val req = RescheduleRequest(
            session = session(day = 1),
            enrollment = enrollment(),
            todayDayOfWeek = 1,
            occupiedDaysThisWeek = setOf(1),
            allSessionsThisWeek = listOf(session(1), session(3), session(6))
        )
        val result = SessionRescheduler.reschedule(req)
        assertTrue(result is RescheduleResult.Moved)
        assertEquals(3, (result as RescheduleResult.Moved).newDayOfWeek)
    }

    @Test fun skips_blackout_days() {
        val req = RescheduleRequest(
            session = session(day = 1),
            enrollment = enrollment("1:AVAILABLE,3:BLACKOUT,6:AVAILABLE"),
            todayDayOfWeek = 1,
            occupiedDaysThisWeek = setOf(1),
            allSessionsThisWeek = listOf(session(1), session(3), session(6))
        )
        val result = SessionRescheduler.reschedule(req)
        assertEquals(6, (result as RescheduleResult.Moved).newDayOfWeek)
    }

    @Test fun drops_easy_when_no_slots_available() {
        // Week ends Thursday (today = 5), no preferred days remain
        val easySession  = session(day = 1, type = "EASY",  status = "SKIPPED")
        val tempoSession = session(day = 3, type = "TEMPO")
        val longSession  = session(day = 6, type = "LONG_RUN")
        val req = RescheduleRequest(
            session = tempoSession,
            enrollment = enrollment("1:AVAILABLE,3:AVAILABLE,6:AVAILABLE"),
            todayDayOfWeek = 5,
            occupiedDaysThisWeek = setOf(1, 3, 6),
            allSessionsThisWeek = listOf(easySession, tempoSession, longSession)
        )
        val result = SessionRescheduler.reschedule(req)
        // Should drop the easiest available scheduled session (long run, since easy is already skipped)
        assertTrue(result is RescheduleResult.Dropped)
    }

    @Test fun defer_returns_deferred() {
        val req = RescheduleRequest(
            session = session(day = 1),
            enrollment = enrollment(),
            todayDayOfWeek = 1,
            occupiedDaysThisWeek = setOf(1),
            allSessionsThisWeek = listOf(session(1))
        )
        val result = SessionRescheduler.defer(req)
        assertTrue(result is RescheduleResult.Deferred)
    }

    @Test fun respects_48h_recovery_gap_for_hard_sessions() {
        // TEMPO on Tue(2). Today=Mon(1). Only Wed(3) remains — 1 day gap, violates 48h.
        val req = RescheduleRequest(
            session = session(day = 1, type = "TEMPO"),
            enrollment = enrollment("1:AVAILABLE,2:AVAILABLE,3:AVAILABLE"),
            todayDayOfWeek = 1,
            occupiedDaysThisWeek = setOf(1, 2),
            allSessionsThisWeek = listOf(
                session(1, "TEMPO"),
                session(2, "TEMPO"),
                session(3, "EASY")
            )
        )
        val result = SessionRescheduler.reschedule(req)
        // Wed(3) is 1 day after Tue TEMPO — recovery gap violated → no slot → Dropped
        assertTrue(result is RescheduleResult.Dropped)
    }

    @Test fun skips_none_level_days() {
        // Day 4 has NONE level — should not be used as a rescheduling target even if free
        val req = RescheduleRequest(
            session = session(day = 1),
            enrollment = enrollment("1:AVAILABLE,4:NONE,6:AVAILABLE"),
            todayDayOfWeek = 1,
            occupiedDaysThisWeek = setOf(1),
            allSessionsThisWeek = listOf(session(1), session(6))
        )
        val result = SessionRescheduler.reschedule(req)
        // Day 4 (NONE) skipped, goes to day 6
        assertEquals(6, (result as RescheduleResult.Moved).newDayOfWeek)
    }
}
```

**Step 2: Run to confirm fails**
```
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.SessionReschedulerTest" 2>&1 | tail -30
```

**Step 3: Implement SessionRescheduler**

Create `app/src/main/java/com/hrcoach/domain/bootcamp/SessionRescheduler.kt`:

```kotlin
package com.hrcoach.domain.bootcamp

import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity

data class RescheduleRequest(
    val session: BootcampSessionEntity,
    val enrollment: BootcampEnrollmentEntity,
    /** ISO day-of-week: 1=Mon … 7=Sun */
    val todayDayOfWeek: Int,
    val occupiedDaysThisWeek: Set<Int>,
    val allSessionsThisWeek: List<BootcampSessionEntity>
)

sealed class RescheduleResult {
    data class Moved(val newDayOfWeek: Int) : RescheduleResult()
    data class Dropped(val droppedSessionId: Long) : RescheduleResult()
    object Deferred : RescheduleResult()
}

object SessionRescheduler {

    private val hardTypes = setOf("TEMPO", "INTERVALS")

    fun reschedule(req: RescheduleRequest): RescheduleResult {
        val prefs = BootcampEnrollmentEntity.parseDayPreferences(req.enrollment.preferredDays)
        val validDays = findValidDays(req, prefs)
        if (validDays.isNotEmpty()) return RescheduleResult.Moved(validDays.first())
        val toDrop = lowestPrioritySession(req.allSessionsThisWeek, req.session)
        return RescheduleResult.Dropped(toDrop.id)
    }

    fun defer(@Suppress("UNUSED_PARAMETER") req: RescheduleRequest): RescheduleResult =
        RescheduleResult.Deferred

    private fun findValidDays(req: RescheduleRequest, prefs: List<DayPreference>): List<Int> {
        val hardDaysOtherThanThis = req.allSessionsThisWeek
            .filter { it.sessionType in hardTypes && it.dayOfWeek != req.session.dayOfWeek }
            .map { it.dayOfWeek }
            .toSet()

        return (req.todayDayOfWeek + 1..7).filter { candidate ->
            val pref = prefs.find { it.day == candidate }
            val isRunnable = pref != null &&
                pref.level != DaySelectionLevel.NONE &&
                pref.level != DaySelectionLevel.BLACKOUT
            val isOccupied = candidate in req.occupiedDaysThisWeek
            val violatesRecovery = hardDaysOtherThanThis.any { kotlin.math.abs(it - candidate) < 2 } ||
                (req.session.sessionType in hardTypes &&
                    hardDaysOtherThanThis.any { kotlin.math.abs(it - candidate) < 2 })
            isRunnable && !isOccupied && !violatesRecovery
        }
    }

    private fun lowestPrioritySession(
        sessions: List<BootcampSessionEntity>,
        current: BootcampSessionEntity
    ): BootcampSessionEntity {
        val candidates = sessions.filter {
            it.id != current.id && it.status == BootcampSessionEntity.STATUS_SCHEDULED
        }
        return candidates
            .minByOrNull { dropPriority(it.sessionType) }
            ?: current
    }

    /** Lower number = drop first */
    private fun dropPriority(type: String): Int = when (type) {
        "EASY"      -> 0
        "TEMPO"     -> 1
        "INTERVALS" -> 2
        "LONG_RUN"  -> 3
        else        -> 1
    }
}
```

**Step 4: Run tests**
```
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.SessionReschedulerTest" 2>&1 | tail -20
```
Expected: PASS

**Step 5: Commit**
```
git add app/src/main/java/com/hrcoach/domain/bootcamp/SessionRescheduler.kt \
        app/src/test/java/com/hrcoach/domain/bootcamp/SessionReschedulerTest.kt
git commit -m "feat(bootcamp): SessionRescheduler with 48h recovery rule and drop priority"
```

---

### Task 7: Reschedule state in BootcampUiState + repository helpers

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampUiState.kt`
- Modify: `app/src/main/java/com/hrcoach/data/repository/BootcampRepository.kt`

**Step 1: Add reschedule fields to BootcampUiState**

Add to `BootcampUiState` data class:
```kotlin
// Reschedule bottom sheet
val rescheduleSheetSessionId: Long? = null,
val rescheduleAutoTargetDay: Int? = null,
val rescheduleAutoTargetLabel: String? = null,
```

**Step 2: Add repository helpers to BootcampRepository.kt**

After `swapSessionToRestDay`:

```kotlin
suspend fun rescheduleSession(sessionId: Long, newDayOfWeek: Int) {
    val session = bootcampDao.getSessionById(sessionId) ?: return
    bootcampDao.updateSession(session.copy(dayOfWeek = newDayOfWeek))
}

suspend fun deferSession(sessionId: Long) {
    val session = bootcampDao.getSessionById(sessionId) ?: return
    bootcampDao.updateSession(session.copy(status = BootcampSessionEntity.STATUS_DEFERRED))
}

suspend fun dropSession(sessionId: Long) {
    val session = bootcampDao.getSessionById(sessionId) ?: return
    bootcampDao.updateSession(
        session.copy(
            status = BootcampSessionEntity.STATUS_SKIPPED,
            completedAtMs = System.currentTimeMillis()
        )
    )
}
```

**Step 3: Compile check**
```
./gradlew assembleDebug 2>&1 | grep -E "^.*error:" | head -10
```

**Step 4: Commit**
```
git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampUiState.kt \
        app/src/main/java/com/hrcoach/data/repository/BootcampRepository.kt
git commit -m "feat(bootcamp): reschedule UI state fields and repository helpers"
```

---

### Task 8: Wire reschedule into BootcampViewModel

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt`

**Step 1: Add reschedule functions after `swapTodayForRest`**

```kotlin
fun requestReschedule(sessionId: Long) {
    viewModelScope.launch {
        val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return@launch
        val sessions = bootcampRepository.getSessionsForWeek(enrollment.id, _uiState.value.absoluteWeek)
        val session = sessions.find { it.id == sessionId } ?: return@launch
        val today = LocalDate.now().dayOfWeek.value
        val occupied = sessions.map { it.dayOfWeek }.toSet()
        val req = RescheduleRequest(
            session = session,
            enrollment = enrollment,
            todayDayOfWeek = today,
            occupiedDaysThisWeek = occupied,
            allSessionsThisWeek = sessions
        )
        val result = SessionRescheduler.reschedule(req)
        val (targetDay, targetLabel) = when (result) {
            is RescheduleResult.Moved -> result.newDayOfWeek to dayLabelFor(result.newDayOfWeek)
            else -> null to null
        }
        _uiState.update {
            it.copy(
                rescheduleSheetSessionId = sessionId,
                rescheduleAutoTargetDay = targetDay,
                rescheduleAutoTargetLabel = targetLabel
            )
        }
    }
}

fun confirmReschedule() {
    val sessionId = _uiState.value.rescheduleSheetSessionId ?: return
    val newDay = _uiState.value.rescheduleAutoTargetDay
    viewModelScope.launch {
        if (newDay != null) {
            bootcampRepository.rescheduleSession(sessionId, newDay)
        } else {
            bootcampRepository.dropSession(sessionId)
        }
        clearRescheduleSheet()
        loadBootcampState()
    }
}

fun deferSession() {
    val sessionId = _uiState.value.rescheduleSheetSessionId ?: return
    viewModelScope.launch {
        bootcampRepository.deferSession(sessionId)
        clearRescheduleSheet()
        loadBootcampState()
    }
}

fun dismissRescheduleSheet() = clearRescheduleSheet()

private fun clearRescheduleSheet() {
    _uiState.update {
        it.copy(
            rescheduleSheetSessionId = null,
            rescheduleAutoTargetDay = null,
            rescheduleAutoTargetLabel = null
        )
    }
}
```

**Step 2: Add missing imports**
```kotlin
import com.hrcoach.domain.bootcamp.RescheduleRequest
import com.hrcoach.domain.bootcamp.RescheduleResult
import com.hrcoach.domain.bootcamp.SessionRescheduler
```

**Step 3: Compile check**
```
./gradlew assembleDebug 2>&1 | grep -E "^.*error:" | head -10
```

**Step 4: Commit**
```
git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt
git commit -m "feat(bootcamp): wire requestReschedule/defer/drop into BootcampViewModel"
```

---

### Task 9: Add reschedule UI to BootcampScreen

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt`

**Step 1: Add `onReschedule` param to `ActiveBootcampDashboard`**

Find the `ActiveBootcampDashboard` composable function signature and add:
```kotlin
onReschedule: (Long) -> Unit,
```

**Step 2: Locate where today's session Start button is rendered**

Search for the composable inside `ActiveBootcampDashboard` that renders the "Start" or "Go for a run" button for today's session. It will reference `session.isToday` and `onStartWorkout`. Below the primary button (after it, inside the same Column or Box), add:

```kotlin
if (session.isToday && !session.isCompleted && session.sessionId != null) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "can't make it today?",
        style = MaterialTheme.typography.labelSmall,
        color = CardeaTextTertiary,
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .clickable { onReschedule(session.sessionId) }
    )
}
```

**Step 3: Add the RescheduleBottomSheet composable** (new private composable at the bottom of the file)

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RescheduleBottomSheet(
    autoTargetLabel: String?,
    onConfirm: () -> Unit,
    onChooseDay: () -> Unit,
    onDefer: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF12161F),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (autoTargetLabel != null) "Move to $autoTargetLabel"
                       else "No slots left this week",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
            if (autoTargetLabel != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardeaGradient)
                        .clickable { onConfirm() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sounds good",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                text = "Choose a different day",
                style = MaterialTheme.typography.labelMedium,
                color = CardeaTextTertiary,
                modifier = Modifier.clickable { onChooseDay() }
            )
            Text(
                text = "I'm not sure yet",
                style = MaterialTheme.typography.labelMedium,
                color = CardeaTextTertiary,
                modifier = Modifier.clickable { onDefer() }
            )
        }
    }
}
```

**Note on "Choose a different day":** For now, `onChooseDay` calls `onDefer()` as a safe fallback (user can reschedule manually later). A date-picker dialog can be added as a follow-up if desired.

**Step 4: Show the sheet in BootcampScreen**

Inside the `BootcampScreen` composable, after the `DeleteConfirmDialog` block, add:

```kotlin
if (uiState.rescheduleSheetSessionId != null) {
    RescheduleBottomSheet(
        autoTargetLabel = uiState.rescheduleAutoTargetLabel,
        onConfirm  = { viewModel.confirmReschedule() },
        onChooseDay = { viewModel.deferSession() },   // fallback until date picker added
        onDefer    = { viewModel.deferSession() },
        onDismiss  = { viewModel.dismissRescheduleSheet() }
    )
}
```

**Step 5: Wire `onReschedule` into the `ActiveBootcampDashboard` call site** (~line 131)

```kotlin
ActiveBootcampDashboard(
    uiState = uiState,
    onStartWorkout = onStartWorkout,
    onPause = { viewModel.pauseBootcamp() },
    onResume = { viewModel.resumeBootcamp() },
    onEndProgram = { viewModel.showDeleteConfirmDialog() },
    onBootcampWorkoutStarting = viewModel::onBootcampWorkoutStarting,
    onDismissTierPrompt = viewModel::dismissTierPrompt,
    onAcceptTierChange = viewModel::acceptTierChange,
    onConfirmIllness = viewModel::confirmIllness,
    onDismissIllness = viewModel::dismissIllness,
    onSwapTodayForRest = viewModel::swapTodayForRest,
    onGraduateGoal = viewModel::graduateCurrentGoal,
    onReschedule = { sessionId -> viewModel.requestReschedule(sessionId) }  // new
)
```

**Step 6: Add missing imports** (ModalBottomSheet, rememberModalBottomSheetState if not present):
```kotlin
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
```

**Step 7: Compile check + run all tests**
```
./gradlew assembleDebug 2>&1 | grep -E "^.*error:" | head -20
./gradlew testDebugUnitTest 2>&1 | tail -20
```

**Step 8: Commit**
```
git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt
git commit -m "feat(ui): reschedule sheet and cant-make-it link on bootcamp dashboard"
```

---

## Cross-Agent Notes

Tasks **1–3** and **4–5** are independent — can be dispatched as parallel agents if desired. Tasks 6–9 build on each other sequentially.

**Task 6 (SessionRescheduler)** is the best candidate for Codex/Gemini delegation — it is pure Kotlin logic with no Android dependencies and has complete test coverage specified above.
