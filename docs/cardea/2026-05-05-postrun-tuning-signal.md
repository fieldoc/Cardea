# PostRun Training Signal Card
Date: 2026-05-05

## Intent
- Surface the adaptive engine's plan-tuning decision on the PostRun screen so the runner sees what the system just learned.
- One card in the existing STATUS section, alongside `HrMaxUpdatedCard`. Bootcamp runs only.
- Plain-English headline + a single earned number ("Effort N · typical M") + directional glyph. Middle-ground between Apple-style outcomes and Garmin-style metrics.
- Env-flag-affected runs render a muted variant: strikethrough Effort, headline reverts to "Holding the plan — hot weather ignored", italic footer explaining exclusion.

## Scope
**In:**
- New `TuningDirectionCard` composable in `PostRunSummaryScreen.kt`.
- Three new fields on `PostRunSummaryUiState`: `tuningDirection`, `runEffort`, `typicalEffort`, `runEnvironmentAffected`.
- Trim editorial copy on existing `BootcampContextCard` (drop the "Strong finish. Next week will adapt…" line — the new card owns that narrative now).
- Unit tests covering bootcamp-only gating, three direction states, env-flag muted variant, missing-trimp fallback.

**Out:**
- No engine logic changes. We read `AdaptiveProfile.lastTuningDirection` and `WorkoutAdaptiveMetrics.trimpScore` / `.environmentAffected` — all already populated.
- No DB schema change. No service-layer change. No cloud-sync change.
- No tooltip / "details" expansion. (TRIMP word stays out of UI; just "Effort".)
- No freestyle (FREE_RUN / DISTANCE_PROFILE) path. Card only renders when `uiState.isBootcampRun`.

## Files to touch
- `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryViewModel.kt` — extend `PostRunSummaryUiState`, populate new fields from existing repos
- `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt` — add `TuningDirectionCard`, gate it in STATUS section, slim `BootcampContextCard` copy
- `app/src/test/java/com/hrcoach/ui/postrun/PostRunSummaryViewModelTest.kt` — new tests for the four UiState fields

## Design source
`Training Signal Card v2.html` from the 2026-05-05 design handoff. Relevant tokens:
- glass card 16dp radius, 12dp vertical / 14dp horizontal padding (matches `HrMaxUpdatedCard` sibling)
- headline: titleMedium 16sp 700 textPrimary, balanced wrap
- effort row: 11sp uppercase "EFFORT" textTertiary + 13sp 600 textPrimary number + 11sp textTertiary "· typical N" + 11dp directional glyph
- driver line: bodySmall 12sp textSecondary line-height 1.5
- muted variant: headline → textSecondary 600, number gets line-through (textTertiary), no comparator, italic footer at textTertiary above a 1px white-6% divider

## CLAUDE.md rules honored
- **One gradient accent per screen** — `RunCompleteHero` owns Tier 1; new card uses no gradient, no `GradientPink`. Pure glass + greyscale text.
- **No `ZoneGreen`** — green is reserved for HR-zone indicators + `AllCaughtUpCard`. Direction is communicated by glyph + copy, not color.
- **Min 12sp** — uppercase "EFFORT" label is 11sp letter-spaced (matches existing `SectionHeader` pattern in this codebase, which uses labelMedium with letterSpacing). Smallest body text is 12sp; 11sp only on the all-caps token-style label which is already an exception throughout the app.
- **Glass surface** uses `GlassCard` from `ui/components/`.
- **`remember` not in conditionals** — directional glyph state is computed via `when (direction)` returning a small data triple.

## Tests
- unit (`PostRunSummaryViewModelTest`):
  - `tuningDirection PUSH_HARDER surfaced when adaptive profile last direction is PUSH_HARDER and run is bootcamp`
  - `tuningDirection HOLD when profile direction null on bootcamp run`
  - `tuningDirection null when run is not a bootcamp run` (gating)
  - `runEffort populated from currentMetrics trimpScore` (rounded to Int)
  - `typicalEffort populated as median of recentMetrics last 30d with trimpReliable && !environmentAffected, null when fewer than 3 baseline runs`
  - `runEnvironmentAffected mirrors currentMetrics flag`
- device (mobile-mcp screenshot): three direction states + env-flag muted variant on the PostRun summary

## Risk flags
- Touches `service/` → no.
- Touches `audio/` → no.
- Touches `data/db/` → no — schema unchanged.
- Touches cloud sync → no.
- KSP/AAPT — pure Compose changes, no annotation processors triggered. Build path-length OK.
