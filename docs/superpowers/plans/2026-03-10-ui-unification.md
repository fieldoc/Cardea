# UI Unification Implementation Plan

> **Note (2026-03-29):** This plan's uniform gradient-on-all-CTAs approach has been refined. Cardea now uses a 3-tier visual hierarchy: gradient reserved for Tier 1 (single most important metric per screen), glass+white for Tier 2, glass+secondary for Tier 3. See the updated Section 2.3 in `docs/plans/2026-03-02-cardea-ui-ux-design.md`.

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Unify the Cardea design system across all screens — eliminate hardcoded legacy colors, Material 3 default leakage, and component duplication identified in the UI/UX critique.

**Architecture:** All changes are purely within Compose UI files. No ViewModel, data, or nav changes needed. New shared wrappers `CardeaSlider` and `CardeaSwitch` are added to `ui/components/`. The `StatItem` composable in `GlassCard.kt` is already the canonical stat layout — screens are migrated to use it.

**Tech Stack:** Jetpack Compose, Material 3, Kotlin. No new dependencies required.

---

## Chunk 1: Shared Components

### Task 1: Add CardeaSlider and CardeaSwitch to ui/components/

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/components/CardeaInputs.kt`

- [ ] **Step 1: Create the file with both wrappers**

```kotlin
package com.hrcoach.ui.components

import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hrcoach.ui.theme.CardeaTextTertiary
import com.hrcoach.ui.theme.GlassHighlight
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientPink

/**
 * Cardea-styled slider. Active track and thumb use GradientBlue; inactive track uses GlassHighlight.
 */
@Composable
fun CardeaSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = SliderDefaults.colors(
            thumbColor = GradientBlue,
            activeTrackColor = GradientBlue,
            inactiveTrackColor = GlassHighlight,
            activeTickColor = GradientBlue,
            inactiveTickColor = CardeaTextTertiary
        )
    )
}

/**
 * Cardea-styled switch. Checked state uses GradientPink track; thumb is white.
 */
@Composable
fun CardeaSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedTrackColor = GradientPink,
            checkedThumbColor = androidx.compose.ui.graphics.Color.White,
            uncheckedTrackColor = GlassHighlight,
            uncheckedThumbColor = CardeaTextTertiary
        )
    )
}
```

- [ ] **Step 2: Verify the file compiles (no imports missing)**

Run: `cd C:/Users/glm_6/AndroidStudioProjects/HRapp && ./gradlew.bat :app:compileDebugKotlin --quiet 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL or only pre-existing errors.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/components/CardeaInputs.kt
git commit -m "feat(components): add CardeaSlider and CardeaSwitch wrappers with Cardea tokens"
```

---

## Chunk 2: HomeScreen — Three Fixes

### Task 2: HomeScreen — promote Bootcamp, fix ring size, unify stat labels

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt`

The current file has:
- Line 80 `size(80.dp)` for `EfficiencyRing` → change to `90.dp`
- Lines 272–287: `LastRunStat` private composable → replace usages with `StatItem`
- Lines 193–200: Quick Links row with `QuickLinkChip("Bootcamp", ...)` → replace Bootcamp chip with a full-width `GlassCard`

- [ ] **Step 1: Fix EfficiencyRing — upscale to 90dp**

In `HomeScreen.kt` around line 218, change:
```kotlin
// OLD
.size(80.dp)
```
to:
```kotlin
// NEW
.size(90.dp)
```

- [ ] **Step 2: Promote Bootcamp — replace the QuickLinkChip with a dedicated GlassCard**

Replace the entire "Quick Links" `Row` block (lines 192–200) with:

```kotlin
// Bootcamp Card — structured training entry point
GlassCard(
    modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onGoToBootcamp),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "STRUCTURED TRAINING",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = CardeaTextSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Bootcamp",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Text(
                text = "Adaptive program — phases, HR zones, life-aware",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTextSecondary
            )
        }
        CardeaButton(
            text = "Jump back in",
            onClick = onGoToBootcamp,
            modifier = Modifier.height(36.dp),
            innerPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
            cornerRadius = 10.dp
        )
    }
}

// Secondary quick links (no Bootcamp — it has its own card above)
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp)
) {
    QuickLinkChip("Progress", Modifier.weight(1f), onGoToProgress)
    QuickLinkChip("History", Modifier.weight(1f), onGoToHistory)
}
```

Also add the missing import for `clickable` (already imported) and `sp` (already imported via `unit.sp`).

- [ ] **Step 3: Replace LastRunStat usages with StatItem**

