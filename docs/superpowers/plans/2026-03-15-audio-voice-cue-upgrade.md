# Audio & Voice Cue Upgrade Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Android TTS with pre-recorded neural voice lines, add configurable informational cues, and upgrade earcons to richer pre-recorded audio assets.

**Architecture:** Pre-generate all voice/earcon assets at dev time via a Python script using `edge-tts` and numpy audio synthesis. Ship as `res/raw/*.ogg` assets. Replace `VoiceCoach` (TTS → MediaPlayer) and `EarconSynthesizer` (AudioTrack → SoundPool). Add 4 new CoachingEvent types with trigger logic in `CoachingEventRouter`. Add individually toggleable informational cue settings with a new UI section.

**Tech Stack:** Kotlin, Jetpack Compose, Android MediaPlayer, SoundPool, Python `edge-tts`, numpy/scipy for earcon synthesis

**Spec:** `docs/superpowers/specs/2026-03-15-audio-voice-cue-upgrade-design.md`

---

## Chunk 1: Asset Generation Pipeline

### Task 1: Create the voice/earcon generation script

**Files:**
- Create: `scripts/generate-voice-assets.py`

- [ ] **Step 1: Write the generation script**

```python
#!/usr/bin/env python3
"""Generate voice and earcon audio assets for Cardea coaching cues.

Requires: pip install edge-tts numpy scipy
Output:   app/src/main/res/raw/voice_*.ogg and earcon_*.ogg
"""
import asyncio
import os
import struct
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np

# ── Config ──────────────────────────────────────────────────────────
VOICE = "en-US-ChristopherNeural"
RATE = "-5%"
SAMPLE_RATE = 44100
OUTPUT_DIR = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res" / "raw"

# ── Voice lines ─────────────────────────────────────────────────────
VOICE_LINES: dict[str, str] = {
    "voice_speed_up": "Pick it up",
    "voice_slow_down": "Ease back a little",
    "voice_return_to_zone": "Back in zone",
    "voice_predictive_warning": "Watch your pace",
    "voice_segment_change": "Next segment",
    "voice_signal_lost": "Signal lost",
    "voice_signal_regained": "Signal back",
    "voice_halfway": "Halfway",
    "voice_workout_complete": "Workout complete",
    "voice_in_zone_confirm": "Still in zone",
}

# Km splits 1–50
KM_WORDS = [
    "", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
    "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen",
    "eighteen", "nineteen", "twenty", "twenty one", "twenty two", "twenty three",
    "twenty four", "twenty five", "twenty six", "twenty seven", "twenty eight",
    "twenty nine", "thirty", "thirty one", "thirty two", "thirty three", "thirty four",
    "thirty five", "thirty six", "thirty seven", "thirty eight", "thirty nine", "forty",
    "forty one", "forty two", "forty three", "forty four", "forty five", "forty six",
    "forty seven", "forty eight", "forty nine", "fifty",
]
for i in range(1, 51):
    VOICE_LINES[f"voice_km_{i}"] = f"Kilometer {KM_WORDS[i]}"

# ── Earcon definitions ──────────────────────────────────────────────
# Each earcon is a list of (freq_hz, duration_ms, harmonics) tuples with gaps
# harmonics = [(relative_freq_multiplier, amplitude_ratio), ...]

def adsr_envelope(n_samples: int, attack_ms: int = 15, decay_ms: int = 30,
                  sustain_level: float = 0.6, release_ms: int = 50) -> np.ndarray:
    """Soft ADSR envelope."""
    sr = SAMPLE_RATE
    attack = int(sr * attack_ms / 1000)
    decay = int(sr * decay_ms / 1000)
    release = int(sr * release_ms / 1000)
    sustain = max(0, n_samples - attack - decay - release)

    env = np.concatenate([
        np.linspace(0, 1, attack),
        np.linspace(1, sustain_level, decay),
        np.full(sustain, sustain_level),
        np.linspace(sustain_level, 0, release),
    ])
    # Trim or pad to exact length
    if len(env) > n_samples:
        env = env[:n_samples]
    elif len(env) < n_samples:
        env = np.pad(env, (0, n_samples - len(env)))
    return env


def generate_tone(freq: float, duration_ms: int,
                  harmonics: list[tuple[float, float]] | None = None) -> np.ndarray:
    """Generate a tone with harmonics and ADSR envelope."""
    n = int(SAMPLE_RATE * duration_ms / 1000)
    t = np.arange(n) / SAMPLE_RATE
    # Fundamental
    signal = np.sin(2 * np.pi * freq * t)
    # Harmonics
    if harmonics:
        for mult, amp in harmonics:
            signal += amp * np.sin(2 * np.pi * freq * mult * t)
    # Normalize
    peak = np.max(np.abs(signal))
    if peak > 0:
        signal = signal / peak
    # Apply envelope
    signal *= adsr_envelope(n)
    return signal


def silence(duration_ms: int) -> np.ndarray:
    return np.zeros(int(SAMPLE_RATE * duration_ms / 1000))


def concat(*parts: np.ndarray) -> np.ndarray:
    return np.concatenate(parts)


def low_pass(signal: np.ndarray, cutoff_hz: float = 4000) -> np.ndarray:
    """Simple first-order low-pass filter for warmth."""
    from scipy.signal import butter, sosfilt
    sos = butter(2, cutoff_hz, btype='low', fs=SAMPLE_RATE, output='sos')
    return sosfilt(sos, signal)


# Standard harmonics: octave at 0.3, fifth at 0.15
H_WARM = [(2.0, 0.3), (3.0, 0.15)]
H_BRIGHT = [(2.0, 0.4), (3.0, 0.2), (4.0, 0.1)]

EARCONS: dict[str, callable] = {}

def earcon(name: str):
    def decorator(fn):
        EARCONS[name] = fn
        return fn
    return decorator

@earcon("earcon_speed_up")
def _():
    # Rising arpeggio C5-E5-G5 with harmonics
    return concat(
        generate_tone(523.25, 100, H_BRIGHT), silence(50),
        generate_tone(659.25, 100, H_BRIGHT), silence(50),
        generate_tone(783.99, 120, H_BRIGHT),
    )

@earcon("earcon_slow_down")
def _():
    # Falling arpeggio G4-E4-C4 with warm harmonics
    return concat(
        generate_tone(392.00, 140, H_WARM), silence(40),
        generate_tone(329.63, 140, H_WARM), silence(40),
        generate_tone(261.63, 160, H_WARM),
    )

@earcon("earcon_return_to_zone")
def _():
    # Warm two-note chime C5+E5 mixed
    n = int(SAMPLE_RATE * 350 / 1000)
    t = np.arange(n) / SAMPLE_RATE
    s = (np.sin(2 * np.pi * 523.25 * t) +
         0.7 * np.sin(2 * np.pi * 659.25 * t) +
         0.3 * np.sin(2 * np.pi * 523.25 * 2 * t))
    s = s / np.max(np.abs(s)) * adsr_envelope(n, attack_ms=20, release_ms=80)
    return s

@earcon("earcon_predictive_warning")
def _():
    return concat(
        generate_tone(523.25, 80, H_WARM), silence(40),
        generate_tone(783.99, 100, H_WARM),
    )

@earcon("earcon_segment_change")
def _():
    return concat(
        generate_tone(800, 60, H_BRIGHT), silence(130),
        generate_tone(800, 60, H_BRIGHT),
    )

@earcon("earcon_signal_lost")
def _():
    return concat(
        generate_tone(600, 80, H_WARM), silence(40),
        generate_tone(600, 80, H_WARM), silence(40),
        generate_tone(600, 80, H_WARM),
    )

@earcon("earcon_signal_regained")
def _():
    return generate_tone(1200, 150, H_BRIGHT)

@earcon("earcon_halfway")
def _():
    return concat(
        generate_tone(659.25, 100, H_WARM), silence(60),
        generate_tone(783.99, 140, H_WARM),
    )

@earcon("earcon_km_split")
def _():
    return generate_tone(880, 80, H_BRIGHT)

@earcon("earcon_workout_complete")
def _():
    return concat(
        generate_tone(523.25, 120, H_BRIGHT), silence(60),
        generate_tone(659.25, 120, H_BRIGHT), silence(60),
        generate_tone(783.99, 120, H_BRIGHT), silence(60),
        generate_tone(1046.50, 200, H_BRIGHT),
    )

@earcon("earcon_in_zone_confirm")
def _():
    # Gentle single warm chime
    return generate_tone(659.25, 200, H_WARM)


# ── WAV / OGG helpers ───────────────────────────────────────────────

def write_wav(path: str, signal: np.ndarray):
    """Write a mono 16-bit WAV file."""
    signal = np.clip(signal, -1, 1)
    pcm = (signal * 32767).astype(np.int16)
    n_samples = len(pcm)
    with open(path, 'wb') as f:
        # RIFF header
        data_size = n_samples * 2
        f.write(b'RIFF')
        f.write(struct.pack('<I', 36 + data_size))
        f.write(b'WAVE')
        # fmt chunk
        f.write(b'fmt ')
        f.write(struct.pack('<IHHIIHH', 16, 1, 1, SAMPLE_RATE, SAMPLE_RATE * 2, 2, 16))
        # data chunk
        f.write(b'data')
        f.write(struct.pack('<I', data_size))
        f.write(pcm.tobytes())


def wav_to_ogg(wav_path: str, ogg_path: str):
    """Convert WAV to OGG using ffmpeg."""
    subprocess.run(
        ["ffmpeg", "-y", "-i", wav_path, "-c:a", "libvorbis", "-q:a", "3", ogg_path],
        capture_output=True, check=True,
    )


def mp3_to_ogg(mp3_path: str, ogg_path: str):
    """Convert MP3 to OGG using ffmpeg."""
    subprocess.run(
        ["ffmpeg", "-y", "-i", mp3_path, "-c:a", "libvorbis", "-q:a", "4", ogg_path],
        capture_output=True, check=True,
    )


# ── Main ────────────────────────────────────────────────────────────

async def generate_voice_line(name: str, text: str, output_dir: Path):
    """Generate a single voice line as .ogg via edge-tts."""
    import edge_tts
    mp3_path = str(output_dir / f"{name}.mp3")
    ogg_path = str(output_dir / f"{name}.ogg")
    communicate = edge_tts.Communicate(text, VOICE, rate=RATE)
    await communicate.save(mp3_path)
    mp3_to_ogg(mp3_path, ogg_path)
    os.remove(mp3_path)
    print(f"  voice: {name}.ogg")


async def main():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    print("Generating voice lines...")
    for name, text in VOICE_LINES.items():
        await generate_voice_line(name, text, OUTPUT_DIR)

    print("Generating earcons...")
    for name, gen_fn in EARCONS.items():
        signal = gen_fn()
        signal = low_pass(signal, cutoff_hz=5000)
        signal = np.clip(signal * 0.8, -1, 1)  # headroom

        with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
            wav_path = tmp.name
        write_wav(wav_path, signal)
        ogg_path = str(OUTPUT_DIR / f"{name}.ogg")
        wav_to_ogg(wav_path, ogg_path)
        os.remove(wav_path)
        print(f"  earcon: {name}.ogg")

    print(f"\nDone! {len(VOICE_LINES)} voice + {len(EARCONS)} earcon files in {OUTPUT_DIR}")


if __name__ == "__main__":
    asyncio.run(main())
```

