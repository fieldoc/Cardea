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
import com.hrcoach.domain.bootcamp.PlannedSession
import com.hrcoach.domain.bootcamp.CoachingCopyGenerator
import com.hrcoach.domain.bootcamp.SessionType
import com.hrcoach.domain.bootcamp.TierCtlRanges
import com.hrcoach.domain.model.WorkoutAdaptiveMetrics
import com.hrcoach.domain.engine.FitnessSignalEvaluator
import com.hrcoach.domain.engine.TierPromptDirection
import com.hrcoach.domain.engine.TuningDirection
import com.hrcoach.domain.model.BootcampGoal
import com.hrcoach.domain.model.TrainingPhase
import com.hrcoach.service.WorkoutState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

    private var welcomeBackDismissed = false
    private var illnessPromptSnoozedUntilMs = 0L
    private var loadJob: Job? = null

    init {
        loadBootcampState()
    }

    private fun loadBootcampState() {
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        loadJob = viewModelScope.launch {
            try {
                // Apply gap adjustment once at startup (prevents write-inside-collect re-emission)
                val initial = bootcampRepository.getActiveEnrollmentOnce()
                if (initial != null) {
                    applyGapAdjustmentIfNeeded(initial)
                }

                // Then observe the Flow for ongoing UI updates.
                // Track last-seen enrollment to avoid redundant refreshes from our own writes.
                var lastSeen: BootcampEnrollmentEntity? = null
                bootcampRepository.getActiveEnrollment().collect { enrollment ->
                    if (enrollment == lastSeen) return@collect
                    lastSeen = enrollment
                    if (enrollment == null) {
                        _uiState.value = BootcampUiState(
                            isLoading = false,
                            loadError = null,
                            hasActiveEnrollment = false,
                            isPaused = false
                        )
                    } else {
                        refreshFromEnrollment(enrollment)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = t.message ?: "Failed to load bootcamp data."
                    )
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
            val targetWeek = PhaseEngine(
                goal = BootcampGoal.valueOf(enrollment.goalType),
                phaseIndex = gapAction.phaseIndex,
                weekInPhase = gapAction.weekInPhase,
                runsPerWeek = enrollment.runsPerWeek,
                targetMinutes = enrollment.targetMinutesPerRun
            ).absoluteWeek
            bootcampRepository.deleteSessionsAfterWeek(enrollment.id, targetWeek - 1)
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
        val recentMetrics = workoutMetricsRepository.getRecentMetrics(limitDays = RECENT_METRICS_DAYS)
        val enrollmentForPrompt = clearTierPromptSnoozeIfCtlSafe(goal, enrollment, profile.ctl)

        val engine = PhaseEngine(
            goal = goal,
            phaseIndex = enrollment.currentPhaseIndex,
            weekInPhase = enrollment.currentWeekInPhase,
            runsPerWeek = enrollment.runsPerWeek,
            targetMinutes = enrollment.targetMinutesPerRun
        )

        val fitnessSignals = FitnessSignalEvaluator.evaluate(profile, recentMetrics)
        val fitnessLevel = FitnessEvaluator.assess(profile, recentMetrics)
        val illnessFlag = fitnessSignals.illnessFlag && System.currentTimeMillis() >= illnessPromptSnoozedUntilMs
        val tierPrompt = resolveTierPromptDirection(
            goal = goal,
            profileCtl = profile.ctl,
            profileAtl = profile.atl,
            totalSessions = profile.totalSessions,
            enrollment = enrollmentForPrompt,
            recentMetrics = recentMetrics,
            currentPhase = engine.currentPhase
        )
        val tierPromptDirection = if (illnessFlag) TierPromptDirection.NONE else tierPrompt.direction
        val tierPromptEvidence = if (illnessFlag) null else tierPrompt.evidence

        val lastSession = bootcampRepository.getLastCompletedSession(enrollment.id)
        val daysSinceLastRun = computeDaysSinceLastRun(enrollment, lastSession)
        val gapStrategy = GapAdvisor.assess(daysSinceLastRun)
        val gapAction = GapAdvisor.action(gapStrategy, enrollment.currentPhaseIndex, enrollment.currentWeekInPhase)

        val today = LocalDate.now().dayOfWeek.value
        val preferredDays = BootcampEnrollmentEntity.parseDayPreferences(enrollment.preferredDays)
        val activePreferredDays = preferredDays.filter { it.level != com.hrcoach.domain.bootcamp.DaySelectionLevel.NONE }
        val scheduledSessions = ensureCurrentWeekSessions(
            enrollment = enrollment,
            engine = engine,
            preferredDays = preferredDays,
            tuningDirection = fitnessSignals.tuningDirection
        )

        val sessionItems = scheduledSessions.map { session ->
            SessionUiItem(
                dayLabel = dayLabelFor(session.dayOfWeek),
                typeName = sessionTypeDisplayName(session.sessionType, session.presetId),
                minutes = session.targetMinutes,
                isCompleted = session.status != BootcampSessionEntity.STATUS_SCHEDULED,
                isToday = session.dayOfWeek == today,
                sessionId = session.id
            )
        }

        val nextScheduledSession = scheduledSessions.firstOrNull { it.status == BootcampSessionEntity.STATUS_SCHEDULED }
        val missedSession = scheduledSessions.any {
            it.dayOfWeek < today &&
                it.status != BootcampSessionEntity.STATUS_COMPLETED &&
                it.status != BootcampSessionEntity.STATUS_SKIPPED
        }
        val scheduledRestDay = scheduledSessions.none { it.dayOfWeek == today }
        val upcomingWeeks = engine.lookaheadWeeks(2).map { lookahead ->
            UpcomingWeekItem(
                weekNumber = lookahead.weekNumber,
                isRecoveryWeek = lookahead.isRecovery,
                sessions = lookahead.sessions.map { session ->
                    SessionUiItem(
                        dayLabel = "",
                        typeName = sessionTypeDisplayName(session.type.name, session.presetId),
                        minutes = session.minutes,
                        isCompleted = false,
                        isToday = false
                    )
                }
            )
        }

        _uiState.value = BootcampUiState(
            isLoading = false,
            loadError = null,
            hasActiveEnrollment = true,
            isPaused = enrollment.status == BootcampEnrollmentEntity.STATUS_PAUSED,
            goal = goal,
            currentPhase = engine.currentPhase,
            absoluteWeek = engine.absoluteWeek,
            totalWeeks = engine.totalWeeks,
            weekInPhase = enrollment.currentWeekInPhase,
            isRecoveryWeek = engine.isRecoveryWeek,
            weeksUntilNextRecovery = engine.weeksUntilNextRecovery,
            showGraduationCta = engine.absoluteWeek >= engine.totalWeeks,
            nextSession = nextScheduledSession?.toPlannedSession(),
            nextSessionDayLabel = nextScheduledSession?.let { dayLabelFor(it.dayOfWeek) },
            currentWeekSessions = sessionItems,
            activePreferredDays = activePreferredDays,
            upcomingWeeks = upcomingWeeks,
            welcomeBackMessage = if (welcomeBackDismissed) null else gapAction.welcomeMessage,
            needsCalibration = gapAction.requiresCalibration,
            fitnessLevel = fitnessLevel,
            tuningDirection = fitnessSignals.tuningDirection,
            illnessFlag = illnessFlag,
            tierPromptDirection = tierPromptDirection,
            tierPromptEvidence = tierPromptEvidence,
            scheduledRestDay = scheduledRestDay,
            missedSession = missedSession,
            swapRestMessage = null
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
        _uiState.update { it.copy(showOnboarding = true, onboardingStep = 0) }
    }

    fun setOnboardingStep(step: Int) {
        _uiState.update { it.copy(onboardingStep = step) }
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

    fun onBootcampWorkoutStarting() {
        // Store in WorkoutState (singleton) so the post-run NavEntry's separate
        // BootcampViewModel instance can read it via onWorkoutCompleted().
        val nextScheduled = _uiState.value.currentWeekSessions.firstOrNull { it.sessionId != null && !it.isCompleted }
        WorkoutState.setPendingBootcampSessionId(nextScheduled?.sessionId)
    }

    fun completeOnboarding() {
        val state = _uiState.value
        val goal = state.onboardingGoal ?: return
        val preferredDays = defaultPreferredDays(state.onboardingRunsPerWeek)
        viewModelScope.launch {
            val startDate = System.currentTimeMillis()
            bootcampRepository.createEnrollment(
                goal = goal,
                targetMinutesPerRun = state.onboardingMinutes,
                runsPerWeek = state.onboardingRunsPerWeek,
                preferredDays = preferredDays,
                startDate = startDate
            )
            // Session seeding is handled by refreshFromEnrollment() which fires
            // automatically when the Flow re-emits after createEnrollment writes.
            _uiState.update { it.copy(showOnboarding = false) }
        }
    }

    private fun defaultPreferredDays(runsPerWeek: Int): List<Int> = when (runsPerWeek) {
        2 -> listOf(2, 5)              // Tue, Fri
        3 -> listOf(1, 3, 6)           // Mon, Wed, Sat
        4 -> listOf(1, 3, 5, 7)        // Mon, Wed, Fri, Sun
        else -> listOf(1, 2, 4, 6, 7)  // Mon, Tue, Thu, Sat, Sun (5+)
    }

    fun pauseBootcamp() {
        viewModelScope.launch {
            val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return@launch
            bootcampRepository.pauseEnrollment(enrollment.id)
        }
    }

    fun resumeBootcamp() {
        viewModelScope.launch {
            val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return@launch
            bootcampRepository.resumeEnrollment(enrollment.id)
        }
    }

    fun showDeleteConfirmDialog() {
        _uiState.update { it.copy(showDeleteConfirmDialog = true) }
    }

    fun dismissDeleteConfirmDialog() {
        _uiState.update { it.copy(showDeleteConfirmDialog = false) }
    }

    fun deleteBootcamp() {
        viewModelScope.launch {
            val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return@launch
            WorkoutState.setPendingBootcampSessionId(null)
            bootcampRepository.deleteEnrollment(enrollment.id)
            _uiState.update { it.copy(showDeleteConfirmDialog = false) }
        }
    }

    fun dismissWelcomeBack() {
        welcomeBackDismissed = true
        _uiState.update { it.copy(welcomeBackMessage = null) }
    }

    fun dismissTierPrompt() {
        viewModelScope.launch {
            val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return@launch
            val nextDismissCount = enrollment.tierPromptDismissCount + 1
            val snoozeWeeks = TierCtlRanges.snoozeWeeksForDismissCount(nextDismissCount)
            val snoozedUntilMs = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(snoozeWeeks.toLong() * 7L)
            bootcampRepository.updateEnrollment(
                enrollment.copy(
                    tierPromptDismissCount = nextDismissCount,
                    tierPromptSnoozedUntilMs = snoozedUntilMs
                )
            )
            _uiState.update {
                it.copy(
                    tierPromptDirection = TierPromptDirection.NONE,
                    tierPromptEvidence = null
                )
            }
        }
    }

    fun acceptTierChange(direction: TierPromptDirection) {
        if (direction == TierPromptDirection.NONE) return
        viewModelScope.launch {
            val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return@launch
            val delta = when (direction) {
                TierPromptDirection.UP -> 1
                TierPromptDirection.DOWN -> -1
                TierPromptDirection.NONE -> 0
            }
            val updatedTierIndex = (enrollment.tierIndex + delta)
                .coerceIn(TierCtlRanges.minTierIndex, TierCtlRanges.maxTierIndex)

            bootcampRepository.updateEnrollment(
                enrollment.copy(
                    tierIndex = updatedTierIndex,
                    tierPromptDismissCount = 0,
                    tierPromptSnoozedUntilMs = 0L
                )
            )
            _uiState.update {
                it.copy(
                    tierPromptDirection = TierPromptDirection.NONE,
                    tierPromptEvidence = null
                )
            }
        }
    }

    fun retryLoad() {
        loadBootcampState()
    }

    fun confirmIllness() {
        illnessPromptSnoozedUntilMs = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(ILLNESS_CONFIRM_SNOOZE_DAYS)
        _uiState.update {
            it.copy(
                illnessFlag = false,
                tuningDirection = TuningDirection.EASE_BACK
            )
        }
    }

    fun dismissIllness() {
        illnessPromptSnoozedUntilMs = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(ILLNESS_DISMISS_SNOOZE_DAYS)
        _uiState.update { it.copy(illnessFlag = false) }
    }

    fun swapTodayForRest() {
        viewModelScope.launch {
            val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return@launch
            val session = bootcampRepository.getNextScheduledSession(enrollment.id) ?: return@launch
            val sessionId = session.id
            bootcampRepository.swapSessionToRestDay(sessionId)
            refreshFromEnrollment(enrollment)
            _uiState.update {
                it.copy(swapRestMessage = "Rest day saved. Today's run was swapped out.")
            }
        }
    }

    fun graduateCurrentGoal() {
        viewModelScope.launch {
            val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return@launch
            bootcampRepository.graduateEnrollment(enrollment.id)
        }
    }

    suspend fun onWorkoutCompleted(workoutId: Long): Boolean {
        val expectedSessionId = WorkoutState.pendingBootcampSessionId ?: return false
        WorkoutState.setPendingBootcampSessionId(null)
        val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return false
        val goal = BootcampGoal.valueOf(enrollment.goalType)
        val preferredDays = BootcampEnrollmentEntity.parseDayPreferences(enrollment.preferredDays)
        val engine = PhaseEngine(
            goal = goal,
            phaseIndex = enrollment.currentPhaseIndex,
            weekInPhase = enrollment.currentWeekInPhase,
            runsPerWeek = enrollment.runsPerWeek,
            targetMinutes = enrollment.targetMinutesPerRun
        )
        val currentWeekSessions = ensureCurrentWeekSessions(
            enrollment = enrollment,
            engine = engine,
            preferredDays = preferredDays,
            tuningDirection = _uiState.value.tuningDirection
        )
        val nextSession = currentWeekSessions.firstOrNull { it.id == expectedSessionId } ?: return false

        bootcampRepository.updateSession(
            nextSession.copy(
                status = BootcampSessionEntity.STATUS_COMPLETED,
                completedWorkoutId = workoutId
            )
        )

        val refreshedWeek = bootcampRepository.getSessionsForWeek(enrollment.id, nextSession.weekNumber)
        if (refreshedWeek.isNotEmpty() && refreshedWeek.all { it.status == BootcampSessionEntity.STATUS_COMPLETED }) {
            val nextEngine = if (engine.shouldAdvancePhase()) {
                engine.advancePhase()
            } else {
                engine.copy(weekInPhase = engine.weekInPhase + 1)
            }
            val updatedEnrollment = enrollment.copy(
                currentPhaseIndex = nextEngine.phaseIndex,
                currentWeekInPhase = nextEngine.weekInPhase
            )
            bootcampRepository.updateEnrollment(updatedEnrollment)
            ensureCurrentWeekSessions(
                enrollment = updatedEnrollment,
                engine = nextEngine,
                preferredDays = preferredDays,
                tuningDirection = _uiState.value.tuningDirection
            )
        }

        return true
    }

    private suspend fun ensureCurrentWeekSessions(
        enrollment: BootcampEnrollmentEntity,
        engine: PhaseEngine,
        preferredDays: List<com.hrcoach.domain.bootcamp.DayPreference>,
        tuningDirection: TuningDirection = TuningDirection.HOLD
    ): List<BootcampSessionEntity> {
        val weekNumber = engine.absoluteWeek
        val existing = bootcampRepository.getSessionsForWeek(enrollment.id, weekNumber)
        if (existing.isNotEmpty()) return existing

        val currentPresetIndices = loadPresetIndicesForWeek(enrollment.id, weekNumber)
        val plannedSessions = engine.planCurrentWeek(
            tierIndex = enrollment.tierIndex,
            tuningDirection = tuningDirection,
            currentPresetIndices = currentPresetIndices
        )
        if (plannedSessions.isEmpty()) return emptyList()

        val availableDays = preferredDays
            .filter { it.level != com.hrcoach.domain.bootcamp.DaySelectionLevel.NONE }
            .map { it.day }
        val entities = plannedSessions.mapIndexed { index, session ->
            BootcampRepository.buildSessionEntity(
                enrollmentId = enrollment.id,
                weekNumber = weekNumber,
                dayOfWeek = availableDays.getOrElse(index) { index + 1 }.coerceIn(1, 7),
                sessionType = session.type.name,
                targetMinutes = session.minutes,
                presetId = session.presetId
            )
        }
        bootcampRepository.insertSessions(entities)
        return bootcampRepository.getSessionsForWeek(enrollment.id, weekNumber)
    }

    private suspend fun clearTierPromptSnoozeIfCtlSafe(
        goal: BootcampGoal,
        enrollment: BootcampEnrollmentEntity,
        profileCtl: Float
    ): BootcampEnrollmentEntity {
        if (!TierCtlRanges.isCtlInRange(goal, enrollment.tierIndex, profileCtl)) return enrollment
        if (enrollment.tierPromptSnoozedUntilMs <= 0L && enrollment.tierPromptDismissCount == 0) return enrollment

        val cleared = enrollment.copy(
            tierPromptDismissCount = 0,
            tierPromptSnoozedUntilMs = 0L
        )
        bootcampRepository.updateEnrollment(cleared)
        return cleared
    }

    private fun resolveTierPromptDirection(
        goal: BootcampGoal,
        profileCtl: Float,
        profileAtl: Float,
        totalSessions: Int,
        enrollment: BootcampEnrollmentEntity,
        recentMetrics: List<WorkoutAdaptiveMetrics>,
        currentPhase: TrainingPhase
    ): TierPromptDecision {
        if (totalSessions < TierCtlRanges.MIN_SESSIONS_FOR_PROMPT) return TierPromptDecision.None
        if (System.currentTimeMillis() < enrollment.tierPromptSnoozedUntilMs) return TierPromptDecision.None

        val range = TierCtlRanges.rangeFor(goal, enrollment.tierIndex)
        val lowerBound = range.first.toFloat()
        val upperBound = range.last.toFloat()
        val tsb = profileCtl - profileAtl

        val snapshots = buildRecentWeeklyLoadSnapshots(recentMetrics)
        if (snapshots.size < TierCtlRanges.MIN_CONSECUTIVE_WEEKS_FOR_PROMPT) return TierPromptDecision.None

        val aboveWeeks = consecutiveWeeksFromNow(snapshots) { it.ctlProxy > upperBound }
        val belowWeeks = consecutiveWeeksFromNow(snapshots) { it.ctlProxy < lowerBound }

        if (
            profileCtl > upperBound &&
            aboveWeeks >= TierCtlRanges.MIN_CONSECUTIVE_WEEKS_FOR_PROMPT &&
            tsb > 0f &&
            enrollment.tierIndex < TierCtlRanges.maxTierIndex
        ) {
            val ctlTrend = ctlTrendAcrossWeeks(snapshots, aboveWeeks)
            return TierPromptDecision(
                direction = TierPromptDirection.UP,
                evidence = CoachingCopyGenerator.tierPromptCopy(
                    direction = TierPromptDirection.UP,
                    aboveOrBelowWeeks = aboveWeeks,
                    ctlTrend = ctlTrend,
                    tsb = tsb
                )
            )
        }

        if (
            profileCtl < lowerBound &&
            belowWeeks >= TierCtlRanges.MIN_CONSECUTIVE_WEEKS_FOR_PROMPT &&
            enrollment.tierIndex > TierCtlRanges.minTierIndex
        ) {
            val latestWeek = snapshots.first()
            val priorWeek = snapshots.getOrNull(1)
            val atlDeclining = priorWeek != null && latestWeek.totalLoad < priorWeek.totalLoad
            val belowPlannedFrequency = latestWeek.sessionCount < enrollment.runsPerWeek
            val intentionalDeload = currentPhase == TrainingPhase.TAPER || atlDeclining || belowPlannedFrequency
            if (intentionalDeload) return TierPromptDecision.None

            val ctlTrend = ctlTrendAcrossWeeks(snapshots, belowWeeks)
            return TierPromptDecision(
                direction = TierPromptDirection.DOWN,
                evidence = CoachingCopyGenerator.tierPromptCopy(
                    direction = TierPromptDirection.DOWN,
                    aboveOrBelowWeeks = belowWeeks,
                    ctlTrend = ctlTrend,
                    tsb = tsb
                )
            )
        }

        return TierPromptDecision.None
    }

    private fun buildRecentWeeklyLoadSnapshots(
        recentMetrics: List<WorkoutAdaptiveMetrics>,
        lookbackWeeks: Int = 6
    ): List<WeeklyLoadSnapshot> {
        val now = System.currentTimeMillis()
        return (0 until lookbackWeeks).map { weekOffset ->
            val endMs = now - TimeUnit.DAYS.toMillis((weekOffset * 7L))
            val startMs = now - TimeUnit.DAYS.toMillis(((weekOffset + 1L) * 7L))
            val weekMetrics = recentMetrics.filter { metric ->
                metric.recordedAtMs >= startMs &&
                    metric.recordedAtMs < endMs &&
                    metric.trimpReliable &&
                    !metric.environmentAffected
            }
            val totalLoad = weekMetrics.sumOf { (it.trimpScore ?: 0f).toDouble() }.toFloat()
            WeeklyLoadSnapshot(
                totalLoad = totalLoad,
                ctlProxy = totalLoad / 7f,
                sessionCount = weekMetrics.size
            )
        }
    }

    private fun consecutiveWeeksFromNow(
        snapshots: List<WeeklyLoadSnapshot>,
        predicate: (WeeklyLoadSnapshot) -> Boolean
    ): Int {
        var count = 0
        for (snapshot in snapshots) {
            if (predicate(snapshot)) {
                count++
            } else {
                break
            }
        }
        return count
    }

    private fun ctlTrendAcrossWeeks(snapshots: List<WeeklyLoadSnapshot>, weeks: Int): Float {
        if (weeks <= 1 || snapshots.isEmpty()) return 0f
        val cappedIndex = (weeks - 1).coerceAtMost(snapshots.lastIndex)
        val latest = snapshots.first().ctlProxy
        val prior = snapshots[cappedIndex].ctlProxy
        return latest - prior
    }

    private data class WeeklyLoadSnapshot(
        val totalLoad: Float,
        val ctlProxy: Float,
        val sessionCount: Int
    )

    private data class TierPromptDecision(
        val direction: TierPromptDirection,
        val evidence: String?
    ) {
        companion object {
            val None = TierPromptDecision(TierPromptDirection.NONE, null)
        }
    }

    private suspend fun loadPresetIndicesForWeek(
        enrollmentId: Long,
        weekNumber: Int
    ): Map<String, Int> {
        if (weekNumber <= 1) return emptyMap()
        val previousWeek = bootcampRepository.getSessionsForWeek(enrollmentId, weekNumber - 1)
        if (previousWeek.isEmpty()) return emptyMap()
        return previousWeek
            .mapNotNull { sessionTypePresetKey(it.sessionType) }
            .distinct()
            .associateWith { 1 }
    }

    private fun sessionTypePresetKey(rawType: String): String? {
        val type = runCatching { SessionType.valueOf(rawType) }.getOrNull() ?: return null
        return when (type) {
            SessionType.EASY -> "easy"
            SessionType.TEMPO -> "tempo"
            SessionType.INTERVAL -> "interval"
            SessionType.STRIDES -> "strides"
            SessionType.LONG -> "long"
            SessionType.RACE_SIM,
            SessionType.DISCOVERY,
            SessionType.CHECK_IN -> null
        }
    }

    private fun dayLabelFor(dayOfWeek: Int): String =
        DayOfWeek.of(dayOfWeek.coerceIn(1, 7)).getDisplayName(TextStyle.SHORT, Locale.getDefault())

    private fun sessionTypeDisplayName(rawType: String, presetId: String? = null): String {
        val presetLabel = SessionType.displayLabelForPreset(presetId)
        if (presetLabel != null) return presetLabel

        return runCatching {
            SessionType.valueOf(rawType)
        }.getOrNull()?.let { sessionType ->
            when (sessionType) {
                SessionType.EASY -> "Easy"
                SessionType.LONG -> "Long"
                SessionType.TEMPO -> "Tempo (Z3)"
                SessionType.INTERVAL -> "Intervals (Z5)"
                SessionType.STRIDES -> "Strides"
                SessionType.RACE_SIM -> "Race Sim"
                SessionType.DISCOVERY -> "Discovery"
                SessionType.CHECK_IN -> "Check In"
            }
        } ?: rawType.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    private fun BootcampSessionEntity.toPlannedSession(): PlannedSession = PlannedSession(
        type = runCatching { SessionType.valueOf(sessionType) }.getOrDefault(SessionType.EASY),
        minutes = targetMinutes,
        presetId = presetId
    )

    companion object {
        private const val RECENT_METRICS_DAYS = 42
        const val ILLNESS_CONFIRM_SNOOZE_DAYS = 10L
        const val ILLNESS_DISMISS_SNOOZE_DAYS = 1L
    }

}
