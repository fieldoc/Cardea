"""
Generate premium Apple-quality earcon WAV files for Cardea coaching alerts.

Design principles (modeled after iOS/watchOS coaching & navigation sounds):
  - Bell/marimba physical modeling: inharmonic partials (not just integer harmonics)
  - Exponential amplitude envelopes (not linear ADSR)
  - Filtered noise layer for breathy "air" texture
  - Subtle pitch bend on attack (notes "bloom" into pitch)
  - Multi-stage reverb: early reflections + diffuse tail
  - Gentle low-pass rolloff on decay (brighter on attack, warmer as it fades)
  - Micro-detuned unison layers for stereo-like width in mono

Output: 44100Hz mono 16-bit WAV.
"""

import wave, struct, math, os

RATE = 44100
OUT_DIR = "app/src/main/res/raw"


def ns(dur):
    """Number of samples for duration in seconds."""
    return int(RATE * dur)


def sine(freq, t, phase=0.0):
    return math.sin(2.0 * math.pi * freq * t + phase)


# ---------------------------------------------------------------------------
# Envelopes — exponential, not linear
# ---------------------------------------------------------------------------

def exp_decay(t, attack_s, decay_s, total_dur):
    """Fast exponential attack, smooth exponential decay. Sounds natural."""
    if t < attack_s:
        # Quadratic attack (slightly softer than linear)
        return (t / attack_s) ** 0.7
    else:
        elapsed = t - attack_s
        remaining_dur = total_dur - attack_s
        if remaining_dur <= 0:
            return 0.0
        # Exponential decay with adjustable rate
        tau = decay_s  # time constant
        env = math.exp(-elapsed / tau)
        # Fade to zero at end
        end_fade = max(0.0, 1.0 - (elapsed / remaining_dur) ** 2)
        return env * end_fade


def bell_env(t, total_dur, brightness_decay=0.3):
    """Bell-like envelope: instant attack, long exponential ring."""
    if t < 0.002:
        return (t / 0.002) ** 0.5
    return math.exp(-t / brightness_decay) * max(0, 1.0 - (t / total_dur) ** 3)


# ---------------------------------------------------------------------------
# Bell / marimba partial model
# ---------------------------------------------------------------------------

def bell_partial(freq, amplitude, t, decay_rate, pitch_bend=0.0, bend_time=0.02):
    """
    Single bell partial with:
      - Exponential amplitude decay
      - Optional pitch bend (cents up/down that resolves to target freq)
      - Per-partial decay rate (higher partials decay faster = natural)
    """
    # Pitch bend: start slightly sharp/flat, resolve exponentially
    if pitch_bend != 0 and t < bend_time * 4:
        bend_factor = pitch_bend * math.exp(-t / bend_time)
        actual_freq = freq * (2 ** (bend_factor / 1200))  # cents to ratio
    else:
        actual_freq = freq

    env = math.exp(-t / decay_rate)
    return amplitude * env * sine(actual_freq, t)


def bell_note(freq, t, dur, brightness=1.0, pitch_bend_cents=15.0):
    """
    Full bell/marimba note with inharmonic partials.
    Inharmonic ratios based on real bell acoustics.
    """
    if t > dur:
        return 0.0

    # Bell partials: frequency ratios are NOT integer (this is the key to
    # sounding like a real instrument vs cheap synth)
    # Based on empirical bell/marimba partial ratios
    partials = [
        # (freq_ratio, amplitude, decay_time_s)
        (1.000, 0.50, 0.35 * brightness),   # fundamental
        (2.000, 0.25, 0.25 * brightness),   # octave
        (2.997, 0.12, 0.18 * brightness),   # ~perfect 12th (slightly flat)
        (4.071, 0.08, 0.12 * brightness),   # inharmonic — bell character
        (5.194, 0.05, 0.08 * brightness),   # inharmonic
        (6.278, 0.03, 0.05 * brightness),   # high shimmer
        (0.500, 0.08, 0.40 * brightness),   # sub-octave warmth
    ]

    val = 0.0
    for ratio, amp, decay in partials:
        # Higher partials get more pitch bend (natural instrument behavior)
        bend = pitch_bend_cents * ratio * 0.3
        val += bell_partial(freq * ratio, amp, t, decay, bend, 0.015)

    # Master envelope
    master = bell_env(t, dur, 0.25 * brightness)
    return val * master


