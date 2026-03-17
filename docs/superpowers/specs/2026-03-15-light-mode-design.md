# Light Mode Design Spec

**Date**: 2026-03-15
**Status**: Draft

## Overview

Add a consciously-designed light mode to Cardea with a 3-way theme toggle (System / Light / Dark) in the Profile screen. The current dark glass-morphic identity translates to a "frosted paper" aesthetic in light mode — warm off-white backgrounds, soft shadows replacing glass borders, and the same neon gradient accent system.

## Design Decisions

### 1. Theme Switching UX

- **Location**: Profile screen, new "Appearance" section between the Profile hero card and "Configuration"
- **Control**: 3-segment `SingleChoiceSegmentedButtonRow` — System | Light | Dark
- **Default**: System (follows `isSystemInDarkTheme()`)
- **Persistence**: `SharedPreferences` via a new `ThemePreferencesRepository` (consistent with existing repos like `AudioSettingsRepository`)
- **Propagation**: `MainActivity` reads the preference and passes it to `CardeaTheme`, which selects the appropriate color scheme

### 2. Light Mode Color Palette

The light palette inverts the relationship: dark content on warm-white backgrounds, with the same neon gradient accents.

| Token | Dark Value | Light Value | Rationale |
|-------|-----------|-------------|-----------|
| `bgPrimary` | `#050505` (matte black) | `#FAFAFA` (warm off-white) | Not pure white — reduces eye strain, feels premium |
| `bgSecondary` | `#0D0D0D` (elevated black) | `#F0F0F2` (cool gray-white) | Card-level elevation distinction |
| `glassBorder` | `#1AFFFFFF` (10% white) | `#1A000000` (10% black) | Same opacity, inverted base |
| `glassHighlight` | `#0AFFFFFF` (4% white) | `#0A000000` (4% black) | Subtle depth cue |
| `glassSurface` | `#33FFFFFF` (20% white) | `#0F000000` (6% black) | Lower alpha in light — dark overlays are visually heavier |
| `textPrimary` | `#FFFFFF` (white) | `#1A1A1A` (near-black) | High contrast body text |
| `textSecondary` | `#A1A1AA` (neutral gray) | `#6B6B73` (medium gray) | Readable secondary text on white |
| `textTertiary` | `#52525B` (dark gray) | `#A1A1AA` (light gray) | Hints and disabled states |
| `surfaceVariant` | `#18181B` | `#E8E8EC` | Slight offset from bgSecondary |

#### Unchanged tokens (same in both modes)
- **Gradient stops**: `GradientRed`, `GradientPink`, `GradientBlue`, `GradientCyan` — these saturated neons pop on both backgrounds
- **`CardeaGradient`**, **`CardeaCtaGradient`**, **`CardeaNavGradient`** — same brush definitions
- **Zone colors**: `ZoneGreen`, `ZoneAmber`, `ZoneRed` — athletic neons work on both. Amber (`#FACC15`) is the one exception — it needs a darker variant in light mode for text-on-white legibility
- **Achievement base hues**: `AchievementSlate`, `AchievementSky`, `AchievementGold` — unchanged

#### Adjusted achievement alpha tokens
Achievement border/bg tokens use alpha-over-background. In light mode the alpha values increase slightly since dark-on-white needs more opacity to be visible:

| Token | Dark Alpha | Light Alpha |
|-------|-----------|-------------|
| `AchievementSlateBorder` | 15% | 20% |
| `AchievementSlateBg` | 8% | 10% |
| `AchievementSkyBorder` | 25% | 30% |
| `AchievementSkyBg` | 8% | 10% |
| `AchievementGoldBorder` | 30% | 35% |
| `AchievementGoldBg` | 12% | 14% |

### 3. GlassCard → Adaptive Surface Card

The dark mode GlassCard effect (low-alpha white gradient fill + 1dp white-alpha border) is invisible on a white background. In light mode, `GlassCard` becomes a "frosted paper" card:

- **Border**: `1dp` solid `glassBorder` (10% black) — same structural role, inverted color
- **Fill**: subtle solid `bgSecondary` (`#F0F0F2`) instead of a gradient — the gradient is invisible in light mode anyway
- **Shadow**: `2dp` elevation via `CardDefaults.cardElevation(defaultElevation = 2.dp)` — replaces the glass shimmer with physical depth
- **Shape**: unchanged `RoundedCornerShape(18.dp)`

