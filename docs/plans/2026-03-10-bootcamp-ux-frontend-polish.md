# Bootcamp UX Frontend Polish Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 5 user-facing affordance and feedback gaps in the Bootcamp UI. Each task requires visual/interaction design judgment — shapes, animations, copy, and interaction patterns must match the Cardea glass-morphic design system.

**Architecture:** All changes are contained in Compose UI layers (`ui/bootcamp/`, `ui/postrun/`). No domain or data layer changes needed. Uses existing `GlassCard`, `CardeaButton`, `ModalBottomSheet`, and theme tokens.

**Tech Stack:** Jetpack Compose, Material 3, Cardea design system (`ui/theme/Color.kt`)

**Design system rules (MUST follow):**
- Use `GlassCard` for all card surfaces
- Use tokenized text colors: `CardeaTextPrimary`, `CardeaTextSecondary`, `CardeaTextTertiary`
- Use `CardeaGradient` / `GradientBlue` / `GradientCyan` for branded accents
- Never use ad-hoc `Color.White.copy(alpha = X)` — use tokens
- `CardeaGradient` is a fixed 4-stop brand gradient — do not alter or recreate it

---

## Chunk 1: Interaction Affordance Fixes

### Task 1: HRR Countdown Timer UI (Finding 1 — UI portion)

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryViewModel.kt`

**Context:** The existing `HrrCooldownCard()` is static text telling the user to walk for 120 seconds. There is no timer. The user has no feedback on progress and no indication when the measurement window ends.

**NOTE:** The BLE disconnect bug (Finding 1 logic portion) that prevents actual HR measurement during cooldown is a separate issue and out of scope for this plan. This task focuses on making the *existing UI* useful — specifically, giving the user a live countdown and clear phase labeling, so the experience is no longer a dead-end even before the BLE fix lands.

**Design decisions:**
- Linear progress bar (horizontal) + large countdown text — matches Cardea's data-dense, no-chrome aesthetic better than a circular ring (which is already used for the HR ring in the active workout)
- "Walk slowly" instruction stays prominent at top
- When timer expires: show "Measurement window closed — check history for HRR1 data" (since BLE may or may not have captured data depending on timing)
- Color: `GradientCyan` for the progress fill (cool/rest tone)

- [ ] **Step 1: Add hrrCooldownSecondsRemaining to PostRunSummaryUiState**

In `PostRunSummaryViewModel.kt`, add to `PostRunSummaryUiState`:

```kotlin
data class PostRunSummaryUiState(
    // ... existing fields ...
    val isHrrActive: Boolean = false,
    val hrrSecondsRemaining: Int = 120   // ADD
)
```

- [ ] **Step 2: Add ViewModel tick function**

In `PostRunSummaryViewModel.kt`, add a timer that ticks down when `isHrrActive`:

```kotlin
init {
    load()
    startHrrCountdownIfNeeded()
}

private fun startHrrCountdownIfNeeded() {
    viewModelScope.launch {
        // Wait for load to settle
        _uiState.collect { state ->
            if (state.isHrrActive && !state.isLoading) {
                // Start the countdown
                runCatching {
                    for (remaining in 119 downTo 0) {
                        kotlinx.coroutines.delay(1_000L)
                        _uiState.update { it.copy(hrrSecondsRemaining = remaining) }
                    }
                    // Countdown finished — close the HRR window
                    _uiState.update { it.copy(isHrrActive = false) }
                }
                return@collect  // stop observing after countdown starts
            }
        }
    }
}
```

Add import: `import kotlinx.coroutines.flow.first`

- [ ] **Step 3: Replace HrrCooldownCard composable**

In `PostRunSummaryScreen.kt`, replace the existing `HrrCooldownCard()` composable with:

```kotlin
@Composable
private fun HrrCooldownCard(secondsRemaining: Int) {
    val progress = secondsRemaining / 120f
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsWalk,
                    contentDescription = null,
                    tint = GradientCyan,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Recovery Walk",
                    style = MaterialTheme.typography.titleMedium,
                    color = CardeaTextPrimary
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${secondsRemaining}s",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = GradientCyan
                )
            }

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(GlassBorder)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(listOf(GradientCyan, GradientBlue))
                        )
                )
            }

            Text(
                text = "Walk slowly — we're measuring your recovery heart rate.",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTextSecondary
            )
        }
    }
}
```

Add needed imports:
```kotlin
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import com.hrcoach.ui.theme.GradientCyan
import com.hrcoach.ui.theme.CardeaTextPrimary
import com.hrcoach.ui.theme.GlassBorder
```

- [ ] **Step 4: Update call site in PostRunSummaryScreen**

Change:
```kotlin
// OLD
if (uiState.isHrrActive) {
    HrrCooldownCard()
}

