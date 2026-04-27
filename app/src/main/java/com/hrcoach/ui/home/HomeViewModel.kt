package com.hrcoach.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.data.firebase.FirebasePartnerRepository
import com.hrcoach.data.repository.BootcampRepository
import com.hrcoach.data.repository.UserProfileRepository
import com.hrcoach.data.repository.WorkoutRepository
import com.hrcoach.domain.model.DistanceUnit
import com.hrcoach.domain.achievement.StreakCalculator
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

data class HomeUiState(
    val greeting: String = "Good morning",
    val lastWorkout: WorkoutEntity? = null,
    val workoutsThisWeek: Int = 0,
    val weeklyTarget: Int = 4,
    val isSessionRunning: Boolean = false,
    val hasActiveBootcamp: Boolean = false,
    val nextSession: BootcampSessionEntity? = null,
    val currentWeekNumber: Int = 1,
    val sessionStreak: Int = 0,
    val isNextSessionToday: Boolean = false,
    val nextSessionDayLabel: String = "",
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
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val bootcampRepository: BootcampRepository,
    private val partnerRepository: FirebasePartnerRepository,
    private val userProfileRepository: UserProfileRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val distanceUnit = DistanceUnit.fromString(userProfileRepository.getDistanceUnit())
    private val maxHr: Int? = userProfileRepository.getMaxHr()
    private val restHr: Int? = null

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            workoutRepository.getAllWorkouts(),
            bootcampRepository.getActiveEnrollment(),
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

            // Only ACTIVE enrollment gets the hero card — PAUSED/GRADUATED fall back to no-bootcamp UI
            val activeEnrollment = enrollment?.takeIf {
                it.status == BootcampEnrollmentEntity.STATUS_ACTIVE
            }

            val bootcampGoal = activeEnrollment?.let {
                runCatching { BootcampGoal.valueOf(it.goalType) }.getOrNull()
            }
            val phaseEngine = bootcampGoal?.let {
                PhaseEngine(
                    goal = it,
                    phaseIndex = activeEnrollment.currentPhaseIndex,
                    weekInPhase = activeEnrollment.currentWeekInPhase,
                    runsPerWeek = activeEnrollment.runsPerWeek,
                    targetMinutes = activeEnrollment.targetMinutesPerRun
                )
            }
            val bootcampTotalWeeks = phaseEngine?.totalWeeks ?: 12
            val displayAbsoluteWeek: Int = phaseEngine?.absoluteWeek ?: 1
            val bootcampPercentComplete = displayAbsoluteWeek.toFloat() / bootcampTotalWeeks

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

            val today = now.toLocalDate()

            // Find next session that is today or in the future (not past-day leftovers).
            // The DAO's getNextSession() returns the earliest SCHEDULED/DEFERRED by
            // weekNumber+dayOfWeek, which may be a past day still marked SCHEDULED.
            // Bootcamp screen filters these out — home screen must match.
            val nextSession: BootcampSessionEntity?
            val nextSessionDate: LocalDate?
            if (activeEnrollment != null) {
                val enrollStartDate = Instant.ofEpochMilli(activeEnrollment.startDate)
                    .atZone(zone).toLocalDate()
                // Anchor to the Monday of the enrollment start week.
                // session.dayOfWeek is ISO (1=Mon, 7=Sun), NOT an offset from enrollStartDate.
                val enrollWeekMonday = enrollStartDate.with(DayOfWeek.MONDAY)
                val allPending = bootcampRepository.getScheduledAndDeferredSessions(activeEnrollment.id)
                val match = allPending.firstOrNull { session ->
                    val sessionDate = enrollWeekMonday
                        .plusWeeks((session.weekNumber - 1).toLong())
                        .plusDays((session.dayOfWeek - 1).toLong())
                    !sessionDate.isBefore(today)
                }
                nextSession = match
                nextSessionDate = match?.let { session ->
                    enrollWeekMonday
                        .plusWeeks((session.weekNumber - 1).toLong())
                        .plusDays((session.dayOfWeek - 1).toLong())
                }
            } else {
                nextSession = null
                nextSessionDate = null
            }

            val isNextSessionToday = nextSessionDate == today
            val nextSessionDayLabel = nextSessionDate?.dayOfWeek
                ?.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
                ?.uppercase()
                ?: ""

            val sessionStreak = if (activeEnrollment != null) {
                val allSessions = bootcampRepository.getSessionsForEnrollmentOnce(activeEnrollment.id)
                StreakCalculator.computeSessionStreak(allSessions, activeEnrollment.startDate)
            } else 0

            val blePrefs = context.getSharedPreferences("ble_prefs", Context.MODE_PRIVATE)
            val sensorName = blePrefs.getString("last_device_name", null)
            val sensorLastSeen = blePrefs.getLong("last_connected_ms", 0L).takeIf { it > 0L }

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
                hasActiveBootcamp = activeEnrollment != null,
                nextSession = nextSession,
                currentWeekNumber = displayAbsoluteWeek,
                sessionStreak = sessionStreak,
                isNextSessionToday = isNextSessionToday,
                nextSessionDayLabel = nextSessionDayLabel,
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
}
