# Audio Cues — Education & Clarity

**Date:** 2026-04-19
**Status:** Draft, awaiting review

## Problem

Cardea fires up to twelve distinct audio events during a workout (zone alerts, predictive warnings, in-zone confirmations, splits, halfway, segment change, signal lost/regained, workout complete, pause/resume tones, and the startup countdown). The system is layered, escalating, priority-gated, and verbosity-gated — which is powerful, but tester feedback is consistent: *people do not know what the sounds mean, when they fire, or why they repeat.*

Root causes (from an audit of the current system):

1. **No sound→meaning mapping is ever shown to the user.** Nothing previews earcons, nothing labels them in settings, nothing captions them during the run.
2. **Only education is three one-liner verbosity hints** in Account → Audio & Coaching. That describes *volume* of speech, not *which events* exist.
3. **The most novel cue — predictive warning — fires while the runner is still in zone.** "Watch your pace" reads as a false alarm because the predictive intent is invisible.
4. **Positive reinforcement (IN_ZONE_CONFIRM) and correction (SPEED_UP / SLOW_DOWN) use the same sonic register** in the user's mind.
5. **The three-tier escalation (earcon → +voice → +vibration)** is a real and useful feature, but the user experiences it as the same alert repeating and assumes the app is glitchy.
6. **Audio is ephemeral; there is no visual twin.** Miss a chime and you have no way to find out what it was.
7. **Verbosity modes trade sound-count, not information-quality.** "MINIMAL vs FULL" doesn't tell the user which coaching decisions they're degrading.

This feature addresses (1), (2), (3), (5), (6) directly. It lays groundwork for (4) — an earcon-vocabulary redesign — which is deferred to a follow-up phase pending user data.

## Goals

- A first-time user can, within two workouts, reliably identify what each audio cue means without asking.
- At any moment during a run, the user can glance at the screen and see what just fired and why.
- In settings, the user can open a "Sound Library" and preview every cue with a plain-language description.
- The most misinterpreted cues (predictive warning, in-zone confirmation) are rephrased to be self-explanatory.
- Vibration at escalation tier 3 encodes direction (speed-up vs slow-down) tactilely.
- No regression: existing verbosity, vibration, cue toggles, and escalation semantics continue to work identically.

## Non-goals

- **Re-synthesizing the earcon WAVs.** Redesigning the 12-sound palette into a coherent musical family is the highest-leverage audio change but also the riskiest and hardest to validate. Deferred to Phase 2 after we measure whether the labeling/banner fixes close the confusion gap on their own.
- **Race / quiet mode** (critical-only voice + vibration-only zone alerts). Valuable but separable.
- **Localization of new strings.** English-only in line with the current app.
- **Runtime "explain this sound" voice command.** Out of scope.

## Solution overview

Six integrated components, shippable as one feature:

1. **Sound Library screen** — a new settings screen inventorying every cue with icon, name, *when it fires*, *what to do*, and a ▶ Preview button.
2. **In-workout cue banner** — a transient pill overlay on the active workout screen, showing the name and one-line meaning of whatever cue just fired, for ~3.5 s.
3. **TTS rephrase pass** — replace the vague strings (principally `PREDICTIVE_WARNING` "Watch your pace") with self-explanatory phrasing; audit `IN_ZONE_CONFIRM` rotation for positivity.
4. **First-workout audio primer** — a one-time, skippable three-slide modal shown on the first tap of Start Workout, explaining the three audio layers (chime / voice / vibration) and the predictive-warning concept.
5. **Patterned vibration at tier 3** — direction-coded vibration pulses: short-short for speed up, long-long for slow down.
6. **Post-run "Sounds heard today" recap** — on the post-run summary, for the user's first three workouts, an expandable section listing which cues fired and what each meant.

Every component is small; together they form the holistic education pass.

## Component designs

### 1. Sound Library screen

**Location:** New screen, route `sound_library`, navigated from the Account → Audio & Coaching section.

**Entry point:** In [AccountScreen.kt](app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt), within the Audio & Coaching section (alongside voice verbosity, earcon/voice volume sliders), add a row:

