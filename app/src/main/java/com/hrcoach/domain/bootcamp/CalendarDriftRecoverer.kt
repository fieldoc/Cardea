package com.hrcoach.domain.bootcamp

import android.util.Log
import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.data.repository.BootcampRepository
import com.hrcoach.domain.engine.TuningDirection
import com.hrcoach.domain.model.BootcampGoal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Self-heals the case where the bootcamp engine falls behind the calendar.
 *
 * Triggered on app startup (BootcampViewModel + HomeViewModel) when the engine is
 * stuck on a week whose latest completion is in a strictly earlier calendar week
 * than today. Without this, the bootcamp strip paints last-week's session entities
 * onto today's calendar date band, making completed runs from a previous calendar
 * week look like checks against today's strip.
 *
 * **Data-driven trigger.** A formula like `enrollStartDate + (weekNumber-1)*7d`
 * does not reliably map weekNumber → calendar week: pre-enrollment sessions, the
 * user pulling sessions forward via Reschedule, or schedule manipulation can offset
 * weekNumber from calendar weeks. Instead we compare today's `with(MONDAY)` against
 * the engine-week's latest COMPLETED session's `with(MONDAY)`. If today's calendar
 * week is strictly later, the engine has fallen behind — fire.
 *
 * Action when stuck: SKIP residual SCHEDULED/DEFERRED sessions of the engine-week
 * (timestamped to the latest completion's calendar Sunday 23:59 — keeps
 * StreakCalculator honest), advance the engine, seed the next week. Loops for
 * multi-week drift; bounded by [LOOP_GUARD_MAX_ADVANCES].
 *
 * No-op when:
 *  - A workout is in flight ([isWorkoutActive] or [pendingSessionId] non-null) —
 *    avoids racing [BootcampSessionCompleter.complete] which rejects non-SCHEDULED
 *    targets and would silently drop the user's bootcamp attribution.
 *  - The engine-week has zero completions — that's GapAdvisor's territory (rewind),
 *    not ours (advance).
 *  - Today's calendar week is the same as (or earlier than) the latest completion's
 *    calendar week — engine is in sync (or behind, but the user just completed
 *    something so let it ride).
 *  - Advancing would graduate the user — leave it stuck so the normal completion
 *    path handles graduation cleanly with all its side effects (achievements,
 *    cloud backup of the graduated enrollment, etc.).
 *
 * Idempotent: a second invocation immediately after a successful recovery sees
 * the freshly-seeded next-week sessions (no completions yet), the "no completions"
 * guard fires, and Outcome.NoChange is returned.
 *
 * Pure of [com.hrcoach.service.WorkoutState] — callers read the workout state
 * and pass [isWorkoutActive] / [pendingSessionId] in. Keeps this class testable
 * without bringing the foreground service into unit-test scope.
 */
@Singleton
class CalendarDriftRecoverer @Inject constructor(
    private val bootcampRepository: BootcampRepository
) {

    sealed class Outcome {
        object NoChange : Outcome()
        data class Recovered(
            val finalEngine: PhaseEngine,
            val finalEnrollment: BootcampEnrollmentEntity,
            val weeksAdvanced: Int
        ) : Outcome()
    }

    suspend fun recover(
        enrollment: BootcampEnrollmentEntity,
        engine: PhaseEngine,
        today: LocalDate,
        zone: ZoneId,
        isWorkoutActive: Boolean,
        pendingSessionId: Long?
    ): Outcome {
        // Race gate — see class doc.
        if (isWorkoutActive || pendingSessionId != null) return Outcome.NoChange

        runCatching { BootcampGoal.valueOf(enrollment.goalType) }.getOrNull()
            ?: return Outcome.NoChange

        val todayMonday = today.with(DayOfWeek.MONDAY)

        var currentEngine = engine
        var currentEnrollment = enrollment
        var weeksAdvanced = 0

        while (weeksAdvanced < LOOP_GUARD_MAX_ADVANCES) {
            val sessions = bootcampRepository.getSessionsForWeek(
                currentEnrollment.id, currentEngine.absoluteWeek
            )
            if (sessions.isEmpty()) break

            // Find this engine-week's latest completion. Used both to detect drift
            // and to past-date the auto-skip timestamp.
            val latestCompletionMs = sessions
                .filter { it.status == BootcampSessionEntity.STATUS_COMPLETED }
                .mapNotNull { it.completedAtMs }
                .maxOrNull() ?: break // no completions → GapAdvisor's territory

            val latestCompletionDate = Instant.ofEpochMilli(latestCompletionMs)
                .atZone(zone)
                .toLocalDate()
            val latestCompletionMonday = latestCompletionDate.with(DayOfWeek.MONDAY)

            // Drift trigger: today's calendar week is strictly past the latest
            // completion's calendar week. Same-week is "still in sync."
            if (!todayMonday.isAfter(latestCompletionMonday)) break

            // Determine what advancing would look like. If null → graduation:
            // bail without writing anything, let normal completion path graduate.
            val nextEngine = currentEngine.advance() ?: break

            // Skip residuals, timestamped to the end of the latest completion's
            // calendar week (Sunday 23:59 local). Keeps the SKIP semantically
            // anchored to the week it belongs to, not to "now".
            val latestCompletionSunday = latestCompletionMonday.plusDays(6)
            val skipTimestampMs = latestCompletionSunday
                .atTime(23, 59)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
            val residuals = sessions.filter {
                it.status == BootcampSessionEntity.STATUS_SCHEDULED ||
                    it.status == BootcampSessionEntity.STATUS_DEFERRED
            }
            for (r in residuals) {
                bootcampRepository.autoSkipSession(r.id, skipTimestampMs)
            }

            // Re-read the just-skipped week so preset-index carry-forward sees the
            // final state (mirrors BootcampSessionCompleter's pattern of building
            // simulatedWeek before seeding).
            val completedWeekSnapshot = bootcampRepository.getSessionsForWeek(
                currentEnrollment.id, currentEngine.absoluteWeek
            )

            val nextWeekEntities = BootcampWeekSeeder.seed(
                enrollmentId = currentEnrollment.id,
                nextEngine = nextEngine,
                preferredDays = currentEnrollment.preferredDays,
                tierIndex = currentEnrollment.tierIndex,
                tuningDirection = TuningDirection.HOLD,
                completedWeekSessions = completedWeekSnapshot,
                lastTierChangeWeek = currentEnrollment.lastTierChangeWeek
            )

            val updatedEnrollment = currentEnrollment.copy(
                currentPhaseIndex = nextEngine.phaseIndex,
                currentWeekInPhase = nextEngine.weekInPhase
            )
            bootcampRepository.updateEnrollment(updatedEnrollment)
            if (nextWeekEntities.isNotEmpty()) {
                bootcampRepository.insertSessions(nextWeekEntities)
            }

            currentEngine = nextEngine
            currentEnrollment = updatedEnrollment
            weeksAdvanced++
        }

        if (weeksAdvanced >= LOOP_GUARD_MAX_ADVANCES) {
            Log.w(TAG, "Loop guard tripped after $LOOP_GUARD_MAX_ADVANCES advances; bailing")
        }

        return if (weeksAdvanced > 0) {
            Outcome.Recovered(currentEngine, currentEnrollment, weeksAdvanced)
        } else {
            Outcome.NoChange
        }
    }

    companion object {
        private const val TAG = "CalendarDriftRecoverer"
        private const val LOOP_GUARD_MAX_ADVANCES = 12
    }
}
