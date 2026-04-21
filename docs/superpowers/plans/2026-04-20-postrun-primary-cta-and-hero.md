# PostRun Primary CTA + Hero Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace PostRun's weak Row-of-two-equal-buttons + solid-pink "Run Complete" label with (a) a single pinned `CardeaButton("Done")` at the bottom of the screen and (b) a proper `CardeaGradient`-filled hero headline that aligns with Home's `PulseHero` pattern.

**Architecture:** Convert the `Scaffold` `content` from a single `verticalScroll` Column into a `Scaffold { Column { scrollable content (weight 1f) + pinned actionRow } }`. Remove the "View Route" button (duplicate of `onDone` → HistoryDetail). Replace the pink `Text("Run Complete")` with a gradient-fill Text using the `compositingStrategy = Offscreen` + `BlendMode.SrcIn` pattern already used by `PulseHero.kt:271-279`.

**Tech Stack:** Kotlin, Jetpack Compose, existing `CardeaButton`, `CardeaTheme.colors.gradient`, `BlendMode.SrcIn` gradient text pattern.

---

## File Structure

- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt` — hero + pinned footer refactor, remove "View Route" button, delete `onViewHistory` param.
- Modify: `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt` — drop `onViewHistory` wiring around line 648 (still kept on `HistoryDetailScreen` — only the PostRun callsite changes).

**No unit tests** — this is layout-only. Verification is via build + manual device check + `preview_screenshot` if preview tooling is connected.

---

## Task 1: Extract hero composable with gradient text

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt`

- [ ] **Step 1.1: Add a private `RunCompleteHero` composable**

Insert immediately after `PostRunContentState` enum at `PostRunSummaryScreen.kt:81`:

```kotlin
@Composable
private fun RunCompleteHero(
    visible: Boolean,
    distanceText: String,
    modifier: Modifier = Modifier
) {
    val gradient = CardeaTheme.colors.gradient  // CardeaGradient 4-stop
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(500)) +
            scaleIn(initialScale = 0.92f, animationSpec = tween(500)),
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = "RUN COMPLETE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontSize = 11.sp
                ),
                color = CardeaTheme.colors.textTertiary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = distanceText,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 44.sp,
                    letterSpacing = (-0.5).sp
                ),
                color = CardeaTheme.colors.textPrimary,
                modifier = Modifier
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(brush = gradient, blendMode = BlendMode.SrcIn)
                    }
            )
        }
    }
}
```

- [ ] **Step 1.2: Add required imports at top of file**