> **Sound library**
> Preview what each coaching cue sounds like.
> [›]

Tapping navigates to `Routes.SOUND_LIBRARY`.

**Screen layout:** Standard Cardea glass-card screen. Top bar with back arrow + title "Sound Library". Introductory paragraph:

> Cardea uses chimes, voice, and vibration to coach you. Tap any cue to hear what it sounds like.

Then a scrollable list of GlassCard rows grouped into four sections by priority/role:

**Zone alerts (critical)**
- SPEED_UP — "Slow for your target — pick up the pace."
- SLOW_DOWN — "Working harder than your target — ease off."
- SIGNAL_LOST — "Heart-rate sensor disconnected. Check your strap."
- SIGNAL_REGAINED — "Sensor reconnected."

**Pace guidance (normal)**
- RETURN_TO_ZONE — "You just came back into your target zone."
- PREDICTIVE_WARNING — "You're in zone now, but trending toward the edge. Adjust early to stay in."
- SEGMENT_CHANGE — "Next interval starting."

**Milestones (informational)**
- KM_SPLIT / MILE_SPLIT — "Distance marker — each kilometer (or mile) you complete."
- HALFWAY — "You've reached 50% of your target."
- WORKOUT_COMPLETE — "You've reached your target distance or time."

**Reassurance (informational)**
- IN_ZONE_CONFIRM — "Cruising nicely — fired periodically while you're holding zone."

Each row has:
- Small circular icon, drawn from the confirmed-available Material icons set per CLAUDE.md: `ArrowUpward` for SPEED_UP, `ArrowDownward` for SLOW_DOWN, `Favorite` for SIGNAL_LOST/REGAINED, `Check` for RETURN_TO_ZONE, `Notifications` for PREDICTIVE_WARNING, `ChevronRight` for SEGMENT_CHANGE, `Timer` for splits/halfway/complete, `FavoriteBorder` for IN_ZONE_CONFIRM. If a better match is desired, use a custom vector asset under `res/drawable/` — do not assume `CheckCircle`, `Warning`, `SkipNext`, or `Flag` exist in `Icons.Default`.
- Event name (titleSmall, white)
- One-line description (bodySmall, textSecondary)
- Right-edge ▶ Preview button — plays the earcon via `EarconPlayer.play(event)` on tap; visual "Playing…" state for ~600 ms.

**Implementation notes:**
- A new Composable `SoundLibraryScreen(onBack: () -> Unit)` in `ui/account/SoundLibraryScreen.kt`.
- A new entry `Routes.SOUND_LIBRARY = "sound_library"` in `NavGraph.kt`, wired with a `composable(Routes.SOUND_LIBRARY)` block.
- `AccountViewModel` exposes the existing `EarconPlayer` through a new `previewEarcon(event: CoachingEvent)` method, or the screen injects `EarconPlayer` directly via Hilt. Favor the latter for separation.
- Preview plays *only* the earcon (never TTS). Keeps the screen fast and avoids audio focus tangles.
- Volume respects the user's current `earconVolume` setting.
- No persistence / state to save; this is a pure reference screen.

### 2. In-workout cue banner

**What:** A transient overlay at the top of the active workout screen (beneath the mission card) that appears when any audio cue fires. Shows the event name and a one-line meaning. Auto-dismisses after 3500 ms. Replaced immediately if another cue fires while visible.

