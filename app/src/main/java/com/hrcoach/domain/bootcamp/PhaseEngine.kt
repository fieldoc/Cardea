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

    val weeksUntilNextRecovery: Int
        get() {
            if (isRecoveryWeek) return 0
            var w = weekInPhase + 1
            var steps = 0
            while (steps < 10) {
                if (w > 0 && (w + 1) % 3 == 0) return steps + 1
                w++
                steps++
            }
            return steps
        }

    fun planCurrentWeek(
        tierIndex: Int = 0,
        tuningDirection: com.hrcoach.domain.engine.TuningDirection = com.hrcoach.domain.engine.TuningDirection.HOLD,
        currentPresetIndices: Map<String, Int> = emptyMap()
    ): List<PlannedSession> {
        val effectiveMinutes = if (isRecoveryWeek) {
            (targetMinutes * 0.8f).toInt()
        } else {
            targetMinutes
        }
        return SessionSelector.weekSessions(
            phase = currentPhase,
            goal = goal,
            runsPerWeek = runsPerWeek,
            targetMinutes = effectiveMinutes,
            tierIndex = tierIndex
        )
    }

    fun shouldAdvancePhase(): Boolean =
        weekInPhase >= phaseMidpointWeeks(currentPhase)

    fun advancePhase(): PhaseEngine {
        val nextIndex = if (phaseIndex + 1 >= goal.phaseArc.size) 0 else phaseIndex + 1
        return copy(phaseIndex = nextIndex, weekInPhase = 0)
    }

    fun lookaheadWeeks(count: Int, tierIndex: Int = 1): List<WeekLookahead> {
        if (count <= 0) return emptyList()
        val result = mutableListOf<WeekLookahead>()
        var cursor = this
        repeat(count) {
            cursor = if (cursor.shouldAdvancePhase()) {
                cursor.advancePhase()
            } else {
                cursor.copy(weekInPhase = cursor.weekInPhase + 1)
            }
            result.add(
                WeekLookahead(
                    weekNumber = cursor.absoluteWeek,
                    isRecovery = cursor.isRecoveryWeek,
                    sessions = cursor.planCurrentWeek(tierIndex = tierIndex)
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