Add these imports (merge with existing imports — don't duplicate):

```kotlin
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
```

- [ ] **Step 1.3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Unused warning for `RunCompleteHero` is acceptable — it's used in Task 2.

- [ ] **Step 1.4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt
git commit -m "feat(postrun): add gradient RunCompleteHero composable"
```

---

## Task 2: Pin primary Done button and wire the hero

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt`

- [ ] **Step 2.1: Restructure the `CONTENT` branch to separate scroll body from pinned footer**

Find the `PostRunContentState.CONTENT ->` branch (starts `PostRunSummaryScreen.kt:192`). Replace the entire `Column { ... }` block with:

```kotlin
PostRunContentState.CONTENT -> {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RunCompleteHero(
                visible = showCelebration,
                distanceText = uiState.distanceText
            )

            if (uiState.newAchievements.isNotEmpty()) {
                NewAchievementsSection(achievements = uiState.newAchievements)
            }

            if (isHrrActive) {
                HrrCooldownCard(endTimeMs = uiState.workoutEndTimeMs)
            }

            uiState.hrMaxDelta?.let { (oldMax, newMax) ->
                HrMaxUpdatedCard(oldMax = oldMax, newMax = newMax)
            }

            uiState.bootcampProgressLabel
                ?.takeIf { it.isNotBlank() }
                ?.let { progressLabel ->
                    BootcampContextCard(
                        progressLabel = progressLabel,
                        weekComplete = uiState.bootcampWeekComplete
                    )
                }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryStatCard(
                    title = stringResource(R.string.label_duration),
                    value = uiState.durationText,
                    icon = Icons.Default.Timer,
                    modifier = Modifier.weight(1f)
                )
                SummaryStatCard(
                    title = stringResource(R.string.label_avg_hr),
                    value = uiState.avgHrText,
                    icon = Icons.Default.Favorite,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = "Compared to Similar Runs",
                style = MaterialTheme.typography.titleLarge,
                color = CardeaTheme.colors.textPrimary
            )

            if (uiState.comparisons.isEmpty()) {
                GlassCard {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Insights,
                            contentDescription = null,
                            tint = CardeaTheme.colors.textSecondary
                        )
                        Column {
                            Text(
                                text = "Not enough data yet.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = CardeaTheme.colors.textPrimary
                            )
                            Text(
                                text = "Complete a few similar sessions to unlock this view.",
                                style = MaterialTheme.typography.bodySmall,
                                color = CardeaTheme.colors.textSecondary
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Based on ${uiState.similarRunCount} similar sessions",
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTheme.colors.textSecondary
                )
                uiState.comparisons.forEach { item ->
                    GlassCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = CardeaTheme.colors.textPrimary
                                )
                                Text(
                                    text = item.value,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = CardeaTheme.colors.textPrimary
                                )
                                item.insight?.let { insight ->
                                    Text(
                                        text = insight,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CardeaTheme.colors.textSecondary
                                    )
                                }
                            }
                            item.delta?.let { delta ->
                                Text(
                                    text = delta,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when (item.positive) {
                                        true -> ZoneGreen
                                        false -> ZoneRed
                                        null -> CardeaTheme.colors.textSecondary
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.showSoundsRecap) {
                SoundsHeardSection(
                    counts = uiState.cueCounts,
                    onSeeLibrary = onNavigateToSoundLibrary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Pinned action footer — sits below the scroll area, always visible.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardeaBgPrimary.copy(alpha = 0.92f))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            CardeaButton(
                text = stringResource(R.string.button_done),
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                cornerRadius = 14.dp
            )
        }
    }
}
```

- [ ] **Step 2.2: Drop the distance stat card from the 3-up row**

Rationale: the hero now shows distance prominently. The 3-card row is reduced to 2 (duration + avg HR) as shown in Step 2.1's code block. No further change needed — the block above already does this.

- [ ] **Step 2.3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2.4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt
git commit -m "feat(postrun): pin Done CTA and use gradient hero, drop duplicate View Route"
```

---

## Task 3: Remove the now-unused `onViewHistory` param

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt`

- [ ] **Step 3.1: Delete `onViewHistory` from `PostRunSummaryScreen` signature**

At `PostRunSummaryScreen.kt:87`, change:

```kotlin
fun PostRunSummaryScreen(
    workoutId: Long,
    onViewHistory: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
    onNavigateToSoundLibrary: () -> Unit = {},
    viewModel: PostRunSummaryViewModel = hiltViewModel()
)
```

to:

```kotlin
fun PostRunSummaryScreen(
    workoutId: Long,
    onDone: () -> Unit,
    onBack: () -> Unit,
    onNavigateToSoundLibrary: () -> Unit = {},
    viewModel: PostRunSummaryViewModel = hiltViewModel()
)
```

- [ ] **Step 3.2: Delete the `onViewHistory` wiring in NavGraph**

At `NavGraph.kt:646`, replace the `PostRunSummaryScreen(...)` call block:

Find:
```kotlin
PostRunSummaryScreen(
    workoutId = workoutId,
    onViewHistory = {
        navController.navigate(Routes.historyDetail(workoutId)) {
            popUpTo(Routes.HISTORY) { inclusive = false }
            launchSingleTop = true
        }
    },
    onDone = {
```

Replace with:
```kotlin
PostRunSummaryScreen(
    workoutId = workoutId,
    onDone = {
```

(Leave everything from `onDone = {` onward unchanged.)

- [ ] **Step 3.3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3.4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt \
        app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt
git commit -m "refactor(postrun): remove unused onViewHistory callback"
```

---

## Task 4: Device verification

- [ ] **Step 4.1: Build and install**

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 4.2: Complete a short run via simulation**

Account → Simulation → Start a 2-min sim run. Stop it.
Observe the PostRun screen:
1. Hero reads "RUN COMPLETE" (small grey) over a large gradient distance value.
2. Scroll body has 2 stat cards (duration, avg HR) — no distance stat card.
3. Done button is pinned at the bottom, always visible while scrolling.
4. Tapping Done navigates to HistoryDetail (or Bootcamp dashboard for bootcamp runs).

- [ ] **Step 4.3: Capture a screenshot for the PR**

```bash
adb shell screencap //sdcard/postrun.png
adb pull //sdcard/postrun.png /tmp/postrun.png
```

- [ ] **Step 4.4: Run full unit test suite to confirm no regressions**

Run: `./gradlew testDebugUnitTest`
Expected: ALL PASS.

---

## Self-review checklist

- [x] Existing imports (`AnimatedVisibility`, `scaleIn`, `fadeIn`, `tween`) already present in file; new ones explicitly listed.
- [x] `CardeaTheme.colors.gradient` is the 4-stop `CardeaGradient` (confirmed at `HomeScreen.kt:601`, `PulseHero.kt:261`).
- [x] `CardeaBgPrimary` already imported in the file — used for the pinned footer background.
- [x] `onViewHistory` is the only parameter being removed; NavGraph callers are the only external references (confirmed via grep).
- [x] Hero uses `displaySmall` which IS defined in `HrCoachTypography` (per CLAUDE.md typography list).
- [x] Pinned footer sits below scroll area in same `Column` — no conflicting `Modifier.align`.
