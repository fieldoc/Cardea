package com.hrcoach.ui.bootcamp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.repository.AdaptiveProfileRepository
import com.hrcoach.data.repository.BootcampRepository
import com.hrcoach.data.repository.UserProfileRepository
import com.hrcoach.domain.bootcamp.PhaseEngine
import com.hrcoach.domain.model.BootcampGoal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.hrcoach.domain.bootcamp.DayPreference
import com.hrcoach.domain.bootcamp.DaySelectionLevel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@HiltViewModel
class BootcampSettingsViewModel @Inject constructor(
    private val bootcampRepository: BootcampRepository,
    private val userProfileRepository: UserProfileRepository,
    private val adaptiveProfileRepository: AdaptiveProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BootcampSettingsUiState())
    val uiState: StateFlow<BootcampSettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val enrollment = bootcampRepository.getActiveEnrollmentOnce()
            if (enrollment == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            val days = BootcampEnrollmentEntity.parseDayPreferences(enrollment.preferredDays)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    goal = BootcampGoal.valueOf(enrollment.goalType),
                    runsPerWeek = enrollment.runsPerWeek,
                    targetMinutesPerRun = enrollment.targetMinutesPerRun,
                    tierIndex = enrollment.tierIndex,
                    startDateMs = normalizeToLocalDateStart(enrollment.startDate),
                    hrMax = userProfileRepository.getMaxHr(),
                    preferredDays = days,
                    editGoal = BootcampGoal.valueOf(enrollment.goalType),
                    editRunsPerWeek = enrollment.runsPerWeek,
                    editTargetMinutesPerRun = enrollment.targetMinutesPerRun,
                    editTierIndex = enrollment.tierIndex,
                    editStartDateMs = normalizeToLocalDateStart(enrollment.startDate),
                    editHrMaxInput = userProfileRepository.getMaxHr()?.toString().orEmpty(),
                    editPreferredDays = days
                )
            }
        }
    }

    fun setGoal(goal: BootcampGoal) {
        _uiState.update { it.copy(editGoal = goal, saveError = null) }
    }

    fun setRunsPerWeek(runsPerWeek: Int) {
        _uiState.update { state ->
            val normalizedDays = normalizePreferredDays(
                currentDays = state.editPreferredDays,
                runsPerWeek = runsPerWeek
            )
            state.copy(
                editRunsPerWeek = runsPerWeek,
                editPreferredDays = normalizedDays,
                saveError = null
            )
        }
    }

    fun setTargetMinutesPerRun(targetMinutesPerRun: Int) {
        _uiState.update {
            it.copy(
                editTargetMinutesPerRun = targetMinutesPerRun,
                saveError = null
            )
        }
    }

    fun setTierIndex(tierIndex: Int) {
        _uiState.update {
            it.copy(
                editTierIndex = tierIndex.coerceIn(0, 2),
                saveError = null
            )
        }
    }

    fun shiftStartDate(daysDelta: Long) {
        _uiState.update { state ->
            val date = Instant.ofEpochMilli(state.editStartDateMs)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .plusDays(daysDelta)
            state.copy(
                editStartDateMs = atStartOfDayMs(date),
                saveError = null
            )
        }
    }

    fun setHrMaxInput(value: String) {
        val trimmed = value.filter { it.isDigit() }.take(3)
        _uiState.update {
            it.copy(
                editHrMaxInput = trimmed,
                hrMaxValidationError = validateHrMax(trimmed),
                saveError = null
            )
        }
    }

    fun cycleDayPreference(day: Int) {
        _uiState.update { state ->
            val current = state.editPreferredDays.toMutableList()
            val existingIndex = current.indexOfFirst { it.day == day }
            
            if (existingIndex != -1) {
                val nextLevel = current[existingIndex].level.next()
                if (nextLevel == DaySelectionLevel.NONE) {
                    current.removeAt(existingIndex)
                } else {
                    current[existingIndex] = current[existingIndex].copy(level = nextLevel)
                }
            } else {
                // Only add if we haven't reached the limit, or allow selection but validation will catch it?
                // Standard behavior is to allow selection then user sees they have too many.
                current.add(DayPreference(day, DaySelectionLevel.AVAILABLE))
            }
            
            state.copy(
                editPreferredDays = current.sortedBy { it.day },
                saveError = null
            )
        }
    }

    fun toggleBlackoutDay(day: Int) {
        _uiState.update { state ->
            val current = state.editPreferredDays.toMutableList()
            val existing = current.indexOfFirst { it.day == day }
            if (existing != -1 && current[existing].level == DaySelectionLevel.BLACKOUT) {
                current.removeAt(existing)   // long press on BLACKOUT → clear it
            } else {
                if (existing != -1) current.removeAt(existing)
                current.add(DayPreference(day, DaySelectionLevel.BLACKOUT))
            }
            state.copy(editPreferredDays = current.sortedBy { it.day }, saveError = null)
        }
    }

    fun saveSettings(onDone: () -> Unit) {
        val state = _uiState.value
        state.preferredDaysValidationError?.let { validationError ->
            _uiState.update { it.copy(saveError = validationError) }
            return
        }
        state.hrMaxValidationError?.let { validationError ->
            _uiState.update { it.copy(saveError = validationError) }
            return
        }
        if (!state.hasChanges || state.isSaving) return
        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            try {
                val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: run {
                    _uiState.update { it.copy(isSaving = false, saveError = "No active program found.") }
                    return@launch
                }
                val normalizedDays = normalizePreferredDays(
                    currentDays = state.editPreferredDays,
                    runsPerWeek = state.editRunsPerWeek
                )
                if (normalizedDays.size != state.editRunsPerWeek) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saveError = "Select exactly ${state.editRunsPerWeek} days - your program is set to ${state.editRunsPerWeek} runs/week. To change your frequency, edit it above."
                        )
                    }
                    return@launch
                }
                val oldGoal = BootcampGoal.valueOf(enrollment.goalType)
                val oldEngine = PhaseEngine(
                    goal = oldGoal,
                    phaseIndex = enrollment.currentPhaseIndex,
                    weekInPhase = enrollment.currentWeekInPhase,
                    runsPerWeek = enrollment.runsPerWeek,
                    targetMinutes = enrollment.targetMinutesPerRun
                )
                val clampedPhaseIndex = enrollment.currentPhaseIndex.coerceIn(0, state.editGoal.phaseArc.lastIndex)
                val clampedWeekInPhase = enrollment.currentWeekInPhase.coerceIn(
                    0,
                    state.editGoal.phaseArc[clampedPhaseIndex].weeksRange.last - 1
                )
                val newEngine = PhaseEngine(
                    goal = state.editGoal,
                    phaseIndex = clampedPhaseIndex,
                    weekInPhase = clampedWeekInPhase,
                    runsPerWeek = state.editRunsPerWeek,
                    targetMinutes = state.editTargetMinutesPerRun
                )
                val resetFromWeek = minOf(oldEngine.absoluteWeek, newEngine.absoluteWeek)

                val parsedHrMax = state.editHrMaxInput.toIntOrNull()
                if (parsedHrMax != null && parsedHrMax in 100..220 && parsedHrMax != state.hrMax) {
                    userProfileRepository.setMaxHr(parsedHrMax)
                    val profile = adaptiveProfileRepository.getProfile()
                    adaptiveProfileRepository.saveProfile(
                        profile.copy(
                            hrMax = parsedHrMax,
                            hrMaxIsCalibrated = true
                        )
                    )
                }

                bootcampRepository.updateEnrollment(
                    enrollment.copy(
                        goalType = state.editGoal.name,
                        runsPerWeek = state.editRunsPerWeek,
                        targetMinutesPerRun = state.editTargetMinutesPerRun,
                        tierIndex = state.editTierIndex.coerceIn(0, 2),
                        startDate = state.editStartDateMs,
                        currentPhaseIndex = clampedPhaseIndex,
                        currentWeekInPhase = clampedWeekInPhase,
                        preferredDays = BootcampEnrollmentEntity.serializeDayPreferences(normalizedDays)
                    )
                )
                bootcampRepository.updatePreferredDays(
                    enrollmentId = enrollment.id,
                    newDays = normalizedDays,
                    currentWeekNumber = oldEngine.absoluteWeek
                )
                bootcampRepository.deleteScheduledSessionsFromWeek(enrollment.id, resetFromWeek + 1)
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        goal = state.editGoal,
                        runsPerWeek = state.editRunsPerWeek,
                        targetMinutesPerRun = state.editTargetMinutesPerRun,
                        tierIndex = state.editTierIndex.coerceIn(0, 2),
                        startDateMs = state.editStartDateMs,
                        hrMax = parsedHrMax ?: state.hrMax,
                        preferredDays = normalizedDays,
                        editGoal = state.editGoal,
                        editRunsPerWeek = state.editRunsPerWeek,
                        editTargetMinutesPerRun = state.editTargetMinutesPerRun,
                        editTierIndex = state.editTierIndex.coerceIn(0, 2),
                        editStartDateMs = state.editStartDateMs,
                        editHrMaxInput = (parsedHrMax ?: state.hrMax)?.toString().orEmpty(),
                        editPreferredDays = normalizedDays
                    )
                }
                onDone()
            } catch (t: Throwable) {
                _uiState.update { it.copy(isSaving = false, saveError = t.message ?: "Save failed") }
            }
        }
    }

    private fun validateHrMax(input: String): String? {
        if (input.isBlank()) return null
        val value = input.toIntOrNull() ?: return "Enter a valid max HR between 100 and 220."
        return if (value in 100..220) null else "Max HR must be between 100 and 220."
    }

    private fun normalizeToLocalDateStart(ms: Long): Long {
        val date = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
        return atStartOfDayMs(date)
    }

    private fun atStartOfDayMs(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun normalizePreferredDays(
        currentDays: List<DayPreference>,
        runsPerWeek: Int
    ): List<DayPreference> {
        val cappedRuns = runsPerWeek.coerceIn(2, 5)
        val deduped = currentDays
            .sortedBy { it.day }
            .distinctBy { it.day }
            .toMutableList()

        while (deduped.size > cappedRuns) {
            deduped.removeAt(deduped.lastIndex)
        }

        if (deduped.size < cappedRuns) {
            val existingDaySet = deduped.map { it.day }.toMutableSet()
            val defaults = defaultPreferredDays(cappedRuns)
            defaults.forEach { pref ->
                if (deduped.size >= cappedRuns) return@forEach
                if (existingDaySet.add(pref.day)) {
                    deduped.add(pref)
                }
            }
            (1..7).forEach { day ->
                if (deduped.size >= cappedRuns) return@forEach
                if (existingDaySet.add(day)) {
                    deduped.add(DayPreference(day, DaySelectionLevel.AVAILABLE))
                }
            }
        }

        return deduped.sortedBy { it.day }
    }

    private fun defaultPreferredDays(runsPerWeek: Int): List<DayPreference> {
        val days = when (runsPerWeek) {
            2 -> listOf(2, 5)
            3 -> listOf(1, 3, 6)
            4 -> listOf(1, 3, 5, 7)
            else -> listOf(1, 2, 4, 6, 7)
        }
        return days.mapIndexed { index, day ->
            val level = if (index == days.lastIndex) {
                DaySelectionLevel.LONG_RUN_BIAS
            } else {
                DaySelectionLevel.AVAILABLE
            }
            DayPreference(day, level)
        }
    }
}
