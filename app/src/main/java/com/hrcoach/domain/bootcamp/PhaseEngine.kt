package com.hrcoach.domain.bootcamp

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

    val isRecoveryWeek: Boolean
        get() = weekInPhase > 0 && (weekInPhase + 1) % 3 == 0

    fun planCurrentWeek(): List<PlannedSession> {
        val effectiveMinutes = if (isRecoveryWeek) {
            (targetMinutes * 0.8f).toInt()
        } else {
            targetMinutes
        }
        return SessionSelector.weekSessions(
            phase = currentPhase,
            goal = goal,
            runsPerWeek = runsPerWeek,
            targetMinutes = effectiveMinutes
        )
    }

    fun shouldAdvancePhase(): Boolean =
        weekInPhase >= phaseMidpointWeeks(currentPhase)

    fun advancePhase(): PhaseEngine {
        val nextIndex = if (phaseIndex + 1 >= goal.phaseArc.size) 0 else phaseIndex + 1
        return copy(phaseIndex = nextIndex, weekInPhase = 0)
    }

    companion object {
        fun phaseMidpointWeeks(phase: TrainingPhase): Int {
            val range = phase.weeksRange
            return (range.first + range.last) / 2
        }
    }
}