# ---------------------------------------------------------------------------
# Noise texture — filtered for "air" quality
# ---------------------------------------------------------------------------

# Deterministic noise via simple LCG (no random module needed for reproducibility)
_noise_state = 12345

def _noise():
    global _noise_state
    _noise_state = (_noise_state * 1103515245 + 12345) & 0x7FFFFFFF
    return (_noise_state / 0x7FFFFFFF) * 2.0 - 1.0


def filtered_noise(t, dur, center_freq=4000, bandwidth=2000, level=0.02):
    """
    Band-pass filtered noise for breathy texture.
    Simple resonant filter approximation.
    """
    env = math.exp(-t / (dur * 0.4)) * max(0, 1.0 - (t / dur) ** 2)
    raw = _noise()
    # Simple bandpass via modulated noise
    bp = raw * sine(center_freq, t) * 2.0
    return bp * env * level


# ---------------------------------------------------------------------------
# Reverb — early reflections + diffuse tail
# ---------------------------------------------------------------------------

def premium_reverb(samples, dry_wet=0.25, room_size=0.6):
    """
    Two-stage reverb:
    1. Early reflections (discrete delays, preserves transient clarity)
    2. Diffuse tail (dense delays with feedback, creates space)
    """
    n = len(samples)

    # Early reflections — short, distinct
    early_taps = [
        (int(0.011 * RATE), 0.18),
        (int(0.019 * RATE), 0.14),
        (int(0.027 * RATE), 0.10),
        (int(0.037 * RATE), 0.07),
    ]

    # Diffuse tail — longer, overlapping
    diffuse_taps = [
        (int(0.053 * RATE * room_size), 0.12),
        (int(0.071 * RATE * room_size), 0.09),
        (int(0.097 * RATE * room_size), 0.07),
        (int(0.127 * RATE * room_size), 0.05),
        (int(0.151 * RATE * room_size), 0.03),
        (int(0.179 * RATE * room_size), 0.02),
    ]

    max_delay = max(d for d, _ in diffuse_taps)
    tail_extra = int(0.15 * RATE)
    out = [0.0] * (n + max_delay + tail_extra)

    # Dry signal
    for i in range(n):
        out[i] += samples[i]

    # Early reflections
    for delay, gain in early_taps:
        for i in range(n):
            out[i + delay] += samples[i] * gain * dry_wet * 2

    # Diffuse tail (with simple feedback approximation)
    for delay, gain in diffuse_taps:
        for i in range(n):
            out[i + delay] += samples[i] * gain * dry_wet * 3

    # Smooth fade on tail
    fade_start = n
    fade_len = len(out) - n
    for i in range(fade_start, len(out)):
        progress = (i - fade_start) / fade_len
        out[i] *= (1.0 - progress) ** 2.5  # smooth power curve

    return out


# ---------------------------------------------------------------------------
# Dynamics — soft limiter for consistent loudness
# ---------------------------------------------------------------------------

def soft_limit(samples, threshold=0.7):
    """Soft-knee limiter (like Apple's mastering chain)."""
    out = []
    for s in samples:
        a = abs(s)
        if a <= threshold:
            out.append(s)
        else:
            sign = 1 if s >= 0 else -1
            # Soft knee: tanh compression above threshold
            excess = (a - threshold) / (1.0 - threshold + 0.001)
            compressed = threshold + (1.0 - threshold) * math.tanh(excess)
            out.append(sign * compressed)
    return out


# ---------------------------------------------------------------------------
# Output
# ---------------------------------------------------------------------------

def write_wav(filename, samples, target_loudness=0.82):
    """Write normalized, limited mono 16-bit WAV."""
    # Soft limit
    samples = soft_limit(samples)

    # Normalize to target loudness
    peak = max(abs(s) for s in samples) or 1.0
    scale = target_loudness / peak

    path = os.path.join(OUT_DIR, filename)
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        for s in samples:
            val = int(s * scale * 32767)
            val = max(-32767, min(32767, val))
            w.writeframes(struct.pack('<h', val))

    size = os.path.getsize(path)
    dur = len(samples) / RATE
    print(f"  {filename}: {dur:.3f}s, {size:,} bytes")


# ===================================================================
# EARCON DEFINITIONS
# ===================================================================

