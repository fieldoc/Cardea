package com.hrcoach.domain.bootcamp

import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.data.repository.BootcampRepository
import com.hrcoach.domain.engine.TuningDirection

/**
 * Builds the [BootcampSessionEntity] list for [nextEngine]'s week, given the user's
 * preferred days and the just-completed week's sessions (used to carry forward preset
 * indices). Shared by [BootcampSessionCompleter] (week rollover via completion) and
 * [CalendarDriftRecoverer] (week rollover via calendar drift).
 *
 * Returns an empty list when the engine plans no sessions for the next week
 * (e.g. graduation-adjacent).
 */
internal object BootcampWeekSeeder {

    fun seed(
        enrollmentId: Long,
        nextEngine: PhaseEngine,
        preferredDays: List<DayPreference>,
        tierIndex: Int,
        tuningDirection: TuningDirection,
        completedWeekSessions: List<BootcampSessionEntity>,
        lastTierChangeWeek: Int?
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
            currentPresetIndices = currentPresetIndices,
            lastTierChangeWeek = lastTierChangeWeek
        )
        if (plannedSessions.isEmpty()) return emptyList()

        val availableDays = preferredDays
            .filter {
                it.level == DaySelectionLevel.AVAILABLE ||
                    it.level == DaySelectionLevel.LONG_RUN_BIAS
            }
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
