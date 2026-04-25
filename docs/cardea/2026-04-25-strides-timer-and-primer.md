# Strides timer + first-time primer
Date: 2026-04-25

## Intent
- Replace the current "Time for strides!" text-only cue (WFS:611) with a structured rep timer: one-shot voice announcement at the 20-min mark, then earcon-driven 20s work / 60s rest cycles for the prescribed rep count, then a set-complete earcon.
- Earcons (transition chimes), not voice, drive the per-rep cadence — counting reps in your head is necessary information, but per-rep voice ("ease off, jog to recover") would be supplementary nagging by repetition.
- Add a one-time bootcamp primer that explains strides on first encounter (form-not-speed, target effort, recovery rule), reusing the existing `dismissPrimerThenProceed` pattern.
- Add an audio setting `stridesTimerEarcons` (default ON) so purists can opt out and revert to today's freeform behavior.
- Gate everything on `WorkoutConfig.guidanceTag == "strides"` so both `strides_20s` and `zone2_with_strides` get the same treatment.

## Scope boundary
**In:**
- New `StridesController` (per-workout state machine, like AdaptivePaceController)
- 3 new earcon WAVs (`earcon_strides_go`, `earcon_strides_ease`, `earcon_strides_set_complete`) loaded by `EarconPlayer`
- One-shot strides announcement via `VoicePlayer.speakAnnouncement`
- `AudioSettings.stridesTimerEarcons: Boolean` (default true) — local Gson + cloud paths
- Bootcamp primer modal on first strides session, persisted in DataStore
- Rep-count derivation from session duration (20→4, 22→6, 24→8, 26→10)
- WFS hookup in `processTick` after the existing strides cue branch
- Unit tests for `StridesController` state machine
- Settings UI: one row in `AudioSettingsSection`