def gen_speed_up():
    """
    SPEED UP — ascending bell dyad with bloom.
    Two marimba-like notes: A4 → E5 (perfect fifth up = energetic, clear).
    Apple Maps "turn soon" energy.
    """
    dur = 0.60
    n = ns(dur)
    samples = []

    note1 = 440.00   # A4
    note2 = 659.25   # E5

    for i in range(n):
        t = i / RATE
        val = 0.0
        global _noise_state
        _noise_state = 12345 + i  # deterministic per-sample

        # Note 1: A4 — warm, grounded
        val += bell_note(note1, t, 0.35, brightness=0.9, pitch_bend_cents=12)

        # Note 2: E5 — brighter, enters after note 1's attack
        if t > 0.12:
            t2 = t - 0.12
            val += bell_note(note2, t2, 0.40, brightness=1.1, pitch_bend_cents=18) * 1.1

        # Air texture
        val += filtered_noise(t, dur, center_freq=5000, bandwidth=3000, level=0.012)

        samples.append(val)

    samples = premium_reverb(samples, dry_wet=0.22, room_size=0.5)
    write_wav("earcon_speed_up.wav", samples)


def gen_slow_down():
    """
    SLOW DOWN — descending bell dyad, warmer.
    E5 → A4 (same interval reversed = settling, calming).
    Slightly longer decay, less brightness.
    """
    dur = 0.65
    n = ns(dur)
    samples = []

    note1 = 659.25   # E5
    note2 = 440.00   # A4

    for i in range(n):
        t = i / RATE
        val = 0.0
        global _noise_state
        _noise_state = 54321 + i

        # Note 1: E5 — starts bright, decays
        val += bell_note(note1, t, 0.30, brightness=0.85, pitch_bend_cents=10)

        # Note 2: A4 — warmer, enters with overlap
        if t > 0.14:
            t2 = t - 0.14
            val += bell_note(note2, t2, 0.42, brightness=0.75, pitch_bend_cents=8)

        # Softer air
        val += filtered_noise(t, dur, center_freq=3500, bandwidth=2000, level=0.008)

        samples.append(val)

    samples = premium_reverb(samples, dry_wet=0.25, room_size=0.6)
    write_wav("earcon_slow_down.wav", samples)


def gen_in_zone_confirm():
    """
    IN ZONE (v3) — serene singing-bowl chord, lifted an octave for outdoor cut-through.
    G5 + D6 (perfect fifth) with slow bloom, long decay, lots of reverb.
    G4 sub for body. Same meditation-bell character; the higher fundamentals
    survive wind/traffic masking that buried the v2 G4+D5 voicing.
    """
    dur = 0.70
    n = ns(dur)
    samples = []

    root = 783.99   # G5 — lifted from G4 so fundamental clears rumble band
    fifth = 1174.66 # D6 — perfect fifth above, consonant & resolved

    for i in range(n):
        t = i / RATE
        val = 0.0
        global _noise_state
        _noise_state = 67890 + i

        # Root — singing bowl: slow attack, very long ring, slightly raised
        # brightness (higher register has less natural body to lean on)
        val += bell_note(root, t, dur, brightness=0.65, pitch_bend_cents=6) * 0.85

        # Perfect fifth — enters gently, even softer
        if t > 0.04:
            t2 = t - 0.04
            val += bell_note(fifth, t2, dur - 0.04, brightness=0.55,
                             pitch_bend_cents=5) * 0.40

        # Sub-octave hum (G4) — was G3, raised an octave to stay audible
        # outdoors while still anchoring the chord.
        sub_env = bell_env(t, dur, brightness_decay=0.50)
        val += sub_env * 0.10 * sine(392.00, t)  # G4

        # Very gentle low-freq air (breathy, not hissy)
        val += filtered_noise(t, dur, center_freq=2500, bandwidth=1500, level=0.005)

        samples.append(val)

    # Extra reverb — spacious, singing-bowl-in-a-temple feel
    samples = premium_reverb(samples, dry_wet=0.35, room_size=0.85)
    write_wav("earcon_in_zone_confirm.wav", samples)