Replace the `Row` block inside the "Last Run" `GlassCard` (lines 174–181):
```kotlin
// OLD
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween
) {
    LastRunStat("Date", dateStr)
    LastRunStat("Distance", "%.2f km".format(distKm))
    LastRunStat("Duration", "${durationMin}m")
}
```
with:
```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween
) {
    StatItem("Date", dateStr)
    StatItem("Distance", "%.2f km".format(distKm))
    StatItem("Duration", "${durationMin}m")
}
```

Add import at top: `import com.hrcoach.ui.components.StatItem`

Then delete the private `LastRunStat` composable (lines 271–288).

- [ ] **Step 4: Build to verify**

Run: `./gradlew.bat :app:compileDebugKotlin --quiet 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt
git commit -m "feat(home): promote bootcamp to GlassCard, upscale ring 80->90dp, use StatItem"
```

---

## Chunk 3: HistoryListScreen — GlassCard + StatItem + Typography

### Task 3: HistoryListScreen — migrate to GlassCard, StatItem, CardeaTextSecondary

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/history/HistoryListScreen.kt`

Issues:
- Line 97: hardcoded `Color(0xFFB6C2D1)` → use `CardeaTextSecondary`
- Lines 169–175: `WorkoutCard` uses custom `Card` with `HistoryGlass` → migrate to `GlassCard`
- Lines 122–155: `HistoryEmptyState` also uses custom `Card` → migrate to `GlassCard`
- Lines 241–268: `HistoryMetricChip` → replace with `StatItem`
- Various hardcoded `Color(0xFFF5F7FB)`, `Color(0xFF8FA4B7)` → use `CardeaTextSecondary` / `Color.White`

- [ ] **Step 1: Fix subtitle typography (line 97)**

```kotlin
// OLD
color = Color(0xFFB6C2D1)
// NEW
color = CardeaTextSecondary
```

Add import: `import com.hrcoach.ui.theme.CardeaTextSecondary` (already present).

- [ ] **Step 2: Migrate HistoryEmptyState from custom Card to GlassCard**

Replace the `Card(...)` block in `HistoryEmptyState` (lines 124–155) with:
```kotlin
GlassCard(
    modifier = Modifier
        .fillMaxWidth()
        .padding(top = 24.dp),
    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp)
) {
    Text(
        text = "Your run archive is still empty",
        style = MaterialTheme.typography.headlineSmall,
        color = Color.White,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Start a workout to unlock route replay, post-run insights, and long-term progress trends.",
        style = MaterialTheme.typography.bodyLarge,
        color = CardeaTextSecondary
    )
    Spacer(modifier = Modifier.height(4.dp))
    CardeaButton(
        text = stringResource(R.string.button_start_workout),
        onClick = onStartWorkout,
        modifier = Modifier.height(44.dp),
        innerPadding = PaddingValues(horizontal = 24.dp)
    )
}
```

Remove the now-unused imports: `BorderStroke`, `Card`, `CardDefaults`.

- [ ] **Step 3: Migrate WorkoutCard from custom Card to GlassCard + replace HistoryMetricChip with StatItem**

Replace the `WorkoutCard` composable body. The entire `Card(...)` block (lines 169–238) becomes:

```kotlin
GlassCard(
    modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick),
    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp)
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = date,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            ) {
                Text(
                    text = workout.mode.asModeLabel(),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = distanceLabel,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Tap for route and stats",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTextSecondary
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatItem(
            label = "Duration",
            value = duration,
            modifier = Modifier.weight(1f)
        )
        StatItem(
            label = "Avg pace",
            value = paceLabel,
            modifier = Modifier.weight(1f)
        )
    }
}
```

Add imports:
```kotlin
import com.hrcoach.ui.components.StatItem
```

Remove the `HistoryMetricChip` composable entirely (lines 241–268) — it's now dead code.
Remove the `HistoryGlass` private constant and `HistoryBackdrop` is fine to keep.
Remove unused imports: `Surface` from material3 (still used inline — keep it), `BorderStroke`, `Card`, `CardDefaults`.

- [ ] **Step 4: Build to verify**

Run: `./gradlew.bat :app:compileDebugKotlin --quiet 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/history/HistoryListScreen.kt
git commit -m "refactor(history): migrate to GlassCard/StatItem, remove legacy color hardcodes"
```

---

## Chunk 4: ProgressScreen — CTA + TrendLineChart Glow

### Task 4: ProgressScreen — replace OutlinedButton + apply BlurMaskFilter glow

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/progress/ProgressScreen.kt`

