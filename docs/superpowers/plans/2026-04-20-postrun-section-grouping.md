# PostRun Section-Grouping Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Break PostRun's uniform 12-card vertical stripe into four labeled sections — **Status** (HRR + HRmax delta), **Your Run** (hero + stats), **Compared** (context cards), **Extras** (bootcamp, achievements, sounds recap) — using the existing `SectionHeader` component, so the eye has rhythm and hierarchy.

**Architecture:** Replace the flat `Arrangement.spacedBy(12.dp)` Column with section wrappers. Each section has: (1) `SectionHeader(title)` (the shared component from `ui/components/SectionHeader.kt`), (2) tighter internal spacing (`spacedBy(8.dp)`), (3) larger inter-section spacing (`spacedBy(20.dp)` at the outer level). Normalise `PostRunComparison` card layout to title / value / single-line context.

**Tech Stack:** Kotlin, Jetpack Compose, existing `SectionHeader` + `GlassCard` + `CardeaTheme` tokens.

---

## File Structure

- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt` — introduce sections.

Small refactor only — no new files, no ViewModel changes. No unit tests (pure layout).

---

## Task 1: Introduce section structure

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt`

- [ ] **Step 1.1: Add the `SectionHeader` import**

At the top of the file, add:
```kotlin
import com.hrcoach.ui.components.SectionHeader
```

- [ ] **Step 1.2: Replace the flat scroll body with sectioned structure**

This plan assumes **Plans 2 (primary-cta-and-hero) and 3 (hrr-cooldown-priority) have already been applied**. If executing in isolation, adapt the anchors accordingly.

In the `PostRunContentState.CONTENT` branch, replace the inner scrollable `Column` content with:

```kotlin
Column(
    modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(24.dp)  // inter-section
) {
    // ── Section 1: Status (HRR + HRmax delta) ──
    // Shown only when at least one status card has content. Status goes first
    // because HRR is time-sensitive and HRmax is the most notable change.
    val hasStatus = isHrrActive || uiState.hrMaxDelta != null
    if (hasStatus) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader("STATUS")
            if (isHrrActive) {
                HrrCooldownCard(endTimeMs = uiState.workoutEndTimeMs)
            }
            uiState.hrMaxDelta?.let { (oldMax, newMax) ->
                HrMaxUpdatedCard(oldMax = oldMax, newMax = newMax)
            }
        }
    }

    // ── Section 2: Your Run (hero + 2 stat cards) ──
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RunCompleteHero(
            visible = showCelebration,
            distanceText = uiState.distanceText
        )
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
    }

    // ── Section 3: Compared to Similar Runs ──
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = "COMPARED",
            subtitle = if (uiState.comparisons.isNotEmpty()) {
                "vs. ${uiState.similarRunCount} similar sessions"
            } else null
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
            uiState.comparisons.forEach { item -> ComparisonRow(item) }
        }
    }

    // ── Section 4: Extras (bootcamp, achievements, sounds recap) ──
    val hasExtras = uiState.newAchievements.isNotEmpty() ||
        !uiState.bootcampProgressLabel.isNullOrBlank() ||
        uiState.showSoundsRecap
    if (hasExtras) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader("MORE")
            uiState.bootcampProgressLabel
                ?.takeIf { it.isNotBlank() }
                ?.let { progressLabel ->
                    BootcampContextCard(
                        progressLabel = progressLabel,
                        weekComplete = uiState.bootcampWeekComplete
                    )
                }
            if (uiState.newAchievements.isNotEmpty()) {
                NewAchievementsSection(achievements = uiState.newAchievements)
            }
            if (uiState.showSoundsRecap) {
                SoundsHeardSection(
                    counts = uiState.cueCounts,
                    onSeeLibrary = onNavigateToSoundLibrary
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
}
```

- [ ] **Step 1.3: Add the normalised `ComparisonRow` composable**

Append at the bottom of the file, below `BootcampContextCard`:

```kotlin
@Composable
private fun ComparisonRow(item: PostRunComparison) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = CardeaTheme.colors.textSecondary
                )
                Text(
                    text = item.value,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = CardeaTheme.colors.textPrimary
                )
                // Normalised context line: prefer delta when present, else insight,
                // else nothing. No more mixed layouts across rows.
                val contextLine = item.delta ?: item.insight
                contextLine?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            item.delta != null -> when (item.positive) {
                                true -> ZoneGreen
                                false -> ZoneRed
                                null -> CardeaTheme.colors.textSecondary
                            }
                            else -> CardeaTheme.colors.textSecondary
                        }
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 1.4: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Unused warnings for the old inline comparison Row body are acceptable only if any remain — the replacement in Step 1.2 should have fully supplanted it.

- [ ] **Step 1.5: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt
git commit -m "feat(postrun): group content into Status / Your Run / Compared / More sections"
```

---

## Task 2: Normalise internal spacing on `GlassCard` inside sections

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt`

- [ ] **Step 2.1: Tighten `HrMaxUpdatedCard` and `BootcampContextCard` padding**

These cards currently use `GlassCard`'s default padding. Within a section they should feel tighter than stand-alone cards. Edit both composables (lines ~490 and ~525):

For `HrMaxUpdatedCard`, change:
```kotlin
GlassCard {
```
to:
```kotlin
GlassCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
```

Same change for `BootcampContextCard`. Add the import `import androidx.compose.foundation.layout.PaddingValues` if not already present.

- [ ] **Step 2.2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2.3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt
git commit -m "refactor(postrun): tighten status card padding for in-section use"
```

---

## Task 3: Device verification

- [ ] **Step 3.1: Build, install, and complete a sim run**

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Account → Simulation → start/stop a sim run.

- [ ] **Step 3.2: Observe section structure**

Expected on PostRun:
1. "STATUS" section visible (HRR card, possibly HRmax card).
2. Hero + 2-stat row (no section header — hero IS the header for its section).
3. "COMPARED" header with optional "vs. N similar sessions" subtitle.
4. Comparison rows all share the same visual shape — title (small), value (large), context line (small).
5. "MORE" section with bootcamp/achievements/sounds recap as applicable.
6. ~24dp gaps between sections, ~10dp gaps within.

- [ ] **Step 3.3: Test empty states — first-ever run, freestyle**

1. `adb shell pm clear com.hrcoach` (destroys DB — run on simulator or test device only).
2. Complete onboarding + first FREE_RUN sim.
3. Expected: STATUS section absent (no HRR triggered on <0 km run? — might still show), Your Run always present, COMPARED shows "Not enough data", MORE section absent (no achievements, no bootcamp, no sounds recap gated on metrics).

- [ ] **Step 3.4: Capture a PR screenshot**

```bash
adb shell screencap //sdcard/postrun_sections.png
adb pull //sdcard/postrun_sections.png /tmp/postrun_sections.png
```

- [ ] **Step 3.5: Run full unit test suite**

Run: `./gradlew testDebugUnitTest`
Expected: ALL PASS (no unit test surface affected; gate for regressions).

---

## Self-review checklist

- [x] `SectionHeader` exists at `ui/components/SectionHeader.kt` (verified via grep).
- [x] Every section wrapper is gated by a `hasX` condition so empty sections collapse entirely.
- [x] `ComparisonRow` normalises layout — delta-or-insight (not both) — fixing the "some rows have two extra lines" inconsistency.
- [x] Inter-section spacing (`24.dp`) > intra-section spacing (`10.dp`) > inside-card line spacing (inherited) — clear hierarchy.
- [x] No changes to `PostRunSummaryViewModel` — state shape unchanged.
- [x] `Icons.Default.Timer` and `Icons.Default.Favorite` already imported (verified).
