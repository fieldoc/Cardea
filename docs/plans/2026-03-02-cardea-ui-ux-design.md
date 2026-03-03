# Cardea UI/UX Redesign — Design Document

**Date:** 2026-03-02
**Status:** Approved
**Scope:** Full visual redesign + new screens (Home, Account). Content and logic locked.

---

## 1. Overview

The app is renamed from **HR Coach** to **Cardea** (after the Roman Goddess of health and thresholds). This document defines the complete UI/UX redesign: design tokens, new screens, navigation expansion, and branding integration. The visual specification in `ui_ux_spec.md` is canonical; this document describes the implementation strategy.

All business logic, metric calculations, database schema, and data shown remain unchanged. Only visual presentation changes.

---

## 2. Design Tokens (Global)

All tokens implemented in `Color.kt` and referenced by `CardeaTheme`.

### 2.1 Backgrounds
```
CardeaBgPrimary   = #0B0F17
CardeaBgSecondary = #0F1623
Background rendered as radial gradient: circle at top-left, #0F1623 → #0B0F17
```

### 2.2 Glass Surface System
```
Glass fill:      linear-gradient(180deg, rgba(255,255,255,0.06), rgba(255,255,255,0.02))
Glass border:    rgba(255,255,255,0.06)  [≈ Color(0x0FFFFFFF)]
Glass highlight: rgba(255,255,255,0.08)  [≈ Color(0x14FFFFFF)]
Backdrop blur:   12dp blur (via Compose BlurMaskFilter or RenderEffect API 31+)
Corner radius:   18dp
```

### 2.3 Cardea Core Gradient
```
135deg: #FF5A5F → #FF2DA6 → #5B5BFF → #00D1FF
Stops:  0%        35%        65%        100%
```
Used for: active nav icons, Start Workout button, Efficiency Ring foreground, active recording indicators.

### 2.4 Text Colors
```
CardeaTextPrimary   = #FFFFFF
CardeaTextSecondary = #9AA4B2
CardeaTextTertiary  = #5A6573  (inactive/disabled)
```

### 2.5 Zone Colors (preserved, adjusted names)
```
ZoneGreen = #34D399  (in-zone)
ZoneAmber = #F59E0B  (warning)
ZoneRed   = #EF4444  (out-of-zone)
```

---

## 3. Navigation Architecture

**5 bottom tabs:** Home · Workout · History · Progress · Account

| Tab | Route | Screen | Notes |
|-----|-------|---------|-------|
| Home | `home` | `HomeScreen` | New — dashboard overview |
| Workout | `setup` | `SetupScreen` | Existing — renamed tab |
| History | `history` | `HistoryListScreen` | Existing |
| Progress | `progress` | `ProgressScreen` | Existing |
| Account | `account` | `AccountScreen` | New — profile + settings |

**Special routes (not in bottom bar):**
- `splash` — Cardea splash/landing
- `workout` — `ActiveWorkoutScreen` (shown during live workout, replaces nav)
- `history/{workoutId}` — detail
- `postrun/{workoutId}` — post-run summary

**Bottom bar behavior:**
- Visible on: Home, Workout (setup), History, Progress, Account
- Hidden during: active workout, splash, detail screens
- Height: 72dp, glass material, Cardea gradient for active icon fill

**Start destination:** `splash` → auto-navigates to `home` after animation.

---

## 4. Screen Designs

