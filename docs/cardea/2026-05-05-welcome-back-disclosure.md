# Welcome-Back Schedule-Rewind Disclosure
Date: 2026-05-05

## Intent
- After a gap of 15+ days, GapAdvisor silently rewinds week/phase, optionally demotes tier, and `deleteSessionsAfterWeek()` removes stale future sessions. The current `WelcomeBackDialog` mentions only the tier easing (and only when it happens) — schedule-side changes are invisible.
- Replace the override-style single string with a structured disclosure that names BOTH dimensions (schedule + intensity) with concrete numbers (week X→Y, N sessions cleared), plus a persistent breadcrumb chip on the week strip until the next session is completed.
- Goal: user always has a causal story for "why did my week counter move backwards / where did my upcoming sessions go."

## Scope boundary
**In:**
- New `WelcomeBackDisclosure` UiState payload (replaces `welcomeBackMessage: String?`).
- VM captures old/new phase + week, deletion count; composes disclosure for MEANINGFUL_BREAK and worse.
- Restructured dialog: title + two labeled rows (Schedule, Intensity) with concrete values; one row hides if no change in that dimension.
- New `ScheduleRewindBreadcrumb` chip on the week strip header (compact, dismissible, auto-clears once any session in the new week is completed).
- Persistent breadcrumb survives process restart (DataStore flag keyed by enrollment id + adjustmentTimestamp).

**Out:**
- Undo/refuse-the-rewind affordance (separate, much bigger feature — agency rather than disclosure).
- Disclosure for SHORT_BREAK (no rewind happens, only an inserted return session). Unchanged.
- Tier-only demotions WITHOUT a gap (e.g. illness signals): stays out of this dialog; uses existing tier-prompt flow.
- Copy localization beyond English.

## Files to touch
- `data/db/BootcampDao.kt` — `deleteSessionsAfterWeek(...)` return type `Unit` → `Int` (Room @Query DELETE returns deleted-row count natively).
- `data/repository/BootcampRepository.kt` — bubble Int through both `deleteSessionsAfterWeek` and `deleteSessionsFromWeek` callers; check no other call site discards return.
- `app/src/test/.../FakeBootcampDao.kt` + `WorkoutForegroundServiceFinishBootcampEarlyTest.kt` — update fake/override signatures to return Int.
- `ui/bootcamp/BootcampUiState.kt` — replace `welcomeBackMessage: String?` with `welcomeBackDisclosure: WelcomeBackDisclosure?`. New data class with `scheduleChange: ScheduleChange?`, `intensityChange: IntensityChange?`, `sessionsCleared: Int`.
- `ui/bootcamp/BootcampViewModel.kt` — `applyGapAdjustmentIfNeeded()` returns the disclosure; `refreshFromEnrollment()` passes through; `_pendingTierDemotedMessage` removed.
- Persistence: new DataStore key `pendingWelcomeBackBreadcrumb` (JSON: enrollment id + adjustment timestamp + payload). Cleared by `BootcampSessionCompleter` once a session in the new week completes, AND by user dismiss.
- `ui/bootcamp/BootcampScreen.kt` — `WelcomeBackDialog` signature changes to take `WelcomeBackDisclosure`; new internal composables `ScheduleRow`, `IntensityRow`. New `ScheduleRewindBreadcrumb` rendered between week-date-range header and the 7-day strip.
- `ui/bootcamp/components/` — extract `ScheduleRewindBreadcrumb.kt` if it exceeds ~40 lines.
- Tests:
  - `BootcampViewModelTest.kt` — matrix: (MEANINGFUL_BREAK without tier change), (MEANINGFUL_BREAK + tier demotion), (EXTENDED_BREAK), (LONG_ABSENCE), (FULL_RESET). Assert disclosure structure and sessionsCleared count.
  - New `WelcomeBackDisclosureFormatterTest.kt` if formatting logic is extracted into a pure helper (recommended for testability).
  - Update `BootcampViewModelTest.kt:247` mock for `deleteSessionsAfterWeek` to return Int.

## CLAUDE.md rules honored
- **Bootcamp scheduling:** rewind happens only in `applyGapAdjustmentIfNeeded()`; `getNextSession()` not regressed; ISO dayOfWeek / Monday-anchor session-date math untouched.
- **DataStore/Slider rule:** breadcrumb persistence uses a single write on dismiss / on session complete, never inside Compose recompositions.
- **Compose:** dialog uses `remember { }` only at top level (no conditionals); chip uses `.clip()` before `.background()` for rounded edge; respects three-gradient rule (chip uses neutral glass, NOT a gradient).
- **Cloud sync:** disclosure is transient UI state, **not** persisted to Firebase. CloudBackupManager / CloudRestoreManager untouched.
- **No PRs preference:** local merge → push.

## Risk flags
- ⚠ **DAO signature change** touches `data/db/` — auto DB backup BEFORE `adb install -r`, per `.claude/rules/adb-data-backup.md`.
- No `service/` or audio changes — dual-pause / notification-stop / USAGE_ASSISTANCE_NAVIGATION_GUIDANCE rules N/A.
- No schema change (no `@Entity` field added; only DAO method return type) — no Room migration needed.
- No CloudBackup field added.
- WelcomeBackDialog override path (`_pendingTierDemotedMessage`) is being removed; verify no other call site sets it. Grep confirms it's only set at line 178 in the same VM.

## Tests
**Unit:**
- VM disclosure matrix (5 cases above).
- DAO returns correct deletion count (Room @Query DELETE returning Int — covered indirectly via repository test if missing; add `BootcampDaoTest.deleteSessionsAfterWeek_returnsCount`).
- Breadcrumb auto-clear on next session complete (test in `BootcampSessionCompleterTest`).

**Device (mobile-mcp):**
- Simulate 20-day gap (MEANINGFUL_BREAK): screenshot dialog showing both rows; verify chip on week strip.
- Simulate 35-day gap with low CTL (EXTENDED_BREAK + tier demotion): screenshot dialog showing both rows with concrete numbers.
- Simulate 70-day gap (LONG_ABSENCE): screenshot dialog mentioning Discovery Run + reset to Base.
- Tap "Got it"; confirm chip persists.
- Complete a session in the new week; confirm chip vanishes.
- Kill + relaunch app after rewind, before completing a session; confirm chip rehydrates from DataStore.

## Layered review tier
- Layer 0 (grep): standard.
- Layer 1 (unit): mandatory.
- Layer 2 (Haiku): mandatory — diff will be ~250 lines.
- Layer 3 (Sonnet): trigger because we touch `data/` (DAO signature + repository), even though no schema migration.
- Layer 4 (full ai-review): skip. Not schema, not cloud, not security.

## Visual companion
Pair with frontend-design at the dialog/chip drafting step. Prompt to include: Cardea three-gradient hierarchy, glass surfaces for the chip (not pink), CardeaCtaGradient reserved for the dialog confirm button only.

## Open follow-ups (not in this change)
- Snackbar undo flow ("Restore my old schedule") — design separately; would need to persist the *pre-rewind* enrollment snapshot for the undo window.
- Localization of disclosure copy.
- Analytics event on dialog show / dismiss / chip dismiss / breadcrumb auto-clear.
