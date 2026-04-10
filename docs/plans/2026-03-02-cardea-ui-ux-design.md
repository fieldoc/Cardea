# Cardea UI/UX Design — Authoritative Specification

**Date:** 2026-03-02  
**Last revised:** 2026-04-09 (unified & polished — token drift fixed, light mode added, typography formalised)  
**Status:** Approved  
**Scope:** Design tokens, all screens, navigation, branding, motion.

> **Implementation note:** When this spec conflicts with `Color.kt`, the spec is wrong — fix the spec, not the code. This document was updated on 2026-04-09 to reflect actual implementation values.

---

## 1. Overview

Cardea (Roman Goddess of health and thresholds) replaces HR Coach. This spec defines the complete visual system. Business logic, metric calculations, database schema, and data shown are **not** in scope.

**Theme:** Dark glass-morphic athletic UI. Matte black canvas, translucent glass surfaces, and one precision gradient. Light mode is a supported user preference — see §2.6.

---

## 2. Design Tokens

All tokens live in `ui/theme/Color.kt` and are referenced only through `CardeaTheme`.

### 2.1 Backgrounds

| Token | Value | Use |
|-------|-------|-----|
| `CardeaBgPrimary` | `#050505` | Screen backgrounds |
| `CardeaBgSecondary` | `#0D0D0D` | Elevated surfaces, cards at rest |

Rendered as a radial gradient anchored at top-left: `#0D0D0D → #050505`.

### 2.2 Glass Surface System

Glass is the primary surface language. Three properties define a glass surface:

| Property | Value | Notes |
|----------|-------|-------|
| Fill | `linear-gradient(180deg, rgba(255,255,255,0.13), rgba(255,255,255,0.08))` | `GlassSurface = 0x33FFFFFF` |
| Border | `rgba(255,255,255,0.10)` | `GlassBorder = 0x1AFFFFFF` |
| Highlight | `rgba(255,255,255,0.04)` | `GlassHighlight = 0x0AFFFFFF` — top edge only |
| Backdrop blur | 12dp | `RenderEffect` API 31+, `BlurMaskFilter` fallback |

**Corner radii are tied to the 3-tier hierarchy (§2.3):** 18dp / 14dp / 12dp.

### 2.3 Cardea Gradient — 3-Tier Usage Hierarchy

The gradient is NOT applied uniformly. Overuse destroys its impact ("colour vomit"). Three distinct variants exist:

**Full gradient** `CardeaGradient` — 4-stop, 135°:
```
#FF4D5A → #FF2DA6 → #4D61FF → #00E5FF
  0%        35%       65%       100%
```

**CTA gradient** `CardeaCtaGradient` — 2-stop (Red → Pink):
```
#FF4D5A → #FF2DA6
```
Warmer and more energetic — used on buttons and action chips.

**Nav gradient** `CardeaNavGradient` — 2-stop (Blue → Cyan):
```
#4D61FF → #00E5FF
```
Cooler — used on active nav icons to reduce visual noise in the bar.

**Tier assignment per screen:**

| Tier | Treatment | Corner | What gets it |
|------|-----------|--------|-------------|
| **Tier 1** | Gradient text, gradient border, or gradient-filled CTA | 18dp | **One metric per screen** — the single most important/actionable value. Primary CTA button. |
| **Tier 2** | White text on plain glass | 14dp | Supporting metrics |
| **Tier 3** | `CardeaTextSecondary` on glass | 12dp | Ambient info, labels |

Gradient also appears on: active nav icons (`CardeaNavGradient`), the efficiency ring foreground. It does **not** appear on chart lines, every CTA, or every ring simultaneously.

### 2.4 Text Colors

| Token | Dark value | Light value | Use |
|-------|-----------|-------------|-----|
| `CardeaTextPrimary` | `#FFFFFF` | `#1A1A1A` | Headings, key metrics |
| `CardeaTextSecondary` | `#A1A1AA` | `#6B6B73` | Labels, captions |
| `CardeaTextTertiary` | `#52525B` | `#A1A1AA` | Inactive, disabled, Tier 3 ambient |

### 2.5 Zone Colors

These are semantic — do not use them decoratively.

| Token | Value | Meaning |
|-------|-------|---------|
| `ZoneGreen` | `#22C55E` | In-zone (target reached) |
| `ZoneAmber` | `#FACC15` | Warning (approaching boundary) |
| `ZoneRed` | `#EF4444` | Out-of-zone (action required) |

### 2.6 Light Mode

Light mode is a first-class supported preference. The system uses `CardeaLightBg*` and `LightGlass*` tokens from `Color.kt`. The gradient, zone colors, and gradient variants are identical in both modes. Glass borders invert to `rgba(0,0,0,0.10)` (`LightGlassBorder = 0x1A000000`).

Do **not** flag light mode as a bug.

---

## 3. Typography

System font (no custom typeface). All sizes in sp.

