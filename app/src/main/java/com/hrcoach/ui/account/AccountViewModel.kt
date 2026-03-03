package com.hrcoach.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrcoach.data.repository.AudioSettingsRepository
import com.hrcoach.data.repository.MapsSettingsRepository
import com.hrcoach.data.repository.UserProfileRepository
import com.hrcoach.data.repository.WorkoutRepository
import com.hrcoach.domain.model.AudioSettings
import com.hrcoach.domain.model.VoiceVerbosity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountUiState(
    val totalWorkouts: Int = 0,
    val mapsApiKey: String = "",
    val mapsApiKeySaved: Boolean = false,
    val earconVolume: Int = 80,
    val voiceVerbosity: VoiceVerbosity = VoiceVerbosity.MINIMAL,
    val enableVibration: Boolean = true,
    val maxHr: Int? = null,
    val maxHrInput: String = "",
    val maxHrSaved: Boolean = false
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val audioRepo: AudioSettingsRepository,
    private val mapsRepo: MapsSettingsRepository,
    private val workoutRepo: WorkoutRepository,
    private val userProfileRepo: UserProfileRepository
) : ViewModel() {

    private val _mapsKey      = MutableStateFlow("")
    private val _mapsKeySaved = MutableStateFlow(false)
    private val _volume       = MutableStateFlow(80)
    private val _verbosity    = MutableStateFlow(VoiceVerbosity.MINIMAL)
    private val _vibration    = MutableStateFlow(true)

    private val _maxHr      = MutableStateFlow<Int?>(null)
    private val _maxHrInput = MutableStateFlow("")
    private val _maxHrSaved = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            _mapsKey.value = mapsRepo.getMapsApiKey()
            val settings = audioRepo.getAudioSettings()
            _volume.value    = settings.earconVolume
            _verbosity.value = settings.voiceVerbosity
            _vibration.value = settings.enableVibration
        }
        _maxHr.value = userProfileRepo.getMaxHr()
        _maxHrInput.value = _maxHr.value?.toString() ?: ""
    }

    val uiState: StateFlow<AccountUiState> = combine(
        workoutRepo.getAllWorkouts().map { it.size },
        _mapsKey,
        _mapsKeySaved,
        _volume,
        _verbosity
    ) { count, key, saved, vol, verb ->
        AccountUiState(
            totalWorkouts   = count,
            mapsApiKey      = key,
            mapsApiKeySaved = saved,
            earconVolume    = vol,
            voiceVerbosity  = verb,
            enableVibration = _vibration.value
        )
    }.combine(
        combine(_maxHr, _maxHrInput, _maxHrSaved) { hr, hrInput, hrSaved ->
            Triple(hr, hrInput, hrSaved)
        }
    ) { base, (hr, hrInput, hrSaved) ->
        base.copy(
            maxHr      = hr,
            maxHrInput = hrInput,
            maxHrSaved = hrSaved
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState())

    fun setMapsApiKey(key: String) {
        _mapsKey.value = key
        _mapsKeySaved.value = false
    }
    fun saveMapsApiKey() {
        viewModelScope.launch {
            mapsRepo.setMapsApiKey(_mapsKey.value)
            _mapsKeySaved.value = true
        }
    }

    fun setVolume(v: Float) { _volume.value = ((v / 5f).toInt() * 5).coerceIn(0, 100) }
    fun setVerbosity(v: VoiceVerbosity) { _verbosity.value = v }
    fun setVibration(v: Boolean) { _vibration.value = v }

    fun saveAudioSettings() {
        viewModelScope.launch {
            audioRepo.saveAudioSettings(
                AudioSettings(
                    earconVolume    = _volume.value,
                    voiceVerbosity  = _verbosity.value,
                    enableVibration = _vibration.value
                )
            )
        }
    }

    fun setMaxHrInput(value: String) {
        _maxHrInput.value = value
        _maxHrSaved.value = false
    }

    fun saveMaxHr() {
        val value = _maxHrInput.value.toIntOrNull() ?: return
        if (value !in 100..220) return
        userProfileRepo.setMaxHr(value)
        _maxHr.value = value
        _maxHrSaved.value = true
    }
}
