# Bootcamp Dashboard Actions Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add dismiss+reschedule to the missed session card, make preferred days strip open an interactive bottom sheet, and wire the orphaned BootcampSettingsScreen into navigation.

**Architecture:** All three fixes are UI-layer wiring with no new domain logic. BootcampViewModel gains one thin delegation method (`savePreferredDays`). Day-staging state lives in the composable so cancel-without-saving requires no cleanup. Settings navigation adds a route to NavGraph and a menu item to the 3-dot overflow.

**Tech Stack:** Jetpack Compose, Hilt, Room, `combinedClickable` (ExperimentalFoundationApi), `ModalBottomSheet` (ExperimentalMaterial3Api — already opted-in in BootcampScreen)

---

## File Map

| File | Change |
|------|--------|
| `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt` | Add `savePreferredDays(days)` — delegates to `bootcampRepository.updatePreferredDays` |
| `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt` | (1) new `MissedSessionCard` composable + replace static `StatusCard` block; (2) new `DayChipRow` composable + day-sheet bottom sheet + new params through the call chain; (3) `onNavigateToSettings` param + "Program settings" menu item |
| `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt` | Add `BOOTCAMP_SETTINGS` to `Routes`, register `BootcampSettingsScreen` composable, wire `onNavigateToSettings` callback at the `BootcampScreen` call site |

No new files. No DAO/repository/domain changes beyond calling the existing `updatePreferredDays`.

---

## Chunk 1: All three fixes

### Task 1: BootcampViewModel — `savePreferredDays`

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt` (after line 575)

> **Context:** The design spec proposed `cycleDayPreference(day)` and `toggleBlackoutDay(day)` as two new VM methods. **This plan intentionally deviates from that API.** Instead, day-state staging lives entirely in the composable (`remember { mutableStateOf(...) }`) and the VM receives only a single `savePreferredDays(days)` call on "Done". This eliminates the need for intermediate repo writes per tap, makes cancel-without-save free (no cleanup needed), and keeps the VM lean. Do NOT revert to the per-tap pattern.
>
> `bootcampRepository.updatePreferredDays(enrollmentId, newDays, weekNumber)` already exists (BootcampRepository.kt:128). `DayPreference` is referenced with its fully-qualified name throughout this file — follow the same pattern.

- [ ] **Step 1: Add `savePreferredDays` to BootcampViewModel**

In `BootcampViewModel.kt`, after the closing brace of `graduateCurrentGoal()` at line 575, insert:

```kotlin
    fun savePreferredDays(days: List<com.hrcoach.domain.bootcamp.DayPreference>) {
        viewModelScope.launch {
            val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return@launch
            bootcampRepository.updatePreferredDays(enrollment.id, days, _uiState.value.absoluteWeek)
        }
    }
```

- [ ] **Step 2: Compile check**

```bash
.\gradlew.bat :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL with no errors.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt
git commit -m "feat(vm): add savePreferredDays delegation to BootcampViewModel"
```

---

### Task 2: BootcampScreen — MissedSessionCard

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt`

> **Context:** The current block at lines 1122–1127 renders a read-only `StatusCard`. Replace it with a dismissible card that also has a Reschedule CTA.
>
> `missedDismissed` state lives at the top of `ActiveBootcampDashboard` so it persists across recompositions during the session but resets when the screen is re-entered.
>
> The missed session ID is recovered from `uiState.currentWeekDays`: find the first day with `dayOfWeek < todayDow` whose session is not completed. `todayDow` is read from `WeekDayItem.isToday` — no new imports needed.
>
> `onReschedule: (Long) -> Unit` already exists in `ActiveBootcampDashboard`'s signature at line 1075.
>
> `Icons.Default.Close` is not yet imported — add it.

- [ ] **Step 1: Add `Close` icon import**

In `BootcampScreen.kt`, after the line:
```kotlin
import androidx.compose.material.icons.filled.Check
```
add:
```kotlin
import androidx.compose.material.icons.filled.Close
```