def gen_return_to_zone():
    """
    RETURN TO ZONE (v3) — concise rising resolution.
    D5 → G5: rising perfect fourth, two warm bells.
    Trimmed from a 1.04s three-note arpeggio (D4→G4→D5) because this fires
    every time you re-enter zone after a drift — repeats fast in a noisy zone.
    Lifted from D4/G4/D5 (147–587 Hz fundamentals) to D5/G5 (587–784 Hz) so
    it survives outdoor masking. Sub raised G3→G4. Still warm (brightness 0.65,
    not the celebratory 1.0+ of zone-correction cues) so it reads as "settled
    back in" rather than "achievement unlocked".
    """
    dur = 0.55
    n = ns(dur)
    samples = []

    notes = [
        (587.33, 0.000, 0.42),  # D5 — entry note
        (783.99, 0.10,  0.45),  # G5 — resolves the fourth
    ]

    for i in range(n):
        t = i / RATE
        val = 0.0
        global _noise_state
        _noise_state = 11111 + i

        for freq, onset, note_dur in notes:
            if t >= onset:
                t_local = t - onset
                # Warm but lifted brightness — audible without celebrating
                val += bell_note(freq, t_local, note_dur, brightness=0.65,
                                 pitch_bend_cents=6) * 0.70

        # Sub hum (G4) appears after the arpeggio lands — grounding warmth.
        # Was D3 (147 Hz); raised two octaves to G4 to clear rumble masking.
        if t > 0.18:
            sub_t = t - 0.18
            sub_env = bell_env(sub_t, dur - 0.18, brightness_decay=0.4)
            val += sub_env * 0.08 * sine(392.00, t)  # G4

        # Soft mid air
        val += filtered_noise(t, dur, center_freq=3000, bandwidth=1500, level=0.005)

        samples.append(val)

    # Moderate reverb — arrival, but not as spacious as workout_complete
    samples = premium_reverb(samples, dry_wet=0.25, room_size=0.55)
    write_wav("earcon_return_to_zone.wav", samples)


def gen_predictive_warning():
    """
    PREDICTIVE WARNING (v3) — dissonance that resolves to consonance.
    Phase 1 (0–350ms): B4 + C5 minor-2nd dissonance, sharper attack so the
    beating is audible outdoors (v2 was so subtle it just read as "a bell played").
    Phase 2 (350–600ms): B4 fades, C5 sustains alone with a brighter ring —
    encodes "and you have time to fix it" by pulling toward consonance.
    "Heads up" not "alarm".
    """
    dur = 0.60
    n = ns(dur)
    samples = []

    freq1 = 493.88  # B4
    freq2 = 523.25  # C5
    phase1_dur = 0.35  # both bells active
    phase2_onset = 0.30  # C5 sustain layer crossfades in

    for i in range(n):
        t = i / RATE
        val = 0.0
        global _noise_state
        _noise_state = 22222 + i

        # Phase 1: B4 — dissonant partner, fades by 350ms
        if t < phase1_dur:
            # Linear fade-out of B4 in last 80ms of phase 1 to avoid click
            b4_env = 1.0 if t < phase1_dur - 0.08 else max(0.0, (phase1_dur - t) / 0.08)
            val += bell_note(freq1, t, phase1_dur, brightness=0.95,
                             pitch_bend_cents=6) * 0.75 * b4_env

        # Phase 1: C5 — dissonant partner, brighter than v2 so beating reads
        val += bell_note(freq2, t, phase1_dur, brightness=1.0,
                         pitch_bend_cents=6) * 0.75

        # Phase 2: C5 sustain ring — comes in as B4 fades, resolves the chord
        if t > phase2_onset:
            t_sus = t - phase2_onset
            sus_dur = dur - phase2_onset
            val += bell_note(freq2, t_sus, sus_dur, brightness=0.85,
                             pitch_bend_cents=4) * 0.45

        # Subtle air
        val += filtered_noise(t, dur, center_freq=3500, bandwidth=2000, level=0.006)

        samples.append(val)

    # Drier than v2 (was 0.25) so the beating between B4/C5 reads clearly
    samples = premium_reverb(samples, dry_wet=0.20, room_size=0.50)
    write_wav("earcon_predictive_warning.wav", samples)


