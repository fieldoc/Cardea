package com.hrcoach.ui.workout

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrcoach.data.repository.AudioSettingsRepository
import com.hrcoach.data.repository.UserProfileRepository
import com.hrcoach.domain.model.AudioSettings
import com.hrcoach.domain.model.DistanceUnit
import com.hrcoach.service.WorkoutForegroundService
import com.hrcoach.ui.components.settings.AudioSettingsEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the mid-run settings bottom sheet.
 *
 * Mirrors [com.hrcoach.ui.account.AccountViewModel.saveAudioSettings] — persist to repo and
 * broadcast [WorkoutForegroundService.ACTION_RELOAD_AUDIO_SETTINGS] so the running service
 * reloads live. Do NOT call `CoachingAudioManager.applySettings()` from here.
 *
 * Slider persistence contract mirrors the DataStore slider rule: update in-memory state flow
 * on every drag tick (`committed = false`), persist + broadcast only on release
 * (`committed = true`). Toggles and segmented buttons persist on every event because they
 * don't fire at slider rates.
 */
@HiltViewModel
class ActiveRunSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioRepo: AudioSettingsRepository,
    private val userProfileRepo: UserProfileRepository,
) : ViewModel() {

    private val _audioSettings = MutableStateFlow(audioRepo.getAudioSettings())
    val audioSettings: StateFlow<AudioSettings> = _audioSettings.asStateFlow()

    private val _distanceUnit = MutableStateFlow(DistanceUnit.fromString(userProfileRepo.getDistanceUnit()))
    val distanceUnit: StateFlow<DistanceUnit> = _distanceUnit.asStateFlow()

    /**
     * Handle an event from the shared [com.hrcoach.ui.components.settings.AudioSettingsSection].
     *
     * Volume events update in-memory on drag and save on release. Every other event saves
     * immediately.
     */
    fun onEvent(event: AudioSettingsEvent) {
        val current = _audioSettings.value
        when (event) {
            is AudioSettingsEvent.EarconVolumeChanged -> {
                _audioSettings.value = current.copy(earconVolume = event.value.toInt())
                if (event.committed) save()
            }
            is AudioSettingsEvent.VoiceVolumeChanged -> {
                _audioSettings.value = current.copy(voiceVolume = event.value.toInt())
                if (event.committed) save()
            }
            is AudioSettingsEvent.VoiceVerbosityChanged -> {
                _audioSettings.value = current.copy(voiceVerbosity = event.verbosity)
                save()
            }
            is AudioSettingsEvent.VibrationEnabledToggled -> {
                _audioSettings.value = current.copy(enableVibration = event.enabled)
                save()
            }
            is AudioSettingsEvent.HalfwayReminderToggled -> {
                _audioSettings.value = current.copy(enableHalfwayReminder = event.enabled)
                save()
            }
            is AudioSettingsEvent.KmSplitsToggled -> {
                _audioSettings.value = current.copy(enableKmSplits = event.enabled)
                save()
            }
            is AudioSettingsEvent.WorkoutCompleteToggled -> {
                _audioSettings.value = current.copy(enableWorkoutComplete = event.enabled)
                save()
            }
            is AudioSettingsEvent.InZoneConfirmToggled -> {
                _audioSettings.value = current.copy(enableInZoneConfirm = event.enabled)
                save()
            }
            is AudioSettingsEvent.StridesTimerEarconsToggled -> {
                _audioSettings.value = current.copy(stridesTimerEarcons = event.enabled)
                save()
            }
        }
    }

    /**
     * Writes the current in-memory state to the repo and notifies the running workout service
     * so it reloads audio settings live. `startService` is a no-op when the service isn't
     * running.
     */
    fun save() {
        val settings = _audioSettings.value
        viewModelScope.launch {
            audioRepo.saveAudioSettings(settings)
            runCatching {
                context.startService(
                    Intent(context, WorkoutForegroundService::class.java).apply {
                        action = WorkoutForegroundService.ACTION_RELOAD_AUDIO_SETTINGS
                    }
                )
            }
        }
    }

    /**
     * Signals the workout service to finalize the bootcamp session with whatever progress has
     * been made so far, treating it as completed rather than discarded.
     */
    fun finishBootcampEarly() {
        runCatching {
            context.startService(
                Intent(context, WorkoutForegroundService::class.java).apply {
                    action = WorkoutForegroundService.ACTION_FINISH_BOOTCAMP_EARLY
                }
            )
        }
    }
}
