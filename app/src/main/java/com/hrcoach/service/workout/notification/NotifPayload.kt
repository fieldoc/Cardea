package com.hrcoach.service.workout.notification

import com.hrcoach.domain.model.ZoneStatus

/**
 * All data needed to render one frame of the workout notification.
 *
 * Built once per processTick by WorkoutForegroundService and passed to
 * WorkoutNotificationHelper.update(payload). Data class equality is used
 * by the helper to skip redundant notification posts when the payload
 * has not changed since the last tick.
 */
data class NotifPayload(
    /** e.g. "Aerobic Tempo · Target 145", or "Free Run", or "Get HR signal…" */
    val titleText: String,
    /** e.g. "18:30 / 45:00 · +13 BPM", or "18:30 / ∞ · ON TARGET" */
    val subtitleText: String,
    /** Current heart rate in bpm. 0 when no HR signal yet. */
    val currentHr: Int,
    /** Drives the badge gradient and rim accent. */
    val zoneStatus: ZoneStatus,
    /** Seconds since workout start (pauses subtracted). Used for MediaSession position + progress bar. */
    val elapsedSeconds: Long,
    /** Total planned duration in seconds. 0 for free run / unknown. */
    val totalSeconds: Long,
    /** True when workout is manually paused OR auto-paused. Drives action button + badge dimming. */
    val isPaused: Boolean,
    /** True when totalSeconds is unknown — progress bar is rendered indeterminate. */
    val isIndeterminate: Boolean,
)