Implementation: `GlassCard` reads its fill brush and elevation from `CompositionLocal`. In dark mode: gradient fill + 0dp elevation (current behavior). In light mode: solid fill + 2dp elevation.

### 4. Token Architecture

#### Current problem
Colors are top-level `val` constants in `Color.kt`. Screens import them directly (`import com.hrcoach.ui.theme.CardeaTextPrimary`). This bypasses any runtime theme switching — the values are compile-time constants.

#### Solution: `CardeaColors` data class + CompositionLocal

```kotlin
// New data class holding all semantic color tokens
data class CardeaColors(
    val bgPrimary: Color,
    val bgSecondary: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val glassSurface: Color,
    val glassFillBrush: Brush,
    val glassElevation: Dp,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val surfaceVariant: Color,
    val gradient: Brush,
    val ctaGradient: Brush,
    val navGradient: Brush,
    val zoneGreen: Color,
    val zoneAmber: Color,
    val zoneRed: Color,
    val divider: Color,
    val achievementSlateBorder: Color,
    val achievementSlateBg: Color,
    val achievementSkyBorder: Color,
    val achievementSkyBg: Color,
    val achievementGoldBorder: Color,
    val achievementGoldBg: Color,
    // Accent color used for profile/avatar highlights
    val accentPink: Color,
    // "On gradient" text — always white regardless of theme
    val onGradient: Color,
    // Blackout day colors (bootcamp calendar)
    val blackoutBg: Color,
    val blackoutBorder: Color,
    val blackoutText: Color,
    // Chart grid
    val chartGrid: Color,
    // Map overlay
    val mapOverlayBg: Color,
)
```

Two instances: `DarkCardeaColors` and `LightCardeaColors`.

A `LocalCardeaColors` CompositionLocal is provided by `CardeaTheme`. An accessor object `CardeaTheme.colors` makes usage clean:

```kotlin
// Usage in screens:
Text(color = CardeaTheme.colors.textPrimary)
Box(Modifier.background(CardeaTheme.colors.bgPrimary))
```

This replaces direct imports of `CardeaTextPrimary`, `CardeaBgPrimary`, etc. The old top-level vals remain as `internal` for backward compatibility during migration but are no longer the canonical API.

#### Migration of existing CompositionLocals
The three existing locals (`LocalGlassBorder`, `LocalSubtleText`, `LocalCardeaGradient`) and `CardeaThemeTokens` become thin wrappers around `LocalCardeaColors` for backward compat, then are deprecated.

### 5. Gradient Treatment in Light Mode

- **CTA buttons** (`CardeaCtaGradient` background with white text): unchanged. The neon red-to-pink gradient is vibrant on both white and black backgrounds, and the white text on a saturated gradient is legible regardless of surrounding theme.
- **Text brushes** (gradient as text fill): unchanged. Gradient-filled text is used on hero numbers and works well on both backgrounds since the gradient colors are fully saturated.
- **Border brushes** (gradient as border): unchanged.
- **Ambient tint gradients** (low-alpha gradient washes on hero cards): these use screen-specific alpha values like `Color(0x33FF2DA6)`. In light mode these remain the same — a pink tint at 20% alpha over white produces a soft blush, which is intentional and attractive.

### 6. Special Surface Treatments

#### Map overlays (HistoryDetailScreen)
Map stat overlays currently use `Color(0x99000000)` (60% black). This works on both themes since it's overlaying a Google Maps tile, not the app background. No change needed.

#### Delete swipe (HistoryListScreen)
Red background (`#EF4444`) with white icon. Works on both themes. No change.

#### Bottom sheets (AccountScreen, BootcampScreen)
`containerColor` currently `CardeaBgSecondary`. Will automatically use the correct value via `CardeaTheme.colors.bgSecondary`.

#### Active workout screen
Dark backgrounds during active workouts are intentional (outdoor readability). **The active workout screen always uses dark theme** regardless of the app-level preference. This is standard in fitness apps — Strava, Nike Run Club, and Garmin all do this.

### 7. Screens Requiring Hardcoded Color Migration

Audit of ~100 hardcoded `Color(0x...)` and `Color.White/Black` usages:

| Screen | Hardcoded count | Migration notes |
|--------|----------------|-----------------|
| `BootcampSettingsScreen.kt` | ~18 | Blackout day colors → `CardeaTheme.colors.blackout*`; `Color.White` text → `textPrimary`/`onGradient` |
| `SetupScreen.kt` | ~18 | Inline gradient washes → keep as-is (alpha tints work on both); `Color.White` → `textPrimary`; mode chip colors → extract to tokens |
| `HistoryDetailScreen.kt` | ~13 | Map overlays → keep; glass surfaces → `CardeaTheme.colors`; `Color.White` text → `textPrimary` |
| `BootcampScreen.kt` | ~10 | Blackout colors → `blackout*` tokens; glass surfaces → theme; divider → `divider` |
| `AccountScreen.kt` | ~8 | `Color(0xFFFF6B8A)` → `accentPink`; `Color(0x0FFFFFFF)` surfaces → `glassHighlight`; `Color.White` → `onGradient` (on gradient buttons) |
| `HomeScreen.kt` | ~6 | Ambient gradient washes → keep; status dot color → `zoneGreen` |
| `ProgressScreen.kt` | 1 | Default `valueColor = Color.White` → `textPrimary` |
| `ProgressViewModel.kt` | 5 | Chart series colors → keep (these are data-encoding colors, not theme-dependent) |
| `ActiveWorkoutScreen.kt` | 2 | Forced dark — no changes |
| `PostRunSummaryScreen.kt` | 2 | Ambient gradient washes → keep |
| `CalendarHeatmap.kt` | 2 | Empty cell → `surfaceVariant`; text → `textSecondary` |
| `BarChart.kt` | 1 | Grid → `chartGrid` token |
| `ScatterPlot.kt` | 1 | Grid → `chartGrid` token |
| `NavGraph.kt` | 1 | Icon tint → `textPrimary` |
| `ActiveSessionCard.kt` | 2 | `Color.White` → `onGradient` (inside gradient background) |
| `CardeaInputs.kt` | 1 | Switch thumb → `onGradient` |
| `GlassCard.kt` | 2 | Fill brush → `glassFillBrush` from theme |

**Total sites to modify**: ~55 (remaining ~45 are either ambient washes that work on both themes, forced-dark surfaces, or map overlays).

### 8. Type.kt

`labelSmall` has `color = SubtleText` hardcoded. This needs to be removed (set to `Color.Unspecified`) so it inherits from the theme's `onSurface` or is set explicitly at the call site. This is a one-line change but could cause regressions if any screen relies on the implicit color.

### 9. Persistence & Plumbing

```
ThemePreferencesRepository (SharedPreferences, "cardea_theme_prefs")
  ├── getThemeMode(): ThemeMode  [SYSTEM | LIGHT | DARK]
  └── setThemeMode(mode: ThemeMode)

ThemeMode enum in domain/model/ThemeMode.kt

AccountViewModel
  ├── inject ThemePreferencesRepository
  ├── _themeMode: MutableStateFlow<ThemeMode>
  └── setThemeMode(mode: ThemeMode)

MainActivity
  ├── inject ThemePreferencesRepository
  ├── observe themeMode
  └── pass isDarkTheme to CardeaTheme()
```

`MainActivity` needs to observe the preference reactively so the theme switches immediately without an app restart. A `StateFlow` in a shared ViewModel (or direct `SharedPreferences` listener) handles this.

### 10. Status Bar & Navigation Bar

- Light mode: dark status bar icons + dark nav bar icons (`enableEdgeToEdge` with `SystemBarStyle.light`)
- Dark mode: light status bar icons + light nav bar icons (current behavior)
- Must update in `MainActivity.onCreate` and react to theme changes

## Out of Scope

- Dynamic Material You color theming (the Cardea brand gradient is the identity — no device-color override)
- Per-screen theme overrides beyond the active workout exception
- Custom fonts or typography changes for light mode
- Animated theme transition effects

## Risk Areas

1. **`labelSmall` implicit color**: Removing `color = SubtleText` from `Type.kt` could surface unstyled text in screens that rely on the default. Mitigation: grep for `labelSmall` usages and verify each has an explicit `color` parameter.
2. **Active workout forced-dark**: The workout screen must correctly force dark mode even when the app is in light mode. Needs a `CompositionLocalProvider` override wrapping `ActiveWorkoutScreen`.
3. **Third-party composables**: Google Maps tiles, Material 3 dialogs, and bottom sheets have their own theming. Most will pick up `MaterialTheme.colorScheme` automatically, but dialogs with hardcoded `containerColor` need manual updates.