| Role | Size | Weight | Token/Usage |
|------|------|--------|-------------|
| Hero metric | 40sp | Bold | Primary stat tiles (goal, streak) |
| Section headline | 30sp | SemiBold | PULSE card session type |
| Title | 20sp | SemiBold | Card headers |
| Body | 15sp | Regular | Greeting, descriptions |
| Label | 13sp | Medium | Chip labels, nav labels |
| Caption | 11sp | Regular | Nav labels, fine print |

Avoid mixing more than 3 size levels per screen. Size AND color signal importance — a small, secondary-colored label paired with a large, white metric is the core visual pattern.

---

## 4. Navigation

### 4.1 Tab Structure

**5 tabs, bottom bar:**

| Tab | Route | Screen |
|-----|-------|--------|
| Home | `home` | `HomeScreen` |
| Workout | `setup` | `SetupScreen` |
| History | `history` | `HistoryListScreen` |
| Progress | `progress` | `ProgressScreen` |
| Account | `account` | `AccountScreen` |

**Off-tab routes:**
- `splash` — branded entry point
- `workout` — `ActiveWorkoutScreen` (hides bottom bar)
- `history/{workoutId}` — detail
- `postrun/{workoutId}` — post-run summary

### 4.2 Bottom Bar Spec

| Property | Value |
|----------|-------|
| Height | 72dp |
| Background | `linear-gradient(180deg, rgba(255,255,255,0.08), rgba(255,255,255,0.03))` |
| Top border | `GlassBorder` (1dp) |
| Active icon | `CardeaNavGradient` fill via `BlendMode.SrcIn` + `CompositingStrategy.Offscreen` |
| Inactive icon | `CardeaTextTertiary` |
| Icon size | 24dp |
| Label size | 11sp |

Bottom bar is hidden during: active workout, splash, history detail, post-run summary.

**Start destination:** `splash` → auto-navigates to `home` after 1800ms.

---

## 5. Screen Specifications

### 5.1 Splash

- Background: `CardeaBgPrimary` solid
- `CardeaLogo` composable, centered: 180dp (`LogoSize.LARGE`), gradient-filled
- "Cardea" wordmark: 20sp, weight 600, gradient text
- Tagline: "Listen to your body." — `CardeaTextSecondary`, 15sp
- Logo animation: scale `1.0 → 1.06`, 800ms, `FastOutSlowIn`, repeating
- Auto-navigate to `home` at 1800ms — no progress bar

### 5.2 Home — PULSE Layout

Fixed-height, non-scrollable. 16dp horizontal padding on the bottom half.

**Greeting Row**  
Left: "Good [morning / afternoon / evening]" — 15sp, `CardeaTextSecondary`.  
Right: two 32dp icon-buttons (sensor status + profile), Canvas-drawn icons, glass borders.

**PULSE Hero** — 195dp fixed  
Session name (label at 13sp `CardeaTextSecondary`), session type (30sp SemiBold, white), detail line, zone pill.  
Background: Canvas-drawn ECG gradient line at 0.45 opacity with radial glow. Subtle gradient wash behind text.

**CTA Row** — 56dp fixed  
Full-width `CardeaButton` using `CardeaCtaGradient`. Label: "Start Session" (bootcamp active) or "Set Up Bootcamp" (none).

**Bottom Half** — fills remaining height, no scroll

```
┌─────────────────────────────────────────────────────┐
│  GoalTile (flex 1.2, Tier 1)  │  StreakTile (flex 0.8, Tier 2)  │  ← Primary Row (weighted)
├─────────────────────────────────────────────────────┤
│  BootcampTile (Tier 2)        │  VolumeTile (Tier 2)             │  ← Mid Row (IntrinsicSize.Min)
├─────────────────────────────────────────────────────┤
│  Coaching Strip — ECG icon + single advisory line   │  ← Tier 3
└─────────────────────────────────────────────────────┘
```

**GoalTile** — Tier 1: gradient border, 40sp gradient-painted number, 18dp corners.  
**StreakTile** — Tier 2: plain glass, 40sp white number, 18dp corners.  
**BootcampTile** — Tier 2: 44dp ring, gradient progress bar, 14dp corners.  
**VolumeTile** — Tier 2: DST/MIN bar columns, 20% white fill, 14dp corners.  
**Coaching Strip** — Tier 3: ECG Canvas icon at 0.5 opacity, `CardeaTextSecondary` line, 12dp corners.

Design rules: Canvas icons only (no emoji, no Material icons on primary metrics). Size AND color both signal importance — gradient reserved for one accent metric + CTA.

### 5.3 Workout Setup (SetupScreen)

- Mode selector chips: glass-styled, gradient border + gradient indicator on selected chip
- `TargetCard` using `GlassCard`, 18dp corners
- Guided workout mode: `PresetGrid` inside `TargetCard`:
  - Section headers: `labelSmall`, gradient via `ShaderBrush`
  - 8 `PresetCard` items; selected state: 2dp `CardeaGradient` border
  - `SegmentTimelineStrip`: 6dp Canvas bar, colors by intensity (low=`#2B8C6E`, moderate=`#E8A838`, high=`#FF4D5A`)
  - Escape hatch: `TextButton("Custom segment editor…")` below the grid