def gen_segment_change():
    """
    SEGMENT CHANGE (v3) — three quick same-pitch taps. "Page-turn" gesture.
    v2 was G5→C6 ascending P4, identical to HALFWAY — destroyed the milestone
    family vs interval family distinction. Three same-pitch E5 taps explicitly
    avoid the arrival/celebration shape: no rising contour, no chord, no
    resolution. Rhythmic + repeating reads as "moving on, next thing".
    """
    dur = 0.50
    n = ns(dur)
    samples = []

    tap_pitch = 659.25  # E5 — sits between KM_SPLIT (D5) and SIGNAL_REGAINED (G5)
    tap_onsets = [0.000, 0.090, 0.180]  # 90ms apart — tight rhythm
    tap_dur = 0.20

    for i in range(n):
        t = i / RATE
        val = 0.0
        global _noise_state
        _noise_state = 33333 + i

        for onset in tap_onsets:
            if t >= onset:
                t_local = t - onset
                if t_local < tap_dur:
                    val += bell_note(tap_pitch, t_local, tap_dur,
                                     brightness=0.85, pitch_bend_cents=8) * 0.55

        val += filtered_noise(t, dur, center_freq=4500, bandwidth=2000, level=0.006)

        samples.append(val)

    # Very dry — informational, not ceremonial
    samples = premium_reverb(samples, dry_wet=0.15, room_size=0.40)
    write_wav("earcon_segment_change.wav", samples)


def gen_signal_lost():
    """
    SIGNAL LOST (v3) — descending perfect fourth with a brief static hiss.
    D5 → A4 (P4 down, was D5→Bb4 m3 in v2). The interval bump avoids echoing
    SLOW_DOWN's P5 descent; the front-loaded broadband hiss telegraphs
    "transmission problem" so it's clearly distinct from "tempo problem".
    Still muted brightness — concern, not panic.
    Pair-symmetric with SIGNAL_REGAINED (E5→G5 m3 ascent).
    """
    dur = 0.60
    n = ns(dur)
    samples = []

    for i in range(n):
        t = i / RATE
        val = 0.0
        global _noise_state
        _noise_state = 44444 + i

        # Static hiss burst at start — 100ms broadband noise that fades out.
        # Telegraphs "signal problem" before the bell tones land.
        if t < 0.10:
            hiss_env = (1.0 - t / 0.10) ** 1.5
            val += _noise() * 0.06 * hiss_env

        # D5 — slightly muted, enters under the tail of the hiss
        val += bell_note(587.33, t, 0.30, brightness=0.65, pitch_bend_cents=8)

        # A4 — warmer, deeper drop (P4 instead of m3)
        if t > 0.18:
            val += bell_note(440.00, t - 0.18, 0.40, brightness=0.55,
                             pitch_bend_cents=6)

        # Mid-low band noise sustained at very low level — residual "static"
        val += filtered_noise(t, dur, center_freq=2500, bandwidth=1500, level=0.006)

        samples.append(val)

    samples = premium_reverb(samples, dry_wet=0.28, room_size=0.65)
    write_wav("earcon_signal_lost.wav", samples)


def gen_signal_regained():
    """
    SIGNAL REGAINED (v3) — two-note ascending minor third. Pair-mirror of LOST.
    E5 → G5 (m3 up). v2 was a single G5 ping, easily confused with KM_SPLIT's
    single E5 tap. Two-note ascent restores pair-symmetry with SIGNAL_LOST
    (D5→A4 P4 down) — runners learn the family by the shape, not by the pitch.
    Different interval (m3) from the zone-correction pair (P5) so the signal
    family has its own identity.
    """
    dur = 0.45
    n = ns(dur)
    samples = []

    for i in range(n):
        t = i / RATE
        val = 0.0
        global _noise_state
        _noise_state = 55555 + i

        # E5 — entry note
        val += bell_note(659.25, t, 0.28, brightness=1.10, pitch_bend_cents=15)

        # G5 — bright resolution
        if t > 0.10:
            val += bell_note(783.99, t - 0.10, 0.32, brightness=1.20,
                             pitch_bend_cents=18) * 1.05

        val += filtered_noise(t, dur, center_freq=6500, bandwidth=3000, level=0.009)

        samples.append(val)

    samples = premium_reverb(samples, dry_wet=0.18, room_size=0.40)
    write_wav("earcon_signal_regained.wav", samples)