- [ ] **Step 2: Install dependencies and run the script**

Run:
```bash
pip install edge-tts numpy scipy
python scripts/generate-voice-assets.py
```
Expected: ~71 `.ogg` files in `app/src/main/res/raw/`

- [ ] **Step 3: Verify assets exist**

Run: `ls app/src/main/res/raw/voice_*.ogg app/src/main/res/raw/earcon_*.ogg | wc -l`
Expected: 71 (60 voice + 11 earcon)

- [ ] **Step 4: Commit**

```bash
git add scripts/generate-voice-assets.py app/src/main/res/raw/voice_*.ogg app/src/main/res/raw/earcon_*.ogg
git commit -m "feat(audio): add voice and earcon asset generation pipeline"
```

---

## Chunk 2: Core Model & Playback Engine Changes

### Task 2: Add new CoachingEvent entries

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/model/CoachingEvent.kt`

- [ ] **Step 1: Add the 4 new enum entries**

```kotlin
enum class CoachingEvent {
    SPEED_UP,
    SLOW_DOWN,
    RETURN_TO_ZONE,
    PREDICTIVE_WARNING,
    SEGMENT_CHANGE,
    SIGNAL_LOST,
    SIGNAL_REGAINED,
    HALFWAY,
    KM_SPLIT,
    WORKOUT_COMPLETE,
    IN_ZONE_CONFIRM
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (new enum entries are additive — no callers break because existing `when` branches have `else` clauses)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/model/CoachingEvent.kt
git commit -m "feat(audio): add HALFWAY, KM_SPLIT, WORKOUT_COMPLETE, IN_ZONE_CONFIRM events"
```

### Task 3: Expand AudioSettings with informational cue toggles

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/model/AudioSettings.kt`

- [ ] **Step 1: Add nullable boolean fields**

```kotlin
data class AudioSettings(
    val earconVolume: Int = 80,
    val voiceVerbosity: VoiceVerbosity = VoiceVerbosity.MINIMAL,
    val enableVibration: Boolean = true,
    val enableHalfwayReminder: Boolean? = null,
    val enableKmSplits: Boolean? = null,
    val enableWorkoutComplete: Boolean? = null,
    val enableInZoneConfirm: Boolean? = null
)
```

Note: `Boolean?` with `null` default. Consumers use `!= false` to treat null as true. This handles Gson deserialization of existing user data where these fields are absent.

- [ ] **Step 2: Verify compilation**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/model/AudioSettings.kt
git commit -m "feat(audio): add informational cue toggle fields to AudioSettings"
```

### Task 4: Replace EarconSynthesizer with EarconPlayer (SoundPool)

**Files:**
- Create: `app/src/main/java/com/hrcoach/service/audio/EarconPlayer.kt`
- Modify: `app/src/main/java/com/hrcoach/service/audio/CoachingAudioManager.kt` (swap reference)
- Delete: `app/src/main/java/com/hrcoach/service/audio/EarconSynthesizer.kt`
- Delete: `app/src/main/java/com/hrcoach/service/audio/EarconWaveforms.kt`

- [ ] **Step 1: Create EarconPlayer.kt**

```kotlin
package com.hrcoach.service.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.hrcoach.R
import com.hrcoach.domain.model.CoachingEvent

class EarconPlayer(context: Context) {

