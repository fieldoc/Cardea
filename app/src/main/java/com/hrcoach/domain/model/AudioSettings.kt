package com.hrcoach.domain.model

data class AudioSettings(
    val earconVolume: Int = 80,
    val voiceVolume: Int = 80,
    val voiceVerbosity: VoiceVerbosity = VoiceVerbosity.FULL,
    val enableVibration: Boolean = true,
    val enableHalfwayReminder: Boolean? = null,
    val enableKmSplits: Boolean? = null,
    val enableWorkoutComplete: Boolean? = null,
    val enableInZoneConfirm: Boolean? = null,
    // How often IN_ZONE_CONFIRM reassurance fires while steady in zone.
    // Default STANDARD = first at 3 min, then every 5 min. See ConfirmCadence docs.
    val inZoneConfirmCadence: ConfirmCadence = ConfirmCadence.STANDARD,
    // When true, the first SPEED_UP/SLOW_DOWN alert at MINIMAL verbosity includes voice
    // (not just earcon). Default true — MINIMAL users opted for fewer events, so when one
    // fires it should be maximally informative. Set false to restore the classic 3-tier
    // escalation (earcon-only → earcon+voice → earcon+voice+vibration) at MINIMAL; FULL
    // always uses the 3-tier pattern regardless of this flag.
    val minimalTierOneVoice: Boolean = true,
    // True after the user sees the first-workout audio primer. Default false for
    // fresh installs. See AudioPrimerDialog / SetupViewModel for gating logic.
    val audioPrimerShown: Boolean = false,
    // Per-rep strides timer chimes (Go / Ease / Set-Complete). Default true. Gated in
    // CoachingAudioManager.playStrides* on top of the standard verbosity earcon gate.
    // The one-shot voice announcement at strides start is NOT gated by this flag.
    val stridesTimerEarcons: Boolean = true,
    // True after the user sees the first-strides bootcamp primer. Default false for
    // fresh installs. See StridesPrimer / BootcampViewModel for gating logic.
    // Persisted under key "bootcamp_strides_primer_seen" semantically (the field
    // lives inside the AudioSettings JSON; the SharedPreferences key namespace is
    // owned by AudioSettingsRepository).
    val stridesPrimerSeen: Boolean = false
)

enum class VoiceVerbosity {
    OFF,
    MINIMAL,
    FULL
}

/**
 * Cadence for the IN_ZONE_CONFIRM reassurance cue while the runner is steady in zone.
 * [firstMs] is the delay before the first confirm of a zone entry; [repeatMs] is the
 * gap between subsequent confirms.
 *
 * FREQUENT   — legacy 3-min-flat cadence. ~6 events on a 30-min steady run.
 * STANDARD   — default. Quick first reassurance then backs off. ~3 events on a 30-min run.
 * REDUCED    — near-silent steady state. First confirm at 3 min, then every 10 min.
 */
enum class ConfirmCadence(val firstMs: Long, val repeatMs: Long) {
    FREQUENT(180_000L, 180_000L),
    STANDARD(180_000L, 300_000L),
    REDUCED(180_000L, 600_000L)
}
