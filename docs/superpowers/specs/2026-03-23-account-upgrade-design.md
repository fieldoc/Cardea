# Account Upgrade Design

**Date:** 2026-03-23
**Status:** Draft
**Scope:** Firebase Auth, profile claim flow, emblem avatar system, per-user data isolation

---

## Problem

The current account system is a "cardboard sham":
- Profile is just SharedPreferences (`UserProfileRepository`) with a display name and Unicode symbol avatar
- No authentication — anyone can change the name at any time
- No data isolation — all workout/bootcamp/achievement data is global
- No foundation for social features (no stable identity, no auth token)
- Avatar choices are 10 generic Unicode glyphs that don't feel premium

## Goals

1. **Real identity** — Firebase Auth backs every profile with a cloud identity
2. **Profile permanence** — soft onboarding, then "claim" after first workout locks the profile
3. **Premium avatars** — 24 custom athletic emblem SVGs rendered via Compose Canvas with Cardea gradient
4. **Data isolation** — each authenticated user sees only their own workouts, bootcamps, and achievements
5. **Social readiness** — stable UID and profile data suitable for future social features

## Non-Goals

- Cloud data sync (workout data stays local)
- Profile photo upload
- Social features (friend list, sharing, leaderboards)
- Account deletion UI (future work)
- Password reset UI (Firebase handles via email link)

---

## Architecture

### 1. Firebase Authentication

**Dependencies:** Add `firebase-auth` to `app/build.gradle.kts` (Firebase BOM already present at 33.8.0).

**Auth providers:** Email/password + Google Sign-In (via `firebase-auth` + `play-services-auth`).

**New class: `AuthRepository`** (`data/repository/AuthRepository.kt`)
- Wraps `FirebaseAuth` instance
- Exposes `currentUser: StateFlow<FirebaseUser?>` (listens to `AuthStateListener`)
- Exposes `currentUserId: String?` (shorthand for `currentUser.value?.uid`)
- Methods: `signInWithEmail()`, `signUpWithEmail()`, `signInWithGoogle()`, `signOut()`, `isAuthenticated(): Boolean`
- Singleton via Hilt `@Singleton`

**Auth state flow:**
```
App launch
  → Check FirebaseAuth.currentUser
    → null → Show AuthScreen (sign in / sign up)
    → non-null → Normal app flow, scoped to UID
```

**AuthScreen** (`ui/auth/AuthScreen.kt` + `AuthViewModel.kt`)
- Email/password sign-in form with toggle to sign-up mode
- Google Sign-In button
- Error handling (invalid email, wrong password, network errors)
- On success → navigate to `home`

**Navigation changes:**
- New `auth` route added before `home` in NavGraph
- Start destination: `auth` if not authenticated, `home` if authenticated
- `signOut()` navigates back to `auth` and clears backstack

### 2. Profile Claim Flow

**Trigger:** After first workout completes (in `WorkoutForegroundService.stopWorkout()` or post-run summary).

**New flag in UserProfileRepository:** `isProfileClaimed: Boolean` (SharedPreferences, keyed by UID).

**Claim bottom sheet** (`ui/account/ProfileClaimSheet.kt`):
- Display name field (3-20 chars, required)
- Emblem avatar grid (4x6 grid of the 24 emblems)
- Optional bio/tagline (max 40 chars)
- "Claim Profile" gradient CTA button
- If user isn't authenticated yet, this sheet includes inline sign-up fields (email + password) or Google Sign-In button
- On claim: saves profile, sets `isProfileClaimed = true`, sets `profileClaimedAtMs`

**Post-claim restrictions:**
- Display name: editable once per 30 days. `lastNameChangeMs` tracked in SharedPreferences (per UID). UI shows "Editable in X days" when locked.
- Avatar emblem: changeable freely (no cooldown)
- Bio: changeable freely

**Profile data model** (stored in `UserProfileRepository`, keyed by UID):
```
displayName: String          // 3-20 chars
avatarEmblemId: String       // e.g. "pulse", "bolt", "summit"
bio: String                  // 0-40 chars, optional
isProfileClaimed: Boolean
profileClaimedAtMs: Long     // member-since date
lastNameChangeMs: Long       // for 30-day cooldown
maxHr: Int?                  // existing, moved to per-UID
```

**Runner level** (auto-computed, not stored):
- 0-9 workouts → "Beginner"
- 10-49 → "Intermediate"
- 50-199 → "Advanced"
- 200+ → "Elite"

Computed in `AccountViewModel` from workout count. Displayed on `ProfileHeroCard`.

### 3. Emblem Avatar System

**24 emblems** rendered as Compose Canvas paths (no bitmap assets):

