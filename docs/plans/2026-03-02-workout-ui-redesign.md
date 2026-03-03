# Active Workout Screen — UI/UX Redesign

**Date:** 2026-03-02
**Status:** Approved
**Scope:** Full layout redesign of `ActiveWorkoutScreen` + minor polish to `SetupScreen`.
**Design system:** Cardea (see `2026-03-02-cardea-ui-ux-design.md` for tokens).

---

## 1. Overview

The `ActiveWorkoutScreen` received only minimal Cardea styling in the previous pass (background + transparent TopAppBar). This redesign introduces a **hero HR ring** as the focal point, moves the connect action into the ring's disconnected state, and fixes several consistency and wording issues across the workout flow.

Content, data model (except one new field), and business logic are unchanged.

---

## 2. Screen Layout

```
┌──────────────────────────────┐
│  Cardea           00:12:34   │  ← transparent overlay row (no Scaffold TopAppBar)
│                              │     "Cardea" = gradient text (ShaderBrush)
│       ◉  IN ZONE             │  ← ZoneStatusPill
│                              │
│    ╭────────────────╮        │
│   ╱  [gradient ring] ╲       │  ← HrRing (160dp, 8dp stroke)
│  │       142          │      │
│  │       bpm          │      │
│   ╲                  ╱       │
│    ╰────────────────╯        │
│                              │
│    Target      Projected     │
│    145 bpm     144 bpm       │  ← InlineMetric row
│                              │
│  ┌─────────────────────────┐ │
│  │  Ease up slightly       │ │  ← GuidanceCard (glass, pulsing zone-color border)
│  └─────────────────────────┘ │
│                              │
│  ┌─────────────────────────┐ │
│  │ Distance   Pace  Avg HR │ │  ← GlassCard stats row
│  │  3.2 km   5:12   141    │ │
│  └─────────────────────────┘ │
│                              │
│  [ Pause ]       [ ■ Stop ]  │  ← control buttons
└──────────────────────────────┘
```

**Disconnected ring state:**
```
│    ╭────────────────╮        │
│   ╱  [grey dim ring] ╲       │  ← CardeaTextTertiary stroke, no fill
│  │   ◯  Connect HR   │      │  ← gradient dot + "Connect HR" text
│  │      monitor      │      │    entire Box is clickable → onConnectHr()
│   ╲                  ╱       │
│    ╰────────────────╯        │
```

---

## 3. New Component: HrRing

**File:** `app/src/main/java/com/hrcoach/ui/workout/HrRing.kt`

```
@Composable
fun HrRing(
    hr: Int,
    isConnected: Boolean,
    zoneColor: Color,
    pulseScale: Float,
    onConnectHr: () -> Unit,
    modifier: Modifier = Modifier
)
```

### Connected state
- Outer `Box`: 160dp × 160dp, `CircleShape` clip, `scale(pulseScale)`
- Background track circle: `zoneColor.copy(alpha = 0.12f)` fill via `Canvas drawCircle`
- Ring arc: full 360° `drawArc`, stroke 8dp, cap `Round`, brush = `CardeaGradient`
  (use `ShaderBrush` with `TileMode.Clamp` mapped to the 160dp bounding box)
- Center: HR number (`displayLarge`, `CardeaTextPrimary`) + `"bpm"` label (`labelSmall`, `CardeaTextSecondary`)
- Pulse: reuses existing `pulseScale` `animateFloatAsState` from the screen (passed in)

### Disconnected state
- Same outer `Box`, `clickable { onConnectHr() }`
- Ring arc: `CardeaTextTertiary` stroke (dim, no gradient)
- Background track: none
- Center:
  - 10dp gradient dot (`Box` with `CardeaGradient` background, `CircleShape`)
  - `"Connect HR"` (`titleSmall`, `CardeaTextPrimary`)
  - `"monitor"` (`labelSmall`, `CardeaTextSecondary`)

---

## 4. Changes to ActiveWorkoutScreen