Issues:
- Line 129: `OutlinedButton` → `CardeaButton`
- Lines 483–488: fake alpha glow → `BlurMaskFilter` glow using `Paint`

- [ ] **Step 1: Replace OutlinedButton with CardeaButton**

In `ProgressScreen.kt` around line 129:
```kotlin
// OLD
OutlinedButton(onClick = onStartWorkout) {
    Text(stringResource(R.string.button_start_workout))
}
```
```kotlin
// NEW
com.hrcoach.ui.components.CardeaButton(
    text = stringResource(R.string.button_start_workout),
    onClick = onStartWorkout,
    modifier = Modifier
        .height(48.dp)
        .fillMaxWidth(0.7f),
    innerPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
)
```

Remove import: `import androidx.compose.material3.OutlinedButton`

- [ ] **Step 2: Add BlurMaskFilter glow to TrendLineChart**

The `TrendLineChart` composable (lines 423–518) uses an alpha-based fake glow. Replace the glow drawing in `onDrawBehind` with a true `BlurMaskFilter` approach.

At the top of the file, add imports:
```kotlin
import android.graphics.BlurMaskFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
```

In `TrendLineChart`, inside `onDrawBehind`, replace:
```kotlin
// OLD glow pass
drawPath(
    path = path,
    brush = chartGradient,
    alpha = 0.15f,
    style = Stroke(width = 18f, cap = StrokeCap.Round)
)
```
with:
```kotlin
// NEW — true neon glow via BlurMaskFilter
drawIntoCanvas { canvas ->
    val glowPaint = Paint().apply {
        asFrameworkPaint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 5f
            maskFilter = BlurMaskFilter(
                16f, // ~6dp blur radius
                BlurMaskFilter.Blur.NORMAL
            )
            // Use GradientPink as the glow color
            color = android.graphics.Color.argb(100, 0xFF, 0x2D, 0xA6)
        }
    }
    canvas.drawPath(path, glowPaint)
}
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew.bat :app:compileDebugKotlin --quiet 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/progress/ProgressScreen.kt
git commit -m "feat(progress): replace OutlinedButton with CardeaButton; add BlurMaskFilter neon glow to TrendLineChart"
```

---

## Chunk 5: SetupScreen and AccountScreen — Cardea Input Tokens

