package com.hrcoach.domain.bootcamp

import com.hrcoach.domain.engine.TuningDirection
import com.hrcoach.domain.model.BootcampGoal
import com.hrcoach.domain.model.TrainingPhase

data class PhaseEngine(
    val goal: BootcampGoal,
    val phaseIndex: Int,
    val weekInPhase: Int,
    val runsPerWeek: Int = 3,
    val targetMinutes: Int = 30
) {
    val currentPhase: TrainingPhase
        get() = goal.phaseArc[phaseIndex.coerceIn(goal.phaseArc.indices)]

    val totalWeeks: Int
        get() = goal.phaseArc.sumOf { phaseMidpointWeeks(it) }

    val absoluteWeek: Int
        get() {
            var sum = 0
            for (i in 0 until phaseIndex) {
                sum += phaseMidpointWeeks(goal.phaseArc[i])
            }
            return sum + weekInPhase + 1
        }

    fun isRecoveryWeek(tuningDirection: TuningDirection? = null): Boolean {
        if (weekInPhase == 0) return false
        val cadence = when (tuningDirection) {
            TuningDirection.EASE_BACK -> 2
            TuningDirection.PUSH_HARDER -> 4
            TuningDirection.HOLD, null -> 3
        }
        return (weekInPhase + 1) % cadence == 0
    }

    fun weeksUntilNextRecovery(tuningDirection: TuningDirection? = null): Int {
        if (isRecoveryWeek(tuningDirection)) return 0
        // For EVERGREEN: recovery is always week 3 of 4-week micro-cycle
        if (currentPhase == TrainingPhase.EVERGREEN) {
            return (3 - (weekInPhase % 4)).let { if (it <= 0) it + 4 else it }
        }
        var cursor = this
        var steps = 0
        while (steps < 12) {
            cursor = if (cursor.shouldAdvancePhase()) {
                cursor.advancePhase() ?: return steps + 1 // graduation — no more recovery
            } else {
                cursor.copy(weekInPhase = cursor.weekInPhase + 1)
            }
            steps++
            if (cursor.isRecoveryWeek(tuningDirection)) return steps
        }
        return steps
    }

    fun planCurrentWeek(
        tierIndex: Int = 0,
        tuningDirection: com.hrcoach.domain.engine.TuningDirection = com.hrcoach.domain.engine.TuningDirection.HOLD,
        currentPresetIndices: Map<String, Int> = emptyMap()
    ): List<PlannedSession> {
        val effectiveMinutes = if (isRecoveryWeek(tuningDirection)) {
            (targetMinutes * 0.65f).toInt()
        } else {
            targetMinutes
        }
        return SessionSelector.weekSessions(
            phase = currentPhase,
            goal = goal,
            runsPerWeek = runsPerWeek,
            targetMinutes = effectiveMinutes,
            tierIndex = tierIndex,
            tuningDirection = tuningDirection,
            weekInPhase = weekInPhase,
            absoluteWeek = absoluteWeek,
            isRecoveryWeek = isRecoveryWeek(tuningDirection)
        )
    }

    fun shouldAdvancePhase(): Boolean =
        weekInPhase >= phaseMidpointWeeks(currentPhase)

    fun advancePhase(): PhaseEngine? {
        val nextIndex = phaseIndex + 1
        return if (nextIndex >= goal.phaseArc.size) {
            if (currentPhase == TrainingPhase.EVERGREEN) {
                // EVERGREEN wraps to itself — reset weekInPhase, stay in EVERGREEN
                copy(weekInPhase = 0)
            } else if (goal == BootcampGoal.CARDIO_HEALTH) {
                copy(phaseIndex = 0, weekInPhase = 0)
            } else {
                null // Race goals graduate
            }
        } else {
            copy(phaseIndex = nextIndex, weekInPhase = 0)
        }
    }

    fun lookaheadWeeks(
        count: Int,
        tierIndex: Int = 1,
        tuningDirection: TuningDirection? = null
    ): List<WeekLookahead> {
        if (count <= 0) return emptyList()
        val result = mutableListOf<WeekLookahead>()
        var cursor = this
        repeat(count) {
            cursor = if (cursor.shouldAdvancePhase()) {
                cursor.advancePhase() ?: return result // graduated — stop lookahead
            } else {
                cursor.copy(weekInPhase = cursor.weekInPhase + 1)
            }
            result.add(
                WeekLookahead(
                    weekNumber = cursor.absoluteWeek,
                    isRecovery = cursor.isRecoveryWeek(tuningDirection),
                    sessions = cursor.planCurrentWeek(
                        tierIndex = tierIndex,
                        tuningDirection = tuningDirection ?: TuningDirection.HOLD
                    )
                )
            )
        }
        return result
    }

    companion object {
        fun phaseMidpointWeeks(phase: TrainingPhase): Int {
            val range = phase.weeksRange
            return (range.first + range.last) / 2
        }
    }
}

data class WeekLookahead(
    val weekNumber: Int,
    val isRecovery: Boolean,
    val sessions: List<PlannedSession>
)
