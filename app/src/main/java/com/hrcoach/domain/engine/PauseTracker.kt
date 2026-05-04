package com.hrcoach.domain.engine

/**
 * Owns the active-time arithmetic for a workout: start time, manual-pause windows, and
 * auto-pause windows. Single source of truth for `activeMs(now)` so that the live elapsed
 * display, end-of-run `activeDurationSeconds`, and the cloud-backup field all agree.
 *
 * Not thread-safe: callers must serialize entry-point calls. WFS does this naturally —
 * pause/resume run on the main thread from intent handlers, and `processTick` is
 * single-flight on the IO dispatcher inside the foreground service.
 */
class PauseTracker {

    var isManuallyPaused: Boolean = false
        private set

    var isAutoPaused: Boolean = false
        private set

    private var started: Boolean = false
    private var workoutStartMs: Long = 0L
    private var pauseStartMs: Long = 0L
    private var totalPausedMs: Long = 0L
    private var autoPauseStartMs: Long = 0L
    private var totalAutoPausedMs: Long = 0L

    fun reset() {
        isManuallyPaused = false
        isAutoPaused = false
        started = false
        workoutStartMs = 0L
        pauseStartMs = 0L
        totalPausedMs = 0L
        autoPauseStartMs = 0L
        totalAutoPausedMs = 0L
    }

    fun start(nowMs: Long) {
        started = true
        workoutStartMs = nowMs
    }

    /** User pressed Pause. Returns true iff state changed. */
    fun manualPause(nowMs: Long): Boolean {
        if (isManuallyPaused) return false
        pauseStartMs = nowMs
        isManuallyPaused = true
        // Guard 1: latch any in-progress auto-pause so it isn't double-subtracted later.
        // Also clear isAutoPaused — without this, the toggle-off handler can later read a
        // stale "true" with autoPauseStartMs=0 and add clock.now() to the running total.
        if (isAutoPaused && autoPauseStartMs > 0L) {
            totalAutoPausedMs += nowMs - autoPauseStartMs
            autoPauseStartMs = 0L
            isAutoPaused = false
        }
        return true
    }

    /** User pressed Resume. Returns true iff state changed. */
    fun manualResume(nowMs: Long): Boolean {
        if (!isManuallyPaused) return false
        if (pauseStartMs > 0L) {
            totalPausedMs += nowMs - pauseStartMs
            pauseStartMs = 0L
        }
        isManuallyPaused = false
        // Guard 2: if auto-pause is still flagged, restart its window from now so the
        // eventual auto-resume doesn't re-count time already inside the manual window.
        if (isAutoPaused) {
            autoPauseStartMs = nowMs
        }
        return true
    }

    /**
     * AutoPauseDetector fired PAUSED. Returns true iff state changed.
     *
     * No-op while the user is manually paused — auto-pause windows that overlap a manual
     * pause have no meaning (the manual pause already covers the whole span) and processing
     * them causes double-subtraction at manual resume. This is the missing guard that
     * caused workout #33's activeDurationSeconds=221s for an 88-minute run.
     */
    fun autoPause(nowMs: Long): Boolean {
        if (isManuallyPaused) return false
        if (isAutoPaused) return false
        isAutoPaused = true
        autoPauseStartMs = nowMs
        return true
    }

    /**
     * AutoPauseDetector fired RESUMED. Returns true iff state changed.
     *
     * Same gating as `autoPause`: ignored while manually paused.
     */
    fun autoResume(nowMs: Long): Boolean {
        if (isManuallyPaused) return false
        if (!isAutoPaused) return false
        if (autoPauseStartMs > 0L) {
            totalAutoPausedMs += nowMs - autoPauseStartMs
        }
        autoPauseStartMs = 0L
        isAutoPaused = false
        return true
    }

    /**
     * User toggled auto-pause OFF mid-run via the in-run audio settings sheet. Cleanly
     * latches any in-progress auto-pause window and clears the flag.
     *
     * Required guard: only accumulate when `autoPauseStartMs > 0L`. Without it, calling
     * this in the post-Guard-1 state (isAutoPaused=true, autoPauseStartMs=0 because manual
     * pause absorbed the window) would compute `clock.now() - 0` ≈ 1.78 trillion and add
     * that to the running total, sending `activeMs` permanently negative.
     */
    fun disableAutoPause(nowMs: Long) {
        if (isAutoPaused && autoPauseStartMs > 0L) {
            totalAutoPausedMs += nowMs - autoPauseStartMs
        }
        autoPauseStartMs = 0L
        isAutoPaused = false
    }

    /**
     * Active wall time minus all paused windows (manual + auto, completed + in-progress).
     * Always >= 0. Returns 0 before `start()` is called.
     */
    fun activeMs(nowMs: Long): Long {
        if (!started) return 0L
        val currentManualMs = if (pauseStartMs > 0L) nowMs - pauseStartMs else 0L
        val currentAutoMs = if (isAutoPaused && autoPauseStartMs > 0L) nowMs - autoPauseStartMs else 0L
        return (nowMs - workoutStartMs - totalPausedMs - totalAutoPausedMs - currentManualMs - currentAutoMs)
            .coerceAtLeast(0L)
    }

    fun activeSeconds(nowMs: Long): Long = activeMs(nowMs) / 1000L
}
