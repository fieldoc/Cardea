# UI/UX Unification Pass — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Align HistoryDetailScreen, PostRunSummaryScreen, HistoryListScreen, and ProgressScreen with the Cardea design spec (see `docs/plans/2026-03-02-ui-unification-design.md`).

**Architecture:** In-place patching only — no new components, no screen restructuring. All Cardea tokens live in `ui/theme/Color.kt`. The gradient button pattern (Box + clip + background(CardeaGradient) + clickable) is established in SetupScreen.

**Tech Stack:** Kotlin, Jetpack Compose, Canvas DrawScope. Build: `./gradlew assembleDebug`.

---

### Task 1: HistoryDetailScreen — Fix backdrop and scaffold container

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/history/HistoryDetailScreen.kt`

**Step 1: Identify the two things to change**

In `HistoryDetailScreen.kt`:
- Line ~80: `private val DetailBackdrop = Brush.verticalGradient(...)` — old pre-Cardea blues
- Line ~114: `Scaffold(containerColor = Color(0xFF061019), ...)` — hardcoded opaque old color
- Top AppBar colors at ~129: `containerColor = Color(0xFF061019)` — same

**Step 2: Replace `DetailBackdrop` and add missing imports**

Add imports (at top, after existing `import androidx.compose.ui.graphics.Brush`):
```kotlin
import com.hrcoach.ui.theme.CardeaBgPrimary
import com.hrcoach.ui.theme.CardeaBgSecondary
```

Replace the `DetailBackdrop` val:
```kotlin
// BEFORE
private val DetailBackdrop = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF061019),
        Color(0xFF0D1623),
        Color(0xFF162739)
    )
)

