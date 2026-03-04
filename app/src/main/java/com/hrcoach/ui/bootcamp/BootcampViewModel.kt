package com.hrcoach.ui.bootcamp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.data.repository.AdaptiveProfileRepository
import com.hrcoach.data.repository.BootcampRepository
import com.hrcoach.data.repository.UserProfileRepository
import com.hrcoach.data.repository.WorkoutMetricsRepository
import com.hrcoach.domain.bootcamp.FitnessEvaluator
import com.hrcoach.domain.bootcamp.GapAdvisor
import com.hrcoach.domain.bootcamp.PhaseEngine
import com.hrcoach.domain.model.BootcampGoal
import com.hrcoach.domain.model.WorkoutAdaptiveMetrics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class BootcampViewModel @Inject constructor(
    private val bootcampRepository: BootcampRepository,
    private val adaptiveProfileRepository: AdaptiveProfileRepository,
    private val userProfileRepository: UserProfileRepository,
    private val workoutMetricsRepository: WorkoutMetricsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BootcampUiState())
    val uiState: StateFlow<BootcampUiState> = _uiState.asStateFlow()

    init {
        loadBootcampState()
    }

    private fun loadBootcampState() {
        viewModelScope.launch {
            // Apply gap adjustment once at startup (prevents write-inside-collect re-emission)
            val initial = bootcampRepository.getActiveEnrollmentOnce()
            if (initial != null) {
                applyGapAdjustmentIfNeeded(initial)
            }

            // Then observe the Flow for ongoing UI updates
            bootcampRepository.getActiveEnrollment().collect { enrollment ->
                if (enrollment == null) {
                    _uiState.value = BootcampUiState(isLoading = false, hasActiveEnrollment = false)
                } else {
                    refreshFromEnrollment(enrollment)
                }
            }
        }
    }

    private suspend fun applyGapAdjustmentIfNeeded(enrollment: BootcampEnrollmentEntity) {
        val lastSession = bootcampRepository.getLastCompletedSession(enrollment.id)
        val daysSinceLastRun = computeDaysSinceLastRun(enrollment, lastSession)
        val gapStrategy = GapAdvisor.assess(daysSinceLastRun)
        val gapAction = GapAdvisor.action(gapStrategy, enrollment.currentPhaseIndex, enrollment.currentWeekInPhase)
        if (gapAction.phaseIndex != enrollment.currentPhaseIndex || gapAction.weekInPhase != enrollment.currentWeekInPhase) {
            bootcampRepository.updateEnrollment(
                enrollment.copy(
                    currentPhaseIndex = gapAction.phaseIndex,
                    currentWeekInPhase = gapAction.weekInPhase
                )
            )
        }
    }

    private suspend fun refreshFromEnrollment(enrollment: BootcampEnrollmentEntity) {
        val goal = BootcampGoal.valueOf(enrollment.goalType)
        val profile = adaptiveProfileRepository.getProfile()
        val fitnessLevel = FitnessEvaluator.assess(profile, emptyList<WorkoutAdaptiveMetrics>())

        val lastSession = bootcampRepository.getLastCompletedSession(enrollment.id)
        val daysSinceLastRun = computeDaysSinceLastRun(enrollment, lastSession)
        val gapStrategy = GapAdvisor.assess(daysSinceLastRun)
        val gapAction = GapAdvisor.action(gapStrategy, enrollment.currentPhaseIndex, enrollment.currentWeekInPhase)

        // Use the already-adjusted phase/week from the entity (gap adjustment was applied at init)
        val engine = PhaseEngine(
            goal = goal,
            phaseIndex = enrollment.currentPhaseIndex,
            weekInPhase = enrollment.currentWeekInPhase,
            runsPerWeek = enrollment.runsPerWeek,
            targetMinutes = enrollment.targetMinutesPerRun
        )

        val weekSessions = engine.planCurrentWeek()
        val today = LocalDate.now().dayOfWeek.value
        val preferredDays = parsePreferredDays(enrollment.preferredDays)

        val sessionItems = weekSessions.mapIndexed { index, session ->
            val dayOfWeek = preferredDays.getOrElse(index) { index + 1 }
            SessionUiItem(
                dayLabel = DayOfWeek.of(dayOfWeek).getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                typeName = session.type.name.lowercase().replaceFirstChar { it.uppercase() },
                minutes = session.minutes,
                isCompleted = false,
                isToday = dayOfWeek == today,
                sessionId = null
            )
        }

        // Find next session: first session with dayOfWeek >= today, fallback to first
        val nextSessionIndex = sessionItems.indices.firstOrNull { index ->
            val dayOfWeek = preferredDays.getOrElse(index) { index + 1 }
            dayOfWeek >= today
        } ?: 0

        val nextSessionItem = sessionItems.getOrNull(nextSessionIndex)

        _uiState.value = BootcampUiState(
            isLoading = false,
            hasActiveEnrollment = true,
            goal = goal,
            currentPhase = engine.currentPhase,
            absoluteWeek = engine.absoluteWeek,
            totalWeeks = engine.totalWeeks,
            weekInPhase = enrollment.currentWeekInPhase,
            isRecoveryWeek = engine.isRecoveryWeek,
            nextSession = weekSessions.getOrNull(nextSessionIndex),
            nextSessionDayLabel = nextSessionItem?.dayLabel,
            currentWeekSessions = sessionItems,
            welcomeBackMessage = gapAction.welcomeMessage,
            needsCalibration = gapAction.requiresCalibration,
            fitnessLevel = fitnessLevel
        )
    }

    private fun computeDaysSinceLastRun(
        enrollment: BootcampEnrollmentEntity,
        lastSession: BootcampSessionEntity?
    ): Int {
        if (lastSession?.completedWorkoutId == null) return 0
        val now = System.currentTimeMillis()
        val lastDate = enrollment.startDate + TimeUnit.DAYS.toMillis(
            ((lastSession.weekNumber - 1) * 7 + (lastSession.dayOfWeek - 1)).toLong()
        )
        return ((now - lastDate) / TimeUnit.DAYS.toMillis(1)).toInt().coerceAtLeast(0)
    }

    // --- Onboarding ---

    fun startOnboarding() {
        _uiState.update { it.copy(showOnboarding = true) }
    }

    fun setOnboardingGoal(goal: BootcampGoal) {
        val warning = if (_uiState.value.onboardingMinutes < goal.warnBelowMinutes) {
            "${goal.name.replace('_', ' ')} training typically needs at least ${goal.suggestedMinMinutes} min per session."
        } else null
        _uiState.update { it.copy(onboardingGoal = goal, onboardingTimeWarning = warning) }
    }

    fun setOnboardingMinutes(minutes: Int) {
        val goal = _uiState.value.onboardingGoal
        val warning = if (goal != null && minutes < goal.warnBelowMinutes) {
            "${goal.name.replace('_', ' ')} training typically needs at least ${goal.suggestedMinMinutes} min per session."
        } else null
        _uiState.update { it.copy(onboardingMinutes = minutes, onboardingTimeWarning = warning) }
    }

    fun setOnboardingRunsPerWeek(runs: Int) {
        _uiState.update { it.copy(onboardingRunsPerWeek = runs) }
    }

    fun completeOnboarding(preferredDays: List<Int>) {
        val state = _uiState.value
        val goal = state.onboardingGoal ?: return
        viewModelScope.launch {
            bootcampRepository.createEnrollment(
                goal = goal,
                targetMinutesPerRun = state.onboardingMinutes,
                runsPerWeek = state.onboardingRunsPerWeek,
                preferredDays = preferredDays,
                startDate = System.currentTimeMillis()
            )
            _uiState.update { it.copy(showOnboarding = false) }
        }
    }

    fun pauseBootcamp() {
        viewModelScope.launch {
            val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return@launch
            bootcampRepository.pauseEnrollment(enrollment.id)
        }
    }

    fun dismissWelcomeBack() {
        _uiState.update { it.copy(welcomeBackMessage = null) }
    }

    private fun parsePreferredDays(json: String): List<Int> =
        json.removeSurrounding("[", "]").split(",").mapNotNull { it.trim().toIntOrNull() }
}
