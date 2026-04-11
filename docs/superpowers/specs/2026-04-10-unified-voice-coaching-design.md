# Unified Voice Coaching System

**Date:** 2026-04-10
**Status:** Design

## Problem

Two independent voice systems speak conflicting guidance for the same HR zone event:

- **VoiceCoach** (MediaPlayer) plays pre-recorded MP3 clips — e.g. "Slow down"
- **TtsBriefingPlayer** (Android TTS) speaks dynamic guidance — e.g. "HR settling back - hold steady"

At FULL verbosity, both play sequentially for SPEED_UP/SLOW_DOWN events. Messages often contradict each other, and two different voice genders/characters feel unpolished.

Additionally, the startup sequence plays the countdown *before* the TTS briefing, and the briefing is fire-and-forget — the workout timer starts while TTS is still speaking.

The ToneGenerator countdown beeps are inaudible on some devices when layered over music.

## Solution

### 1. Single Voice System — All-TTS

Drop `VoiceCoach` (MediaPlayer/MP3) entirely. All spoken coaching goes through a unified TTS player (expanded from `TtsBriefingPlayer`, renamed to `VoicePlayer`).

- TTS speaks the adaptive guidance text directly — the same strings `AdaptivePaceController.buildGuidance()` already generates
- One voice, one system, one volume control
- Priority gating (from `VoiceEventPriority`) transfers to the TTS player: CRITICAL events can interrupt NORMAL/INFORMATIONAL, but not vice versa
- Uses `TextToSpeech.QUEUE_FLUSH` for critical interrupts, `QUEUE_ADD` for sequential

### 2. Startup Sequence — Briefing → Countdown → Timer

Reorder and fix the startup flow:

1. **TTS briefing speaks** (e.g. "26 minute easy run. Aim for heart rate around 145.") — `playStartSequence()` **awaits TTS completion** before proceeding
2. **Countdown MP3 plays** — a curated `countdown_321_go.mp3` via MediaPlayer (marimba-style 3-2-1-GO, ~4s). Updates `WorkoutState.countdownSecondsRemaining` for UI display.
3. **`playStartSequence()` returns** — only then does the workout timer start

The countdown uses a pre-recorded MP3 (not ToneGenerator) because:
- ToneGenerator `TONE_PROP_BEEP` is inaudible on some devices over music
- The countdown is a fixed ritual — the one place where a curated sound asset is the right choice
- Consistent, premium sound on every device

`StartupSequencer` switches from `ToneGenerator` to `MediaPlayer` with `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`.

### 3. Verbosity Levels

Three levels, redefined for TTS-only:

| Level | Earcons | Voice (TTS) | Events voiced |
|-------|---------|-------------|---------------|
| **OFF** | No | No | None |
| **MINIMAL** | Yes | Critical + Normal only | SPEED_UP, SLOW_DOWN, RETURN_TO_ZONE, PREDICTIVE_WARNING, SEGMENT_CHANGE, SIGNAL_LOST, SIGNAL_REGAINED |
| **FULL** | Yes | All events | Above + IN_ZONE_CONFIRM, HALFWAY, KM_SPLIT, WORKOUT_COMPLETE |

### 4. KM Splits by Workout Mode

- **STEADY_STATE / DISTANCE_PROFILE:** Simple — "Kilometer 5" (number only). Runner's focus is on HR targets, not pace stats.
- **FREE_RUN:** Rich — "Kilometer 5. Pace: 5 minutes 40." Includes average pace since no HR target occupies attention.

The split text is built inside `VoicePlayer.speakEvent()` based on the current `WorkoutConfig.mode`.

### 5. What Gets Deleted

| Item | Reason |
|------|--------|
| `VoiceCoach.kt` | Replaced by expanded VoicePlayer (TTS) |
| 60+ `voice_*.mp3` files in `res/raw/` | No longer needed — all coaching is TTS |
| `ToneGenerator` countdown in `StartupSequencer` | Replaced by `countdown_321_go.mp3` via MediaPlayer |
| Dual-play path in `CoachingAudioManager.fireEvent()` lines 74-84 | Single TTS call replaces MP3+TTS sequential play |

### 6. What Gets Kept (Unchanged)

| Item | Why |
|------|-----|
| `EarconPlayer` (SoundPool) | Zone alert tones are distinct from voice — short non-speech audio cues |
| `EscalationTracker` | Escalation logic (earcon-only → earcon+voice → earcon+voice+vibration) still applies |
| `VibrationManager` | Independent haptic feedback |
| `VoiceEventPriority` | Reused in VoicePlayer for TTS interrupt logic |
| `CoachingAudioManager` | Orchestrator role unchanged, just simplified internally |
| `AdaptivePaceController.buildGuidance()` | Source of all coaching text — unchanged |
| `CoachingEventRouter` | Event routing logic unchanged |

### 7. New File: `countdown_321_go.mp3`

Custom-generated marimba-style countdown:
- Three C6 (1047 Hz) marimba ticks at 1-second intervals
- One C7 (2093 Hz) sustained GO tone with warm sustain layer
- ~4 seconds total duration
- Generated with Python (sine synthesis + inharmonic partials + exponential decay)
- Original to Cardea — no licensing concerns

Placed in `app/src/main/res/raw/countdown_321_go.wav` (WAV — Android MediaPlayer handles natively, no encoding dependency needed).

### 8. VoicePlayer Design

Renamed from `TtsBriefingPlayer`. Responsibilities:

1. **Workout briefing** at start (existing `speakBriefing()`)
2. **Coaching event speech** — speaks `guidanceText` for zone alerts, or generates simple text for informational events
3. **KM split announcements** — mode-aware text generation
4. **Priority gating** — tracks current playback priority, prevents low-priority events from interrupting high-priority speech
5. **Verbosity filtering** — respects OFF/MINIMAL/FULL settings

Key API:
```kotlin
fun speakBriefing(config: WorkoutConfig)          // startup briefing
fun speakEvent(event: CoachingEvent, guidanceText: String?, config: WorkoutConfig?)  // coaching events
fun isPlaying(): Boolean
suspend fun awaitCompletion()                      // for startup sequence ordering
fun destroy()
```

The `speakEvent()` method internally:
- Checks verbosity level against event priority (MINIMAL skips INFORMATIONAL events)
- Builds appropriate text (guidance text for zone alerts, "Kilometer N" for splits, etc.)
- Applies priority gating (won't interrupt CRITICAL with INFORMATIONAL)

### 9. CoachingAudioManager Changes

`fireEvent()` simplifies from the current dual-path to:

```
earcon (if enabled) → delay → voicePlayer.speakEvent(event, guidanceText, config)
```

No more `voiceCoach.speak()` + `voiceCoach.awaitCompletion()` + `ttsBriefingPlayer.speak()` chain. Single call.

### 10. Risk: TTS Init Latency

Android TTS can take 200-500ms to initialize on first use. Current mitigation (already in `TtsBriefingPlayer`): buffer pending text if TTS isn't ready, speak when init completes. This carries forward to `VoicePlayer` unchanged.

On devices where TTS is completely unavailable (rare), the app degrades to earcons-only — same as current `VoiceVerbosity.MINIMAL` behavior.
