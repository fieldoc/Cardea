package com.hrcoach.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the pause-accounting state machine that previously lived inline in
 * WorkoutForegroundService. The bugs targeted here came from a real run
 * (workout #33 on 2026-05-03) where an 88-minute wall-clock session reported
 * activeDurationSeconds=221 — see TTS log run_20260503_100205.log. Two distinct
 * failure modes converge in the regression cases below.
 */
class PauseTrackerTest {

    // ── Baseline (no overlap) ─────────────────────────────────────────────────

    @Test
    fun cleanRunWithNoPauses_activeMatchesWall() {
        val t = PauseTracker()
        t.start(1_000L)
        assertEquals(60_000L, t.activeMs(61_000L))
    }

    @Test
    fun manualPauseAndResume_subtractsExactlyTheManualWindow() {
        val t = PauseTracker()
        t.start(0L)
        t.manualPause(60_000L)
        t.manualResume(120_000L)
        // Wall = 180s, paused = 60s → active = 120s
        assertEquals(120_000L, t.activeMs(180_000L))
    }

    @Test
    fun pureAutoPauseCycle_subtractsExactlyTheAutoWindow() {
        val t = PauseTracker()
        t.start(0L)
        t.autoPause(60_000L)
        t.autoResume(90_000L)
        assertEquals(120_000L, t.activeMs(150_000L))
    }

    @Test
    fun activeMsBeforeStart_isZero() {
        val t = PauseTracker()
        assertEquals(0L, t.activeMs(123_456L))
    }

    @Test
    fun activeMsClampsAtZero_neverNegative() {
        val t = PauseTracker()
        t.start(1_000L)
        // Directly impossible via real flow, but guards against any future arithmetic regression.
        // Force a negative by pausing before start: not exposed publicly, so verify via the
        // happy path of nowMs == workoutStartMs.
        assertEquals(0L, t.activeMs(1_000L))
    }

    // ── Manual + auto overlap (the workout #33 bug) ───────────────────────────

    /**
     * REGRESSION: workout #33 happened because AutoPauseDetector kept firing while the
     * user was manually paused. Each silent PAUSE→RESUME cycle accumulated into
     * totalAutoPausedMs, and the manual-resume path then added the FULL manual window
     * to totalPausedMs — overlap was subtracted twice.
     *
     * Scenario: user pauses at t=60s, walks around briefly during the pause (auto fires
     * PAUSE at t=65s, RESUME at t=120s when they move >1.0 m/s), then finally hits Resume
     * at t=180s. The auto window (65→120, 55s) sits entirely inside the manual window
     * (60→180, 120s), so activeMs MUST subtract only 120s.
     */
    @Test
    fun autoEventsDuringManualPause_doNotDoubleSubtract() {
        val t = PauseTracker()
        t.start(0L)
        // Wall = 240s, manual pause from 60s to 180s (120s window).
        t.manualPause(60_000L)
        t.autoPause(65_000L)    // detector fires while user is manually paused
        t.autoResume(120_000L)  // detector fires RESUMED (e.g. user fidgeted)
        t.manualResume(180_000L)
        assertEquals(120_000L, t.activeMs(240_000L))
    }

    /**
     * Variant: detector cycles MULTIPLE times during a long manual pause. Each cycle
     * should be a no-op so the only thing accounted for is the manual window itself.
     */
    @Test
    fun multipleAutoCyclesDuringManualPause_doNotDoubleSubtract() {
        val t = PauseTracker()
        t.start(0L)
        // Wall = 600s, manual pause from 60s to 540s (480s window).
        t.manualPause(60_000L)
        t.autoPause(63_000L);  t.autoResume(120_000L)  // cycle 1: 57s
        t.autoPause(150_000L); t.autoResume(200_000L)  // cycle 2: 50s
        t.autoPause(250_000L); t.autoResume(400_000L)  // cycle 3: 150s
        t.manualResume(540_000L)
        assertEquals(120_000L, t.activeMs(600_000L))
    }

    /**
     * State-change return values must reflect that auto events during manual pause are
     * inert — callers depend on this to suppress audio feedback (we don't want the
     * "Run resumed" TTS firing while the user is sitting on a bench, manually paused).
     */
    @Test
    fun autoEventsDuringManualPause_returnFalse() {
        val t = PauseTracker()
        t.start(0L)
        t.manualPause(60_000L)
        assertFalse("autoPause must be no-op while manually paused", t.autoPause(65_000L))
        assertFalse("autoResume must be no-op while manually paused", t.autoResume(70_000L))
    }

    // ── Guard 1: auto-pause first, then manual on top ─────────────────────────

    @Test
    fun manualPauseAfterAutoPause_latchesAutoWindow() {
        val t = PauseTracker()
        t.start(0L)
        t.autoPause(60_000L)
        t.manualPause(70_000L)        // Guard 1: latches the 10s auto window
        t.manualResume(180_000L)      // adds 110s manual window
        // Total subtracted: 10s (auto) + 110s (manual) = 120s. Wall=240s → active=120s.
        assertEquals(120_000L, t.activeMs(240_000L))
    }

    /**
     * After Guard 1 latches, the WorkoutState.isAutoPaused flag should be cleared inside
     * the tracker too — otherwise the toggle-off handler sees a stale "true" with
     * autoPauseStartMs=0 and produces nonsense.
     */
    @Test
    fun manualPauseAfterAutoPause_clearsIsAutoPaused() {
        val t = PauseTracker()
        t.start(0L)
        t.autoPause(60_000L)
        assertTrue(t.isAutoPaused)
        t.manualPause(70_000L)
        assertFalse(
            "Guard 1 must clear isAutoPaused so toggle-off doesn't misfire later",
            t.isAutoPaused,
        )
    }

    // ── ACTION_TOGGLE_AUTO_PAUSE catastrophic case ────────────────────────────

    /**
     * REGRESSION: if the toggle-off handler doesn't guard `autoPauseStartMs > 0L`, calling
     * it after Guard 1 has zeroed `autoPauseStartMs` (while `isAutoPaused` is still true)
     * computes `now - 0` ≈ 1.78 trillion ms and adds that to the total. activeMs goes
     * permanently negative (clamped to 0).
     *
     * Even with the isAutoPaused-clearing fix above, this test pins the contract: the
     * handler must never be the source of a billion-second jump.
     */
    @Test
    fun disableAutoPauseAfterGuard1_doesNotAddEpochMillis() {
        val t = PauseTracker()
        // Realistic epoch-ms scale so an unguarded `now - 0` produces a recognisable blowup.
        val realisticNow = 1_780_000_000_000L
        t.start(realisticNow)
        t.autoPause(realisticNow + 60_000L)
        t.manualPause(realisticNow + 70_000L)   // Guard 1 zeros autoPauseStartMs
        t.disableAutoPause(realisticNow + 80_000L)
        t.manualResume(realisticNow + 180_000L)
        // Wall since start = 240s. Auto window 60→70 (10s, latched by Guard 1) plus manual
        // window 70→180 (110s) = 120s subtracted. Active = 120s.
        val active = t.activeMs(realisticNow + 240_000L)
        assertEquals(
            "disableAutoPause after Guard 1 must not add clock.now() to totalAutoPausedMs",
            120_000L,
            active,
        )
    }

    @Test
    fun disableAutoPauseDuringHonestAutoPause_accumulatesElapsedNotEpoch() {
        val t = PauseTracker()
        t.start(0L)
        t.autoPause(60_000L)
        t.disableAutoPause(90_000L)   // auto window was 30s
        // Wall = 180s, auto-window = 30s → active = 150s
        assertEquals(150_000L, t.activeMs(180_000L))
        assertFalse(t.isAutoPaused)
    }

    @Test
    fun disableAutoPauseWhenNotPaused_isNoOp() {
        val t = PauseTracker()
        t.start(0L)
        t.disableAutoPause(60_000L)
        assertEquals(120_000L, t.activeMs(120_000L))
    }

    // ── End-of-run accounting (matches stopWorkout's activeSec formula) ───────

    @Test
    fun activeMsWhilePaused_subtractsInProgressManualWindow() {
        val t = PauseTracker()
        t.start(0L)
        t.manualPause(60_000L)
        // Still paused; nowMs has advanced 60s into the pause.
        // Wall=120s, in-progress manual=60s → active=60s (frozen at pre-pause value).
        assertEquals(60_000L, t.activeMs(120_000L))
    }

    @Test
    fun activeMsWhileAutoPaused_subtractsInProgressAutoWindow() {
        val t = PauseTracker()
        t.start(0L)
        t.autoPause(60_000L)
        assertEquals(60_000L, t.activeMs(120_000L))
    }

    // ── Real workout #33 timeline (smoke test) ────────────────────────────────

    /**
     * Replays the major pause windows from run_20260503_100205.log to verify the fix
     * produces a sane active duration. Wall ≈ 5320s. Pre-fix the device reported 221s;
     * with the fix in place active should land near the expected ~2500s (sum of pause
     * windows ≈ 2820s). Exact value depends on the small autopauses we elide here, so
     * the assertion is on the order of magnitude rather than a literal match.
     */
    @Test
    fun workout33Timeline_landsCloseToExpectedActiveDuration() {
        val t = PauseTracker()
        t.start(0L)

        // First-of-session autopause cycle around +419s
        t.autoPause(419_200L);   t.autoResume(438_500L)
        // Second autopause around +471s
        t.autoPause(471_300L);   t.autoResume(475_500L)

        // Long manual pause #3 from +967s to +1880s, with an autopause inside it at +1358s.
        t.manualPause(967_300L)
        t.autoPause(1_358_100L)  // gated out: should be no-op
        t.manualResume(1_880_500L)

        // Autopause cycle #4
        t.autoPause(1_923_600L); t.autoResume(2_012_500L)

        // Long manual pause #5 from +2081s to +3616s, with concurrent autopause 14ms later.
        t.manualPause(2_081_700L)
        t.autoPause(2_081_714L)  // gated out
        t.manualResume(3_616_500L)

        // Tail autopauses
        t.autoPause(3_833_500L); t.autoResume(3_921_700L)
        t.autoPause(4_747_500L); t.autoResume(4_875_600L)
        t.autoPause(5_265_900L); t.autoResume(5_289_700L)
        t.autoPause(5_319_900L)  // still in progress at end

        val activeSec = t.activeSeconds(5_320_300L)
        // Pre-fix this came out to ~221s. Post-fix it must be in the legitimate active range.
        assertTrue(
            "Expected active ≈ 2500s, got ${activeSec}s (pre-fix bug returned 221s)",
            activeSec in 2_400L..2_600L,
        )
    }
}
