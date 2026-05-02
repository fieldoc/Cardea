# Home Lifecycle States — Graduate / Pause / New
Date: 2026-05-01

## Intent

- Stop reverting Home to the brand-new-user enrollment pitch when a user is **paused** or **graduated**.
- Add a triumphant **Graduate** hero (race-goal complete) with a clear path to a next program — EVERGREEN / Cardio Health prominent, fitness tier preserved.
- Add a **Resume** hero for paused enrollments — no-shame welcome-back tone, "Resume" CTA that calls existing `resumeBootcamp()`.
- Refresh the existing **NoBootcamp** (new-user) hero so it reads consistently alongside the two new heroes.
- Verify pause / resume / end controls in the Workout tab remain reachable; no new control surfaces in this PR.

## Scope boundary

**In:**
- `HomeViewModel` lifecycle derivation (ACTIVE / PAUSED / GRADUATED / NONE).
- `HomeScreen` hero branching — three distinct heroes.
- `GraduateHero` stats display (weeks, sessions, total km from `WorkoutMetricsRepository` + bootcamp session counts).
- "Choose next program" CTA on `GraduateHero` routes to bootcamp setup with EVERGREEN pre-selected (Cardio Health).
- Light visual refresh of `NoBootcampCard` to harmonise with the two new heroes.

**Out:**
- New `STATUS_ENDED` value or schema migration. Deferred until micro-goals/streaks land.
- Multi-program archive, post-grad stats history beyond what we already store.
- Account-tab pause controls (the existing `BootcampScreen` extras menu is the surface).
- EVERGREEN "end" UX changes — `deleteBootcamp()` already works; we just acknowledge it on Home via the no-bootcamp pitch.
- Workout-tab behaviour when paused/graduated (separate concern; this PR is Home only).

## Files to touch

- `app/src/main/java/com/hrcoach/ui/home/HomeViewModel.kt` — drop `STATUS_ACTIVE` filter at lines 108-110; add sealed `HomeBootcampState` (`Active(enrollment, nextSession)` / `Paused(enrollment)` / `Graduated(enrollment, stats)` / `None`); expose graduate stats query.
- `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt` — replace the `hasActiveBootcamp` branch (lines 1180-1198) with a `when (state.bootcampState)` over the sealed type; add `GraduateHero` and `ResumeCard` composables alongside existing `PulseHero` / `AllCaughtUpCard` / `NoBootcampCard`; pass `onResume`, `onChooseNextProgram` callbacks.
- `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt` (continued) — refresh `NoBootcampCard` copy/spacing only; keep gradient + glass language.
- `app/src/main/java/com/hrcoach/ui/navigation/AppNav.kt` (or whichever wires Home callbacks) — surface `onResume` → `BootcampViewModel.resumeBootcamp()`, `onChooseNextProgram` → bootcamp setup with EVERGREEN default goal.
- `app/src/test/java/com/hrcoach/ui/home/HomeViewModelTest.kt` — new test cases for each `HomeBootcampState` branch.

## CLAUDE.md rules honored

- **No DataStore in `onValueChange`** — N/A here, no sliders touched.
- **`remember` not in conditionals** — `GraduateHero` and `ResumeCard` use `remember { LocalDate.now().toEpochDay() }` at the top of the composable, mirroring `PulseHero` (line 263).
- **Distance unit single source of truth** — graduate stats display total km via `metersToUnit(totalMeters, unit)` then `formatPace(...)` is unused here (no pace in summary). Persist meters, convert at display.
- **Three-gradient design hierarchy** (memory: `feedback_design_hierarchy.md`) — `GraduateHero` uses gradient for the triumph headline ("Marathon ready" or similar) as #1; stats grid uses glass+white as supporting; secondary CTAs use glass+secondary as ambient. `ResumeCard` is glass+secondary throughout (calmer tone).
- **Bootcamp session identity contract** (`docs/claude-rules/bootcamp-scheduling.md`) — not affected; we observe the enrollment row, not session IDs.
- **EVERGREEN tier inheritance** — when user taps "Choose next program" on `GraduateHero`, bootcamp setup VM must read `tierIndex` from prior enrollment / adaptive profile so the marathon graduate is not demoted. Verify in the navigation glue.

## Tests

**Unit (`HomeViewModelTest`):**
- `enrollment.status == ACTIVE` → `HomeBootcampState.Active(enrollment, nextSession)`.
- `enrollment.status == PAUSED` → `HomeBootcampState.Paused(enrollment)`; `nextSession` not consulted.
- `enrollment.status == GRADUATED` → `HomeBootcampState.Graduated(enrollment, stats)`; stats include weeks completed + session count + total km.
- No enrollment row → `HomeBootcampState.None`.
- Status flips ACTIVE → GRADUATED via Flow → state updates without restart.

**Device (mobile-mcp + sim):**
- Sim a workout that completes the final session of a short race goal → land on Home → confirm `GraduateHero` appears (screenshot).
- Pause via `BootcampScreen` extras menu → navigate to Home → confirm `ResumeCard` appears with correct enrollment context (screenshot).
- Tap Resume → status flips back to ACTIVE → Home shows `PulseHero` again.
- Tap "Choose next program" on `GraduateHero` → lands in bootcamp setup with EVERGREEN/Cardio Health pre-selected and tier preserved.
- Fresh-install no-enrollment path → `NoBootcampCard` (refreshed) renders correctly.

## Risk flags

- **No service/ touched** — dual-pause / notification stop gate not relevant.
- **No audio/ touched** — TTS guidance unchanged.
- **No schema change** — no migration, no auto DB backup required. Will still snapshot DB pre-install per skill default.
- **Cloud sync** — `BootcampEnrollmentEntity.status` is already synced (existing field, not new). No change to `CloudBackupManager` / `CloudRestoreManager`.
- **Navigation glue** — new `onChooseNextProgram` callback must thread to bootcamp setup with a default goal arg. If `BootcampSetupViewModel` doesn't accept a pre-selected goal, scope grows by one file. Flag at exec time; do not silently extend.
- **Stats query cost on Home** — graduate stats query runs only when status is GRADUATED. Cache once per enrollment ID; do not recompute every Home recomposition.

## Design session

A separate `ui-ux-pro-max` session generates the three hero composables (Graduate, Resume, NoBootcamp refresh). Prompt is in this PR's chat history; it covers tone, hierarchy, copy direction, and constraints (gradient hierarchy, glass surfaces, EVERGREEN tier preservation messaging). Designs land back as Compose code that Phase 4 integrates.