**Behavior:**
- New field on `WorkoutSnapshot`: `lastCueBanner: CueBanner? = null` where `CueBanner` is a small data class holding `val title: String`, `val subtitle: String`, `val firedAtMs: Long`, `val kind: CueBannerKind` (enum for color accent: ALERT / GUIDANCE / MILESTONE / INFO).
- `CoachingAudioManager.fireEvent(...)` and `StartupSequencer` and `AlertPolicy`-driven escalation each trigger a corresponding banner via a new `WorkoutState.flashCueBanner(kind, title, subtitle)` helper. That helper sets `lastCueBanner` and schedules `updateIn(3500ms) { lastCueBanner = null }` via a single coroutine tracked on a supervisor scope on `WorkoutState`.
- `ActiveWorkoutScreen` observes `snapshot.lastCueBanner` and, when non-null, shows the overlay with a fade-in/fade-out (200 ms).
- Banner respects voice-verbosity gating: if OFF, still show banner (it's a safety/transparency feature — chimes still fire even at OFF). If verbosity MINIMAL or FULL, same.

**Layout:**
```
╔═════════════════════════════════════╗
║ ◐ Predictive warning                ║
║   HR climbing — ease off to stay in ║
╚═════════════════════════════════════╝
```
- Pill shape, 12 dp corner radius.
- Border color indicates kind:
  - ALERT (speed-up, slow-down, signal-lost) → `ZoneRed` @ 0.5 alpha
  - GUIDANCE (predictive warning, return-to-zone, segment change, signal regained) → `GradientPink` @ 0.5 alpha
  - MILESTONE (km/mile split, halfway, complete) → `ZoneGreen` @ 0.5 alpha
  - INFO (in-zone confirm, pause/resume) → `GlassBorder`
- Glass fill via `DarkGlassFillBrush`.
- Title: labelMedium, uppercase, white, bold.
- Subtitle: bodySmall, `textSecondary`.
- Icon: same glyph map as the Sound Library screen.

**Strings:** The (title, subtitle) pair for each cue is stored once, in a single `CueCopy.kt` object keyed by `CoachingEvent`. Both the Sound Library screen and the banner read from this table.

**Accessibility:** The banner has a `semantics { liveRegion = LiveRegionMode.Polite }` so TalkBack also reads the title/subtitle when it appears.

### 3. TTS rephrase pass

**Files:** `VoicePlayer.kt`, `CoachingAudioManager.kt`, and any string literal that generates TTS text (some live inline; the spec treats these as in scope to extract into the shared `CueCopy` table).

**Changes:**

| Event | Current | New |
|---|---|---|
| `PREDICTIVE_WARNING` (default fallback) | "Watch your pace." | "Pace climbing — ease off to hold zone." (or "Pace dropping — pick it up to hold zone." depending on trend direction, which is already available in the projection) |
| `IN_ZONE_CONFIRM` (low-confidence rotation) | "Nice and steady", "Good pace", "Holding zone", "Keep it going" | Keep as-is — these are fine. Audit for any duplicates with zone-alert phrasing; none found. |
| `RETURN_TO_ZONE` fallback | "Back in zone." | Keep. This is clear. |
| `SIGNAL_LOST` | "Signal lost." | "Heart-rate signal lost." (adds specificity without adding length) |
| `SIGNAL_REGAINED` | "Signal regained." | "Heart-rate signal back." |
| `SEGMENT_CHANGE` | "Next segment." | "Next interval." (users consistently say "interval", not "segment") |

The `PREDICTIVE_WARNING` direction-aware phrasing requires the manager to know the sign of the drift. The adaptive controller already emits this via `guidanceText` in most cases, so the behavior change is: when `guidanceText` is null/empty for a predictive event, fall back to the direction-aware phrase. Direction is derived from `AdaptivePaceController.hrSlopeBpmPerMin` — positive slope ⇒ "climbing, ease off"; negative slope ⇒ "dropping, pick it up." This is cleaner than comparing `predictedHr` against `targetHr`, since in DISTANCE_PROFILE mode `targetHr` changes per segment and the slope signal is the authoritative trend.

### 4. First-workout audio primer

**Trigger:** First time the user taps the primary Start Workout button in a session where `AudioSettings.audioPrimerShown == false`. Gated by a new boolean on `AudioSettings`.

Not at the end of onboarding, because the primer makes more sense *in context* (right before they actually start a run). Onboarding already has 9 pages — adding another risks bounce.

**Where to hook:** In `SetupViewModel` (and the bootcamp equivalent, `BootcampViewModel`) — wrap the `startWorkout()` call. If `audioSettings.audioPrimerShown == false`, emit a one-shot event that the screen collects and shows the primer dialog. User dismisses → set `audioPrimerShown = true` → actual start proceeds.

**Content (three slides, skippable at any point via "Skip" in top-right):**

**Slide 1 — "How Cardea coaches you"**
> You'll hear three kinds of sound during your run:
>
> **Chime** — a quick status ping.
> **Voice** — what to do about it.
> **Vibration** — something urgent.
>
> [Next]

**Slide 2 — "When you drift"**
> If you drift out of your target zone, Cardea gives you a chime and a voice cue.
>
> If you're still out of zone 30 seconds later, you'll hear it again — a little louder, with vibration. That's escalation — it means you haven't adjusted yet.
>
> [Next]

**Slide 3 — "A heads-up, before you drift"**
> Sometimes Cardea will coach you *while you're still in zone*. If your heart rate is trending toward the edge, you'll hear a heads-up like "Pace climbing — ease off."
>
> Adjust early and you'll stay in. That's a feature, not a bug.
>
> [Got it — start my run]

Slide 1 has a small inline link: "See all sounds →" which navigates to the Sound Library screen (replacing the primer flow; user re-taps Start Workout after).

**Implementation:**
- New Composable `AudioPrimerDialog(onFinish: () -> Unit, onSeeLibrary: () -> Unit)` in `ui/workout/` (or a shared `ui/components/` folder).
- Adds `audioPrimerShown: Boolean = false` to `AudioSettings`.
- Adds `setAudioPrimerShown(true)` to `AudioSettingsRepository`.
- Two cloud backup/restore paths must be updated (per CLAUDE.md: `AudioSettings` has dual persistence — local Gson blob *and* field-by-field cloud map in `CloudBackupManager.syncSettings` + `CloudRestoreManager.restoreSettings`). Add `audioPrimerShown` to both.
- Setup screen (`SetupScreen.kt`) and Bootcamp screen (`BootcampScreen.kt`) both observe a `showAudioPrimer` state and render the dialog before `startWorkout` actually fires. Simulation bypass (which already skips permission gates) also skips the primer.

### 5. Patterned vibration at tier 3

**File:** `VibrationManager.kt`.

**Current:** `pulseAlert()` emits a two-pulse waveform (150 ms on, 100 ms gap, 150 ms on) regardless of direction.

**New:** Split into `pulseSpeedUp()` and `pulseSlowDown()`.

- **Speed up (urgency-up):** three short pulses — `0, 100, 80, 100, 80, 100` with intensity `0, 255, 0, 255, 0, 255` — fast tap-tap-tap, feels like "hurry."
- **Slow down (urgency-down):** two long pulses — `0, 280, 150, 280` with intensity `0, 200, 0, 200` — slower, heavier, feels like "settle."
- **Signal lost / generic alert:** continue to use the existing `pulseAlert()` pattern.

`EscalationTracker` passes the direction (`SPEED_UP` vs `SLOW_DOWN`) into `VibrationManager` at tier 3. The new public API is a single dispatcher:

```kotlin
fun pulseForEvent(event: CoachingEvent)  // internally: SPEED_UP → pulseSpeedUp(), SLOW_DOWN → pulseSlowDown(), else → pulseAlert()
```

`pulseAlert()` remains a private helper plus a public fallback for callers that don't have a specific event. All new call sites use `pulseForEvent`.

Users are not taught the pattern explicitly — the primer does not mention it. They learn subconsciously through repetition + the directional TTS that fires alongside. That's deliberate; the teaching happens automatically through association.

### 6. Post-run "Sounds heard today" recap (first 3 runs)

**Where:** A new collapsible section on `PostRunSummaryScreen.kt`, rendered only when `workoutIndexAllTime < 3`.

**Content:**
> **Sounds you heard today**
>
> We fired a few coaching cues during your run. Here's what each one meant:
>
> • 2 × slow-down alerts — HR climbed above your target zone.
> • 3 × kilometer splits — a marker every km you completed.
> • 1 × predictive warning — we saw your pace trending and gave you a heads-up before you drifted.
>
> [See all sounds →]   (links to Sound Library)

**Data source:** Add counters to `WorkoutState` for each `CoachingEvent` that fires during the run, persisted as part of `WorkoutMetrics` (a new `cueCountsJson` field, Room-safe as a JSON string). On post-run, `PostRunSummaryViewModel` reads the saved counts + the user's lifetime workout count (via `WorkoutRepository.countWorkouts()`). If lifetime count ≤ 3, render the section. Otherwise, hide.

**Rationale:** Fades out after the user is oriented. Non-intrusive. Educates in the moment the user is already reflecting on the run.

## Data model changes

**`WorkoutSnapshot`** (new fields):
- `lastCueBanner: CueBanner? = null` — transient, not persisted.

**New types:**
- `data class CueBanner(val title: String, val subtitle: String, val firedAtMs: Long, val kind: CueBannerKind)` in `service/audio/`.
- `enum class CueBannerKind { ALERT, GUIDANCE, MILESTONE, INFO }`.
- `object CueCopy` mapping `CoachingEvent` → `(title, subtitle, kind, iconVector)`.

**`AudioSettings`** (one new field):
- `val audioPrimerShown: Boolean = false`.
- Cloud backup: add to `CloudBackupManager.syncSettings` (write) and `CloudRestoreManager.restoreSettings` (read).

**`WorkoutMetrics`** (one new field):
- `val cueCountsJson: String? = null` — a JSON map of `CoachingEvent.name → count`. Optional; null for workouts where no cues fired (e.g. FREE_RUN at OFF verbosity).
- Schema bump: Room migration required. Bump DB version, add `ALTER TABLE workout_metrics ADD COLUMN cueCountsJson TEXT`.

## Navigation

- `Routes.SOUND_LIBRARY = "sound_library"` added to `NavGraph.kt`.
- `composable(Routes.SOUND_LIBRARY) { SoundLibraryScreen(onBack = { navController.popBackStack() }) }`.
- Wired from Account → Audio & Coaching → Sound library row.
- Wired from primer slide 1 ("See all sounds →") — uses a top-level nav that dismisses the primer.
- Wired from post-run recap ("See all sounds →") — nav from post-run to sound library.

## Testing

**Unit tests:**
- `CueCopy` has an entry for every `CoachingEvent` enum value (fail-fast test iterating the enum).
- `WorkoutMetricsRepository` round-trips `cueCountsJson` correctly.
- `AudioSettingsRepository` round-trips `audioPrimerShown`.
- Cloud backup/restore integration test covers `audioPrimerShown`.
- `VibrationManager.pulseForEvent` dispatches to the right internal pattern for each `CoachingEvent`.
- `PREDICTIVE_WARNING` fallback copy selects the climbing vs dropping phrase based on `predictedHr` vs `targetHr`.

**Instrumentation / manual:**
- Fresh install → onboarding → first Start Workout → primer appears → skip → second Start Workout → primer does **not** re-appear.
- Simulation mode bypasses primer (consistent with existing permission bypass pattern).
- Cue banner appears and clears at ~3.5 s for each cue type.
- Cue banner is replaced, not queued, when a second cue fires during its lifetime.
- Sound Library preview plays earcon at current `earconVolume`; layers over music (no audio focus stolen).
- Post-run recap appears for workouts 1, 2, 3 and is absent for workout 4+.

## Rollout / risk

- Additive feature. No removal of existing behavior.
- Migration on `WorkoutMetrics` is a simple column add; nullable, no data backfill.
- One possible regression: if the cue-banner coroutine leaks when the service stops. Mitigation: the auto-clear coroutine lives on `WorkoutState`'s existing scope and is cancelled with the workout.
- Simulation path already has its own bypass pattern; primer follows it.

## Deferred (Phase 2)

- **Earcon WAV vocabulary redesign.** Rebuild the 12 earcons into 4 families (ascending-positive / descending-negative / double-note-milestone / staccato-rising-heads-up). Requires audio production work + A/B data on whether Phase 1 already solved the confusion.
- **Race / Quiet mode** — critical-only voice, vibration-only zone alerts.
- **Per-cue granular toggle extensions** for predictive warnings and in-zone confirm.
- **Cue-explain voice command** ("what was that?") — out of scope, probably never justified.
- **Localization** of all new strings.