// NEW
if (uiState.isHrrActive) {
    HrrCooldownCard(secondsRemaining = uiState.hrrSecondsRemaining)
}
```

- [ ] **Step 5: Add "closed" state card**

After the `HrrCooldownCard` block, add a subtle closed-state card for the first few seconds after the window closes (optional, improves clarity):

```kotlin
// No additional state needed — once isHrrActive = false the card disappears.
// The bootcampProgressLabel or existing summary data will fill the space naturally.
```

- [ ] **Step 6: Compile check**

```bash
.\gradlew.bat :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt \
        app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryViewModel.kt
git commit -m "feat(postrun): add live 120s countdown to HRR cooldown card"
```

---

### Task 2: Tappable Phase Progress Bar — Phase Detail Sheet (Finding 6)

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt`

**Context:** The `PhaseHeader` composable contains a horizontal `Box` progress bar that shows training plan progress. It looks tappable (prominent, bar shape) but has no `clickable` modifier. Users who tap it expect to see phase/week breakdown.

**Design decisions:**
- Tap opens a `ModalBottomSheet` (already used in `BootcampScreen` for reschedule)
- Sheet content: week number, phase name, sessions per week, weeks remaining in phase, total weeks remaining
- Use existing `GlassCard` inside the sheet
- Sheet title: "Your Training Plan"
- No new navigation — this is an in-place information panel

- [ ] **Step 1: Add showPhaseDetail state**

In `BootcampScreen.kt`, find the `PhaseHeader` call site. Add local state:

```kotlin
var showPhaseDetail by remember { mutableStateOf(false) }
```

- [ ] **Step 2: Add clickable to the progress bar Box in PhaseHeader**

In `BootcampScreen.kt`, find the `PhaseHeader` composable. Locate the `Box` that renders the progress fill (search for the filled gradient Box inside PhaseHeader). Add a `Modifier.clickable` to the outer progress container Box.

Since PhaseHeader is a private composable, add an `onProgressClick: () -> Unit` parameter:

```kotlin
// Change signature from:
@Composable
private fun PhaseHeader(uiState: BootcampUiState, ...) { ... }

// To:
@Composable
private fun PhaseHeader(
    uiState: BootcampUiState,
    onProgressClick: () -> Unit,
    ...
) { ... }
```

Inside PhaseHeader, on the progress bar container `Box`, add:

```kotlin
modifier = Modifier
    .fillMaxWidth()
    .height(6.dp)
    .clip(RoundedCornerShape(3.dp))
    .clickable(onClick = onProgressClick)   // ADD
    .background(GlassBorder)
```

Also add a subtle visual hint that it's tappable — a small info icon next to the progress label:

```kotlin
// After the phase progress row label text, add:
Icon(
    imageVector = Icons.Outlined.Info,  // or Icons.Default.ChevronRight
    contentDescription = "View plan details",
    tint = CardeaTextTertiary,
    modifier = Modifier.size(14.dp)
)
```

- [ ] **Step 3: Create PhaseDetailSheet composable**

