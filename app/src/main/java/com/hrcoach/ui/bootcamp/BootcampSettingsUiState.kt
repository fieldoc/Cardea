package com.hrcoach.ui.bootcamp

import com.hrcoach.domain.bootcamp.DayPreference
import com.hrcoach.domain.bootcamp.DaySelectionLevel
import com.hrcoach.domain.bootcamp.DurationScaler
import com.hrcoach.domain.model.BootcampGoal

data class BootcampSettingsUiState(
    val isLoading: Boolean = true,
    val goal: BootcampGoal = BootcampGoal.CARDIO_HEALTH,
    val runsPerWeek: Int = 3,
    val targetMinutesPerRun: Int = 30,
    val tierIndex: Int = 0,
    val startDateMs: Long = System.currentTimeMillis(),
    val hrMax: Int? = null,
    val preferredDays: List<DayPreference> = emptyList(),
    val editGoal: BootcampGoal = BootcampGoal.CARDIO_HEALTH,
    val editRunsPerWeek: Int = 3,
    val editTargetMinutesPerRun: Int = 30,
    val editTierIndex: Int = 0,
    val targetFinishingTimeMinutes: Int? = null,
    val editTargetFinishingTimeMinutes: Int? = null,
    val editTimeWarning: String? = null,
    val editTimeCanProceed: Boolean = true,
    val editStartDateMs: Long = System.currentTimeMillis(),
    val editHrMaxInput: String = "",
    val hrMaxValidationError: String? = null,
    val editPreferredDays: List<DayPreference> = emptyList(),
    val isSaving: Boolean = false,
    val saveError: String? = null
) {
    val preferredDaysValidationError: String?
        get() {
            val runDays = editPreferredDays.count {
                it.level == DaySelectionLevel.AVAILABLE || it.level == DaySelectionLevel.LONG_RUN_BIAS
            }
            return if (runDays == editRunsPerWeek) null
            else "Select exactly $editRunsPerWeek days - your program is set to $editRunsPerWeek runs/week. To change your frequency, edit it above."
        }

    val hasGoalChanges: Boolean
        get() = editGoal != goal

    val hasPreferredDayChanges: Boolean
        get() = editPreferredDays != preferredDays

    val hasRunsPerWeekChanges: Boolean
        get() = editRunsPerWeek != runsPerWeek

    val hasTargetMinutesChanges: Boolean
        get() = editTargetMinutesPerRun != targetMinutesPerRun

    val hasTierChanges: Boolean
        get() = editTierIndex != tierIndex

    val hasFinishingTimeChanges: Boolean
        get() = editTargetFinishingTimeMinutes != targetFinishingTimeMinutes

    val hasStartDateChanges: Boolean
        get() = editStartDateMs != startDateMs

    val hasHrMaxChanges: Boolean
        get() = editHrMaxInput != (hrMax?.toString() ?: "")

    val hasChanges: Boolean
        get() = hasGoalChanges ||
            hasPreferredDayChanges ||
            hasRunsPerWeekChanges ||
            hasTargetMinutesChanges ||
            hasTierChanges ||
            hasFinishingTimeChanges ||
            hasStartDateChanges ||
            hasHrMaxChanges

    val editLongRunMinutes: Int
        get() = DurationScaler.compute(editRunsPerWeek, editTargetMinutesPerRun).longMinutes

    val editWeeklyTotal: Int
        get() {
            val d = DurationScaler.compute(editRunsPerWeek, editTargetMinutesPerRun)
            val easyRuns = if (editRunsPerWeek >= 3) editRunsPerWeek - 1 else editRunsPerWeek
            return d.easyMinutes * easyRuns + d.longMinutes
        }

    val longRunWarning: String?
        get() {
            val longRun = editLongRunMinutes
            return if (longRun < editGoal.minLongRunMinutes) {
                "Your long run (~$longRun min) is shorter than recommended for " +
                    "${editGoal.name.replace('_', ' ')} training (${editGoal.minLongRunMinutes} min)."
            } else null
        }

    val canSave: Boolean
        get() = preferredDaysValidationError == null &&
            hrMaxValidationError == null &&
            hasChanges
}