**Out:**
- Per-rep voice cues ("rep 3 of 5", "ease off") — explicitly not nagging-tier
- Restructuring `WorkoutConfig` to model intervals (state machine handles it inline)
- Changing strides scheduling, eligibility, tier gating, or session selection
- Adding a strides session outside bootcamp
- Renaming the preset (it's already `name = "Easy + Strides"` at PresetLibrary:259 — verify session-list label only)

## Files to touch

**New:**
- `app/src/main/java/com/hrcoach/domain/engine/StridesController.kt` — state machine: Idle → Announce → (Work → Rest) × N → Done
- `app/src/test/java/com/hrcoach/domain/engine/StridesControllerTest.kt` — unit tests
- `app/src/main/java/com/hrcoach/ui/bootcamp/StridesPrimer.kt` — modal Compose (mirrors existing primer pattern)
- `app/src/main/res/raw/earcon_strides_go.wav` — short rising tone (Graham to generate)
- `app/src/main/res/raw/earcon_strides_ease.wav` — short falling tone (Graham to generate)
- `app/src/main/res/raw/earcon_strides_set_complete.wav` — three-note resolve (Graham to generate)

**Modify:**
- `app/src/main/java/com/hrcoach/service/audio/EarconPlayer.kt` — register 3 new sound IDs in load list + `loadedSampleIds` gate
- `app/src/main/java/com/hrcoach/service/audio/CoachingAudioManager.kt` — add `playStridesGo()`, `playStridesEase()`, `playStridesSetComplete()`, gated on `audioSettings.stridesTimerEarcons` AND `shouldPlayEarcon(verbosity)`
- `app/src/main/java/com/hrcoach/service/audio/VoicePlayer.kt` — no schema change; reuse `speakAnnouncement` for the one-shot
- `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt` — instantiate `StridesController` per workout when `guidanceTag == "strides"`; in `processTick` after line ~622, call `strides?.evaluateTick(elapsedSeconds, zoneStatus)` and dispatch returned events to CoachingAudioManager; remove the per-tick text-only quip at line 611–612 (replaced by StridesController's announcement)
- `app/src/main/java/com/hrcoach/domain/model/AudioSettings.kt` — add `stridesTimerEarcons: Boolean = true`
- `app/src/main/java/com/hrcoach/data/repository/AudioSettingsRepository.kt` — Gson local persistence (auto-defaults missing field; verify)
- `app/src/main/java/com/hrcoach/data/firebase/CloudBackupManager.kt` — add field to `syncSettings` map
- `app/src/main/java/com/hrcoach/data/firebase/CloudRestoreManager.kt` — read field in `restoreSettings`
- `app/src/main/java/com/hrcoach/ui/components/settings/AudioSettingsSection.kt` — add toggle row "Strides timer earcons", subtitle "Plays soft chimes for each 20s pickup during strides runs"
- `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt` — add `stridesPrimerSeen` flag (DataStore-backed) + `dismissStridesPrimer()` mirroring existing primer pattern
- `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt` — show `StridesPrimer` modal when next session is strides AND `!stridesPrimerSeen`

**Verify only (no change expected):**
- `app/src/main/java/com/hrcoach/domain/preset/PresetLibrary.kt` — confirm preset name "Easy + Strides" already surfaces in bootcamp session list (line 259)

## CLAUDE.md rules honored

- **Audio pipeline:** new earcons use `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` via existing `EarconPlayer` (SoundPool path, not ToneGenerator) — no new audio routing.
- **`EarconPlayer.play()` gated on `loadedSampleIds`** — register the 3 new IDs in the same setOnLoadCompleteListener flow; do not call `play()` on unloaded IDs (silent drop bug).
- **Verbosity gate:** all 3 strides earcons go through `shouldPlayEarcon(verbosity)`. OFF = silent (consistent with rest of pipeline). MINIMAL and FULL = play.
- **Preset quip isolation (audio-pipeline.md:34):** the strides cue is currently `isPresetQuip = true`, display-only. The new one-shot announcement bypasses this by using `speakAnnouncement` directly — it fires once at trigger time and never repeats, distinct from per-tick quip relay. The current `voiceGuidance: String? = null` for strides stays untouched (RETURN_TO_ZONE / PREDICTIVE_WARNING during a strides run still use the fallback "Back in zone" / "Watch your pace").
- **`AudioSettings` dual persistence (audio-pipeline.md:12):** new field touches BOTH local Gson (auto-defaults) AND cloud field-by-field map. Verify both paths in tests.
- **Live audio settings reload (audio-pipeline.md:10):** toggling the setting mid-workout works automatically via `ACTION_RELOAD_AUDIO_SETTINGS` already wired through `AccountViewModel.saveAudioSettings`.
- **No same-tick races (CLAUDE.md WFS rule):** `StridesController.evaluateTick` is pure (input: elapsedSeconds, zoneStatus; output: list of events). All `WorkoutState.update {}` calls stay in WFS, not in the controller. Earcon dispatch happens after the `update {}` block, same pattern as adaptive results.
- **`CoachingAudioManager.skipNextBriefing` not affected** — strides is mid-workout, not startup.
- **TtsDebugLogger:** new earcon dispatches are captured automatically by existing `EARCON ... action=PLAY|DROP` log lines (audio-pipeline.md:47). The one-shot announcement is captured by the existing TTS logging.

## State machine (StridesController)

```
phase = Idle, repIndex = 0, cycleStartSec = -1, totalReps = repsForDuration(durationMin)

evaluateTick(elapsedSec, zoneStatus) → List<StridesEvent>:
  Idle:
    if elapsedSec >= 1200 && zoneStatus == IN_ZONE:
      phase = Work; cycleStartSec = elapsedSec; repIndex = 1
      emit ANNOUNCE(totalReps), REP_START(repIndex, totalReps)
  Work:
    if elapsedSec - cycleStartSec >= 20:
      phase = Rest
      emit REP_END
  Rest:
    if elapsedSec - cycleStartSec >= 80:  // 20s work + 60s rest
      if repIndex >= totalReps:
        phase = Done
        emit SET_COMPLETE
      else:
        repIndex++; cycleStartSec = elapsedSec; phase = Work
        emit REP_START(repIndex, totalReps)
  Done: no-op
```

`repsForDuration(min)`: 20→4, 22→6, 24→8, 26→10 (default 5 if unmapped).

## Tests

**Unit (StridesControllerTest):**
- Idle stays Idle when elapsedSec < 1200
- Idle stays Idle when zone is OUT (defers trigger until in-zone)
- First in-zone tick at ≥1200s emits ANNOUNCE + REP_START
- Work → Rest transition exactly at +20s emits REP_END
- Rest → Work transition exactly at +80s emits REP_START with repIndex+1
- Final rep emits SET_COMPLETE not REP_START
- After Done, no further events fire even on more ticks
- `repsForDuration` mapping: 20=4, 22=6, 24=8, 26=10, 30=5 (fallback)
- Re-entering OUT_OF_ZONE mid-set does NOT abort the cycle (timer continues — strides already started)

**Device verification (mobile-mcp ladder):**
- Sim a 20-min `strides_20s` run via `SimulationController`; verify at +20:00 the announcement speaks and the first earcon plays
- Watch logcat for the 4 expected REP_START/REP_END pairs + SET_COMPLETE
- Toggle `stridesTimerEarcons` off in Account settings mid-run; verify no further earcons fire (live reload works)
- Cold-start a fresh user (or wipe DataStore key); verify primer shows on first bootcamp strides session and dismisses correctly

**TtsDebugLogger:** pull post-run log; confirm 1 ANNOUNCE TTS line + N×2 + 1 EARCON lines.

## Risk flags
- **Touches `service/audio/`** — earcon load gating is critical (audio-pipeline.md:39). Must register IDs in `loadedSampleIds` before any `play()` call.
- **Touches `AudioSettings`** — dual persistence path required. Skip cloud paths and the setting works locally but doesn't sync, surfacing as a phantom regression on device restore.
- **Touches `WorkoutForegroundService.processTick`** — same-tick race rule. Keep `StridesController.evaluateTick` pure; do all `WorkoutState.update {}` in WFS.
- **Bootcamp primer DataStore key** — namespace it (`bootcamp_strides_primer_seen`) to avoid collision with existing primer keys.
- **Not a schema change** — no Room migration, no DB backup needed.
- **Not a cloud sync field on Workout/TrackPoint** — only AudioSettings cloud path.

## Execution split
- Subagent A: `StridesController` + tests (pure Kotlin, parallel-safe)
- Subagent B: WAV registration in `EarconPlayer` + `CoachingAudioManager` methods + `AudioSettings` field + dual persistence
- Subagent C: `StridesPrimer` Compose + `BootcampViewModel`/`BootcampScreen` wiring
- Main thread: WFS integration after A and B both land; settings UI row after B
- Verification ladder runs after all merged.

## Phasing note
Graham still needs to generate the 3 WAV files. Plan ships in two waves:
1. **Wave 1 (no WAVs needed):** controller + tests + AudioSettings field + cloud paths + primer + WFS integration with placeholder earcon stubs (log-only). Mergeable, fully tested in unit + sim.
2. **Wave 2 (after WAVs land):** drop WAV files into `res/raw/`, swap stubs for real `EarconPlayer` calls, device-verify.