In `BootcampScreen.kt`, add:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhaseDetailSheet(
    uiState: BootcampUiState,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardeaBgPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Your Training Plan",
                style = MaterialTheme.typography.titleLarge,
                color = CardeaTextPrimary,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhaseDetailRow("Phase", uiState.currentPhase?.name
                        ?.replace('_', ' ')
                        ?.lowercase()
                        ?.replaceFirstChar { it.uppercase() } ?: "—")
                    PhaseDetailRow("Overall week", "${uiState.absoluteWeek} of ${uiState.totalWeeks}")
                    PhaseDetailRow("Week in phase", "${uiState.weekInPhase}")
                    if (uiState.isRecoveryWeek) {
                        PhaseDetailRow("This week", "Recovery week — reduced load")
                    } else {
                        PhaseDetailRow("Runs this week", "${uiState.currentWeekSessions.size} scheduled")
                    }
                    uiState.weeksUntilNextRecovery
                        ?.takeIf { it > 0 }
                        ?.let { PhaseDetailRow("Next recovery", "in $it week${if (it == 1) "" else "s"}") }
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Close", color = GradientBlue)
            }
        }
    }
}

@Composable
private fun PhaseDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = CardeaTextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = CardeaTextPrimary)
    }
}
```

- [ ] **Step 4: Wire the sheet into BootcampScreen**

In the main `BootcampScreen` composable body, after `var showPhaseDetail`:

```kotlin
if (showPhaseDetail) {
    PhaseDetailSheet(
        uiState = uiState,
        onDismiss = { showPhaseDetail = false }
    )
}
```

Pass `onProgressClick = { showPhaseDetail = true }` to `PhaseHeader`.

- [ ] **Step 5: Compile check**

```bash
.\gradlew.bat :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt
git commit -m "feat(bootcamp): tappable phase progress bar opens plan detail sheet"
```

---

### Task 3: Tappable Session List Rows (Finding 7)

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampUiState.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt`

**Context:** The "This Week" `SessionRow` items look navigable (styled like history list rows) but have no `clickable` modifier. Tapping does nothing.

**Design decisions:**
- **Completed sessions** (have a `completedWorkoutId`): tap → navigate to `historyDetail(completedWorkoutId)`. Rewarding — lets them revisit their run.
- **Scheduled/upcoming sessions**: tap → show a `SessionDetailSheet` with target zones, duration, and session type description. No navigation needed — info-only.
- **Visual affordance**: add a trailing `>` chevron icon to each row to signal tappability. Completed rows get a filled circle check (already shown) + chevron.

SessionDetailSheet needs: session type name, duration, target HR zone description (if available). This is display-only from `SessionUiItem`.

**Note:** `SessionUiItem` currently lacks `completedWorkoutId`. Add it.

- [ ] **Step 1: Add completedWorkoutId to SessionUiItem**

In `BootcampUiState.kt`, find `SessionUiItem`:

```kotlin
// ADD completedWorkoutId field
data class SessionUiItem(
    val dayLabel: String,
    val typeName: String,
    val minutes: Int,
    val isCompleted: Boolean,
    val isToday: Boolean,
    val sessionId: Long? = null,
    val completedWorkoutId: Long? = null   // ADD
)
```

- [ ] **Step 2: Populate completedWorkoutId in BootcampViewModel**

In `BootcampViewModel.kt`, in the `sessionItems` mapping inside `refreshFromEnrollment`:

```kotlin
val sessionItems = scheduledSessions.map { session ->
    SessionUiItem(
        dayLabel = dayLabelFor(session.dayOfWeek),
        typeName = sessionTypeDisplayName(session.sessionType, session.presetId),
        minutes = session.targetMinutes,
        isCompleted = session.status != BootcampSessionEntity.STATUS_SCHEDULED,
        isToday = session.dayOfWeek == today,
        sessionId = session.id,
        completedWorkoutId = session.completedWorkoutId   // ADD
    )
}
```

- [ ] **Step 3: Add SessionDetailSheet composable**

