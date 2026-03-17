# UI/UX Unification Pass — Design Document

**Date:** 2026-03-02
**Status:** Approved
**Scope:** In-place patches to align HistoryDetailScreen, PostRunSummaryScreen, HistoryListScreen, and ProgressScreen with the Cardea design spec.

---

## Context

The Cardea rebrand was applied to Splash, Home, Account, ActiveWorkout, SetupScreen, and NavBar. Four screens still use pre-Cardea patterns (old backdrops, opaque glass, flat-color CTAs, flat chart lines). This pass brings them into spec without structural rewrites.

**Approach:** In-place patching. No screen-level restructuring, no new components.

---

## Changes by Screen

### 1. HistoryDetailScreen (most diverged)

| Element | Current | Target |
|---|---|---|
| Scaffold `containerColor` | `Color(0xFF061019)` hardcoded | `Color.Transparent` |
| `DetailBackdrop` | `verticalGradient` of teal-blues | `radialGradient(CardeaBgSecondary → CardeaBgPrimary)` |
| `DetailGlass` card fill | `Color(0xCC0D1824)` — 80% opaque | `Color(0x0FFFFFFF)` glass fill |
| `StatsCard` corner radius | `RoundedCornerShape(30.dp)` | 18dp |
| `MoreActionsCard` corner radius | `RoundedCornerShape(30.dp)` | 18dp |
| `MoreActionsCard` primary CTA | `Button()` flat primary color | Gradient `Box` pattern (same as SetupScreen) |
| Map overlay surfaces | `DetailGlass` fill (opaque) | `Color(0x1AFFFFFF)` + `GlassBorder` border |

### 2. PostRunSummaryScreen

| Element | Current | Target |
|---|---|---|
| "Done" CTA button | `Button(containerColor = primary)` flat blue | Gradient `Box` pattern |
| "View on Map" / other secondary | `OutlinedButton` | Leave as-is |

### 3. HistoryListScreen

| Element | Current | Target |
|---|---|---|
| `WorkoutCard` corner radius | `RoundedCornerShape(30.dp)` | 18dp |
| `HistoryEmptyState` corner radius | `RoundedCornerShape(32.dp)` | 18dp |
| `HistoryGlass` opacity | `Color(0x0AFFFFFF)` (~4%) | `Color(0x0FFFFFFF)` (~6%, per spec) |
| Empty state `OutlinedButton` | outlined | Gradient `Box` pattern |

### 4. ProgressScreen

| Element | Current | Target |
|---|---|---|
| `TrendLineChart` stroke | Flat `MaterialTheme.colorScheme.primary` | `CardeaGradient` brush on `drawPath` |
| `TrendLineChart` glow | None | Duplicate path with `BlurMaskFilter(6dp)`, alpha 0.15 |

---

## Out of Scope

- Gradient text for "Run Complete!" heading (PostRunSummaryScreen) — heading is transient, not worth the ShaderBrush complexity
- ProgressScreen `FilterRow` segmented button — custom glass chip styling is a separate pass
- HistoryListScreen: no structural refactor to use `GlassCard` composable

---

## Gradient Button Pattern

Established in SetupScreen. Applied consistently to all primary CTAs:

```kotlin
Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(CardeaGradient)
        .clickable(onClick = onAction),
    contentAlignment = Alignment.Center
) {
    Text(label, color = CardeaTextPrimary, style = MaterialTheme.typography.labelLarge)
}
```

## Gradient Chart Line Pattern

```kotlin
// Glow pass (drawn first, under the main line)
drawIntoCanvas { canvas ->
    canvas.nativeCanvas.drawPath(nativePath, glowPaint) // BlurMaskFilter 6dp, alpha 0.15
}
// Main gradient line
drawPath(path, brush = CardeaGradient, style = Stroke(width = 5f, cap = StrokeCap.Round))
```
