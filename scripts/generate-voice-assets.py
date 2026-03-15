#!/usr/bin/env python3
"""Generate voice and earcon audio assets for Cardea coaching cues.

Requires: pip install edge-tts numpy scipy
Output:   app/src/main/res/raw/voice_*.mp3 and earcon_*.wav

Voice lines use Microsoft Edge Neural TTS (en-US-ChristopherNeural).
Earcons use harmonic synthesis with ADSR envelopes and low-pass filtering.
"""
import asyncio
import os
import struct
import sys
from pathlib import Path

import numpy as np
from scipy.signal import butter, sosfilt

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


# ── Earcon synthesis ────────────────────────────────────────────────

def adsr_envelope(n_samples: int, attack_ms: int = 15, decay_ms: int = 30,
                  sustain_level: float = 0.6, release_ms: int = 50) -> np.ndarray:
    sr = SAMPLE_RATE
    attack = int(sr * attack_ms / 1000)
    decay = int(sr * decay_ms / 1000)
    release = int(sr * release_ms / 1000)
    sustain = max(0, n_samples - attack - decay - release)
    env = np.concatenate([
        np.linspace(0, 1, max(attack, 1)),
        np.linspace(1, sustain_level, max(decay, 1)),
        np.full(sustain, sustain_level),
        np.linspace(sustain_level, 0, max(release, 1)),
    ])
    if len(env) > n_samples:
        env = env[:n_samples]
    elif len(env) < n_samples:
        env = np.pad(env, (0, n_samples - len(env)))
    return env


def generate_tone(freq: float, duration_ms: int,
                  harmonics: list[tuple[float, float]] | None = None) -> np.ndarray:
    n = int(SAMPLE_RATE * duration_ms / 1000)
    t = np.arange(n) / SAMPLE_RATE
    signal = np.sin(2 * np.pi * freq * t)
    if harmonics:
        for mult, amp in harmonics:
            signal += amp * np.sin(2 * np.pi * freq * mult * t)
    peak = np.max(np.abs(signal))
    if peak > 0:
        signal = signal / peak
    signal *= adsr_envelope(n)
    return signal


def silence(duration_ms: int) -> np.ndarray:
    return np.zeros(int(SAMPLE_RATE * duration_ms / 1000))


def concat(*parts: np.ndarray) -> np.ndarray:
    return np.concatenate(parts)


def low_pass(signal: np.ndarray, cutoff_hz: float = 5000) -> np.ndarray:
    sos = butter(2, cutoff_hz, btype='low', fs=SAMPLE_RATE, output='sos')
    return sosfilt(sos, signal)


# Harmonic profiles
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
    return concat(
        generate_tone(523.25, 100, H_BRIGHT), silence(50),
        generate_tone(659.25, 100, H_BRIGHT), silence(50),
        generate_tone(783.99, 120, H_BRIGHT),
    )

@earcon("earcon_slow_down")
def _():
    return concat(
        generate_tone(392.00, 140, H_WARM), silence(40),
        generate_tone(329.63, 140, H_WARM), silence(40),
        generate_tone(261.63, 160, H_WARM),
    )

@earcon("earcon_return_to_zone")
def _():
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
    return generate_tone(659.25, 200, H_WARM)


# ── WAV writer ──────────────────────────────────────────────────────

def write_wav(path: str, signal: np.ndarray):
    signal = np.clip(signal, -1, 1)
    pcm = (signal * 32767).astype(np.int16)
    n_samples = len(pcm)
    with open(path, 'wb') as f:
        data_size = n_samples * 2
        f.write(b'RIFF')
        f.write(struct.pack('<I', 36 + data_size))
        f.write(b'WAVE')
        f.write(b'fmt ')
        f.write(struct.pack('<IHHIIHH', 16, 1, 1, SAMPLE_RATE, SAMPLE_RATE * 2, 2, 16))
        f.write(b'data')
        f.write(struct.pack('<I', data_size))
        f.write(pcm.tobytes())


# ── Main ────────────────────────────────────────────────────────────

async def generate_voice_line(name: str, text: str, output_dir: Path):
    import edge_tts
    mp3_path = str(output_dir / f"{name}.mp3")
    communicate = edge_tts.Communicate(text, VOICE, rate=RATE)
    await communicate.save(mp3_path)
    print(f"  voice: {name}.mp3")


async def main():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    print("Generating voice lines...")
    for name, text in VOICE_LINES.items():
        await generate_voice_line(name, text, OUTPUT_DIR)

    print("Generating earcons...")
    for name, gen_fn in EARCONS.items():
        signal = gen_fn()
        signal = low_pass(signal, cutoff_hz=5000)
        signal = np.clip(signal * 0.8, -1, 1)
        wav_path = str(OUTPUT_DIR / f"{name}.wav")
        write_wav(wav_path, signal)
        print(f"  earcon: {name}.wav")

    total = len(VOICE_LINES) + len(EARCONS)
    print(f"\nDone! {len(VOICE_LINES)} voice + {len(EARCONS)} earcon files in {OUTPUT_DIR}")


if __name__ == "__main__":
    asyncio.run(main())
