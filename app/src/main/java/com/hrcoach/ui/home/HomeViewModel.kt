package com.hrcoach.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.data.firebase.FirebasePartnerRepository
import com.hrcoach.data.repository.BootcampRepository
import com.hrcoach.data.repository.UserProfileRepository
import com.hrcoach.data.repository.WorkoutRepository
import com.hrcoach.domain.model.DistanceUnit
import com.hrcoach.domain.achievement.StreakCalculator
import com.hrcoach.domain.bootcamp.CalendarDriftRecoverer
import com.hrcoach.domain.bootcamp.PhaseEngine
import com.hrcoach.domain.coaching.CoachingInsight
import com.hrcoach.domain.coaching.CoachingInsightEngine
import com.hrcoach.domain.model.BootcampGoal
import com.hrcoach.service.WorkoutState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Lifecycle-aware bootcamp state surfaced on the Home screen.
 *
 * - [None]        — user has never enrolled (or the row was deleted).
 * - [Active]      — `STATUS_ACTIVE` enrollment; mirrors prior "next session" hero.
 * - [Paused]      — `STATUS_PAUSED`; renders a "resume" hero with progress context.
 * - [Graduated]   — `STATUS_GRADUATED`; triumphant summary with weeks/sessions/total km.
 *
 * EVERGREEN goals (CARDIO_HEALTH) should never reach `STATUS_GRADUATED`; the VM defensively
 * promotes such rows back to [Active] (or [None] if no remaining sessions).
 */
sealed interface HomeBootcampState {
    data object None : HomeBootcampState

    data class Active(
        val enrollment: BootcampEnrollmentEntity,
        val nextSession: BootcampSessionEntity?,
        val weekNumber: Int,
        val isToday: Boolean,
        val dayLabel: String,
    ) : HomeBootcampState

    data class Paused(
        val enrollment: BootcampEnrollmentEntity,
        val sessionsDone: Int,
        val sessionsTotal: Int,
        val pausedAtWeek: Int,
        val totalWeeks: Int,
    ) : HomeBootcampState

    data class Graduated(
        val enrollment: BootcampEnrollmentEntity,
        val weeksCompleted: Int,
        val sessionsCompleted: Int,
        /** Total distance for this enrollment's completed workouts, in **kilometres**. */
        val totalKm: Double,
    ) : HomeBootcampState
}