- Start Workout button: `CardeaCtaGradient`, 56dp height, 18dp corners (Tier 1 CTA)

### 5.4 Active Workout (WorkoutScreen)

Standard display: live HR (large, zone-colored), zone label, distance, pace.

**Interval countdown** (preset workouts only — when `segmentLabel != null`):
```
"Interval 2 of 4"          ← titleMedium, primary
⏱ "02:34 remaining"         ← bodyMedium, Timer icon 16dp
"next › Recovery"           ← bodySmall, CardeaTextTertiary
```
Countdown replaces the distance display for time-based preset segments.

Bottom bar hidden. Full-screen layout.

### 5.5 History

Existing screens. Visual upgrades:
- List items: `GlassCard`, 14dp corners
- Route heatmap: `ZoneGreen / ZoneAmber / ZoneRed` stroke
- Chart lines: 2dp, `CardeaGradient` stroke, 6dp glow layer at 0.15 opacity

### 5.6 Progress

Existing screen. Chart spec (§5.8) applied uniformly.

### 5.7 Account (AccountScreen)

Vertical scroll, 16dp padding, 16dp card spacing. All cards: `GlassCard`, glass border, `CardeaTextPrimary` titles.

**Profile Card** (Tier 1 card — gradient border)
- 64dp avatar circle, gradient border, initial letter
- Name: "Runner" (placeholder)
- Subtitle: total workout count

**Max Heart Rate** (inside Profile card or own row)
- `OutlinedTextField` for `maxHr` (bpm, numeric)
- "Save" button → `viewModel.saveMaxHr()`
- Caption: "Used to personalise all preset HR targets."

**Device**
- BLE monitor name (display-only)

**Audio & Alerts**
- Voice coaching: Off / Minimal / Full segmented button; active segment uses `CardeaCtaGradient` accent
- Alert sound volume: slider 0–100% — persist only on `onValueChangeFinished`, not `onValueChange`
- Vibration alerts: toggle; active uses gradient accent

**Maps**
- Google Maps API key field

**About**
- App version string

Row height: 52dp. Section dividers: `GlassBorder`. Active toggles: `CardeaCtaGradient` accent.

### 5.8 Charts (all screens)

| Property | Value |
|----------|-------|
| Stroke width | 2dp |
| Stroke color | `CardeaGradient` |
| Glow layer | Duplicate stroke, 6dp blur, 0.15 opacity |
| Cap style | Round |
| Grid lines | `rgba(255,255,255,0.04)` dark / `rgba(0,0,0,0.08)` light |
| Background | Inside `GlassCard` |

All charts are custom Canvas-drawn (`ui/charts/`). No charting library.

---

## 6. Branding Integration

Gradient appears at:
- Splash logo (full `CardeaGradient`)
- Active nav icons (`CardeaNavGradient`)
- Efficiency ring foreground (full `CardeaGradient`)
- Primary CTA per screen (`CardeaCtaGradient`)
- Tier 1 metric accent per screen (gradient text or border)

Gradient does **not** appear on every CTA, every chart line, or every ring simultaneously.

The `CardeaLogo` composable (`ui/components/CardeaLogo.kt`): heart + ECG line + orbital ring, gradient-filled via Compose brush at render time. Sizes: `LogoSize.LARGE` (180dp, splash), `LogoSize.SMALL` (32dp, nav/header).

---

## 7. Motion

| Property | Value |
|----------|-------|
| Standard duration | 250ms |
| Easing | `FastOutSlowIn` |
| Allowed transitions | Fade, position offset |
| Disallowed | Bounce, spring (except existing HR pulse ring, which uses spring) |
| Nav transitions | Fade + horizontal slide, 300ms |
| Screen entrance | Staggered fade-in: first element at 0ms, each subsequent +40ms |
| Splash logo pulse | Scale `1.0 → 1.06`, 800ms, `FastOutSlowIn`, repeating |
| List items | Fade-in with 24dp upward translate, staggered by index × 30ms |

Motion should feel purposeful and athletic — quick and precise, never bouncy or whimsical.

---

## 8. Component Library

| Composable | Location | Notes |
|-----------|----------|-------|
| `GlassCard` | `ui/components/GlassCard.kt` | All glass surface cards |
| `CardeaLogo` | `ui/components/CardeaLogo.kt` | Two sizes only |
| `CardeaButton` | `ui/components/` | Primary CTA, `CardeaCtaGradient` fill |
| `SegmentTimelineStrip` | `ui/components/` | Canvas-drawn intensity bar |
| Charts | `ui/charts/` | BarChart, PieChart, ScatterPlot — Canvas only |

---

## 9. Out of Scope

- User authentication / cloud sync
- Animated gradient (static brush only for performance)
- Custom typeface
- Android adaptive icon redesign (tracked separately)
- Bounce/spring animations beyond the existing HR pulse