In `BootcampScreen.kt`, add:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionDetailSheet(
    session: SessionUiItem,
    onDismiss: () -> Unit
) {
    val zoneDescription = when {
        session.typeName.contains("Easy", ignoreCase = true) ->
            "Zone 2 · Conversational pace · HR ~65–75% max"
        session.typeName.contains("Tempo", ignoreCase = true) ->
            "Zone 3 · Comfortably hard · HR ~80–87% max"
        session.typeName.contains("Interval", ignoreCase = true) ->
            "Zone 5 · High intensity bursts · HR ~90–95% max"
        session.typeName.contains("Long", ignoreCase = true) ->
            "Zone 2 · Steady endurance · HR ~65–75% max"
        else -> "Follow your target heart rate zone"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardeaBgPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = session.typeName,
                style = MaterialTheme.typography.titleLarge,
                color = CardeaTextPrimary
            )
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhaseDetailRow("Duration", "${session.minutes} min")
                    PhaseDetailRow("Target", zoneDescription)
                    if (session.isToday) {
                        PhaseDetailRow("Scheduled", "Today")
                    } else {
                        PhaseDetailRow("Scheduled", session.dayLabel)
                    }
                }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Close", color = GradientBlue)
            }
        }
    }
}
```

- [ ] **Step 4: Add local state and wire SessionRow**

In the `BootcampScreen` composable body:

```kotlin
var selectedSession by remember { mutableStateOf<SessionUiItem?>(null) }
```

Show the sheet when selected:
```kotlin
selectedSession?.let { session ->
    if (session.isCompleted && session.completedWorkoutId != null) {
        // Navigate to history — handled via callback, see step 5
        LaunchedEffect(session) {
            onSessionHistoryClick(session.completedWorkoutId!!)
            selectedSession = null
        }
    } else {
        SessionDetailSheet(
            session = session,
            onDismiss = { selectedSession = null }
        )
    }
}
```

- [ ] **Step 5: Add onSessionHistoryClick to BootcampScreen**

Add the callback to `BootcampScreen`'s signature:

```kotlin
@Composable
fun BootcampScreen(
    onStartWorkout: (configJson: String) -> Unit,
    onBack: () -> Unit,
    onGoToManualSetup: (() -> Unit)? = null,
    onGoToSessionHistory: ((workoutId: Long) -> Unit)? = null,   // ADD
    viewModel: BootcampViewModel = hiltViewModel()
)
```

- [ ] **Step 6: Add clickable + chevron to SessionRow**

In `BootcampScreen.kt`, find the `SessionRow` composable. Add `onClick: () -> Unit` parameter and `Modifier.clickable`:

```kotlin
@Composable
private fun SessionRow(
    session: SessionUiItem,
    onClick: () -> Unit           // ADD
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)   // ADD
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ... existing content ...

        Spacer(Modifier.weight(1f))

        // ADD chevron
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,   // or KeyboardArrowRight
            contentDescription = null,
            tint = CardeaTextTertiary,
            modifier = Modifier.size(16.dp)
        )
    }
}
```

Update all `SessionRow` call sites to pass `onClick = { selectedSession = session }`.

- [ ] **Step 7: Wire NavGraph to pass onGoToSessionHistory**

In `NavGraph.kt`, in the `BOOTCAMP` composable:

```kotlin
BootcampScreen(
    onStartWorkout = { ... },
    onBack = { navController.popBackStack() },
    onGoToManualSetup = { ... },
    onGoToSessionHistory = { workoutId ->   // ADD
        navController.navigate(Routes.historyDetail(workoutId))
    }
)
```

- [ ] **Step 8: Compile check**

```bash
.\gradlew.bat :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt \
        app/src/main/java/com/hrcoach/ui/bootcamp/BootcampUiState.kt \
        app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt \
        app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt
git commit -m "feat(bootcamp): tappable session rows — completed navigates to history, upcoming shows detail"
```

---

## Chunk 2: Discoverability Improvements

### Task 4: Expandable Phase Lookahead (Finding 9)

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampUiState.kt`

**Context:** `ComingUpCard` shows 2 lookahead weeks hardcoded. Users on Week 2 can't see what Week 8 (taper/peak) looks like. Adding a "View full plan" button expands the lookahead to all remaining weeks.

**Design decisions:**
- "View full plan" TextButton below the `ComingUpCard`
- Tap → show a `FullPlanSheet` (ModalBottomSheet) with all remaining weeks, scrollable
- Each week row shows: week number, phase, session count, total minutes, recovery flag
- Collapsed by default (2 weeks) — no change to default UX

