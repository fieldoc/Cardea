# Profile & Sharing Foundation Design

**Date:** 2026-03-12
**Status:** Approved

## Summary

Make the Profile tab a real, editable identity surface (name + avatar), and lay the data foundation for future Buddy Bootcamp sharing — without building any sharing UI or network layer yet.

## Goals

1. Editable user profile: display name and avatar symbol stored on-device
2. Avatar system using curated unicode symbols rendered in the brand gradient ring
3. Data model ready for future bootcamp sharing (userId, shareable config format)
4. No new backend, no network, no sharing UI this pass

## Non-Goals

- Sharing UI (QR codes, share sheets, import flows)
- Buddy matching or schedule merging logic
- Pace Pal algorithm
- Photo-based avatars
- Account creation or authentication

## Design Decisions

### Avatar System: Unicode Symbols (Option D)

Monochrome unicode glyphs tinted to match the brand color (`#FF6B8A`). Renders identically on all Android devices — no platform-dependent emoji rendering. Curated set of 10:

| Symbol | Meaning |
|--------|---------|
| ♥ | Heart — HR coaching core (default) |
| ★ | Star — achievement |
| ⚡ | Lightning — speed/energy |
| ◆ | Diamond — premium |
| ▲ | Triangle — mountain/elevation |
| ● | Circle — simplicity |
| ✦ | Four-pointed star — sparkle |
| ♦ | Small diamond |
| ↑ | Arrow — progress |
| ∞ | Infinity — endurance |

### Profile Connectivity: Lightweight Sharing Stub (Option B)

Local profile now, but the data model includes a `userId` (UUID) auto-generated on first launch. This key enables future Buddy Bootcamp pairing without requiring any changes to the profile data layer.

### Edit UX: Bottom Sheet (Option C)

Tapping the ProfileHeroCard opens a bottom sheet containing the name field and avatar picker. Keeps the hero card clean — no inline editing or pencil icons.

## Data Model

### UserProfileRepository Changes

Expand existing `SharedPreferences`-backed repository. No Room migration needed.

New fields alongside existing `maxHr`:
- `displayName: String` — default "Runner", max 20 characters
- `avatarSymbol: String` — default "♥", one of the 10 curated symbols
- `userId: String` — UUID v4, auto-generated on first read if absent, never exposed in UI

### ShareableBootcampConfig (New Data Class)

```kotlin
data class ShareableBootcampConfig(
    val goalType: String,
    val targetMinutesPerRun: Int,
    val runsPerWeek: Int,
    val preferredDays: List<DayPreference>,
    val tierIndex: Int,
    val sharerUserId: String,
    val sharerDisplayName: String,
)
```

- `toShareable()` extension on `BootcampEnrollmentEntity` (requires userId + displayName params)
- `toJson()` / `fromJson()` methods using `org.json.JSONObject` (Android built-in, no new dependency)
- `DayPreference` serialization in JSON: use a JSON array of objects `[{"day":1,"level":"AVAILABLE"},{"day":3,"level":"LONG_RUN_BIAS"}]` — NOT the compact `"1:AVAILABLE,3:LONG_RUN_BIAS"` Room converter format, since JSON consumers shouldn't depend on internal DB encoding

### AccountUiState Changes

New fields:
- `displayName: String` (default "Runner")
- `avatarSymbol: String` (default "♥")

## UI Changes

### ProfileHeroCard

- Replace `CardeaLogo` inside the gradient ring with the selected unicode symbol, rendered as `Text` with color `#FF6B8A`
- Replace hardcoded "Runner" with `displayName` from state
- Card becomes clickable — opens edit bottom sheet

### ProfileEditBottomSheet (New Composable)

- Cardea-styled `ModalBottomSheet` with dark glass background
- **Name field:** `CardeaTextField` (or equivalent styled input), max 20 chars
- **Symbol picker:** 5x2 grid of the 10 symbols, each in a mini gradient ring (40dp). Selected symbol gets a brighter ring border or subtle check overlay.
- **Save button:** Existing `GradientSaveButton` pattern
- **Cancel:** Dismiss the sheet (no explicit cancel button needed)

### No Other AccountScreen Changes

All existing settings sections (Maps API key, audio, max HR, auto-pause) remain untouched.

## Future: Buddy Bootcamp (Deferred)

When ready to build sharing:
1. `ShareableBootcampConfig` serialization is already tested
2. Add transport (QR code / deep link / nearby share)
3. Add import flow (preview config → create enrollment from it)
4. Add buddy matching: find overlapping `preferredDays`, generate merged schedule
5. Add Pace Pal: complementary session generation based on tier difference

## Files Touched

- `data/repository/UserProfileRepository.kt` — add name, avatar, userId fields
- `ui/account/AccountViewModel.kt` — add name/avatar to state + update methods
- `ui/account/AccountScreen.kt` — update ProfileHeroCard, add ProfileEditBottomSheet
- `domain/model/ShareableBootcampConfig.kt` — new file
- `data/db/BootcampEnrollmentEntity.kt` — add `toShareable()` extension
