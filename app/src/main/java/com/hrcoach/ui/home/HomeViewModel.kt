package com.hrcoach.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.data.repository.BootcampRepository
import com.hrcoach.data.repository.WorkoutRepository
import com.hrcoach.domain.achievement.StreakCalculator
import com.hrcoach.domain.bootcamp.PhaseEngine
import com.hrcoach.domain.coaching.CoachingInsight
import com.hrcoach.domain.coaching.CoachingInsightEngine
import com.hrcoach.domain.model.BootcampGoal
import com.hrcoach.data.firebase.PartnerRepository
import com.hrcoach.domain.sharing.DayState
import com.hrcoach.domain.sharing.WeekStripState
import com.hrcoach.service.WorkoutState
import com.hrcoach.ui.partner.PartnerCardState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import android.content.Context
import androidx.compose.ui.graphics.Color
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
    val partnerState: PartnerCardState? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val bootcampRepository: BootcampRepository,
    private val partnerRepository: PartnerRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        workoutRepository.getAllWorkouts(),
        bootcampRepository.getActiveEnrollment(),
        WorkoutState.snapshot.map { it.isRunning }.distinctUntilChanged(),
        partnerRepository.observePartnerId()
    ) { workouts, enrollment, isRunning, partnerId ->
        arrayOf(workouts, enrollment, isRunning, partnerId)
    }
    .flatMapLatest { args ->
        @Suppress("UNCHECKED_CAST")
        val workouts = args[0] as List<WorkoutEntity>
        val enrollment = args[1] as BootcampEnrollmentEntity?
        val isRunning = args[2] as Boolean
        val partnerId = args[3] as String?
        flow {
            val zone = ZoneId.systemDefault()
            val now = Instant.now().atZone(zone)
            val weekStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            val thisWeek = workouts.count { it.startTime >= weekStart }

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
            val currentAbsoluteWeek = phaseEngine?.absoluteWeek ?: 1
            val bootcampPercentComplete = currentAbsoluteWeek.toFloat() / bootcampTotalWeeks

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

            val nextSession = activeEnrollment?.let {
                bootcampRepository.getNextSession(it.id)
            }

            val today = now.toLocalDate()
            val nextSessionDate: LocalDate? = if (activeEnrollment != null && nextSession != null) {
                val enrollStartDate = Instant.ofEpochMilli(activeEnrollment.startDate)
                    .atZone(zone).toLocalDate()
                val daysOffset = ((nextSession.weekNumber - 1) * 7L) + (nextSession.dayOfWeek - 1)
                enrollStartDate.plusDays(daysOffset)
            } else null

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

            // Partner state (if paired)
            val partnerCardState = if (partnerId != null) {
                try {
                    val info = partnerRepository.getPartnerInfo(partnerId)
                    val completions = partnerRepository.getPartnerCompletions(partnerId)
                    val todayDow = LocalDate.now().dayOfWeek.value
                    val dayStates = WeekStripState.compute(completions, todayDow)
                    val todayCompletion = completions.firstOrNull { it.weekDay == todayDow }
                    val statusText = if (todayCompletion != null) {
                        "Ran today \u00B7 %.1f km".format(todayCompletion.distanceMeters / 1000.0)
                    } else "Rest day"
                    val statusColor = if (todayCompletion != null) Color(0xFF4ADE80)
                    else Color.White.copy(alpha = 0.4f)
                    PartnerCardState(
                        displayName = info?.displayName ?: "Runner",
                        avatarSymbol = info?.avatarSymbol ?: "\u2665",
                        statusText = statusText,
                        statusColor = statusColor,
                        streakCount = completions.firstOrNull()?.streakCount ?: 0,
                        dayStates = dayStates,
                        programPhase = completions.firstOrNull()?.programPhase,
                        latestCompletionId = null
                    )
                } catch (_: Exception) { null }
            } else null

            emit(HomeUiState(
                greeting = greeting,
                lastWorkout = workouts.firstOrNull(),
                workoutsThisWeek = thisWeek,
                weeklyTarget = activeEnrollment?.runsPerWeek ?: 4,
                isSessionRunning = isRunning,
                hasActiveBootcamp = activeEnrollment != null,
                nextSession = nextSession,
                currentWeekNumber = currentAbsoluteWeek,
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
                partnerState = partnerCardState,
            ))
        }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun refreshPartnerData() {
        viewModelScope.launch {
            val pid = partnerRepository.observePartnerId().first() ?: return@launch
            // The combine flow will re-emit since observePartnerId is a snapshot listener.
            // This method just ensures we re-read completions from Firestore.
            try {
                val info = partnerRepository.getPartnerInfo(pid)
                val completions = partnerRepository.getPartnerCompletions(pid)
                val todayDow = LocalDate.now().dayOfWeek.value
                val dayStates = WeekStripState.compute(completions, todayDow)
                val todayCompletion = completions.firstOrNull { it.weekDay == todayDow }
                val statusText = if (todayCompletion != null) {
                    "Ran today \u00B7 %.1f km".format(todayCompletion.distanceMeters / 1000.0)
                } else "Rest day"
                val statusColor = if (todayCompletion != null) Color(0xFF4ADE80)
                else Color.White.copy(alpha = 0.4f)
                // No direct state mutation — the Firestore snapshot listener on partnerId
                // triggers the combine pipeline. This call refreshes the server-side cache.
            } catch (_: Exception) { /* silent */ }
        }
    }
}