### Task 5: Apply CardeaSlider and CardeaSwitch in SetupScreen

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt`

- [ ] **Step 1: Find all Slider and Switch usages**

Run: `grep -n "Slider\|Switch" app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt`

- [ ] **Step 2: Replace all `Slider(` with `CardeaSlider(` and `Switch(` with `CardeaSwitch(`**

For each `Slider(` occurrence: replace with `CardeaSlider(` keeping all parameters.
For each `Switch(` occurrence: replace with `CardeaSwitch(` and remove the `colors = ...` parameter (it's baked into the wrapper).

Add at top of file:
```kotlin
import com.hrcoach.ui.components.CardeaSlider
import com.hrcoach.ui.components.CardeaSwitch
```

Remove any now-unused imports:
- `SliderDefaults` if present
- `SwitchDefaults` if present

- [ ] **Step 3: Build to verify**

Run: `./gradlew.bat :app:compileDebugKotlin --quiet 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt
git commit -m "refactor(setup): use CardeaSlider/CardeaSwitch for token-consistent inputs"
```

### Task 6: Apply CardeaSlider/Switch + upgrade Profile Card in AccountScreen

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt`

- [ ] **Step 1: Replace Slider with CardeaSlider (around line 176)**

```kotlin
// OLD
Slider(
    value = state.earconVolume.toFloat(),
    onValueChange = viewModel::setVolume,
    valueRange = 0f..100f,
    steps = 19,
    onValueChangeFinished = viewModel::saveAudioSettings
)
```
```kotlin
// NEW
CardeaSlider(
    value = state.earconVolume.toFloat(),
    onValueChange = viewModel::setVolume,
    valueRange = 0f..100f,
    steps = 19,
    onValueChangeFinished = viewModel::saveAudioSettings
)
```

- [ ] **Step 2: Replace Switch with CardeaSwitch (around line 211)**

```kotlin
// OLD
Switch(
    checked = state.enableVibration,
    onCheckedChange = { viewModel.setVibration(it); viewModel.saveAudioSettings() },
    colors = SwitchDefaults.colors(checkedTrackColor = GradientPink)
)
```
```kotlin
// NEW
CardeaSwitch(
    checked = state.enableVibration,
    onCheckedChange = { viewModel.setVibration(it); viewModel.saveAudioSettings() }
)
```

Add imports:
```kotlin
import com.hrcoach.ui.components.CardeaSlider
import com.hrcoach.ui.components.CardeaSwitch
```

Remove unused imports: `Slider`, `Switch`, `SwitchDefaults`, `GradientPink` (if no longer used elsewhere in this file).

- [ ] **Step 3: Upgrade the Profile GlassCard header**

The current profile card (lines 93–118) is a simple `Row`. Upgrade it with a gradient accent bar at the top:

Replace the `GlassCard(modifier = Modifier.fillMaxWidth())` profile block with:

```kotlin
// Profile card with gradient accent
Box(modifier = Modifier.fillMaxWidth()) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        // Gradient accent strip at top of card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(CardeaGradient)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(CardeaGradient),
                contentAlignment = Alignment.Center
            ) {
                CardeaLogo(size = 36.dp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Runner",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White
                )
                Text(
                    text = "${state.totalWorkouts} runs recorded",
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTextSecondary
                )
            }
        }
    }
}
```

- [ ] **Step 4: Build to verify**

Run: `./gradlew.bat :app:compileDebugKotlin --quiet 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt
git commit -m "refactor(account): CardeaSlider/Switch + gradient accent on profile card"
```

---

## Chunk 6: ActiveWorkoutScreen + BootcampScreen

### Task 7: ActiveWorkoutScreen — fix Zone Status Pill dot

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/workout/ActiveWorkoutScreen.kt`

Issue: Line 403–408 inside `ZoneStatusPill` — the inner `Box` dot uses `CardeaGradient` brush but should use solid `zoneColor`.

- [ ] **Step 1: Replace the gradient dot with a solid zoneColor dot**

In `ZoneStatusPill` (around lines 403–408):
```kotlin
// OLD
Box(
    modifier = Modifier
        .size(8.dp)
        .background(CardeaGradient, CircleShape)
)
```
```kotlin
// NEW
Box(
    modifier = Modifier
        .size(8.dp)
        .background(zoneColor, CircleShape)
)
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew.bat :app:compileDebugKotlin --quiet 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/workout/ActiveWorkoutScreen.kt
git commit -m "fix(workout): zone status pill dot uses solid zoneColor instead of full gradient"
```

### Task 8: BootcampScreen — fix icon tint to CardeaTextPrimary

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt`

Issue: `FeatureBullet` (and possibly other icons) use `GradientPink` for static icon tints. Change to `CardeaTextPrimary` (white) for general-purpose icons; reserve gradient for active states.

- [ ] **Step 1: Find all icon tint usages in BootcampScreen**

Run: `grep -n "tint" app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt`

- [ ] **Step 2: Replace GradientPink icon tints with CardeaTextPrimary**

For each `tint = GradientPink` on a decorative/feature icon (not an active/status indicator), change to:
```kotlin
tint = CardeaTextPrimary
```

Keep `tint = ZoneGreen` on checkmarks (completed sessions) — those are semantic status colors.
Keep any gradient on progress/CTA elements.

- [ ] **Step 3: Build to verify**

Run: `./gradlew.bat :app:compileDebugKotlin --quiet 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt
git commit -m "refactor(bootcamp): use CardeaTextPrimary for general icons, reserve color for status"
```

---

## Chunk 7: Final Build Verification

### Task 9: Run full test suite and build

- [ ] **Step 1: Full debug build**

Run: `cd C:/Users/glm_6/AndroidStudioProjects/HRapp && ./gradlew.bat assembleDebug 2>&1 | tail -30`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Unit tests**

Run: `./gradlew.bat test 2>&1 | tail -30`
Expected: BUILD SUCCESSFUL, 0 failures

- [ ] **Step 3: Done**

All 10 UI/UX critique items addressed:
1. ✅ Bootcamp entry promoted to GlassCard on Home
2. ✅ ProgressScreen CTA → CardeaButton (HistoryListScreen already had CardeaButton)
3. ✅ TrendLineChart uses BlurMaskFilter neon glow
4. ✅ CardeaSlider + CardeaSwitch replace stock M3 inputs in Setup + Account
5. ✅ LastRunStat → StatItem in Home; HistoryMetricChip → StatItem in History
6. ✅ EfficiencyRing 80dp → 90dp
7. ✅ WorkoutCard/HistoryEmptyState → GlassCard
8. ✅ Zone Status Pill dot uses solid zoneColor
9. ✅ BootcampScreen icons use CardeaTextPrimary
10. ✅ Account Profile Card has gradient accent strip