    private val soundPool: SoundPool
    private val soundIds = mutableMapOf<CoachingEvent, Int>()
    private var volumeScalar: Float = 0.8f

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attrs)
            .build()

        val mapping = mapOf(
            CoachingEvent.SPEED_UP to R.raw.earcon_speed_up,
            CoachingEvent.SLOW_DOWN to R.raw.earcon_slow_down,
            CoachingEvent.RETURN_TO_ZONE to R.raw.earcon_return_to_zone,
            CoachingEvent.PREDICTIVE_WARNING to R.raw.earcon_predictive_warning,
            CoachingEvent.SEGMENT_CHANGE to R.raw.earcon_segment_change,
            CoachingEvent.SIGNAL_LOST to R.raw.earcon_signal_lost,
            CoachingEvent.SIGNAL_REGAINED to R.raw.earcon_signal_regained,
            CoachingEvent.HALFWAY to R.raw.earcon_halfway,
            CoachingEvent.KM_SPLIT to R.raw.earcon_km_split,
            CoachingEvent.WORKOUT_COMPLETE to R.raw.earcon_workout_complete,
            CoachingEvent.IN_ZONE_CONFIRM to R.raw.earcon_in_zone_confirm,
        )
        mapping.forEach { (event, resId) ->
            soundIds[event] = soundPool.load(context, resId, 1)
        }
    }

    fun setVolume(percent: Int) {
        volumeScalar = (percent.coerceIn(0, 100) / 100f)
    }

    fun play(event: CoachingEvent) {
        val soundId = soundIds[event] ?: return
        soundPool.play(soundId, volumeScalar, volumeScalar, 1, 0, 1f)
    }

    fun destroy() {
        soundPool.release()
    }
}
```

- [ ] **Step 2: Update CoachingAudioManager to use EarconPlayer**

In `CoachingAudioManager.kt`, replace:
- `private val earconSynthesizer = EarconSynthesizer()` → `private val earconPlayer = EarconPlayer(context)`
- `earconSynthesizer.setVolume(settings.earconVolume)` → `earconPlayer.setVolume(settings.earconVolume)`
- `earconSynthesizer.destroy()` → `earconPlayer.destroy()`
- Replace the entire `playEarcon()` method:

```kotlin
private fun playEarcon(event: CoachingEvent) {
    earconPlayer.play(event)
}
```

- [ ] **Step 3: Delete EarconSynthesizer.kt and EarconWaveforms.kt**

```bash
git rm app/src/main/java/com/hrcoach/service/audio/EarconSynthesizer.kt
git rm app/src/main/java/com/hrcoach/service/audio/EarconWaveforms.kt
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/audio/EarconPlayer.kt \
       app/src/main/java/com/hrcoach/service/audio/CoachingAudioManager.kt