// AFTER
private val DetailBackdrop = Brush.radialGradient(
    colors = listOf(CardeaBgSecondary, CardeaBgPrimary)
)
```

**Step 3: Fix Scaffold containerColor and TopAppBar containerColor**

```kotlin
// BEFORE
Scaffold(
    containerColor = Color(0xFF061019),
    topBar = {
        TopAppBar(
            ...
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF061019),
```

```kotlin
// AFTER
Scaffold(
    containerColor = Color.Transparent,
    topBar = {
        TopAppBar(
            ...
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
```

**Step 4: Build and verify**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL. No errors. Install and check HistoryDetail — background should now show the Cardea radial gradient instead of flat dark teal.

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/history/HistoryDetailScreen.kt
git commit -m "fix(history): replace old backdrop with Cardea radial gradient"
```

---

### Task 2: HistoryDetailScreen — Fix DetailGlass and map overlays

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/history/HistoryDetailScreen.kt`

**Step 1: Identify `DetailGlass` usages**

`private val DetailGlass = Color(0xCC0D1824)` — 0xCC alpha = 80% opaque. This makes cards look like solid dark panels instead of glass. Used in:
- `DetailMapCard`: `background(DetailGlass, ...)` and inner `Color(0xFF08111A)`
- `MapHeaderOverlay`: `Surface(color = DetailGlass, ...)`
- `MapLegendOverlay`: `Surface(color = DetailGlass, ...)`
- `StatsCard`: `CardDefaults.cardColors(containerColor = DetailGlass)`
- `MoreActionsCard`: `CardDefaults.cardColors(containerColor = DetailGlass)`

**Step 2: Add `GlassBorder` import**

```kotlin
import com.hrcoach.ui.theme.GlassBorder
```

**Step 3: Replace `DetailGlass` val**

```kotlin
// BEFORE
private val DetailGlass = Color(0xCC0D1824)

// AFTER
private val DetailGlass = Color(0x0FFFFFFF)   // 6% white — spec glass fill
```

**Step 4: Fix `DetailMapCard` inner background**

The inner Box uses `Color(0xFF08111A)` as a map background — replace with `CardeaBgPrimary`:
```kotlin
// BEFORE
.background(Color(0xFF08111A), RoundedCornerShape(31.dp))

// AFTER
.background(CardeaBgPrimary, RoundedCornerShape(31.dp))
```

**Step 5: Fix `MapHeaderOverlay` and `MapLegendOverlay` borders**

Both currently use `Color.White.copy(alpha = 0.08f)` for border — replace with `GlassBorder`:
```kotlin
// BEFORE (both overlays)
border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))

// AFTER
border = BorderStroke(1.dp, GlassBorder)
```

**Step 6: Build and verify**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL. On device: cards in HistoryDetail should now look translucent/glass rather than opaque dark panels.

**Step 7: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/history/HistoryDetailScreen.kt
git commit -m "fix(history): replace opaque DetailGlass with Cardea glass tokens"
```

---

### Task 3: HistoryDetailScreen — Fix card corners and add gradient CTA

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/history/HistoryDetailScreen.kt`

**Step 1: Add missing imports for gradient button**

```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaTextPrimary
```

**Step 2: Fix `StatsCard` corner radius**

```kotlin
// BEFORE
Card(
    modifier = modifier,
    shape = RoundedCornerShape(30.dp),

// AFTER
Card(
    modifier = modifier,
    shape = RoundedCornerShape(18.dp),
```

**Step 3: Fix `MoreActionsCard` corner radius**

```kotlin
// BEFORE
Card(
    shape = RoundedCornerShape(30.dp),

// AFTER
Card(
    shape = RoundedCornerShape(18.dp),
```

**Step 4: Replace `MoreActionsCard` primary `Button` with gradient Box**

The current `Button` (primary CTA inside MoreActionsCard) renders flat `GradientBlue`. Replace it with the Cardea gradient button pattern:

```kotlin
// BEFORE
Button(
    onClick = onViewPostRunSummary,
    modifier = Modifier.fillMaxWidth()
) {
    Text(stringResource(R.string.button_post_run_insights))
}

// AFTER
Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(CardeaGradient)
        .clickable(onClick = onViewPostRunSummary),
    contentAlignment = Alignment.Center
) {
    Text(
        text = stringResource(R.string.button_post_run_insights),
        color = CardeaTextPrimary,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold
    )
}
```

Remove the now-unused `Button` import if no other Button is used in the file. (Check — `AlertDialog` confirmButton and dismissButton use `TextButton`, and there's an `OutlinedButton` in the actions row. No plain `Button` remains, so the import can be removed.)

**Step 5: Also fix `DetailMapCard` outer border radius**

While here — the outer border of the map card uses `RoundedCornerShape(32.dp)` and inner `31.dp`. Align to 18dp:
```kotlin
// BEFORE
.border(
    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    shape = RoundedCornerShape(32.dp)
)
.background(DetailGlass, RoundedCornerShape(32.dp))
...
.background(CardeaBgPrimary, RoundedCornerShape(31.dp))

// AFTER
.border(
    border = BorderStroke(1.dp, GlassBorder),
    shape = RoundedCornerShape(18.dp)
)
.background(DetailGlass, RoundedCornerShape(18.dp))
...
.background(CardeaBgPrimary, RoundedCornerShape(17.dp))
```

**Step 6: Build and verify**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL. Cards in HistoryDetail have 18dp radius; "Post-run Insights" button shows gradient.

**Step 7: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/history/HistoryDetailScreen.kt
git commit -m "fix(history): 18dp card radius, gradient post-run CTA"
```

---

### Task 4: PostRunSummaryScreen — Gradient Done button

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt`

**Step 1: Add imports**

```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaTextPrimary
```

**Step 2: Replace Done `Button` with gradient Box**

The Done button is in the bottom Row alongside "View on Map" `OutlinedButton`. Matching the OutlinedButton's default height (~40dp):

```kotlin
// BEFORE
Button(
    onClick = onDone,
    modifier = Modifier.weight(1f),
    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
) {
    Text(stringResource(R.string.button_done))
}

// AFTER
Box(
    modifier = Modifier
        .weight(1f)
        .height(40.dp)
        .clip(RoundedCornerShape(50.dp))
        .background(CardeaGradient)
        .clickable(onClick = onDone),
    contentAlignment = Alignment.Center
) {
    Text(
        text = stringResource(R.string.button_done),
        color = CardeaTextPrimary,
        style = MaterialTheme.typography.labelLarge
    )
}
```

**Step 3: Remove unused imports**

After the replacement, `Button`, `ButtonDefaults` are no longer used — remove them:
```kotlin
// Remove these:
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
```

**Step 4: Build and verify**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL. "Done" button in PostRunSummary shows Cardea gradient.

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt
git commit -m "fix(postrun): gradient Done button"
```

---

### Task 5: HistoryListScreen — Glass, corners, and gradient empty-state CTA

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/history/HistoryListScreen.kt`

**Step 1: Add imports**

```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaTextPrimary
```

**Step 2: Fix `HistoryGlass` opacity**

```kotlin
// BEFORE
private val HistoryGlass = Color(0x0AFFFFFF)   // ~4%

// AFTER
private val HistoryGlass = Color(0x0FFFFFFF)   // 6% — per spec
```

**Step 3: Fix `WorkoutCard` corner radius**

```kotlin
// BEFORE
Card(
    modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick),
    shape = RoundedCornerShape(30.dp),

// AFTER
Card(
    modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick),
    shape = RoundedCornerShape(18.dp),
```

**Step 4: Fix `HistoryEmptyState` corner radius**

```kotlin
// BEFORE
Card(
    modifier = Modifier
        .fillMaxWidth()
        .padding(top = 24.dp),
    shape = RoundedCornerShape(32.dp),

// AFTER
Card(
    modifier = Modifier
        .fillMaxWidth()
        .padding(top = 24.dp),
    shape = RoundedCornerShape(18.dp),
```

**Step 5: Replace empty-state `OutlinedButton` with gradient Box**

```kotlin
// BEFORE
OutlinedButton(
    onClick = onStartWorkout,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
) {
    Text(stringResource(R.string.button_start_workout))
}

// AFTER
Box(
    modifier = Modifier
        .height(44.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(CardeaGradient)
        .clickable(onClick = onStartWorkout)
        .padding(horizontal = 24.dp),
    contentAlignment = Alignment.Center
) {
    Text(
        text = stringResource(R.string.button_start_workout),
        color = CardeaTextPrimary,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold
    )
}
```

**Step 6: Remove unused imports**

`OutlinedButton` is now unused — remove it:
```kotlin
// Remove:
import androidx.compose.material3.OutlinedButton
```

Also remove `BorderStroke` and `import androidx.compose.foundation.BorderStroke` if the `WorkoutCard` border still uses `BorderStroke(1.dp, Color.White.copy(...))` — check: yes it does (both cards use BorderStroke for the glass border), so keep `BorderStroke`.

**Step 7: Build and verify**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL. History list cards have 18dp radius; empty state has gradient CTA.

**Step 8: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/history/HistoryListScreen.kt
git commit -m "fix(history): glass opacity, 18dp radius, gradient empty-state CTA"
```

---

### Task 6: ProgressScreen — TrendLineChart gradient + glow

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/progress/ProgressScreen.kt`

**Step 1: Add gradient token imports**

In ProgressScreen.kt, add:
```kotlin
import com.hrcoach.ui.theme.GradientRed
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientCyan
```

**Step 2: Locate `TrendLineChart` composable**

It's a private composable starting around line 420. The full Canvas block is inside it. Currently:
```kotlin
val lineColor = MaterialTheme.colorScheme.primary    // ← flat GradientBlue

Canvas(...) {
    // ...path building...
    drawPath(path, lineColor, style = Stroke(width = 5f, cap = StrokeCap.Round))

    series.forEachIndexed { i, pt ->
        val x = stepX * i
        val y = h - ((pt.value - chartMin) / range * h)
        drawCircle(lineColor, radius = 5f, center = Offset(x, y))
    }
}
```

**Step 3: Remove `lineColor` and build gradient inside Canvas**

`lineColor` is declared outside Canvas (it needs `@Composable` context for `MaterialTheme`). Remove it entirely and build the gradient inside the Canvas block where `size` is available:

```kotlin
// REMOVE this line (outside Canvas):
val lineColor = MaterialTheme.colorScheme.primary
```

Inside the Canvas block, after building `path` and before drawing it, add:

```kotlin
val chartGradient = Brush.linearGradient(
    colorStops = arrayOf(
        0.00f to GradientRed,
        0.35f to GradientPink,
        0.65f to GradientBlue,
        1.00f to GradientCyan
    ),
    start = Offset(0f, 0f),
    end = Offset(w, 0f)
)

// Glow pass — drawn before main line so it sits underneath
drawPath(
    path = path,
    brush = chartGradient,
    alpha = 0.15f,
    style = Stroke(width = 18f, cap = StrokeCap.Round)
)
// Main gradient line
drawPath(
    path = path,
    brush = chartGradient,
    style = Stroke(width = 5f, cap = StrokeCap.Round)
)
```

**Step 4: Update dot color**

Replace the `drawCircle(lineColor, ...)` calls:
```kotlin
// BEFORE
drawCircle(lineColor, radius = 5f, center = Offset(x, y))

// AFTER
drawCircle(GradientCyan, radius = 5f, center = Offset(x, y))
```

Note: there are two `drawCircle` calls in the loop — update both.

**Step 5: Build and verify**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL. In the Progress tab, trend line charts (Pace, Aerobic Efficiency, Resting HR, etc.) should render with the Cardea gradient stroke + soft glow.

**Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/progress/ProgressScreen.kt
git commit -m "fix(progress): Cardea gradient + glow on TrendLineChart"
```

---

### Task 7: Run code simplifier

After all changes are committed, invoke the `simplify` skill to review changed code for quality and redundancy.

---

## Verification Checklist

After all tasks:
- [ ] `./gradlew assembleDebug` passes with no errors
- [ ] HistoryDetailScreen: radial gradient backdrop, translucent glass cards, 18dp radius, gradient CTA
- [ ] PostRunSummaryScreen: "Done" button shows gradient
- [ ] HistoryListScreen: 18dp card radius, gradient empty-state CTA, correct glass opacity
- [ ] ProgressScreen: trend lines show gradient stroke with glow