### 4.1 SplashScreen (redesigned)
- Full dark background (#0B0F17)
- Cardea vector logo centered (heart + orbit ring, gradient colored)
- "Cardea" wordmark below logo, headlineLarge weight 600
- Tagline: "Listen to your body." in CardeaTextSecondary
- Logo pulses with subtle scale animation (1.0 → 1.06, 800ms, ease-in-out)
- Duration: 1800ms before navigating to Home
- No progress bar or loading indicator

### 4.2 HomeScreen (new)
Layout: vertical scroll, 16dp horizontal padding, 16dp card spacing.

**Section 1 — Header**
- Left: "Cardea" wordmark (small, gradient text) + "Good [morning/afternoon/evening]" greeting
- Right: Profile avatar (initial/placeholder, taps → Account tab)

**Section 2 — Efficiency Ring Card (glass)**
- 90dp ring, 6dp stroke
- Background ring: rgba(255,255,255,0.08)
- Foreground: Cardea gradient
- Inner number: workouts this week / target (displayed as %, 32sp weight 500)
- Label: "Weekly Activity"

**Section 3 — Last Run Summary Card (glass)**
- Shows: date, distance, duration, avg HR
- If no runs: empty state with subtle prompt to start first run
- Taps → History detail for that run

**Section 4 — Start Run CTA**
- Full-width gradient button (Cardea gradient, 56dp height, 14dp radius)
- Label: "Start a Run" (labelLarge, bold, white)
- Taps → Workout tab

**Section 5 — Quick-links row**
- Two pill buttons: "Progress" → Progress tab, "History" → History tab
- Glass-styled, secondary accent

**Section 6 — Weekly HR Trend (graph card, glass)**
- Mini line graph using Cardea gradient stroke + glow layer
- Grid lines: rgba(255,255,255,0.04)
- Data: last 7 days avg HR if available; empty state if no data

### 4.3 AccountScreen (new)
Layout: vertical scroll, 16dp padding, 16dp card spacing.

**Profile Card (glass)**
- Avatar circle: 64dp, gradient border, initial letter or placeholder icon
- Name: "Runner" (placeholder, editable in future)
- Subtitle: total workout count from repository

**Settings Sections (glass cards)**

*Device*
- BLE monitor preference (display-only for now: currently connected device name)

*Audio & Alerts*
- Google Maps API key (moved from Setup dialog)
- Voice coaching (Off / Minimal / Full segmented button)
- Alert sound volume (slider 0–100%)
- Vibration alerts (toggle)

*About*
- App version
- "Cardea" name + version string

Row height: 52dp. Dividers: rgba(255,255,255,0.06).
Active toggles use Cardea gradient accent.

### 4.4 SetupScreen (visual upgrade, now "Workout" tab)
- Remove Maps API key dialog (moved to Account)
- Apply Cardea gradient to Start Workout button
- Glass card visual update (18dp radius, updated border)
- Mode selector chips: glass styled with gradient indicator for selected
- Card titles use CardeaTextPrimary

### 4.5 Navigation Bar
- Height: 72dp
- Background: linear-gradient(180deg, rgba(255,255,255,0.08), rgba(255,255,255,0.03))
- Top border: rgba(255,255,255,0.06), 1dp
- Active icon: gradient fill (via Canvas shader)
- Inactive icon: CardeaTextTertiary (#5A6573)
- Icon size: 24dp, label size: 11sp

### 4.6 Charts (all screens)
All graphs updated to Cardea spec:
- Stroke: 2dp, Cardea gradient
- Glow layer: duplicate line, blur 6dp, opacity 0.15
- Cap style: round
- Grid lines: rgba(255,255,255,0.04)

---

## 5. Branding Integration

**Where the Cardea brand appears:**
- Splash: full logo + wordmark
- Home: small wordmark in header, avatar taps to Account
- Navigation: gradient active icons (brand color without being loud)
- Start Run button: gradient (brand moment before every run)
- Efficiency Ring foreground: gradient
- All CTAs: gradient fills

**What does NOT change:**
- Metric labels (e.g. "Avg HR", "Distance", "Resting Heart Rate")
- Data calculations or formulas
- Database schema
- Business logic or alert behavior

---

## 6. Motion Spec

- Animation duration: 250ms
- Easing: ease-out (FastOutSlowIn)
- Allowed: fade, position
- Not used: bounce, spring (except for existing HR pulse which uses spring)
- Nav transitions: fade + horizontal slide (existing 300ms, acceptable)

---

## 7. Assets Required

- `ic_cardea_logo.xml` — vector drawable: stylized heart with ECG line + orbit ring
- Gradient is applied via Compose brush at render time (not baked into drawable)
- Logo used at: 64dp (splash), 32dp (home header), 24dp (potential nav)

---

## 8. Document Updates

The following docs are updated/superseded by this design:
- `ui_ux_spec.md` — canonical visual spec (unchanged, this doc implements it)
- `docs/plans/2026-02-25-hr-coaching-app-design.md` — add note: "Superseded by Cardea rebrand per 2026-03-02-cardea-ui-ux-design.md for UI layer; domain/service design remains valid"
- `CLAUDE.md` — update app_name reference from "HR Coach" to "Cardea", add Cardea theme tokens section

---

## 9. Out of Scope

- User authentication / real accounts (Account screen is settings-only for now)
- Cloud sync or profile persistence beyond local prefs
- Animated gradient (static gradient only for performance)
- Custom font (system font unless spec explicitly requires otherwise)
- Android adaptive icon redesign (tracked separately)