git commit -m "feat(audio): replace EarconSynthesizer with SoundPool-based EarconPlayer"
```

### Task 5: Rewrite VoiceCoach to use MediaPlayer with pre-recorded assets

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/audio/VoiceCoach.kt`

- [ ] **Step 1: Rewrite VoiceCoach.kt**

```kotlin
package com.hrcoach.service.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.hrcoach.R
import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.VoiceVerbosity

class VoiceCoach(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    var verbosity: VoiceVerbosity = VoiceVerbosity.MINIMAL

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    fun speak(event: CoachingEvent, guidanceText: String?) {
        if (verbosity == VoiceVerbosity.OFF) return

        val resId = when (verbosity) {
            VoiceVerbosity.OFF -> return
            VoiceVerbosity.MINIMAL -> minimalResFor(event)
            VoiceVerbosity.FULL -> fullResFor(event, guidanceText)
        } ?: return

        // Stop any in-progress playback (QUEUE_FLUSH equivalent)
        mediaPlayer?.run {
            if (isPlaying) stop()
            release()
        }

        mediaPlayer = MediaPlayer.create(context, resId, audioAttributes, 0).also {
            it.setOnCompletionListener { mp -> mp.release() }
            it.start()
        }
    }

    fun destroy() {
        mediaPlayer?.run {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }

    private fun minimalResFor(event: CoachingEvent): Int? {
        return when (event) {
            CoachingEvent.SPEED_UP -> R.raw.voice_speed_up
            CoachingEvent.SLOW_DOWN -> R.raw.voice_slow_down
            CoachingEvent.SEGMENT_CHANGE -> R.raw.voice_segment_change
            CoachingEvent.SIGNAL_LOST -> R.raw.voice_signal_lost
            else -> null
        }
    }

    private fun fullResFor(event: CoachingEvent, guidanceText: String?): Int? {
        return when (event) {
            CoachingEvent.SPEED_UP -> R.raw.voice_speed_up
            CoachingEvent.SLOW_DOWN -> R.raw.voice_slow_down
            CoachingEvent.RETURN_TO_ZONE -> R.raw.voice_return_to_zone
            CoachingEvent.PREDICTIVE_WARNING -> R.raw.voice_predictive_warning
            CoachingEvent.SEGMENT_CHANGE -> R.raw.voice_segment_change
            CoachingEvent.SIGNAL_LOST -> R.raw.voice_signal_lost
            CoachingEvent.SIGNAL_REGAINED -> R.raw.voice_signal_regained
            CoachingEvent.HALFWAY -> R.raw.voice_halfway
            CoachingEvent.WORKOUT_COMPLETE -> R.raw.voice_workout_complete
            CoachingEvent.IN_ZONE_CONFIRM -> R.raw.voice_in_zone_confirm
            CoachingEvent.KM_SPLIT -> kmSplitRes(guidanceText)
        }
    }

    private fun kmSplitRes(guidanceText: String?): Int? {
        val km = guidanceText?.toIntOrNull() ?: return null
        return kmResources[km]
    }

    companion object {
        /** Map of km number (1–50) to R.raw.voice_km_N resource IDs. */
        val kmResources: Map<Int, Int> = mapOf(
            1 to R.raw.voice_km_1, 2 to R.raw.voice_km_2, 3 to R.raw.voice_km_3,
            4 to R.raw.voice_km_4, 5 to R.raw.voice_km_5, 6 to R.raw.voice_km_6,
            7 to R.raw.voice_km_7, 8 to R.raw.voice_km_8, 9 to R.raw.voice_km_9,
            10 to R.raw.voice_km_10, 11 to R.raw.voice_km_11, 12 to R.raw.voice_km_12,
            13 to R.raw.voice_km_13, 14 to R.raw.voice_km_14, 15 to R.raw.voice_km_15,
            16 to R.raw.voice_km_16, 17 to R.raw.voice_km_17, 18 to R.raw.voice_km_18,
            19 to R.raw.voice_km_19, 20 to R.raw.voice_km_20, 21 to R.raw.voice_km_21,
            22 to R.raw.voice_km_22, 23 to R.raw.voice_km_23, 24 to R.raw.voice_km_24,
            25 to R.raw.voice_km_25, 26 to R.raw.voice_km_26, 27 to R.raw.voice_km_27,
            28 to R.raw.voice_km_28, 29 to R.raw.voice_km_29, 30 to R.raw.voice_km_30,
            31 to R.raw.voice_km_31, 32 to R.raw.voice_km_32, 33 to R.raw.voice_km_33,
            34 to R.raw.voice_km_34, 35 to R.raw.voice_km_35, 36 to R.raw.voice_km_36,
            37 to R.raw.voice_km_37, 38 to R.raw.voice_km_38, 39 to R.raw.voice_km_39,
            40 to R.raw.voice_km_40, 41 to R.raw.voice_km_41, 42 to R.raw.voice_km_42,
            43 to R.raw.voice_km_43, 44 to R.raw.voice_km_44, 45 to R.raw.voice_km_45,
            46 to R.raw.voice_km_46, 47 to R.raw.voice_km_47, 48 to R.raw.voice_km_48,
            49 to R.raw.voice_km_49, 50 to R.raw.voice_km_50,
        )
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/audio/VoiceCoach.kt
git commit -m "feat(audio): rewrite VoiceCoach to play pre-recorded neural voice assets"
```