- [ ] **Step 1: Add fullLookaheadWeeks to BootcampUiState**

In `BootcampUiState.kt`:

```kotlin
data class BootcampUiState(
    // ... existing fields ...
    val upcomingWeeks: List<UpcomingWeekItem> = emptyList(),   // existing (2 weeks)
    val fullPlanWeeks: List<UpcomingWeekItem> = emptyList()    // ADD (all remaining weeks)
)
```

- [ ] **Step 2: Populate fullPlanWeeks in BootcampViewModel**

In `BootcampViewModel.kt`, in `refreshFromEnrollment`, after the existing `upcomingWeeks` calculation:

```kotlin
val remainingWeeks = (engine.totalWeeks - engine.absoluteWeek).coerceAtLeast(0)
val fullPlanWeeks = engine.lookaheadWeeks(remainingWeeks).map { lookahead ->
    UpcomingWeekItem(
        weekNumber = lookahead.weekNumber,
        isRecoveryWeek = lookahead.isRecovery,
        sessions = lookahead.sessions.map { session ->
            SessionUiItem(
                dayLabel = "",
                typeName = sessionTypeDisplayName(session.type.name, session.presetId),
                minutes = session.minutes,
                isCompleted = false,
                isToday = false
            )
        }
    )
}
```

Add `fullPlanWeeks = fullPlanWeeks` to the `_uiState.value = BootcampUiState(...)` block.

- [ ] **Step 3: Create FullPlanSheet composable**

In `BootcampScreen.kt`:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullPlanSheet(
    uiState: BootcampUiState,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardeaBgPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Full Training Plan",
                style = MaterialTheme.typography.titleLarge,
                color = CardeaTextPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "${uiState.goal?.name?.replace('_', ' ')} · ${uiState.totalWeeks} weeks total",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTextSecondary,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            uiState.fullPlanWeeks.forEach { week ->
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (week.isRecoveryWeek) "Week ${week.weekNumber} · Recovery" else "Week ${week.weekNumber}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (week.isRecoveryWeek) GradientCyan else CardeaTextPrimary
                            )
                            Text(
                                text = "${week.sessions.size} run${if (week.sessions.size == 1) "" else "s"} · ${week.sessions.sumOf { it.minutes }} min total",
                                style = MaterialTheme.typography.bodySmall,
                                color = CardeaTextSecondary
                            )
                        }
                        if (week.isRecoveryWeek) {
                            Text("↓", color = GradientCyan, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Close", color = GradientBlue)
            }
        }
    }
}
```

- [ ] **Step 4: Add "View full plan" button + sheet trigger**

In `BootcampScreen.kt`, after the `ComingUpCard(...)` call:

```kotlin
var showFullPlan by remember { mutableStateOf(false) }