| ID | Name | Description |
|----|------|-------------|
| `pulse` | Pulse | ECG heartbeat line |
| `bolt` | Bolt | Lightning bolt |
| `summit` | Summit | Mountain peaks |
| `flame` | Flame | Fire with inner cutout |
| `compass` | Compass | 4-point compass rose |
| `shield` | Shield | Shield with cross |
| `ascent` | Ascent | Nested upward chevrons |
| `crown` | Crown | Royal crown |
| `orbit` | Orbit | Atomic orbital rings |
| `infinity` | Infinity | Infinity loop |
| `diamond` | Diamond | Faceted gem |
| `nova` | Nova | 6-point starburst |
| `heart` | Heart | Filled heart |
| `wave` | Wave | Double sine wave |
| `spiral` | Spiral | Golden spiral |
| `trident` | Trident | 3-pronged trident |
| `comet` | Comet | Comet with tail |
| `prism` | Prism | Triangle with refracted rays |
| `ripple` | Ripple | Concentric circles |
| `crescent` | Crescent | Crescent moon with stars |
| `wings` | Wings | Spread wings |
| `helix` | Helix | DNA double helix |
| `focus` | Focus | Crosshair reticle |
| `laurel` | Laurel | Victory laurel wreath |

**Implementation:**

New file: `ui/components/EmblemIcon.kt`
- `EmblemIcon(emblemId: String, size: Dp, modifier: Modifier)` composable
- Uses `Canvas` with `DrawScope` to render each emblem path
- All paths use `CardeaGradient` as fill/stroke brush
- `EmblemRegistry` object maps IDs → draw lambdas: `Map<String, DrawScope.(Size) -> Unit>`
- Emblem surrounded by gradient ring (same pattern as current `ProfileHeroCard`)

New file: `ui/components/EmblemPicker.kt`
- `EmblemPicker(selected: String, onSelect: (String) -> Unit)` composable
- 4x6 `LazyVerticalGrid` of `EmblemIcon` items
- Selected emblem gets `CardeaGradient` ring; unselected gets subtle glass border

**Migration from Unicode symbols:**
- `AVATAR_SYMBOLS` list in `AccountScreen.kt` → deleted
- `avatarSymbol: String` in `UserProfileRepository` → renamed to `avatarEmblemId: String`
- Existing Unicode values mapped to closest emblem: `"♥"` → `"heart"`, `"⚡"` → `"bolt"`, `"★"` → `"nova"`, etc.
- Default for new profiles: `"pulse"` (the heartbeat — most Cardea-branded)

### 4. Data Isolation

**Strategy:** Add `userId TEXT NOT NULL DEFAULT ''` column to all user-data tables. All DAO queries filter by `userId`. This approach (vs. separate DBs per user) allows simpler migrations and a single DB connection.

**Tables requiring `userId` column:**
1. `workouts` — `userId TEXT NOT NULL DEFAULT ''`
2. `track_points` — inherits isolation via FK to workouts (no separate column needed)
3. `workout_metrics` — inherits isolation via FK to workouts (no separate column needed)
4. `bootcamp_enrollments` — `userId TEXT NOT NULL DEFAULT ''`
5. `bootcamp_sessions` — inherits isolation via FK to enrollments (no separate column needed)
6. `achievements` — `userId TEXT NOT NULL DEFAULT ''`

**Room migration (v15 → v16):**
```sql
ALTER TABLE workouts ADD COLUMN userId TEXT NOT NULL DEFAULT '';
ALTER TABLE bootcamp_enrollments ADD COLUMN userId TEXT NOT NULL DEFAULT '';
ALTER TABLE achievements ADD COLUMN userId TEXT NOT NULL DEFAULT '';
```

Existing data gets `userId = ''` (empty string). On first authenticated login, if `userId = ''` rows exist, prompt user: "We found existing workout data on this device. Claim it as yours?" If yes, update all `userId = ''` rows to the authenticated UID. If no (different person logging in), leave orphaned rows.

**DAO changes:**
All query methods gain a `userId: String` parameter:
- `getAllWorkouts(userId: String): Flow<List<WorkoutEntity>>`
- `getActiveEnrollment(userId: String): Flow<BootcampEnrollmentEntity?>`
- `getAllAchievements(userId: String): Flow<List<AchievementEntity>>`
- etc.

Insert methods: entity classes gain `userId` field, populated from `AuthRepository.currentUserId`.

**Repository changes:**
All repositories that use DAOs must receive the current userId. Two approaches:
- **A)** Repositories inject `AuthRepository` and call `currentUserId` internally
- **B)** ViewModels pass `userId` to repository methods

**Recommendation: Approach A.** Repositories inject `AuthRepository`, and internally scope all DAO calls. This keeps the userId concern out of ViewModels and prevents forgotten-parameter bugs.

**SharedPreferences scoping:**
`UserProfileRepository` already uses a single prefs file. Change key format to include UID:
```
"display_name" → "display_name_$uid"
"avatar_symbol" → "avatar_emblem_id_$uid"
"max_hr" → "max_hr_$uid"
```
Similarly for `AudioSettingsRepository`, `AutoPauseSettingsRepository`, `MapsSettingsRepository`.

