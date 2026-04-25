"""Generate 3 strides earcon WAV files (44.1kHz, 16-bit, mono).

- earcon_strides_go.wav: rising glide 600 -> 900 Hz over 280ms.
  Decisive "start your pickup" feel.
- earcon_strides_ease.wav: falling glide 800 -> 500 Hz over 340ms.
  Soft "ease into jog" feel.
- earcon_strides_set_complete.wav: three-note ascending resolve C5-E5-G5,
  220ms each, short envelopes. Achievement chime.
"""
import wave, math, struct, os

SAMPLE_RATE = 44100
SAMPLE_WIDTH = 2  # 16-bit
AMPLITUDE = 0.6  # peak amplitude (0..1) — leave headroom; matches "soft chime" goal
RAW_DIR = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")


def envelope(i, n, attack_ms=10, release_ms=40):
    """Linear attack/release envelope to avoid clicks. i, n in samples."""
    attack = int(SAMPLE_RATE * attack_ms / 1000)
    release = int(SAMPLE_RATE * release_ms / 1000)
    if i < attack:
        return i / attack
    if i > n - release:
        return max(0.0, (n - i) / release)
    return 1.0


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


def gen_glide(start_hz, end_hz, duration_ms):
    """Linear-frequency glide. Returns float samples."""
    n = int(SAMPLE_RATE * duration_ms / 1000)
    phase = 0.0
    out = []
    for i in range(n):
        t = i / n
        f = start_hz + (end_hz - start_hz) * t
        phase += 2 * math.pi * f / SAMPLE_RATE
        s = math.sin(phase) * envelope(i, n) * AMPLITUDE
        out.append(s)
    return out


def gen_three_notes(freqs, note_ms):
    """Sequential notes with short attack/release each. Returns float samples."""
    out = []
    for f in freqs:
        n = int(SAMPLE_RATE * note_ms / 1000)
        phase = 0.0
        for i in range(n):
            phase += 2 * math.pi * f / SAMPLE_RATE
            s = math.sin(phase) * envelope(i, n, attack_ms=8, release_ms=60) * AMPLITUDE
            out.append(s)
    return out


def main():
    os.makedirs(RAW_DIR, exist_ok=True)
    write_wav(os.path.join(RAW_DIR, "earcon_strides_go.wav"),
              gen_glide(600, 900, 280))
    write_wav(os.path.join(RAW_DIR, "earcon_strides_ease.wav"),
              gen_glide(800, 500, 340))
    # C5=523.25, E5=659.25, G5=783.99
    write_wav(os.path.join(RAW_DIR, "earcon_strides_set_complete.wav"),
              gen_three_notes([523.25, 659.25, 783.99], 220))


if __name__ == "__main__":
    main()
