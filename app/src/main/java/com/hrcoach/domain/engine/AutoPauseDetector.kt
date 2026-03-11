package com.hrcoach.domain.engine

enum class AutoPauseEvent { NONE, PAUSED, RESUMED }

/**
 * Detects when a runner stops moving and resumes, using a Schmitt trigger on GPS speed.
 *
 * A Schmitt trigger uses *different* thresholds for stopping vs. resuming, preventing bouncing
 * when GPS speed oscillates near a crosswalk or at a slow shuffle.
 *
 * The confirmation window (default 3 s) requires sustained stillness before triggering PAUSED,
 * absorbing brief GPS noise spikes to zero.
 *
 * Usage: feed each GPS location tick into [update]. Returns [AutoPauseEvent.PAUSED] or
 * [AutoPauseEvent.RESUMED] on a state transition, [AutoPauseEvent.NONE] otherwise.
 */
class AutoPauseDetector(
    private val stopThresholdMs: Float = 0.5f,    // ~1.8 km/h — clearly stopped
    private val resumeThresholdMs: Float = 1.0f,  // ~3.6 km/h — clearly moving again
    private val confirmWindowMs: Long = 3_000L    // must be below stop threshold this long
) {
    private var isAutoPaused: Boolean = false
    // Null means "not currently stopped". Using null (not 0L) avoids ambiguity when nowMs == 0L.
    private var stoppedSinceMs: Long? = null

    /**
     * @param speedMs  Speed in m/s from [android.location.Location.speed], or null if unavailable.
     * @param nowMs    Current epoch time in milliseconds.
     */
    fun update(speedMs: Float?, nowMs: Long): AutoPauseEvent {
        if (speedMs == null) return AutoPauseEvent.NONE

        return if (isAutoPaused) {
            if (speedMs >= resumeThresholdMs) {
                isAutoPaused = false
                stoppedSinceMs = null
                AutoPauseEvent.RESUMED
            } else {
                AutoPauseEvent.NONE
            }
        } else {
            if (speedMs < stopThresholdMs) {
                // Record the first moment we went below the stop threshold
                val since = stoppedSinceMs ?: nowMs.also { stoppedSinceMs = it }
                if (nowMs - since >= confirmWindowMs) {
                    isAutoPaused = true
                    AutoPauseEvent.PAUSED
                } else {
                    AutoPauseEvent.NONE
                }
            } else {
                // Moving — reset the confirmation window
                stoppedSinceMs = null
                AutoPauseEvent.NONE
            }
        }
    }

    fun reset() {
        isAutoPaused = false
        stoppedSinceMs = null
    }
}