---

## Chunk 3: Informational Cue Settings Filtering & Event Routing

### Task 6: Add settings filtering to CoachingAudioManager

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/audio/CoachingAudioManager.kt`

- [ ] **Step 1: Store AudioSettings reference and filter informational events**

Update `CoachingAudioManager` to hold a reference to `AudioSettings` and check toggles before playing informational cues:

```kotlin
// Add a settings field
private var settings: AudioSettings = settings  // from constructor

// In applySettings():
fun applySettings(settings: AudioSettings) {
    this.settings = settings
    earconPlayer.setVolume(settings.earconVolume)
    voiceCoach.verbosity = settings.voiceVerbosity
    vibrationManager.enabled = settings.enableVibration
}

// In fireEvent(), add a guard at the top:
fun fireEvent(event: CoachingEvent, guidanceText: String? = null) {
    // Filter informational cues by individual toggles
    when (event) {
        CoachingEvent.HALFWAY -> if (settings.enableHalfwayReminder == false) return
        CoachingEvent.KM_SPLIT -> if (settings.enableKmSplits == false) return
        CoachingEvent.WORKOUT_COMPLETE -> if (settings.enableWorkoutComplete == false) return
        CoachingEvent.IN_ZONE_CONFIRM -> if (settings.enableInZoneConfirm == false) return
        else -> { /* coaching alerts always pass through */ }
    }
    // ... existing when(event) logic
}
```

Also add the new informational events to the `else` branch (they behave like SEGMENT_CHANGE — earcon + voice, no escalation):

```kotlin
CoachingEvent.HALFWAY,
CoachingEvent.KM_SPLIT,
CoachingEvent.WORKOUT_COMPLETE,
CoachingEvent.IN_ZONE_CONFIRM -> {
    playEarcon(event)
    voiceCoach.speak(event, guidanceText)
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/audio/CoachingAudioManager.kt
git commit -m "feat(audio): filter informational cues by individual toggles in CoachingAudioManager"
```

### Task 7: Add informational cue trigger logic to CoachingEventRouter

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/workout/CoachingEventRouter.kt`

- [ ] **Step 1: Add state tracking fields**

Add after existing private fields:

```kotlin
private var halfwayFired: Boolean = false
private var completeFired: Boolean = false
private var lastKmAnnounced: Int = 0
private var lastVoiceCueTimeMs: Long = 0L
```

Reset them in `reset()`:
```kotlin
fun reset() {
    wasHrConnected = false
    previousZoneStatus = ZoneStatus.NO_DATA
    lastSegmentIndex = -1
    lastPredictiveWarningTime = 0L
    halfwayFired = false
    completeFired = false
    lastKmAnnounced = 0
    lastVoiceCueTimeMs = 0L
}
```

- [ ] **Step 2: Add target distance/time parameters to `route()`**

Update the `route()` signature to include target info:

```kotlin
fun route(
    workoutConfig: WorkoutConfig,
    connected: Boolean,
    distanceMeters: Float,
    elapsedSeconds: Long,
    zoneStatus: ZoneStatus,
    adaptiveResult: AdaptivePaceController.TickResult?,
    guidance: String,
    nowMs: Long,
    emitEvent: (CoachingEvent, String?) -> Unit
)
```

No signature change needed — `workoutConfig` already carries target distance/time info. Add the logic at the end of `route()`, before updating `wasHrConnected` and `previousZoneStatus`:

```kotlin
// ── Informational cues ──────────────────────────────────────

// KM_SPLIT
val currentKm = (distanceMeters / 1000f).toInt()
if (currentKm > lastKmAnnounced && currentKm in 1..50) {
    lastKmAnnounced = currentKm
    emitEvent(CoachingEvent.KM_SPLIT, currentKm.toString())
    lastVoiceCueTimeMs = nowMs
}

// HALFWAY (only when a target exists)
if (!halfwayFired) {
    val targetDistance = workoutConfig.totalDistanceMeters()
    val targetDuration = workoutConfig.totalDurationSeconds()
    val isHalfwayByDistance = targetDistance != null && targetDistance > 0 &&
        distanceMeters >= targetDistance / 2f
    val isHalfwayByTime = targetDuration != null && targetDuration > 0 &&
        elapsedSeconds >= targetDuration / 2
    if (isHalfwayByDistance || isHalfwayByTime) {
        halfwayFired = true
        emitEvent(CoachingEvent.HALFWAY, null)
        lastVoiceCueTimeMs = nowMs
    }
}

// WORKOUT_COMPLETE
if (!completeFired) {
    val targetDistance = workoutConfig.totalDistanceMeters()
    val targetDuration = workoutConfig.totalDurationSeconds()
    val doneByDistance = targetDistance != null && targetDistance > 0 &&
        distanceMeters >= targetDistance
    val doneByTime = targetDuration != null && targetDuration > 0 &&
        elapsedSeconds >= targetDuration
    if (doneByDistance || doneByTime) {
        completeFired = true
        emitEvent(CoachingEvent.WORKOUT_COMPLETE, null)
        lastVoiceCueTimeMs = nowMs
    }
}

// IN_ZONE_CONFIRM (every 3+ minutes of silence while in zone)
if (zoneStatus == ZoneStatus.IN_ZONE &&
    lastVoiceCueTimeMs > 0L &&
    nowMs - lastVoiceCueTimeMs >= 180_000L
) {
    emitEvent(CoachingEvent.IN_ZONE_CONFIRM, null)
    lastVoiceCueTimeMs = nowMs
}
```

- [ ] **Step 3: Update `lastVoiceCueTimeMs` for existing voice-producing events**

After the existing event emissions (SIGNAL_LOST, SIGNAL_REGAINED, RETURN_TO_ZONE, SEGMENT_CHANGE, PREDICTIVE_WARNING), update `lastVoiceCueTimeMs = nowMs`.

- [ ] **Step 4: Add helper methods to WorkoutConfig if missing**

Check if `WorkoutConfig` already has `totalDistanceMeters()` and `totalDurationSeconds()`. If not, add them:

```kotlin
fun totalDistanceMeters(): Float? {
    if (mode == WorkoutMode.FREE_RUN) return null
    return segments.mapNotNull { it.distanceMeters }.sum().takeIf { it > 0 }
}

fun totalDurationSeconds(): Long? {
    if (mode == WorkoutMode.FREE_RUN) return null
    return segments.mapNotNull { it.durationSeconds?.toLong() }.sum().takeIf { it > 0 }
}
```

- [ ] **Step 5: Verify compilation**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/workout/CoachingEventRouter.kt \
       app/src/main/java/com/hrcoach/domain/model/WorkoutConfig.kt
git commit -m "feat(audio): add informational cue trigger logic to CoachingEventRouter"
```

---

## Chunk 4: Settings UI

### Task 8: Add informational cue toggles to SetupScreen

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/setup/SetupViewModel.kt` (add state fields + setters)
- Modify: `app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt` (add UI section)

- [ ] **Step 1: Add state fields to SetupUiState**

In `SetupUiState`, add after `enableVibration`:

```kotlin
val enableHalfwayReminder: Boolean = true,
val enableKmSplits: Boolean = true,
val enableWorkoutComplete: Boolean = true,
val enableInZoneConfirm: Boolean = true,
```

- [ ] **Step 2: Add setter methods to SetupViewModel**

```kotlin
fun setEnableHalfwayReminder(value: Boolean) {
    _uiState.value = _uiState.value.copy(enableHalfwayReminder = value)
    saveAudioSettings()
}
fun setEnableKmSplits(value: Boolean) {
    _uiState.value = _uiState.value.copy(enableKmSplits = value)
    saveAudioSettings()
}
fun setEnableWorkoutComplete(value: Boolean) {
    _uiState.value = _uiState.value.copy(enableWorkoutComplete = value)
    saveAudioSettings()
}
fun setEnableInZoneConfirm(value: Boolean) {
    _uiState.value = _uiState.value.copy(enableInZoneConfirm = value)
    saveAudioSettings()
}
```

- [ ] **Step 3: Update `saveAudioSettings()` and init loading**

In `saveAudioSettings()`, include the new fields:
```kotlin
AudioSettings(
    earconVolume = _uiState.value.earconVolume,
    voiceVerbosity = _uiState.value.voiceVerbosity,
    enableVibration = _uiState.value.enableVibration,
    enableHalfwayReminder = _uiState.value.enableHalfwayReminder,
    enableKmSplits = _uiState.value.enableKmSplits,
    enableWorkoutComplete = _uiState.value.enableWorkoutComplete,
    enableInZoneConfirm = _uiState.value.enableInZoneConfirm,
)
```

In `init` where audio settings are loaded, add:
```kotlin
enableHalfwayReminder = audioSettings.enableHalfwayReminder != false,
enableKmSplits = audioSettings.enableKmSplits != false,
enableWorkoutComplete = audioSettings.enableWorkoutComplete != false,
enableInZoneConfirm = audioSettings.enableInZoneConfirm != false,
```

- [ ] **Step 4: Add Informational Cues UI section to AlertBehaviorCard**

After the "Preview Sounds" section (inside the `if (state.showAdvancedSettings)` block), add:

```kotlin
HorizontalDivider()

Text(
    "Informational Cues",
    style = MaterialTheme.typography.bodyLarge,
    color = CardeaTheme.colors.textPrimary
)
val cuesEnabled = state.voiceVerbosity != VoiceVerbosity.OFF

Row(
    modifier = Modifier.fillMaxWidth().alpha(if (cuesEnabled) 1f else 0.4f),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    Text("Halfway reminder", style = MaterialTheme.typography.bodyMedium, color = CardeaTheme.colors.textSecondary)
    CardeaSwitch(checked = state.enableHalfwayReminder && cuesEnabled, onCheckedChange = { if (cuesEnabled) onHalfwayChange(it) }, enabled = cuesEnabled)
}
Row(
    modifier = Modifier.fillMaxWidth().alpha(if (cuesEnabled) 1f else 0.4f),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    Text("Kilometer splits", style = MaterialTheme.typography.bodyMedium, color = CardeaTheme.colors.textSecondary)
    CardeaSwitch(checked = state.enableKmSplits && cuesEnabled, onCheckedChange = { if (cuesEnabled) onKmSplitsChange(it) }, enabled = cuesEnabled)
}
Row(
    modifier = Modifier.fillMaxWidth().alpha(if (cuesEnabled) 1f else 0.4f),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    Text("Workout complete", style = MaterialTheme.typography.bodyMedium, color = CardeaTheme.colors.textSecondary)
    CardeaSwitch(checked = state.enableWorkoutComplete && cuesEnabled, onCheckedChange = { if (cuesEnabled) onWorkoutCompleteChange(it) }, enabled = cuesEnabled)
}
Row(
    modifier = Modifier.fillMaxWidth().alpha(if (cuesEnabled) 1f else 0.4f),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    Text("In-zone confirmation", style = MaterialTheme.typography.bodyMedium, color = CardeaTheme.colors.textSecondary)
    CardeaSwitch(checked = state.enableInZoneConfirm && cuesEnabled, onCheckedChange = { if (cuesEnabled) onInZoneConfirmChange(it) }, enabled = cuesEnabled)
}
```

Add the corresponding callback parameters to `AlertBehaviorCard`:
```kotlin
onHalfwayChange: (Boolean) -> Unit,
onKmSplitsChange: (Boolean) -> Unit,
onWorkoutCompleteChange: (Boolean) -> Unit,
onInZoneConfirmChange: (Boolean) -> Unit,
```

And wire them from the parent composable calling `AlertBehaviorCard`.

- [ ] **Step 5: Verify compilation**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/setup/SetupViewModel.kt \
       app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt
git commit -m "feat(audio): add informational cue toggles to Setup screen"
```

### Task 9: Add informational cue toggles to AccountScreen

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountViewModel.kt` (add state fields + setters)
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt` (add UI section)

- [ ] **Step 1: Add state fields to AccountUiState**

Add after `enableVibration`:
```kotlin
val enableHalfwayReminder: Boolean = true,
val enableKmSplits: Boolean = true,
val enableWorkoutComplete: Boolean = true,
val enableInZoneConfirm: Boolean = true,
```

- [ ] **Step 2: Add setter methods and update save/load in AccountViewModel**

Same pattern as SetupViewModel — add setters that call `saveAudioSettings()`, update `saveAudioSettings()` to include new fields, update init loading with `!= false` null coalescing.

- [ ] **Step 3: Add UI section to AccountScreen**

After the existing "Vibration Alerts" `SettingToggleRow` and its divider, add a new section:

```kotlin
HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = CardeaTheme.colors.glassBorder)