- [ ] **Step 2: Add `missedDismissed` state to `ActiveBootcampDashboard`**

`ActiveBootcampDashboard` composable starts at line 1062. Its body opens at line 1080. Inside the `Column` (line 1081), directly before the `// Phase header with overflow menu` comment at line 1088, add:

```kotlin
        var missedDismissed by remember { mutableStateOf(false) }
```

- [ ] **Step 3: Replace the static StatusCard block with MissedSessionCard**

Find and replace the block at lines 1122–1127:

```kotlin
        if (uiState.missedSession) {
            StatusCard(
                title = "Missed session detected",
                detail = "You have at least one earlier session this week that is still incomplete."
            )
        }
```

Replace with:

```kotlin
        if (uiState.missedSession && !missedDismissed) {
            val todayDow = uiState.currentWeekDays.firstOrNull { it.isToday }?.dayOfWeek ?: 8
            val missedId = uiState.currentWeekDays
                .firstOrNull { it.dayOfWeek < todayDow && it.session != null && !it.session.isCompleted }
                ?.session?.sessionId
            MissedSessionCard(
                onDismiss = { missedDismissed = true },
                onReschedule = { if (missedId != null) onReschedule(missedId) }
            )
        }
```

- [ ] **Step 4: Add `MissedSessionCard` private composable**

Append the following private composable after the existing `StatusCard` composable (which is around line 838). Place it directly after `StatusCard`'s closing brace:

```kotlin
@Composable
private fun MissedSessionCard(
    onDismiss: () -> Unit,
    onReschedule: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(end = 32.dp)) {
                Text(
                    text = "Missed session detected",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = CardeaTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "You have at least one earlier session this week that is still incomplete.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTextSecondary
                )
                Spacer(modifier = Modifier.height(14.dp))
                CardeaButton(
                    text = "Reschedule",
                    onClick = onReschedule,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = CardeaTextTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
```

- [ ] **Step 5: Compile check**

```bash
.\gradlew.bat :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt
git commit -m "feat(ui): dismissible MissedSessionCard with Reschedule CTA"
```

---

