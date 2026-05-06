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

/**
 * Why a candidate day looks the way it does. Single-valued; multi-reason precedence is
 * OCCUPIED > BLACKOUT > RECOVERY_SPACING > FREE (most restrictive wins). OCCUPIED must
 * surface so the disabled chip can explain itself; BLACKOUT is a stronger user signal
 * than RECOVERY_SPACING (a coach heuristic), so it wins when both apply.
 */
enum class SuggestionReason { FREE, OCCUPIED, RECOVERY_SPACING, BLACKOUT }

data class DaySuggestion(
    /** ISO day-of-week, 1=Mon … 7=Sun */
    val dayOfWeek: Int,
    val reason: SuggestionReason,
    /** True if the day is in the user's preferred-days list at AVAILABLE or LONG_RUN_BIAS. */
    val isPreferred: Boolean
)

sealed class RescheduleResult {
    /**
     * The recommended target day, or null when no FREE day exists in the suggestion list
     * (e.g. every future day is OCCUPIED/BLACKOUT/RECOVERY_SPACING). Callers should fall
     * back to letting the user pick a non-FREE chip via the confirm-dialog flow.
     */
    data class Moved(val firstFreeDayOrNull: Int?) : RescheduleResult()
    object Deferred : RescheduleResult()
}

object SessionRescheduler {

    private val hardTypes = setOf("TEMPO", "INTERVAL", "INTERVALS", "LONG", "RACE_SIM")

    /**
     * Returns one suggestion per future day in the current week (today through Sunday),
     * excluding the session's own day. Sorted with FREE first, then by ascending dayOfWeek
     * within each reason group.
     */
    fun suggestions(req: RescheduleRequest): List<DaySuggestion> {
        val prefs = req.enrollment.preferredDays
        val hardDaysOtherThanThis = req.allSessionsThisWeek
            .filter { it.sessionType in hardTypes && it.dayOfWeek != req.session.dayOfWeek }
            .map { it.dayOfWeek }
            .toSet()

        val raw = (req.todayDayOfWeek..7)
            .filter { it != req.session.dayOfWeek }
            .map { candidate ->
                val pref = prefs.find { it.day == candidate }
                val isPreferred = pref?.level == DaySelectionLevel.AVAILABLE ||
                    pref?.level == DaySelectionLevel.LONG_RUN_BIAS
                val isOccupied = candidate in req.occupiedDaysThisWeek
                val isBlackout = pref?.level == DaySelectionLevel.BLACKOUT
                val violatesRecovery =
                    hardDaysOtherThanThis.any { kotlin.math.abs(it - candidate) < 2 }

                // Precedence: OCCUPIED > BLACKOUT > RECOVERY_SPACING > FREE.
                val reason = when {
                    isOccupied      -> SuggestionReason.OCCUPIED
                    isBlackout      -> SuggestionReason.BLACKOUT
                    violatesRecovery -> SuggestionReason.RECOVERY_SPACING
                    else            -> SuggestionReason.FREE
                }
                DaySuggestion(candidate, reason, isPreferred)
            }

        // Sort: FREE first, then RECOVERY_SPACING, BLACKOUT, OCCUPIED. Within FREE,
        // preferred-FREE comes before non-preferred-FREE so the recommendation lands on
        // a day the user actually planned to run on. Within each tier, ascending dayOfWeek.
        return raw.sortedWith(
            compareBy<DaySuggestion> {
                when (it.reason) {
                    SuggestionReason.FREE             -> 0
                    SuggestionReason.RECOVERY_SPACING -> 1
                    SuggestionReason.BLACKOUT         -> 2
                    SuggestionReason.OCCUPIED         -> 3
                }
            }
                .thenBy { if (it.reason == SuggestionReason.FREE && it.isPreferred) 0 else 1 }
                .thenBy { it.dayOfWeek }
        )
    }

    fun reschedule(req: RescheduleRequest): RescheduleResult {
        val firstFree = suggestions(req).firstOrNull { it.reason == SuggestionReason.FREE }
        return RescheduleResult.Moved(firstFree?.dayOfWeek)
    }

    fun defer(): RescheduleResult = RescheduleResult.Deferred
}