data class HomeUiState(
    val greeting: String = "Good morning",
    val lastWorkout: WorkoutEntity? = null,
    val workoutsThisWeek: Int = 0,
    val weeklyTarget: Int = 4,
    val isSessionRunning: Boolean = false,
    val bootcampState: HomeBootcampState = HomeBootcampState.None,
    val sessionStreak: Int = 0,
    val sensorName: String? = null,
    val sensorLastSeenMs: Long? = null,
    val totalDistanceThisWeekMeters: Double = 0.0,
    val totalTimeThisWeekMinutes: Long = 0,
    val weeklyDistanceTargetKm: Double = 15.0,
    val weeklyTimeTargetMinutes: Long = 90,
    val bootcampTotalWeeks: Int = 12,
    val bootcampPercentComplete: Float = 0f,
    val coachingInsight: CoachingInsight? = null,
    val nudgeBanner: NudgeBannerState? = null,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val maxHr: Int? = null,
    val restHr: Int? = null,
) {
    /**
     * Backwards-compat shim. New call sites should branch on [bootcampState] directly; this
     * exists so [HomeScreen] (owned by another agent) and existing tests continue to compile
     * while the data layer migrates. Returns true only for [HomeBootcampState.Active].
     */
    val hasActiveBootcamp: Boolean
        get() = bootcampState is HomeBootcampState.Active

    /** Convenience accessor for the active hero's next session. */
    val nextSession: BootcampSessionEntity?
        get() = (bootcampState as? HomeBootcampState.Active)?.nextSession

    /** Absolute week index, or 1 when no active enrollment. */
    val currentWeekNumber: Int
        get() = (bootcampState as? HomeBootcampState.Active)?.weekNumber ?: 1

    val isNextSessionToday: Boolean
        get() = (bootcampState as? HomeBootcampState.Active)?.isToday ?: false

    val nextSessionDayLabel: String
        get() = (bootcampState as? HomeBootcampState.Active)?.dayLabel ?: ""
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val bootcampRepository: BootcampRepository,
    private val partnerRepository: FirebasePartnerRepository,
    private val userProfileRepository: UserProfileRepository,
    private val calendarDriftRecoverer: CalendarDriftRecoverer,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val distanceUnit = DistanceUnit.fromString(userProfileRepository.getDistanceUnit())
    private val maxHr: Int? = userProfileRepository.getMaxHr()
    private val restHr: Int? = null

    /**
     * Cache of computed graduate stats keyed by enrollment ID. Stats are expensive (DB joins,
     * session counts) and never change once an enrollment is graduated, so we memoize per-id
     * to avoid recomputing on every Home recomposition / Flow emission.
     */
    private val graduatedStatsCache: MutableMap<Long, HomeBootcampState.Graduated> = mutableMapOf()

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            workoutRepository.getAllWorkouts(),
            bootcampRepository.getLatestEnrollmentAnyStatus(),
            WorkoutState.snapshot.map { it.isRunning }.distinctUntilChanged(),
        ) { workouts, enrollment, isRunning -> Triple(workouts, enrollment, isRunning) },
        partnerRepository.observePartners(),
    ) { triple, partners -> triple to partners }
    .flatMapLatest { (triple, partners) ->
        val (workouts, enrollment, isRunning) = triple
        flow {
            val zone = ZoneId.systemDefault()
            val now = Instant.now().atZone(zone)
            val weekStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

            val thisWeekWorkouts = workouts.filter { it.startTime >= weekStart }
            val totalDistanceM = thisWeekWorkouts.sumOf { it.totalDistanceMeters.toDouble() }
            val totalTimeMin = thisWeekWorkouts.sumOf {
                ((it.endTime - it.startTime) / 60_000L).coerceAtLeast(0)
            }

            val greeting = when (now.hour) {
                in 0..11  -> "Good morning"
                in 12..17 -> "Good afternoon"
                else      -> "Good evening"
            }

            val bootcampGoal = enrollment?.let {
                runCatching { BootcampGoal.valueOf(it.goalType) }.getOrNull()
            }
            val initialPhaseEngine = bootcampGoal?.let {
                PhaseEngine(
                    goal = it,
                    phaseIndex = enrollment.currentPhaseIndex,
                    weekInPhase = enrollment.currentWeekInPhase,
                    runsPerWeek = enrollment.runsPerWeek,
                    targetMinutes = enrollment.targetMinutesPerRun
                )
            }

            // Calendar drift recovery — mirror BootcampViewModel so Home and Training
            // stay in lockstep. Idempotent; on a fresh week or with no completions,
            // returns NoChange and the original enrollment/engine flow through.
            val recoveryOutcome = if (
                enrollment != null &&
                initialPhaseEngine != null &&
                enrollment.status == BootcampEnrollmentEntity.STATUS_ACTIVE
            ) {
                val workoutSnapshot = WorkoutState.snapshot.value
                calendarDriftRecoverer.recover(
                    enrollment = enrollment,
                    engine = initialPhaseEngine,
                    today = LocalDate.now(zone),
                    zone = zone,
                    isWorkoutActive = workoutSnapshot.isRunning,
                    pendingSessionId = workoutSnapshot.pendingBootcampSessionId
                )
            } else null
            val recoveredEnrollment = (recoveryOutcome as? CalendarDriftRecoverer.Outcome.Recovered)
                ?.finalEnrollment
            val phaseEngine = (recoveryOutcome as? CalendarDriftRecoverer.Outcome.Recovered)
                ?.finalEngine ?: initialPhaseEngine
            // Shadow `enrollment` with the recovered version for downstream reads.
            @Suppress("NAME_SHADOWING")
            val enrollment = recoveredEnrollment ?: enrollment

            val bootcampTotalWeeks = phaseEngine?.totalWeeks ?: 12
            val displayAbsoluteWeek: Int = phaseEngine?.absoluteWeek ?: 1
            val bootcampPercentComplete = displayAbsoluteWeek.toFloat() / bootcampTotalWeeks

            // Derive the lifecycle hero state. EVERGREEN/CARDIO_HEALTH should never be GRADUATED;
            // if the data anomaly exists we treat as Active (with whatever sessions remain) or
            // None when the row is unusable.
            val bootcampState: HomeBootcampState = when {
                enrollment == null -> HomeBootcampState.None
                enrollment.status == BootcampEnrollmentEntity.STATUS_GRADUATED &&
                    bootcampGoal == BootcampGoal.CARDIO_HEALTH -> {
                    // Defensive: EVERGREEN never graduates. Promote back to Active.
                    buildActiveState(enrollment, displayAbsoluteWeek, today = now.toLocalDate(), zone = zone)
                }
                enrollment.status == BootcampEnrollmentEntity.STATUS_PAUSED -> {
                    val done = bootcampRepository.getCompletedSessionCount(enrollment.id)
                    val total = bootcampRepository.getTotalSessionCount(enrollment.id)
                    HomeBootcampState.Paused(
                        enrollment = enrollment,
                        sessionsDone = done,
                        sessionsTotal = total,
                        pausedAtWeek = displayAbsoluteWeek,
                        totalWeeks = bootcampTotalWeeks,
                    )
                }
                enrollment.status == BootcampEnrollmentEntity.STATUS_GRADUATED -> {
                    graduatedStatsCache.getOrPut(enrollment.id) {
                        val sessionsCompleted = bootcampRepository.getCompletedSessionCount(enrollment.id)
                        val totalMeters = bootcampRepository.sumCompletedWorkoutDistanceMeters(enrollment.id)
                        val weeksCompleted = bootcampRepository
                            .countConsecutiveCompletedWeeks(enrollment.id)
                            .takeIf { it > 0 }
                            ?: bootcampTotalWeeks
                        HomeBootcampState.Graduated(
                            enrollment = enrollment,
                            weeksCompleted = weeksCompleted,
                            sessionsCompleted = sessionsCompleted,
                            totalKm = totalMeters / 1000.0,
                        )
                    }
                }
                enrollment.status == BootcampEnrollmentEntity.STATUS_ACTIVE -> {
                    buildActiveState(enrollment, displayAbsoluteWeek, today = now.toLocalDate(), zone = zone)
                }
                else -> HomeBootcampState.None
            }

            val activeEnrollment = enrollment?.takeIf { bootcampState is HomeBootcampState.Active }

            // Weekly run count uses bootcamp_sessions (not WorkoutEntity rows) when
            // enrolled, so SKIPPED sessions still count toward the week.
            val thisWeek: Int = if (activeEnrollment != null) {
                bootcampRepository.getSessionsForWeek(activeEnrollment.id, displayAbsoluteWeek)
                    .count {
                        it.status == BootcampSessionEntity.STATUS_COMPLETED ||
                        it.status == BootcampSessionEntity.STATUS_SKIPPED
                    }
            } else {
                workouts.count { it.startTime >= weekStart }
            }

            val weeklyDistanceTargetKm = if (activeEnrollment != null) {
                activeEnrollment.targetMinutesPerRun * activeEnrollment.runsPerWeek * 0.15
            } else 15.0

            val weeklyTimeTargetMin = if (activeEnrollment != null) {
                (activeEnrollment.targetMinutesPerRun * activeEnrollment.runsPerWeek).toLong()
            } else 90L

            val coachingInsight = CoachingInsightEngine.generate(
                workouts = workouts,
                workoutsThisWeek = thisWeek,
                weeklyTarget = activeEnrollment?.runsPerWeek ?: 4,
                hasBootcamp = activeEnrollment != null,
                nowMs = System.currentTimeMillis()
            )

            val sessionStreak = if (activeEnrollment != null) {
                val allSessions = bootcampRepository.getSessionsForEnrollmentOnce(activeEnrollment.id)
                StreakCalculator.computeSessionStreak(allSessions, activeEnrollment.startDate)
            } else 0

            val blePrefs = context.getSharedPreferences("ble_prefs", Context.MODE_PRIVATE)
            val sensorName = blePrefs.getString("last_device_name", null)
            val sensorLastSeen = blePrefs.getLong("last_connected_ms", 0L).takeIf { it > 0L }

            val today = now.toLocalDate()
            val userRanToday = workouts.any { workout ->
                val workoutDate = Instant.ofEpochMilli(workout.startTime).atZone(zone).toLocalDate()
                workoutDate == today
            }
            val nudgeBanner = computeNudgeBanner(
                partners = partners,
                userRanToday = userRanToday,
            )

            emit(HomeUiState(
                greeting = greeting,
                lastWorkout = workouts.firstOrNull(),
                workoutsThisWeek = thisWeek,
                weeklyTarget = activeEnrollment?.runsPerWeek ?: 4,
                isSessionRunning = isRunning,
                bootcampState = bootcampState,
                sessionStreak = sessionStreak,
                sensorName = sensorName,
                sensorLastSeenMs = sensorLastSeen,
                totalDistanceThisWeekMeters = totalDistanceM,
                totalTimeThisWeekMinutes = totalTimeMin,
                weeklyDistanceTargetKm = weeklyDistanceTargetKm,
                weeklyTimeTargetMinutes = weeklyTimeTargetMin,
                bootcampTotalWeeks = bootcampTotalWeeks,
                bootcampPercentComplete = bootcampPercentComplete,
                coachingInsight = coachingInsight,
                nudgeBanner = nudgeBanner,
                distanceUnit = distanceUnit,
                maxHr = maxHr,
                restHr = restHr,
            ))
        }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /**
     * Builds the [HomeBootcampState.Active] hero payload.
     *
     * Honours the bootcamp-scheduling rule: session date is computed from
     * `enrollStartDate.with(MONDAY).plusWeeks(weekNumber-1).plusDays(dayOfWeek-1)` and we filter
     * SCHEDULED+DEFERRED entries against today, NOT relying on `getNextSession()` (which can
     * return past-day SCHEDULED rows).
     */
    private suspend fun buildActiveState(
        enrollment: BootcampEnrollmentEntity,
        displayAbsoluteWeek: Int,
        today: LocalDate,
        zone: ZoneId,
    ): HomeBootcampState.Active {
        val enrollStartDate = Instant.ofEpochMilli(enrollment.startDate)
            .atZone(zone).toLocalDate()
        val enrollWeekMonday = enrollStartDate.with(DayOfWeek.MONDAY)
        val allPending = bootcampRepository.getScheduledAndDeferredSessions(enrollment.id)
        val match = allPending.firstOrNull { session ->
            val sessionDate = enrollWeekMonday
                .plusWeeks((session.weekNumber - 1).toLong())
                .plusDays((session.dayOfWeek - 1).toLong())
            !sessionDate.isBefore(today)
        }
        val nextSessionDate = match?.let { session ->
            enrollWeekMonday
                .plusWeeks((session.weekNumber - 1).toLong())
                .plusDays((session.dayOfWeek - 1).toLong())
        }
        val isToday = nextSessionDate == today
        val dayLabel = nextSessionDate?.dayOfWeek
            ?.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
            ?.uppercase()
            ?: ""
        return HomeBootcampState.Active(
            enrollment = enrollment,
            nextSession = match,
            weekNumber = displayAbsoluteWeek,
            isToday = isToday,
            dayLabel = dayLabel,
        )
    }

    /**
     * Resume a paused bootcamp from the Home Resume hero. No-op when current state is not
     * [HomeBootcampState.Paused] (defensive: prevents accidental status flips if the user double-taps
     * a stale UI). The Flow re-emits naturally once the repository update completes.
     */
    fun resumeBootcamp() {
        val state = uiState.value.bootcampState
        if (state is HomeBootcampState.Paused) {
            viewModelScope.launch {
                bootcampRepository.resumeEnrollment(state.enrollment.id)
            }
        }
    }
}