// After ComingUpCard:
if (uiState.fullPlanWeeks.size > 2) {
    TextButton(
        onClick = { showFullPlan = true },
        modifier = Modifier.align(Alignment.CenterHorizontally)
    ) {
        Text(
            text = "View full plan (${uiState.totalWeeks - uiState.absoluteWeek} weeks remaining)",
            color = GradientBlue,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

if (showFullPlan) {
    FullPlanSheet(
        uiState = uiState,
        onDismiss = { showFullPlan = false }
    )
}
```

- [ ] **Step 5: Compile check**

```bash
.\gradlew.bat :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt \
        app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt \
        app/src/main/java/com/hrcoach/ui/bootcamp/BootcampUiState.kt
git commit -m "feat(bootcamp): View full plan sheet shows all remaining weeks from lookahead"
```

---

### Task 5: Tap Goal Text to Navigate to Settings (Finding 10)

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt`

**Context:** The `PhaseHeader` prominently displays the user's goal (e.g., "Half Marathon") and phase name. Users expect tapping the goal label to navigate to Bootcamp settings where they can change it. Currently the only path is through the `MoreVert` overflow menu.

**Design decisions:**
- Add a `clickable` modifier to the goal label `Text`
- Add a small `Icons.Default.Edit` (14dp) trailing icon to visually signal editability
- Navigate to `BootcampSettingsScreen` (already exists at Routes.BOOTCAMP_SETTINGS or similar)

**Note:** Check whether a `BootcampSettingsScreen` route exists first.

- [ ] **Step 1: Check existing settings route**

In `NavGraph.kt`, search for `BOOTCAMP_SETTINGS` or similar. If not present, check `BootcampSettingsScreen.kt` path.

```bash
# Quick check — look for the route
grep -r "BootcampSettings" app/src/main/java/com/hrcoach/ui/navigation/
```

If `BootcampSettingsScreen` exists but has no route, add a route in the next step.

- [ ] **Step 2: Add BOOTCAMP_SETTINGS route if missing**

In `NavGraph.kt`, `Routes` object:

```kotlin
object Routes {
    // ... existing ...
    const val BOOTCAMP_SETTINGS = "bootcamp_settings"   // ADD IF MISSING
}
```

Add composable entry in `NavHost` (if missing):

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

Add import: `import com.hrcoach.ui.bootcamp.BootcampSettingsScreen`

- [ ] **Step 3: Add onGoToSettings callback to BootcampScreen**

```kotlin
@Composable
fun BootcampScreen(
    onStartWorkout: (configJson: String) -> Unit,
    onBack: () -> Unit,
    onGoToManualSetup: (() -> Unit)? = null,
    onGoToSessionHistory: ((workoutId: Long) -> Unit)? = null,
    onGoToSettings: (() -> Unit)? = null,    // ADD
    viewModel: BootcampViewModel = hiltViewModel()
)
```

- [ ] **Step 4: Make goal label tappable in PhaseHeader**

`PhaseHeader` needs `onGoalClick: () -> Unit`:

```kotlin
@Composable
private fun PhaseHeader(
    uiState: BootcampUiState,
    onProgressClick: () -> Unit,
    onGoalClick: () -> Unit,      // ADD
    ...
) {
```

Find the `Text` rendering the goal name (e.g., `uiState.goal?.name`) and add:

```kotlin
Row(
    modifier = Modifier.clickable(onClick = onGoalClick),   // ADD ROW WRAPPER
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp)
) {
    Text(
        text = goalLabel,
        style = MaterialTheme.typography.titleMedium,
        color = CardeaTextPrimary
    )
    Icon(
        imageVector = Icons.Default.Edit,
        contentDescription = "Edit goal",
        tint = CardeaTextTertiary,
        modifier = Modifier.size(14.dp)
    )
}
```

- [ ] **Step 5: Wire callbacks in BootcampScreen**

Pass `onGoalClick = { onGoToSettings?.invoke() }` to `PhaseHeader`.

- [ ] **Step 6: Wire in NavGraph**

```kotlin
BootcampScreen(
    onStartWorkout = { ... },
    onBack = { navController.popBackStack() },
    onGoToManualSetup = { ... },
    onGoToSessionHistory = { workoutId -> ... },
    onGoToSettings = {                           // ADD
        navController.navigate(Routes.BOOTCAMP_SETTINGS) {
            launchSingleTop = true
        }
    }
)
```

- [ ] **Step 7: Compile check**

```bash
.\gradlew.bat :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt \
        app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt
git commit -m "feat(bootcamp): tap goal label navigates to settings screen"
```

---

## Final verification

- [ ] **Full test suite**

```bash
.\gradlew.bat :app:testDebugUnitTest
```
Expected: All tests pass

- [ ] **Full compile**

```bash
.\gradlew.bat :app:assembleDebug
```
Expected: BUILD SUCCESSFUL

---

## Task Dependency Order

```
Task 1 (HRR timer)       — independent
Task 2 (progress bar)    — independent
Task 3 (session rows)    — independent (but see SessionUiItem change)
Task 4 (lookahead)       — independent
Task 5 (goal tap)        — independent
```

All 5 tasks are fully independent and can be parallelized.
