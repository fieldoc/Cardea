package com.hrcoach.service.workout.notification

import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.ZoneStatus
import com.hrcoach.service.WorkoutSnapshot
import kotlin.math.abs

/**
 * Pure functions that convert a WorkoutSnapshot + WorkoutConfig into
 * a NotifPayload. No Android dependencies — fully unit-testable.
 */
object NotifContentFormatter {

    fun format(
        snapshot: WorkoutSnapshot,
        config: WorkoutConfig,
        totalSeconds: Long,
    ): NotifPayload {
        val paused = snapshot.isPaused || snapshot.isAutoPaused
        val indeterminate = totalSeconds <= 0L
        return NotifPayload(
            titleText = buildTitle(snapshot, config),
            subtitleText = buildSubtitle(snapshot, totalSeconds),
            currentHr = snapshot.currentHr,
            zoneStatus = snapshot.zoneStatus,
            elapsedSeconds = snapshot.elapsedSeconds,
            totalSeconds = totalSeconds,
            isPaused = paused,
            isIndeterminate = indeterminate,
        )
    }

    private fun buildTitle(snapshot: WorkoutSnapshot, config: WorkoutConfig): String {
        // Countdown phase wins over everything else
        val countdown = snapshot.countdownSecondsRemaining
        if (countdown != null && countdown > 0) return "Starting in $countdown…"

        if (snapshot.isFreeRun) return "Free Run"

        val label = config.sessionLabel?.takeIf { it.isNotBlank() } ?: "Workout"

        if (snapshot.isAutoPaused) return "$label · Auto-paused"
        if (snapshot.isPaused) return "$label · Paused"

        // NO_DATA (no HR signal yet) — prompt runner to check the strap
        if (snapshot.zoneStatus == ZoneStatus.NO_DATA || snapshot.currentHr <= 0) {
            return "Get HR signal…"
        }

        return "$label · Target ${snapshot.targetHr}"
    }

    private fun buildSubtitle(snapshot: WorkoutSnapshot, totalSeconds: Long): String {
        val elapsedLabel = formatMinSec(snapshot.elapsedSeconds)
        val totalLabel = if (totalSeconds <= 0L) "∞" else formatMinSec(totalSeconds)
        val deltaLabel = buildDeltaLabel(snapshot)
        return "$elapsedLabel / $totalLabel · $deltaLabel"
    }

    private fun buildDeltaLabel(snapshot: WorkoutSnapshot): String = when (snapshot.zoneStatus) {
        ZoneStatus.IN_ZONE -> "ON TARGET"
        ZoneStatus.ABOVE_ZONE -> {
            val delta = snapshot.currentHr - snapshot.targetHr
            "+${abs(delta)} BPM"
        }
        ZoneStatus.BELOW_ZONE -> {
            val delta = snapshot.targetHr - snapshot.currentHr
            "-${abs(delta)} BPM"
        }
        ZoneStatus.NO_DATA -> "—"
    }

    private fun formatMinSec(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0L)
        val mm = s / 60
        val ss = s % 60
        return "%02d:%02d".format(mm, ss)
    }
}