### Task 3: BootcampScreen — Preferred days bottom sheet

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt`

> **⚠ Deferred compile:** This task adds `onNavigateToSettings` to the `PhaseHeader(...)` call site (Step 5) before Task 4 adds it to `PhaseHeader`'s signature. The file will not compile until Task 4 Step 1 is complete. The compile check is at **Task 4 Step 9** — do not attempt to compile between Task 3 Step 1 and Task 4 Step 8.
>
> **Context:**
>
> **Call chain to wire:** `BootcampScreen` → `ActiveBootcampDashboard` → `PhaseHeader` → `PreferredDaysStrip`. New callbacks flow down; `onSavePreferredDays` bubbles the final staged list back up to the VM.
>
> **Staging pattern:** `showDaySheet` and `stagedDays` live as `remember` state in `ActiveBootcampDashboard`. Tapping a chip updates `stagedDays` locally. "Done" calls `onSavePreferredDays(stagedDays)` and closes the sheet. Dismissing the sheet without Done discards the staged changes.
>
> **Two pure helper functions** (`cycleDayInList`, `toggleBlackoutInList`) are added as private top-level functions in the file. They replicate the logic in `BootcampSettingsViewModel` but return new lists instead of mutating VM state. Both call `level.next()` — confirmed: `DaySelectionLevel.next()` is a member function defined at `domain/bootcamp/DayPreference.kt:9`.
>
> **DayChipRow** is a new private composable. It mirrors the existing `DayChipRow` in `BootcampSettingsScreen.kt` — that one stays private and untouched.
>
> **Imports needed:** `ExperimentalFoundationApi`, `combinedClickable`, `Icons.Default.Star`, `HapticFeedbackType`, `LocalHapticFeedback`, `DaySelectionLevel`, `offset` modifier.

- [ ] **Step 1: Add missing imports**

After the existing imports block in `BootcampScreen.kt` (after line 87, before `@Composable`), add:

```kotlin
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.hrcoach.domain.bootcamp.DaySelectionLevel
```

- [ ] **Step 2: Add `onEditPreferredDays` to `PhaseHeader` and wire it to `PreferredDaysStrip`**

`PhaseHeader` signature at line 1414 currently ends at line 1420. Add `onEditPreferredDays: () -> Unit` as a new parameter:

```kotlin
private fun PhaseHeader(
    uiState: BootcampUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEndProgram: () -> Unit,
    onProgressClick: () -> Unit,
    onGoalClick: () -> Unit,
    onEditPreferredDays: () -> Unit      // ← add this
) {
```

Then find where `PreferredDaysStrip` is called inside `PhaseHeader` (around line 1479):

```kotlin
                    PreferredDaysStrip(
                        preferredDays = uiState.activePreferredDays,
                        modifier = Modifier.padding(top = 8.dp)
                    )
```

Replace with:

```kotlin
                    PreferredDaysStrip(
                        preferredDays = uiState.activePreferredDays,
                        onClick = onEditPreferredDays,
                        modifier = Modifier.padding(top = 8.dp)
                    )
```

- [ ] **Step 3: Update `PreferredDaysStrip` to accept and use `onClick`**

`PreferredDaysStrip` is at line 1213 (lines 1212–1245). Replace the entire function with:
```kotlin
@Composable
private fun PreferredDaysStrip(
    preferredDays: List<DayPreference>,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    val selected = preferredDays.map { it.day }.toSet()
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            labels.forEachIndexed { index, label ->
                val day = index + 1
                val enabled = selected.contains(day)
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .then(
                            if (enabled) Modifier.background(CardeaGradient)
                            else Modifier.background(GlassHighlight)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (enabled) Color.White else CardeaTextTertiary
                    )
                }
            }
        }
        Text(
            text = "Tap to edit",
            style = MaterialTheme.typography.labelSmall,
            color = CardeaTextTertiary,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
```

- [ ] **Step 4: Add `onSavePreferredDays` and `onEditPreferredDays` to `ActiveBootcampDashboard`**

`ActiveBootcampDashboard` signature at line 1062. Add two new parameters after `onGoToManualSetup`:

```kotlin
private fun ActiveBootcampDashboard(
    uiState: BootcampUiState,
    onStartWorkout: (configJson: String) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEndProgram: () -> Unit,
    onBootcampWorkoutStarting: () -> Unit,
    onDismissTierPrompt: () -> Unit,
    onAcceptTierChange: (TierPromptDirection) -> Unit,
    onConfirmIllness: () -> Unit,
    onDismissIllness: () -> Unit,
    onSwapTodayForRest: () -> Unit,
    onGraduateGoal: () -> Unit,
    onReschedule: (Long) -> Unit,
    onProgressClick: () -> Unit,
    onSessionClick: (SessionUiItem) -> Unit,
    onGoalClick: () -> Unit,
    onGoToManualSetup: (() -> Unit)? = null,
    onSavePreferredDays: (List<DayPreference>) -> Unit = {},   // ← add
    onNavigateToSettings: () -> Unit = {}                      // ← add (used in Task 4)
) {
```

- [ ] **Step 5: Add sheet state and `PhaseHeader` callback in `ActiveBootcampDashboard`**

At the top of `ActiveBootcampDashboard`'s `Column` body (after `var missedDismissed` added in Task 2, still inside the `Column`), add:

```kotlin
        var showDaySheet by remember { mutableStateOf(false) }
        var stagedDays by remember(uiState.activePreferredDays) {
            mutableStateOf(uiState.activePreferredDays)
        }
```

> `remember(uiState.activePreferredDays)` re-initialises staging whenever the persisted list changes (e.g. after a save). This prevents stale data if the sheet is opened twice.

Then update the `PhaseHeader` call (around line 1089) to pass the new callback:

```kotlin
        PhaseHeader(
            uiState = uiState,
            onPause = onPause,
            onResume = onResume,
            onEndProgram = onEndProgram,
            onProgressClick = onProgressClick,
            onGoalClick = onGoalClick,
            onEditPreferredDays = { showDaySheet = true },
            onNavigateToSettings = onNavigateToSettings   // ← wired in Task 4; pass through now
        )
```

> Note: `onNavigateToSettings` is added to `PhaseHeader` in Task 4. Add the parameter to the call site now so Task 4 is a smaller diff. It won't compile until Task 4 adds it to PhaseHeader's signature — so do Task 4's PhaseHeader change before the compile check.

- [ ] **Step 6: Hoist `ModalBottomSheet` outside the scroll `Column` in `ActiveBootcampDashboard`**

`ActiveBootcampDashboard` currently contains a single `Column` (line 1081). Wrap the whole body in a `Box` so the sheet is a sibling of the column, not a descendant. Replace the opening of the body:

Current (line 1081):
```kotlin
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
```

Replace with:
```kotlin
    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
```

Then close the `Box` after the `Column`'s closing `}` — i.e., add one more `}` at the very end of `ActiveBootcampDashboard`'s body.

After the `Column`'s closing `}` (after the last item in the column — `ComingUpCard`), add the `ModalBottomSheet` as a sibling inside the `Box`:

```kotlin
        if (showDaySheet) {
            ModalBottomSheet(
                onDismissRequest = { showDaySheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = CardeaBgPrimary
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        text = "Preferred training days",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = CardeaTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap to toggle · Long-press to block a day out",
                        style = MaterialTheme.typography.bodySmall,
                        color = CardeaTextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    // Legend
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        DayLegendChip("run", DaySelectionLevel.AVAILABLE)
                        DayLegendChip("open", DaySelectionLevel.NONE)
                        DayLegendChip("blocked", DaySelectionLevel.BLACKOUT)
                    }
                    DayChipRow(
                        days = stagedDays,
                        onCycleDay = { day -> stagedDays = cycleDayInList(stagedDays, day) },
                        onToggleBlackout = { day -> stagedDays = toggleBlackoutInList(stagedDays, day) }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    CardeaButton(
                        text = "Done",
                        onClick = {
                            onSavePreferredDays(stagedDays)
                            showDaySheet = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    } // closes Box
```

- [ ] **Step 7: Add `DayChipRow` and `DayLegendChip` private composables**

Add these two composables after the existing `PreferredDaysStrip` function in `BootcampScreen.kt`:

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayChipRow(
    days: List<DayPreference>,
    onCycleDay: (Int) -> Unit,
    onToggleBlackout: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val dayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        (1..7).forEach { day ->
            val level = days.firstOrNull { it.day == day }?.level ?: DaySelectionLevel.NONE
            val isSelected = level == DaySelectionLevel.AVAILABLE || level == DaySelectionLevel.LONG_RUN_BIAS
            val isBlackout = level == DaySelectionLevel.BLACKOUT
            val isLongRun = level == DaySelectionLevel.LONG_RUN_BIAS
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .then(
                        when {
                            isBlackout -> Modifier.background(Color(0xFF1C1F26))
                            isSelected -> Modifier.background(CardeaGradient)
                            else       -> Modifier.border(1.dp, GlassBorder, CircleShape)
                        }
                    )
                    .combinedClickable(
                        onClick = { onCycleDay(day) },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleBlackout(day)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = dayLetters[day - 1],
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isBlackout) Color(0xFF8B3A3A) else Color.White
                )
                if (isLongRun) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
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
                        contentDescription = null,
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .then(
                    when (level) {
                        DaySelectionLevel.AVAILABLE,
                        DaySelectionLevel.LONG_RUN_BIAS -> Modifier.background(CardeaGradient)
                        DaySelectionLevel.BLACKOUT      -> Modifier.background(Color(0xFF1C1F26))
                            .border(1.dp, Color(0xFF3D2020), CircleShape)
                        DaySelectionLevel.NONE          -> Modifier.background(GlassHighlight)
                    }
                )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = CardeaTextTertiary
        )
    }
}
```

- [ ] **Step 8: Add `cycleDayInList` and `toggleBlackoutInList` private top-level functions**

Add these after the `DayLegendChip` composable (they are plain functions, not composables):

```kotlin
private fun cycleDayInList(
    days: List<DayPreference>,
    day: Int
): List<DayPreference> {
    val current = days.toMutableList()
    val index = current.indexOfFirst { it.day == day }
    if (index != -1) {
        val nextLevel = current[index].level.next()
        if (nextLevel == DaySelectionLevel.NONE) {
            current.removeAt(index)
        } else {
            current[index] = current[index].copy(level = nextLevel)
        }
    } else {
        current.add(DayPreference(day, DaySelectionLevel.AVAILABLE))
    }
    return current.sortedBy { it.day }
}

private fun toggleBlackoutInList(
    days: List<DayPreference>,
    day: Int
): List<DayPreference> {
    val current = days.toMutableList()
    val index = current.indexOfFirst { it.day == day }
    return if (index != -1 && current[index].level == DaySelectionLevel.BLACKOUT) {
        current.removeAt(index)
        current.sortedBy { it.day }
    } else {
        if (index != -1) current.removeAt(index)
        current.add(DayPreference(day, DaySelectionLevel.BLACKOUT))
        current.sortedBy { it.day }
    }
}
```

- [ ] **Step 9: Wire `onSavePreferredDays` at the `BootcampScreen` call site**

In `BootcampScreen` at line 145, the `ActiveBootcampDashboard(...)` call currently ends before the closing `)`. Add:

```kotlin
                        onSavePreferredDays = { days -> viewModel.savePreferredDays(days) },
```

> Hold off on `onNavigateToSettings` — that is wired in Task 4 Step 4. Remember: do not compile until Task 4 Step 9.

---

### Task 4: Settings navigation

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt`

> **Context:** `BootcampSettingsScreen` (line 80 of its file) already has `onBack: () -> Unit`. It is not registered in NavGraph and has no entry point. The 3-dot menu in `PhaseHeader` currently has two items: "Pause/Resume program" and "End program...".
>
> Add "Program settings" as the first item (before pause/resume) so it's not adjacent to the destructive "End program" option.
>
> `Routes` is defined in NavGraph.kt at line 84.

- [ ] **Step 1: Add `onNavigateToSettings` to `PhaseHeader`**

`PhaseHeader` signature (line 1414):

```kotlin
private fun PhaseHeader(
    uiState: BootcampUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEndProgram: () -> Unit,
    onProgressClick: () -> Unit,
    onGoalClick: () -> Unit,
    onEditPreferredDays: () -> Unit,
    onNavigateToSettings: () -> Unit    // ← add this
) {
```

- [ ] **Step 2: Add "Program settings" menu item to the 3-dot `DropdownMenu` in `PhaseHeader`**

The `DropdownMenu` currently starts at line 1510. Inside it, before the `if (uiState.isPaused)` block, add:

```kotlin
                        DropdownMenuItem(
                            text = { Text("Program settings") },
                            onClick = {
                                menuExpanded = false
                                onNavigateToSettings()
                            }
                        )
```

So the full menu order is: Program settings → Pause/Resume program → End program...

- [ ] **Step 3: Add `onNavigateToSettings` to `BootcampScreen` composable signature**

`BootcampScreen` at line 89:

```kotlin
fun BootcampScreen(
    onStartWorkout: (configJson: String) -> Unit,
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit = {},    // ← add
    onGoToManualSetup: (() -> Unit)? = null,
    viewModel: BootcampViewModel = hiltViewModel()
) {
```

- [ ] **Step 4: Wire `onNavigateToSettings` through to `ActiveBootcampDashboard` in `BootcampScreen`**

In the `ActiveBootcampDashboard(...)` call at line 145, add:

```kotlin
                        onNavigateToSettings = onNavigateToSettings,
```

- [ ] **Step 5: Add `BOOTCAMP_SETTINGS` route to `Routes` in NavGraph**

In `NavGraph.kt`, the `Routes` object at line 84:

```kotlin
object Routes {
    const val SPLASH           = "splash"
    const val HOME             = "home"
    const val SETUP            = "setup"
    const val WORKOUT          = "workout"
    const val PROGRESS         = "progress"
    const val HISTORY          = "history"
    const val ACCOUNT          = "account"
    const val BOOTCAMP         = "bootcamp"
    const val BOOTCAMP_SETTINGS = "bootcampSettings"    // ← add
    const val HISTORY_DETAIL   = "history/{workoutId}"
    const val POST_RUN_SUMMARY = "postrun/{workoutId}?fresh={fresh}"

    fun historyDetail(workoutId: Long): String = "history/$workoutId"
    fun postRunSummary(workoutId: Long, fresh: Boolean = false): String = "postrun/$workoutId?fresh=$fresh"
}
```

- [ ] **Step 6: Import `BootcampSettingsScreen` in NavGraph**

In `NavGraph.kt`, after the existing bootcamp imports:
```kotlin
import com.hrcoach.ui.bootcamp.BootcampScreen
```
add:
```kotlin
import com.hrcoach.ui.bootcamp.BootcampSettingsScreen
```

- [ ] **Step 7: Register `BootcampSettingsScreen` composable in the NavHost**

In `NavGraph.kt`, after the closing `}` of the `Routes.BOOTCAMP` composable block (after line 454), add:

> `defaultEnter` and `defaultExit` are private helper functions defined at lines 577–589 of `NavGraph.kt`. They are used by every other route in the file — use them here too.

```kotlin
            composable(
                route = Routes.BOOTCAMP_SETTINGS,
                enterTransition = { defaultEnter(1) },
                exitTransition = { defaultExit(1) }
            ) {
                BootcampSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
```

- [ ] **Step 8: Wire `onNavigateToSettings` at the `BootcampScreen` call site in NavGraph**

In the `Routes.BOOTCAMP` composable block (line 429), `BootcampScreen(...)` currently has:

```kotlin
                BootcampScreen(
                    onStartWorkout = { configJson -> ... },
                    onBack = { navController.popBackStack() },
                    onGoToManualSetup = { ... }
                )
```

Add `onNavigateToSettings`:

```kotlin
                BootcampScreen(
                    onStartWorkout = { configJson -> ... },
                    onBack = { navController.popBackStack() },
                    onNavigateToSettings = {
                        navController.navigate(Routes.BOOTCAMP_SETTINGS)
                    },
                    onGoToManualSetup = { ... }
                )
```

- [ ] **Step 9: Compile check**

```bash
.\gradlew.bat :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. This is the first compile since Task 3 Step 9 — it covers all three tasks together.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt
git add app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt
git commit -m "feat(ui): preferred days bottom sheet, settings nav entry point"
```

---

## Manual verification checklist

After all tasks pass compile, verify on device/emulator:

1. **Missed session card**: with an active enrollment that has a past-uncompleted session this week — card shows ✕ and "Reschedule"; ✕ hides it for the session; Reschedule opens `RescheduleBottomSheet`
2. **Preferred days strip**: tap the strip in the phase header → bottom sheet opens with correct days highlighted; toggle chips; long-press sets blocked; "Done" saves and reopens sheet shows updated state; dismiss (swipe down) without Done → no change
3. **Settings navigation**: 3-dot menu shows "Program settings" as first item; tapping it navigates to `BootcampSettingsScreen` (title "Program Settings"); back button returns to dashboard
