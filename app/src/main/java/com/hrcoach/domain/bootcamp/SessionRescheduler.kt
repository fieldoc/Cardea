package com.hrcoach.domain.bootcamp

import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity

data class RescheduleRequest(
    val session: BootcampSessionEntity,
    val enrollment: BootcampEnrollmentEntity,
    /** ISO day-of-week: 1=Mon ... 7=Sun */
    val todayDayOfWeek: Int,
    val occupiedDaysThisWeek: Set<Int>,
    val allSessionsThisWeek: List<BootcampSessionEntity>
)

sealed class RescheduleResult {
    data class Moved(val newDayOfWeek: Int) : RescheduleResult()
    data class Dropped(val droppedSessionId: Long) : RescheduleResult()
    object Deferred : RescheduleResult()
}

object SessionRescheduler {

    private val hardTypes = setOf("TEMPO", "INTERVAL", "INTERVALS")

    fun reschedule(req: RescheduleRequest): RescheduleResult {
        val validDays = availableDays(req)
        if (validDays.isNotEmpty()) return RescheduleResult.Moved(validDays.first())
        val toDrop = lowestPrioritySession(req.allSessionsThisWeek, req.session)
        return RescheduleResult.Dropped(toDrop.id)
    }

    fun defer(): RescheduleResult = RescheduleResult.Deferred

    fun availableDays(req: RescheduleRequest): List<Int> {
        val prefs = req.enrollment.preferredDays
        val hardDaysOtherThanThis = req.allSessionsThisWeek
            .filter { it.sessionType in hardTypes && it.dayOfWeek != req.session.dayOfWeek }
            .map { it.dayOfWeek }
            .toSet()

        return (req.todayDayOfWeek..7).filter { candidate ->
            // Never "reschedule" to the session's own day (no-op move)
            if (candidate == req.session.dayOfWeek) return@filter false
            val pref = prefs.find { it.day == candidate }
            val isRunnable = pref != null &&
                pref.level != DaySelectionLevel.NONE &&
                pref.level != DaySelectionLevel.BLACKOUT
            val isOccupied = candidate in req.occupiedDaysThisWeek
            val violatesRecovery = hardDaysOtherThanThis.any { kotlin.math.abs(it - candidate) < 2 }
            isRunnable && !isOccupied && !violatesRecovery
        }
    }

    private fun lowestPrioritySession(
        sessions: List<BootcampSessionEntity>,
        current: BootcampSessionEntity
    ): BootcampSessionEntity {
        val candidates = sessions.filter {
            it.id != current.id && it.status == BootcampSessionEntity.STATUS_SCHEDULED
        }
        return candidates
            .minByOrNull { dropPriority(it.sessionType) }
            ?: current
    }

    /** Lower number = drop first */
    private fun dropPriority(type: String): Int = when (type) {
        "EASY"      -> 0
        "TEMPO"     -> 1
        "INTERVAL", "INTERVALS" -> 2
        "LONG_RUN"  -> 3
        else        -> 1
    }
}
