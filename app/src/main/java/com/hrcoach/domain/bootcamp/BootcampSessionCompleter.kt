package com.hrcoach.domain.bootcamp

import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.data.repository.BootcampRepository
import com.hrcoach.domain.engine.TuningDirection
import com.hrcoach.domain.model.BootcampGoal
import javax.inject.Inject

class BootcampSessionCompleter @Inject constructor(
    private val bootcampRepository: BootcampRepository
) {

    data class CompletionResult(
        val completed: Boolean,
        val weekComplete: Boolean = false,
        val progressLabel: String? = null
    )

    suspend fun complete(
        workoutId: Long,
        pendingSessionId: Long?,
        tuningDirection: TuningDirection = TuningDirection.HOLD
    ): CompletionResult {
        if (pendingSessionId == null) return CompletionResult(completed = false)

        val enrollment = bootcampRepository.getActiveEnrollmentOnce()
            ?: return CompletionResult(completed = false)
        val goal = BootcampGoal.valueOf(enrollment.goalType)
        val engine = PhaseEngine(
            goal = goal,
            phaseIndex = enrollment.currentPhaseIndex,
            weekInPhase = enrollment.currentWeekInPhase,
            runsPerWeek = enrollment.runsPerWeek,
            targetMinutes = enrollment.targetMinutesPerRun
        )

        val currentWeekSessions = bootcampRepository.getSessionsForWeek(enrollment.id, engine.absoluteWeek)
        val targetSession = currentWeekSessions.firstOrNull { it.id == pendingSessionId }
            ?: return CompletionResult(completed = false)

        val completedSession = targetSession.copy(
            status = BootcampSessionEntity.STATUS_COMPLETED,
            completedWorkoutId = workoutId
        )

        val simulatedWeek = currentWeekSessions.map { session ->
            if (session.id == completedSession.id) completedSession else session
        }
        val weekComplete = simulatedWeek.isNotEmpty() &&
            simulatedWeek.all { it.status == BootcampSessionEntity.STATUS_COMPLETED }

        return if (weekComplete) {
            val nextEngine = if (engine.shouldAdvancePhase()) {
                engine.advancePhase()
            } else {
                engine.copy(weekInPhase = engine.weekInPhase + 1)
            }
            val updatedEnrollment = enrollment.copy(
                currentPhaseIndex = nextEngine.phaseIndex,
                currentWeekInPhase = nextEngine.weekInPhase
            )
            val nextWeekEntities = buildNextWeekEntities(
                enrollmentId = enrollment.id,
                nextEngine = nextEngine,
                preferredDays = enrollment.preferredDays,
                tierIndex = enrollment.tierIndex,
                tuningDirection = tuningDirection
            )
            bootcampRepository.completeSessionAndAdvanceWeek(
                completedSession = completedSession,
                updatedEnrollment = updatedEnrollment,
                newSessions = nextWeekEntities
            )
            CompletionResult(
                completed = true,
                weekComplete = true,
                progressLabel = "Week ${engine.absoluteWeek} complete - ${engine.currentPhase.displayName}"
            )
        } else {
            bootcampRepository.completeSessionOnly(completedSession)
            CompletionResult(
                completed = true,
                weekComplete = false,
                progressLabel = "${simulatedWeek.count { it.status == BootcampSessionEntity.STATUS_COMPLETED }} of ${enrollment.runsPerWeek} sessions this week"
            )
        }
    }

    private fun buildNextWeekEntities(
        enrollmentId: Long,
        nextEngine: PhaseEngine,
        preferredDays: List<DayPreference>,
        tierIndex: Int,
        tuningDirection: TuningDirection
    ): List<BootcampSessionEntity> {
        val plannedSessions = nextEngine.planCurrentWeek(
            tierIndex = tierIndex,
            tuningDirection = tuningDirection,
            currentPresetIndices = emptyMap()
        )
        if (plannedSessions.isEmpty()) return emptyList()

        val availableDays = preferredDays
            .filter { it.level != DaySelectionLevel.NONE }
            .map { it.day }

        return plannedSessions.mapIndexed { index, session ->
            BootcampRepository.buildSessionEntity(
                enrollmentId = enrollmentId,
                weekNumber = nextEngine.absoluteWeek,
                dayOfWeek = availableDays.getOrElse(index) { index + 1 }.coerceIn(1, 7),
                sessionType = session.type.name,
                targetMinutes = session.minutes,
                presetId = session.presetId
            )
        }
    }
}