Alternatively, use a separate SharedPreferences file per user: `getSharedPreferences("user_profile_$uid", ...)`. This is cleaner but requires clearing the old unscoped prefs on migration.

**Recommendation:** Separate prefs file per user — cleaner isolation.

### 5. Account Screen Changes

**ProfileHeroCard upgrades:**
- Unicode symbol → `EmblemIcon` composable (Canvas-drawn)
- Show bio below name (if set)
- Show runner level badge (e.g. "Intermediate" in small gradient text)
- Show "Member since Mar 2026" below run count
- Tap → opens ProfileEditBottomSheet (existing, but upgraded)

**ProfileEditBottomSheet upgrades:**
- Name field: shows "Editable in X days" and is disabled when within 30-day cooldown
- Avatar: `EmblemPicker` (4x6 grid) replaces old 5x2 Unicode grid
- New bio/tagline field (max 40 chars)
- Save button → `saveProfile()` with validation

**New Auth section** at bottom of AccountScreen:
- Shows authenticated email address
- "Sign Out" button (with confirmation dialog)
- Sign out: calls `AuthRepository.signOut()`, clears in-memory state, stops active workout if running, navigates to `auth` route

### 6. Service Layer Changes

**WorkoutForegroundService:**
- On `startWorkout()`: reads `AuthRepository.currentUserId` and attaches to the `WorkoutEntity`
- On `stopWorkout()`: if `!isProfileClaimed`, trigger post-workout claim flow (via a SharedPreferences flag or event that the post-run screen checks)

**WorkoutState:**
- No changes needed (it's transient runtime state, not persisted)

---

## File Changes Summary

### New Files
| File | Purpose |
|------|---------|
| `data/repository/AuthRepository.kt` | Firebase Auth wrapper |
| `ui/auth/AuthScreen.kt` | Sign-in / sign-up screen |
| `ui/auth/AuthViewModel.kt` | Auth screen state management |
| `ui/components/EmblemIcon.kt` | Canvas-drawn emblem composable |
| `ui/components/EmblemPicker.kt` | 4x6 grid emblem selection |
| `ui/account/ProfileClaimSheet.kt` | Post-first-workout profile claim |

### Modified Files
| File | Changes |
|------|---------|
| `app/build.gradle.kts` | Add `firebase-auth`, `play-services-auth` deps |
| `data/db/AppDatabase.kt` | Version 16, migration 15→16 (userId columns) |
| `data/db/WorkoutEntity.kt` | Add `userId` field |
| `data/db/WorkoutDao.kt` | Add `userId` param to all queries |
| `data/db/BootcampEnrollmentEntity.kt` | Add `userId` field |
| `data/db/BootcampDao.kt` | Add `userId` param to relevant queries |
| `data/db/AchievementEntity.kt` | Add `userId` field |
| `data/db/AchievementDao.kt` | Add `userId` param to queries |
| `data/repository/UserProfileRepository.kt` | Per-UID SharedPreferences, emblem ID instead of symbol, claim fields |
| `data/repository/WorkoutRepository.kt` | Inject AuthRepository, scope by userId |
| `data/repository/AudioSettingsRepository.kt` | Per-UID prefs file |
| `data/repository/AutoPauseSettingsRepository.kt` | Per-UID prefs file |
| `data/repository/MapsSettingsRepository.kt` | Per-UID prefs file |
| `data/repository/BootcampRepository.kt` | Inject AuthRepository, scope by userId |
| `ui/account/AccountScreen.kt` | EmblemIcon, EmblemPicker, auth section, upgraded hero card |
| `ui/account/AccountViewModel.kt` | Emblem IDs, bio, claim state, name cooldown logic |
| `ui/navigation/NavGraph.kt` | Add `auth` route, conditional start destination |
| `service/WorkoutForegroundService.kt` | Attach userId to workouts, claim flow trigger |
| `di/AppModule.kt` | Provide AuthRepository |

### Deleted
| Item | Reason |
|------|--------|
| `AVATAR_SYMBOLS` list in `AccountScreen.kt` | Replaced by EmblemRegistry |

---

## Testing Strategy

### Unit Tests
- `AuthRepository`: mock `FirebaseAuth`, test sign-in/out state flow
- `UserProfileRepository`: test per-UID key isolation, claim flow, name cooldown
- `EmblemRegistry`: verify all 24 emblem IDs are registered and drawable
- DAO queries: verify `userId` filtering (Room in-memory DB tests)
- Runner level computation: verify thresholds

### Integration Tests
- Migration test: v15 → v16 preserves existing data with `userId = ''`
- Orphan data claim: verify existing rows adopt new UID
- Sign-out clears active state and navigates to auth

### Manual Tests
- Full sign-up → first workout → claim flow → profile displays correctly
- Log out → log in as different user → data isolation verified
- Name change cooldown shows correct countdown
- All 24 emblems render correctly at small (32dp) and large (56dp) sizes