// Informational Cues section header
SettingSection(icon = Icons.Default.Info, title = "Informational Cues") {
    val cuesEnabled = state.voiceVerbosity != VoiceVerbosity.OFF
    Column(modifier = Modifier.alpha(if (cuesEnabled) 1f else 0.4f)) {
        SettingToggleRow(
            icon = null,
            title = "Halfway reminder",
            checked = state.enableHalfwayReminder && cuesEnabled,
            onCheckedChange = { viewModel.setEnableHalfwayReminder(it) },
            enabled = cuesEnabled
        )
        SettingToggleRow(
            icon = null,
            title = "Kilometer splits",
            checked = state.enableKmSplits && cuesEnabled,
            onCheckedChange = { viewModel.setEnableKmSplits(it) },
            enabled = cuesEnabled
        )
        SettingToggleRow(
            icon = null,
            title = "Workout complete",
            checked = state.enableWorkoutComplete && cuesEnabled,
            onCheckedChange = { viewModel.setEnableWorkoutComplete(it) },
            enabled = cuesEnabled
        )
        SettingToggleRow(
            icon = null,
            title = "In-zone confirmation",
            checked = state.enableInZoneConfirm && cuesEnabled,
            onCheckedChange = { viewModel.setEnableInZoneConfirm(it) },
            enabled = cuesEnabled
        )
    }
}
```

Note: Check whether `SettingToggleRow` accepts `enabled` parameter. If not, use `CardeaSwitch` directly with Row like in SetupScreen.

- [ ] **Step 4: Verify compilation**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/account/AccountViewModel.kt \
       app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt
git commit -m "feat(audio): add informational cue toggles to Account screen"
```

