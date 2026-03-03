# Guided Workout Presets — UX Design (Approach B)

**Date:** 2026-03-02
**Status:** Approved
**Implements:** `docs/plans/2026-03-01-preset-workout-profiles.md` (amends Tasks 10, 11; adds Tasks 13, 14)

---

## Overview

Replaces the freeform distance-profile segment builder with a scientifically-backed preset library of 8 workout types. Uses Approach B: Cardea-native glass cards with a segment timeline strip, lazy HRmax onboarding via dialog + persistent AccountScreen field, and segment countdown (time remaining + next segment) in the active workout screen.

---

## 1. Preset Selection UI (SetupScreen)

### Mode selector
- "Distance" chip renamed to **"Guided Workout"**
- No structural change to the 3-chip `ModeSelector` row

### PresetGrid
- Vertical list inside the existing `TargetCard` `GlassCard`
- 4 section headers: BASE AEROBIC / THRESHOLD / INTERVALS / RACE PREP
- Section header style: `MaterialTheme.typography.labelSmall`, color `CardeaGradient` via `ShaderBrush` (or `MaterialTheme.colorScheme.primary` fallback)
- 8 `PresetCard` items, one per preset

### PresetCard

**Unselected state:**
```
GlassCard (glassBorder, no extra border)
  Row:
    Column(weight=1f):  name (titleSmall, SemiBold) / subtitle (bodySmall, onSurfaceVariant)
    Column(End):        durationLabel (labelSmall) / intensityLabel (labelSmall, secondary)
  SegmentTimelineStrip(preset)  ← 6dp tall, bottom of card
```

**Selected state:**
```
GlassCard + 2dp border drawn with CardeaGradient brush (same pattern as Start Workout button)
  (same content as above)
```

### SegmentTimelineStrip composable

A `Canvas`-drawn horizontal bar, 6dp tall, `RoundedCornerShape(3.dp)`, full card width.

Segment colors by intensity bucket (derived from `targetHr / maxHr` approximation using canonical percentages):
- **Low** (< 75% HRmax): `Color(0xFF2B8C6E)` — green
- **Moderate** (75–84%): `Color(0xFFE8A838)` — amber
- **High** (≥ 85%): `Color(0xFFFF5A5F)` — Cardea red (first gradient stop)

Since `maxHr` is not known at render time, intensity is inferred from the preset's `intensityLabel`:
- "Easy" → all low
- "Moderate" → all moderate
- "Hard" / "High" → all high
- "Very High" → alternating high/low (interval pattern)
- Steady-state presets: single solid bar
- Time-based presets: blocks proportional to `durationSeconds`, gap of 2dp between blocks
- Distance-based presets: blocks proportional to segment distance spans

For presets without `maxHr` context, use a fixed canonical color palette per segment label pattern (warmup=green, interval=red, recovery=green/amber, cooldown=green).

### Escape hatch
`TextButton("Custom segment editor…")` below the grid. Sets `selectedPresetId = "custom"`, shows the old segment editor inline. No mode switch required.

---

## 2. HRmax Onboarding

### Lazy dialog (first-tap flow)

Triggered when user taps a preset card and `maxHr == null`.

```
AlertDialog:
  title: "Your Max Heart Rate"
  text:
    "Presets use % of your max HR to personalise targets."
    OutlinedTextField(value=maxHrInput, label="Max HR (bpm)", placeholder="e.g. 185")
    bodySmall: "Tip: 220 − age is a rough guide. A field test gives better results."
  confirmButton: Button("Confirm") → viewModel.confirmMaxHr()
  dismissButton: TextButton("Cancel") → viewModel.dismissHrMaxDialog()
```

On confirm: pending preset auto-selected, dialog dismissed — zero extra taps.

### AccountScreen profile row (persistent access)

New section in `AccountScreen` below Maps API key:

```
GlassCard:
  Text("Profile", titleLarge)
  Text("Max Heart Rate", bodyLarge)
  Row:
    OutlinedTextField(value=maxHrText, label="Max HR (bpm)", keyboardType=Number)
    Button("Save") → viewModel.saveMaxHr()
  bodySmall: "Used to personalise all preset HR targets."
```

Reads `UserProfileRepository.getMaxHr()` on `AccountViewModel` init, writes on Save.

---

## 3. Active Workout Screen — Interval Countdown

### When to show
Only when `WorkoutSnapshot.segmentLabel != null` (i.e. preset is time-based with labelled segments).

### Layout (replaces distance display for time-based workouts)

```
Column:
  Text(segmentLabel, titleMedium, primary color)        ← "Interval 2 of 4"
  Row:
    Icon(Icons.Default.Timer, 16dp)
    Text("$mm:$ss remaining", bodyMedium)               ← countdown to segment end
  if (nextSegmentLabel != null):
    Text("next › $nextSegmentLabel", bodySmall, CardeaTextTertiary)
```

### Computation (WorkoutViewModel)

`WorkoutViewModel` derives `segmentCountdownSeconds` from `WorkoutSnapshot.elapsedSeconds`:
1. Look up `WorkoutConfig` from `PresetLibrary` using `WorkoutSnapshot`'s embedded `presetId` (already stored on `WorkoutConfig`)
2. Walk segments to find current segment boundary → `segmentEndSeconds`
3. `countdown = segmentEndSeconds - elapsedSeconds`
4. `nextLabel` = label of the segment after current (null on last segment)

Exposed as a derived `StateFlow` in `WorkoutViewModel`, recomputed on each `elapsedSeconds` emission from `WorkoutState`.

---

## Implementation delta vs. existing plan

The existing `2026-03-01-preset-workout-profiles.md` plan covers Tasks 1–12. Approach B adds/amends:

| Task | Change |
|---|---|
| Task 10 (SetupScreen) | Use `GlassCard` + gradient border instead of Material3 `Card`; add `SegmentTimelineStrip` |
| Task 11 (WorkoutScreen) | Replace total elapsed with countdown + next-segment label |
| Task 13 (new) | Add MaxHr row to AccountScreen + AccountViewModel |
| Task 14 (new) | `SegmentTimelineStrip` composable (Canvas-drawn) |
