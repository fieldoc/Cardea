# Account Screen Standardization & Social Linking — Design Document

**Date:** 2026-03-23
**Status:** Draft
**Scope:** Visual standardization of AccountScreen to match HomeScreen quality; add social/export linking section.

---

## 1. Problem Statement

The Account screen has functional content but doesn't match the visual polish of HomeScreen and other screens:

1. **Flat background** — uses `bgPrimary` instead of the radial gradient pattern from HomeScreen
2. **No social/export linking section** — no way to connect Strava, Garmin, Apple Health, or export data
3. **"About" footer** — bare text instead of a properly styled card
4. **Section spacing inconsistencies** — mixed spacer sizes, some sections feel cramped
5. **Missing version info card** — version is only shown as tiny footer text

## 2. Design Decisions

### 2.1 Background — Radial Gradient (match HomeScreen)

Replace flat `bgPrimary` with the same `Brush.radialGradient(bgSecondary → bgPrimary)` used in HomeScreen.

### 2.2 Social & Export Section (new)

Add a "Connected Services" section between Workout and the About footer. Three service cards:

| Service | Icon | Status | Action |
|---------|------|--------|--------|
| Strava | Custom Strava-colored icon box (orange) | "Not connected" / "Connected" | Tap to connect (placeholder toast) |
| Garmin Connect | Custom Garmin-colored icon box (blue) | "Not connected" / "Connected" | Tap to connect (placeholder toast) |
| Apple Health | Custom Health-colored icon box (green) | "Not connected" / "Connected" | Tap to connect (placeholder toast) |

Each row: glass card row with colored icon box (30dp, rounded 8dp), service name, status text, and a chevron icon. Tapping shows a toast "Coming soon" since no backend exists.

All three services rendered inside a single `GlassCard` with `HorizontalDivider` separators (matching the Configuration and Audio sections pattern).

### 2.3 Data Export Row

Add an "Export Workouts" row inside the Connected Services card (below the three service rows). Icon: download icon. Subtitle: "Export all workout data as CSV". Tap → placeholder toast.

### 2.4 About Card (upgraded)

Replace the bare text footer with a proper `GlassCard` containing:
- App name + tagline
- Version from BuildConfig
- Centered layout with subtle gradient accent

### 2.5 Section Spacing Standardization

Standardize all inter-section gaps to 20dp (already mostly consistent, just formalize).

## 3. Architecture

**No ViewModel changes needed.** Social linking is UI-only placeholders — no new state, no new repositories. The only change is in `AccountScreen.kt`.

**New composables (private, in AccountScreen.kt):**
- `ConnectedServiceRow(icon, iconColor, name, connected, onClick)` — reusable row for each service
- `AboutCard()` — the upgraded about section

## 4. Implementation Notes

- Use the existing `SettingSection` / `SettingToggleRow` patterns where possible
- Social service icon boxes use custom background colors (Strava orange, Garmin blue, Health green) but maintain the same 30dp rounded box pattern used by setting icons
- Chevron (`Icons.AutoMirrored.Filled.KeyboardArrowRight`) indicates tappable rows
- Toast via `LocalContext.current` for "Coming soon" message
- Background gradient uses `remember` keyed on colors, same pattern as HomeScreen

## 5. Out of Scope

- Actual OAuth integration with any service
- Real data export functionality
- Account/auth system
- Profile photo upload