---

## Chunk 5: Service Wiring & Tests

### Task 10: Wire AudioSettings through WorkoutForegroundService

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`

- [ ] **Step 1: Pass full AudioSettings to CoachingAudioManager**

The service already does:
```kotlin
coachingAudioManager = CoachingAudioManager(this, audioSettingsRepository.getAudioSettings())
```

This already passes the full `AudioSettings` including the new fields. No change needed here — the filtering happens inside `CoachingAudioManager.fireEvent()`.

Verify that `CoachingAudioManager` constructor stores the settings (done in Task 6).

- [ ] **Step 2: Verify compilation**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit (if any changes were needed)**

### Task 11: Update existing tests

**Files:**
- Modify: any test files that reference `EarconSynthesizer`, `EarconWaveforms`, or `CoachingEvent` exhaustive `when` blocks

- [ ] **Step 1: Search for tests that need updating**

```bash
grep -r "EarconSynthesizer\|EarconWaveforms\|CoachingEvent" app/src/test/ --include="*.kt" -l
```

- [ ] **Step 2: Update any broken tests**

- Remove references to deleted `EarconSynthesizer`/`EarconWaveforms`
- Add new `CoachingEvent` entries to any exhaustive `when` blocks in tests
- Update any test that constructs `AudioSettings` — the new nullable fields default to `null` which is fine

- [ ] **Step 3: Write a new test for CoachingEventRouter informational cues**

Create `app/src/test/java/com/hrcoach/service/workout/CoachingEventRouterInfoCuesTest.kt`:

Test cases:
- KM_SPLIT fires at each km boundary (1000m, 2000m, etc.)
- KM_SPLIT does not fire again for the same km
- HALFWAY fires once at 50% of target distance
- HALFWAY does not fire in FREE_RUN mode
- WORKOUT_COMPLETE fires once at target distance
- IN_ZONE_CONFIRM fires after 3 minutes of silence in zone
- IN_ZONE_CONFIRM does not fire if a voice cue was recent
- `reset()` clears all state

- [ ] **Step 4: Run all tests**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 5: Commit**

```bash
git add app/src/test/
git commit -m "test(audio): add informational cue tests and fix broken references"
```

### Task 12: Update buildAlertsSummary in SetupScreen

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt`

- [ ] **Step 1: Update the `buildAlertsSummary` function**

The function at the top of SetupScreen builds a text summary of audio settings for the collapsed view. Add informational cue info:

```kotlin
// After existing parts
val infoCues = mutableListOf<String>()
if (state.enableHalfwayReminder) infoCues.add("Halfway")
if (state.enableKmSplits) infoCues.add("Splits")
if (state.enableInZoneConfirm) infoCues.add("Zone confirm")
if (infoCues.isNotEmpty()) parts.add("Info: ${infoCues.joinToString(", ")}")
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt
git commit -m "feat(audio): include informational cue summary in alert behavior card"
```

### Task 13: Final verification

- [ ] **Step 1: Full build**

Run: `./gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Full test suite**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: All tests pass

- [ ] **Step 3: Final commit if any fixups needed**
