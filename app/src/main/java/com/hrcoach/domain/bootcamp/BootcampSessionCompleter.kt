package com.hrcoach.domain.bootcamp

import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.data.repository.BootcampRepository
import com.hrcoach.domain.achievement.AchievementEvaluator
import com.hrcoach.domain.engine.TuningDirection
import com.hrcoach.domain.model.BootcampGoal
import javax.inject.Inject

class BootcampSessionCompleter @Inject constructor(
    private val bootcampRepository: BootcampRepository,
    private val achievementEvaluator: AchievementEvaluator
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

        // Status guard: only SCHEDULED or DEFERRED sessions can be completed
        if (targetSession.status != BootcampSessionEntity.STATUS_SCHEDULED &&
            targetSession.status != BootcampSessionEntity.STATUS_DEFERRED) {
            return CompletionResult(completed = false)
        }

        val completedSession = targetSession.copy(
            status = BootcampSessionEntity.STATUS_COMPLETED,
            completedWorkoutId = workoutId,
            completedAtMs = System.currentTimeMillis()
        )

        val simulatedWeek = currentWeekSessions.map { session ->
            if (session.id == completedSession.id) completedSession else session
        }
        val weekComplete = simulatedWeek.isNotEmpty() &&
            simulatedWeek.all {
                it.status == BootcampSessionEntity.STATUS_COMPLETED ||
                it.status == BootcampSessionEntity.STATUS_SKIPPED ||
                it.status == BootcampSessionEntity.STATUS_DEFERRED
            }

        return if (weekComplete) {
            val nextEngine = if (engine.shouldAdvancePhase()) {
                engine.advancePhase()
            } else {
                engine.copy(weekInPhase = engine.weekInPhase + 1)
            }

            if (nextEngine == null) {
                // Race goal completed — graduate the enrollment
                bootcampRepository.completeSessionOnly(completedSession)
                bootcampRepository.graduateEnrollment(enrollment.id)
                return CompletionResult(
                    completed = true,
                    weekComplete = true,
                    progressLabel = "Program complete! You're race-ready."
                )
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
                tuningDirection = tuningDirection,
                completedWeekSessions = simulatedWeek
            )
            bootcampRepository.completeSessionAndAdvanceWeek(
                completedSession = completedSession,
                updatedEnrollment = updatedEnrollment,
                newSessions = nextWeekEntities
            )
            val completedWeeks = bootcampRepository.countConsecutiveCompletedWeeks(enrollment.id)
            achievementEvaluator.evaluateWeeklyGoalStreak(completedWeeks, workoutId)
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
        tuningDirection: TuningDirection,
        completedWeekSessions: List<BootcampSessionEntity> = emptyList()
    ): List<BootcampSessionEntity> {
        val currentPresetIndices = completedWeekSessions
            .filter { it.presetIndex != null }
            .mapNotNull { session ->
                val key = sessionTypePresetKey(session.sessionType) ?: return@mapNotNull null
                key to (session.presetIndex ?: 0)
            }
            .toMap()

        val plannedSessions = nextEngine.planCurrentWeek(
            tierIndex = tierIndex,
            tuningDirection = tuningDirection,
            currentPresetIndices = currentPresetIndices
        )
        if (plannedSessions.isEmpty()) return emptyList()

        val availableDays = preferredDays
            .filter { it.level == DaySelectionLevel.AVAILABLE || it.level == DaySelectionLevel.LONG_RUN_BIAS }
            .map { it.day }
        val longRunBiasDay = preferredDays
            .firstOrNull { it.level == DaySelectionLevel.LONG_RUN_BIAS }
            ?.day

        val assigned = SessionDayAssigner.assign(plannedSessions, availableDays, longRunBiasDay)

        return assigned.map { (session, day) ->
            BootcampRepository.buildSessionEntity(
                enrollmentId = enrollmentId,
                weekNumber = nextEngine.absoluteWeek,
                dayOfWeek = day,
                sessionType = session.type.name,
                targetMinutes = session.minutes,
                presetId = session.presetId
            )
        }
    }

    private fun sessionTypePresetKey(rawType: String): String? = when (rawType) {
        "EASY" -> "easy"
        "TEMPO" -> "tempo"
        "INTERVAL" -> "interval"
        "STRIDES" -> "strides"
        "LONG" -> "long"
        else -> null
    }
}
