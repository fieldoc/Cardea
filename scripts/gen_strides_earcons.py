"""Generate 3 strides earcon WAV files (44.1kHz, 16-bit, mono).

v2 (semantic differentiation pass):
  - Replaced pure-sine glides with a soft-mallet timbre (fundamental + one
    inharmonic partial at 2.756x, exponential decay). v1 sines were
    deliberately a different palette from the bell-family coaching cues
    but read as "thin / synth-default" rather than "different family".
  - SET_COMPLETE moved off the C major triad (which is the start of the
    WORKOUT_COMPLETE arpeggio) onto an ascending pentatonic D5-E5-A5 so
    the strides set-complete chime no longer sounds like a "preview" of
    workout-complete.

Cues:
  - earcon_strides_go.wav: rising mallet glide 600 -> 900 Hz over 280ms.
    Decisive "start your pickup" feel.
  - earcon_strides_ease.wav: falling mallet glide 800 -> 500 Hz over 340ms.
    Soft "ease into jog" feel. Mirror-pair with GO.
  - earcon_strides_set_complete.wav: ascending pentatonic D5-E5-A5,
    220ms each, mallet timbre. Achievement chime that doesn't collide
    with the bell-family WORKOUT_COMPLETE motif.
"""
import wave, math, struct, os

SAMPLE_RATE = 44100
SAMPLE_WIDTH = 2  # 16-bit
AMPLITUDE = 0.6  # peak amplitude (0..1) — leave headroom; matches "soft chime" goal
RAW_DIR = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")

# Inharmonic partial ratio borrowed from the bell-model in generate_earcons.py.
# A second sine an octave + minor 6th above the fundamental, decaying faster,
# adds enough harmonic body to lift the timbre out of "pure sine" territory
# while staying soft and percussive (not bell-bright).
MALLET_PARTIAL_RATIO = 2.756
MALLET_PARTIAL_AMP = 0.32


def envelope(i, n, attack_ms=10, release_ms=40):
    """Linear attack/release envelope to avoid clicks. i, n in samples."""
    attack = int(SAMPLE_RATE * attack_ms / 1000)
    release = int(SAMPLE_RATE * release_ms / 1000)
    if i < attack:
        return i / attack
    if i > n - release:
        return max(0.0, (n - i) / release)
    return 1.0


def mallet_envelope(i, n, attack_ms=4, decay_tau_ms=120):
    """Fast attack + exponential decay. Percussive 'mallet' shape."""
    attack = int(SAMPLE_RATE * attack_ms / 1000)
    if i < attack:
        return (i / attack) ** 0.6
    elapsed_s = (i - attack) / SAMPLE_RATE
    tau_s = decay_tau_ms / 1000.0
    env = math.exp(-elapsed_s / tau_s)
    # Smooth fade to zero at the very end so the file ends on silence
    end_fade = max(0.0, 1.0 - ((i - attack) / max(1, n - attack)) ** 3)
    return env * end_fade


def write_wav(path, samples):
    """samples: iterable of floats in [-1, 1]."""
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(SAMPLE_WIDTH)
        w.setframerate(SAMPLE_RATE)
        for s in samples:
            v = max(-1.0, min(1.0, s))
            w.writeframes(struct.pack("<h", int(v * 32767)))
    print(f"wrote {path} ({os.path.getsize(path)} bytes)")


def gen_mallet_glide(start_hz, end_hz, duration_ms):
    """Mallet-timbre glide: fundamental + inharmonic partial, exponential decay.
    Both partials glide together (same start/end ratio)."""
    n = int(SAMPLE_RATE * duration_ms / 1000)
    phase_fund = 0.0
    phase_part = 0.0
    out = []
    for i in range(n):
        t = i / n
        f_fund = start_hz + (end_hz - start_hz) * t
        f_part = f_fund * MALLET_PARTIAL_RATIO
        phase_fund += 2 * math.pi * f_fund / SAMPLE_RATE
        phase_part += 2 * math.pi * f_part / SAMPLE_RATE
        env = mallet_envelope(i, n)
        s = (math.sin(phase_fund) + MALLET_PARTIAL_AMP * math.sin(phase_part))
        s *= env * AMPLITUDE
        out.append(s)
    return out


def gen_mallet_note(freq, duration_ms, decay_tau_ms=140):
    """Single mallet note at fixed pitch. Used for SET_COMPLETE arpeggio."""
    n = int(SAMPLE_RATE * duration_ms / 1000)
    phase_fund = 0.0
    phase_part = 0.0
    out = []
    f_part = freq * MALLET_PARTIAL_RATIO
    for i in range(n):
        phase_fund += 2 * math.pi * freq / SAMPLE_RATE
        phase_part += 2 * math.pi * f_part / SAMPLE_RATE
        env = mallet_envelope(i, n, attack_ms=3, decay_tau_ms=decay_tau_ms)
        s = (math.sin(phase_fund) + MALLET_PARTIAL_AMP * math.sin(phase_part))
        s *= env * AMPLITUDE
        out.append(s)
    return out


def gen_arpeggio(freqs, note_ms):
    """Sequential mallet notes. Returns float samples."""
    out = []
    for f in freqs:
        out.extend(gen_mallet_note(f, note_ms))
    return out


def main():
    os.makedirs(RAW_DIR, exist_ok=True)
    write_wav(os.path.join(RAW_DIR, "earcon_strides_go.wav"),
              gen_mallet_glide(600, 900, 280))
    write_wav(os.path.join(RAW_DIR, "earcon_strides_ease.wav"),
              gen_mallet_glide(800, 500, 340))
    # Pentatonic D5 - E5 - A5 (ascending major 2nd then perfect 4th).
    # Avoids the C5-E5-G5 motif that opens WORKOUT_COMPLETE.
    # D5=587.33, E5=659.25, A5=880.00
    write_wav(os.path.join(RAW_DIR, "earcon_strides_set_complete.wav"),
              gen_arpeggio([587.33, 659.25, 880.00], 220))


if __name__ == "__main__":
    main()