### 4.1 Top bar
- Replace `Scaffold` + `TopAppBar` with a plain `Box` (no Scaffold) plus a manually placed `Row` at the top:
  - Left: `"Cardea"` text with `Brush.linearGradient(CardeaGradient colors)` as `ShaderBrush` text style
  - Right: `formatElapsedHms(uiState.elapsedSeconds)` in `titleMedium`, `CardeaTextSecondary`
  - Padding: `horizontal = 20.dp, vertical = 16.dp`

### 4.2 Zone status pill (ZoneStatusPill)
- Add `"FREE RUN"` label: when `state.isFreeRun && state.hrConnected`, show `"FREE RUN"`
- Add `softWrap = false, overflow = TextOverflow.Ellipsis` to the label `Text` — fixes portrait clipping

### 4.3 HR display
- Replace the existing plain `AnimatedContent` HR number with the new `HrRing` composable
- Pass `onConnectHr` received from the screen's own callback parameter
- Remove the `OutlinedCard` disconnected warning — disconnected state is now handled by the ring

### 4.4 InlineMetric row
- Target: for free run, show `"—"` instead of `"Free run"` (shorter, does not clip)

### 4.5 Stats card
- Replace `StatItem(label = "Lag", ...)` with `StatItem(label = "Avg HR", value = if (state.avgHr > 0) "${state.avgHr}" else "--")`

### 4.6 Distance profile progress bar
- Replace `LinearProgressIndicator` with a custom 4dp Canvas composable:
  - Full width, `CardeaGradient` fill, rounded caps (`StrokeCap.Round`)
  - Track: `GlassBorder` color

### 4.7 Control buttons
- **Pause/Resume:** `Box` with `GlassBorder` 1dp border, `RoundedCornerShape(14.dp)`, transparent fill, white label
- **Stop:** same shape, `ZoneRed.copy(alpha = 0.15f)` fill, `ZoneRed` 1dp border, white label
- Both: `height(56.dp)`, `weight(1f)`, `Row` with `spacedBy(10.dp)`

### 4.8 New callback
Add `onConnectHr: () -> Unit` parameter to `ActiveWorkoutScreen`. Wire in `NavGraph.kt` to send `WorkoutForegroundService.ACTION_SCAN` (or equivalent intent) to trigger BLE rescan.

---

## 5. Data Model Change

**File:** `app/src/main/java/com/hrcoach/service/WorkoutState.kt`

Add to `WorkoutSnapshot`:
```kotlin
val avgHr: Int = 0
```

**File:** `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`

Maintain a running average: accumulate HR readings and divide by count. Update `avgHr` in `WorkoutState` on each HR event alongside existing fields.

---

## 6. SetupScreen Fixes

**File:** `app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt`

### 6.1 HrMonitorCard — scan button styling
Replace the full-width Material `Button` with a gradient-styled CTA:
- Shape: `RoundedCornerShape(14.dp)`
- Background: `CardeaGradient` brush fill (same pattern as Start Workout button)
- Label: `"Scan for Devices"` / `"Scanning..."` (unchanged text)
- Icon: `Icons.Default.Search` (unchanged)

### 6.2 HrMonitorCard — hint text
Replace the two verbose hint lines with one:
```
Before:
  "COOSPO H808S usually appears as \"H808...\" or \"COOSPO...\"."
  "You can still start without a live connection; scanning continues during the run."

After (single line):
  "No signal? You can still start — scanning continues during the run."
```

### 6.3 Collapsed audio hint (line 397)
```
Before: "Buffer, timing, voice, and vibration options are tucked here."
After:  "Audio alerts & timing options"
```

---

## 7. Out of Scope

- HR zone arc ring (data-driven arc encoding was considered and rejected in favour of decorative pulsing ring)
- Real BLE device picker overlay during active workout (connect action triggers rescan via service intent)
- Any changes to domain/service logic beyond the `avgHr` running average
- History, Progress, PostRun screens (unchanged)
