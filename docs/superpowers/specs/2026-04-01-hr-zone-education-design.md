# HR Zone Education — Design Spec

## Summary

Add contextual heart rate zone education throughout Cardea, explaining *why* each zone matters in practical terms. Content is data-driven (personalized when HRmax is known), served at three density levels, and woven into existing screens — no new navigation routes.

## Goals

- Users understand what each training zone does for their running before they start a session
- Tone is concrete and credible — name the mechanism, then name the result
- Physiology appears as interesting flavor, not lectures
- Content adapts density to screen context (badge on busy screens, paragraphs on review screens)
- Personalized BPM ranges when HRmax is known; graceful fallback when not

## Non-Goals

- Adaptive content based on training history (Approach C — future evolution)
- Localization / i18n (can migrate to string resources later)
- New screens or navigation routes
- Formal citations or bibliography in the UI

## Content Model

### Density Levels

| Level | Length | Use case |
|-------|--------|----------|
| `BADGE` | 1-2 words | Active workout screen tag next to zone indicator |
| `ONE_LINER` | 1 sentence | Session cards, hero card subtitle, setup screen |
| `FULL` | 2-4 sentences | Upcoming run detail, progress screen context |

### Zone Categories

Five categories matching existing app usage:

| Zone ID | App session types | Badge text | Content focus |
|---------|------------------|------------|---------------|
| `ZONE_2` | Easy, Long | "Aerobic Base" | Stroke volume, capillary density, mitochondrial adaptation |
| `ZONE_3` | Tempo | "Threshold" | Lactate turnpoint, MCT transporters, sustainable speed ceiling |
| `ZONE_4_5` | Interval | "VO2max" | Cardiac output ceiling, fast-twitch recruitment, oxygen uptake |
| `RECOVERY` | Discovery, Check-In | "Calibration" | Autonomic recovery, zone calibration data, parasympathetic tone |
| `RACE_PACE` | Race Sim | "Race Effort" | Pacing strategy, glycogen management, neuromuscular rehearsal |

### Tone Rules

1. **Name the mechanism, then name the result.** "Stroke volume increases" → "less effort at any pace." Never just the result alone.
2. **No hand-wavey motivational copy.** "Builds your engine" is out. "Strengthens your heart's stroke volume" is in.
3. **Physiology as flavor, not curriculum.** Interesting facts woven naturally into full-density text, not in callout boxes.
4. **Avoid outdated models.** No "fat-burning zone" without nuance. No "lactate is waste." No "you must tear muscles to grow."
5. **Concept accuracy verified against current exercise science** (post-2020 consensus). Claims must hold up if read by someone with sports physiology knowledge.

### Personalization

- **HRmax known:** One-liner and full text include BPM range (e.g., "your Zone 2 is 124–148 BPM"). Computed using same formula as `ZoneEngine` (target ± buffer).
- **HRmax unknown:** General descriptions without BPM. Append nudge: "Run a Discovery session to see your personal ranges."

## Touchpoints

### Priority 1: Pre-Run Screens

**BootcampScreen session cards:**
- Currently: zone label like "Zone 2"
- Add: `ONE_LINER` beneath the zone label
- Session detail expansion: `FULL` density replaces the current generic session-type descriptions (e.g., "A conversational, low-intensity run" becomes the zone education full text)

**HomeScreen BootcampHeroCard:**
- Currently: shows next session info
- Add: `BADGE` next to zone tag, `ONE_LINER` as subtitle line
- No full density — hero card is a glance

### Priority 2: Progress Screen

- Zone distribution charts: `ONE_LINER` as context beneath chart labels
- Dedicated zone breakdown (if present): `FULL` density appropriate

### Priority 3: Setup Screen (manual workout)

- When user selects a target HR: show `ONE_LINER` for whichever zone that HR falls into
- Natural location for Discovery nudge when HRmax is unset

### Bonus: Active Workout Screen

- `BADGE` only — 1-2 words next to existing zone status indicator (green/amber/red)
- Changes dynamically with current zone status
- Zero additional screen real estate

## Architecture

### `ZoneEducationProvider`

- **Location:** `domain/education/ZoneEducationProvider.kt`
- **Dependencies:** None (pure Kotlin, no Android framework)
- **Input:** `ZoneId` enum, `ContentDensity` enum, optional `hrMax: Int?`
- **Output:** `String`

### Supporting Types

```kotlin
enum class ZoneId {
    ZONE_2, ZONE_3, ZONE_4_5, RECOVERY, RACE_PACE
}

enum class ContentDensity {
    BADGE, ONE_LINER, FULL
}
```

### Mapping from Existing Session Types

A helper function maps from the session-type zone labels the app already uses ("Zone 2", "Zone 3-4", etc.) to `ZoneId`. This bridges the education system to existing bootcamp data without changing the data model.

### ViewModel Integration

No new ViewModels. Each existing ViewModel that needs education content:
1. Instantiates or injects `ZoneEducationProvider`
2. Calls it with the appropriate zone and density
3. Exposes the result through existing UiState (new `String?` field)

Examples:
- `HomeViewModel`: adds `zoneEducationBadge: String?` and `zoneEducationOneLiner: String?` to `HomeUiState`
- `BootcampViewModel`: adds education strings to session card data
- `SetupViewModel`: adds `zoneEducationOneLiner: String?` based on selected target HR

### Content Storage

Hardcoded in `ZoneEducationProvider`. The content is small (~15 strings + personalization templates). No database, JSON, or string resources needed. If localization becomes relevant, migrate to string resources then.

## Edge Cases

1. **No HRmax set:** Show general descriptions, omit BPM ranges, include Discovery nudge
2. **Free Run mode:** No target zone → no education content shown on setup/workout screens
3. **Zone boundary overlap:** When a target HR could map to multiple zones, use the session type label as the authority (it's more specific than HR alone)
4. **Content freshness:** Claims verified against post-2020 exercise science during implementation. Content is static after that — no runtime fetching.
