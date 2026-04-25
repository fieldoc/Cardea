package com.hrcoach.domain.engine

import com.hrcoach.domain.model.ZoneStatus

/**
 * Per-workout, deterministic controller for the "strides" finishing set.
 *
 * State machine (see plan: docs/cardea/2026-04-25-strides-timer-and-primer.md):
 *   Idle  --(elapsedSec >= 1200 && IN_ZONE)--> Work (rep 1)   [emit Announce, RepStart(1, N)]
 *   Work  --(+20s in cycle)-------------------> Rest          [emit RepEnd]
 *   Rest  --(+80s in cycle, more reps)--------> Work (rep++)  [emit RepStart(i, N)]
 *   Rest  --(+80s in cycle, last rep)---------> Done          [emit SetComplete]
 *   Done  --(any tick)-------------------------> Done          [no-op]
 *
 * Pure controller: no Android imports, no service/audio dependencies, no wall-clock
 * reads, no randomness. All output goes through the returned event list.
 */
class StridesController(durationMin: Int) {

    sealed class Phase {
        object Idle : Phase()
        object Work : Phase()
        object Rest : Phase()
        object Done : Phase()
    }

    private var phase: Phase = Phase.Idle
    private var repIndex: Int = 0
    private var cycleStartSec: Long = -1L
    private val totalReps: Int = repsForDuration(durationMin)

    /** True once the set has been announced and is in progress; false in Idle and Done. */
    val isActive: Boolean
        get() = phase is Phase.Work || phase is Phase.Rest

    fun evaluateTick(elapsedSec: Long, zoneStatus: ZoneStatus): List<StridesEvent> {
        return when (phase) {
            is Phase.Idle -> tickIdle(elapsedSec, zoneStatus)
            is Phase.Work -> tickWork(elapsedSec)
            is Phase.Rest -> tickRest(elapsedSec)
            is Phase.Done -> emptyList()
        }
    }

    private fun tickIdle(elapsedSec: Long, zoneStatus: ZoneStatus): List<StridesEvent> {
        if (elapsedSec < START_AFTER_SEC) return emptyList()
        if (zoneStatus != ZoneStatus.IN_ZONE) return emptyList()
        phase = Phase.Work
        cycleStartSec = elapsedSec
        repIndex = 1
        return listOf(
            StridesEvent.Announce(totalReps),
            StridesEvent.RepStart(repIndex, totalReps)
        )
    }

    private fun tickWork(elapsedSec: Long): List<StridesEvent> {
        if (elapsedSec - cycleStartSec < WORK_SEC) return emptyList()
        phase = Phase.Rest
        return listOf(StridesEvent.RepEnd)
    }

    private fun tickRest(elapsedSec: Long): List<StridesEvent> {
        if (elapsedSec - cycleStartSec < CYCLE_SEC) return emptyList()
        return if (repIndex >= totalReps) {
            phase = Phase.Done
            listOf(StridesEvent.SetComplete)
        } else {
            repIndex += 1
            cycleStartSec = elapsedSec
            phase = Phase.Work
            listOf(StridesEvent.RepStart(repIndex, totalReps))
        }
    }

    companion object {
        private const val START_AFTER_SEC = 1200L  // 20 min
        private const val WORK_SEC = 20L
        private const val CYCLE_SEC = 80L          // 20s work + 60s rest

        fun repsForDuration(min: Int): Int = when (min) {
            20 -> 4
            22 -> 6
            24 -> 8
            26 -> 10
            else -> 5
        }
    }
}

sealed class StridesEvent {
    data class Announce(val totalReps: Int) : StridesEvent()
    data class RepStart(val repIndex: Int, val totalReps: Int) : StridesEvent()
    object RepEnd : StridesEvent()
    object SetComplete : StridesEvent()
}