def gen_km_split():
    """
    KM SPLIT (v3) — minimal bell tap. Pitch dropped E5 → D5.
    Pitch-ranks below SIGNAL_REGAINED's E5 entry note (and well below G5),
    so "low single tap = routine" and "higher = exception" reads in the
    pitch hierarchy. Still dry, still quick. Informational, not distracting.
    """
    dur = 0.22
    n = ns(dur)
    samples = []

    for i in range(n):
        t = i / RATE
        val = 0.0
        global _noise_state
        _noise_state = 66666 + i

        val += bell_note(587.33, t, dur, brightness=0.9, pitch_bend_cents=10)  # D5

        val += filtered_noise(t, dur, center_freq=4500, bandwidth=2000, level=0.005)

        samples.append(val)

    samples = premium_reverb(samples, dry_wet=0.15, room_size=0.3)
    write_wav("earcon_km_split.wav", samples)


def gen_halfway():
    """
    HALFWAY (v3) — anticipates the WORKOUT_COMPLETE motif.
    C5 → E5 (ascending major 3rd) — the first two notes of WORKOUT_COMPLETE's
    C5→E5→G5→C6 arpeggio. Listener subconsciously hears "halfway through the
    arrival chord". Replaces v2 G5→C6 (P4) which was identical to
    SEGMENT_CHANGE — destroying the milestone family vs interval distinction.
    Milestone family now reads as a clear progression:
        KM_SPLIT (1 note, dry)  →  HALFWAY (2 notes, anticipating)  →
        WORKOUT_COMPLETE (4 notes, full arrival).
    """
    dur = 0.55
    n = ns(dur)
    samples = []

    for i in range(n):
        t = i / RATE
        val = 0.0
        global _noise_state
        _noise_state = 77777 + i

        # Note 1: C5 — root of the completion motif
        val += bell_note(523.25, t, 0.32, brightness=0.95, pitch_bend_cents=12)

        # Note 2: E5 — major 3rd, brighter
        if t > 0.12:
            val += bell_note(659.25, t - 0.12, 0.36, brightness=1.05,
                             pitch_bend_cents=14) * 1.0

        val += filtered_noise(t, dur, center_freq=5000, bandwidth=2500, level=0.008)

        samples.append(val)

    # Slightly more reverb than KM_SPLIT, less than WORKOUT_COMPLETE
    samples = premium_reverb(samples, dry_wet=0.22, room_size=0.55)
    write_wav("earcon_halfway.wav", samples)


def gen_workout_complete():
    """
    WORKOUT COMPLETE — grand ascending bell arpeggio with sustained chord.
    C5 → E5 → G5 → C6, each ringing into a full chord.
    The big payoff. More reverb, longer ring, sparkle.
    """
    dur = 1.10
    n = ns(dur)
    samples = []

    notes = [
        (523.25, 0.000),  # C5
        (659.25, 0.090),  # E5
        (783.99, 0.180),  # G5
        (1046.50, 0.270), # C6
    ]

    for i in range(n):
        t = i / RATE
        val = 0.0
        global _noise_state
        _noise_state = 88888 + i

        for freq, onset in notes:
            if t >= onset:
                t_local = t - onset
                note_dur = dur - onset
                bright = 0.85 + (freq - 500) / 800
                val += bell_note(freq, t_local, note_dur, brightness=bright,
                                 pitch_bend_cents=12) * 0.85

        # Sub-octave warmth after arpeggio completes
        if t > 0.30:
            t_sub = t - 0.30
            val += bell_note(261.63, t_sub, dur - 0.30, brightness=0.5,
                             pitch_bend_cents=5) * 0.12

        # Sparkle/air
        val += filtered_noise(t, dur, center_freq=6500, bandwidth=4000, level=0.010)

        samples.append(val)

    samples = premium_reverb(samples, dry_wet=0.30, room_size=0.75)
    write_wav("earcon_workout_complete.wav", samples)


# ===================================================================
# Generate all
# ===================================================================

if __name__ == "__main__":
    print("Generating Cardea earcons (v3 — semantic differentiation pass)...")
    print()
    gen_speed_up()
    gen_slow_down()
    gen_in_zone_confirm()
    gen_return_to_zone()
    gen_predictive_warning()
    gen_segment_change()
    gen_signal_lost()
    gen_signal_regained()
    gen_km_split()
    gen_halfway()
    gen_workout_complete()
    print()
    print("Done! All 11 earcons generated.")
