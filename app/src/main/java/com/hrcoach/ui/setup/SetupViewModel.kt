package com.hrcoach.ui.setup

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrcoach.data.repository.AudioSettingsRepository
import com.hrcoach.data.repository.MapsSettingsRepository
import com.hrcoach.data.repository.UserProfileRepository
import com.hrcoach.domain.model.AudioSettings
import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.HrSegment
import com.hrcoach.domain.model.VoiceVerbosity
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.domain.preset.PresetLibrary
import com.hrcoach.service.BleConnectionCoordinator
import com.hrcoach.service.audio.EarconPlayer
import com.hrcoach.util.JsonCodec
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SegmentInput(
    val distanceKm: String = "",
    val targetHr: String = ""
)

data class SegmentInputError(
    val distanceKm: String? = null,
    val targetHr: String? = null
)

data class SetupValidationState(
    val steadyStateHr: String? = null,
    val segments: List<SegmentInputError> = emptyList(),
    val bufferBpm: String? = null,
    val alertDelaySec: String? = null,
    val alertCooldownSec: String? = null,
    val canStartWorkout: Boolean = false,
    val startBlockedReason: String? = null
)

data class SetupUiState(
    val mode: WorkoutMode = WorkoutMode.STEADY_STATE,
    val steadyStateHr: String = "140",
    val segments: List<SegmentInput> = listOf(SegmentInput(distanceKm = "5.0", targetHr = "140")),
    val bufferBpm: String = "5",
    val alertDelaySec: String = "15",
    val alertCooldownSec: String = "30",
    val earconVolume: Int = 80,
    val voiceVerbosity: VoiceVerbosity = VoiceVerbosity.MINIMAL,
    val enableVibration: Boolean = true,
    val enableHalfwayReminder: Boolean = true,
    val enableKmSplits: Boolean = true,
    val enableWorkoutComplete: Boolean = true,
    val enableInZoneConfirm: Boolean = true,
    val showAdvancedSettings: Boolean = false,
    val showAppSettings: Boolean = false,
    val isScanning: Boolean = false,
    val discoveredDevices: List<BluetoothDevice> = emptyList(),
    val isHrConnected: Boolean = false,
    val connectedDeviceName: String = "",
    val connectedDeviceAddress: String = "",
    val liveHr: Int = 0,
    val mapsApiKey: String = "",
    val mapsApiKeySaved: Boolean = false,
    val connectionError: String? = null,
    val selectedPresetId: String? = null,
    val showHrMaxDialog: Boolean = false,
    val maxHrInput: String = "",
    val maxHr: Int? = null,
    val pendingPresetId: String? = null,
    val validation: SetupValidationState = SetupValidationState()
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val audioSettingsRepository: AudioSettingsRepository,
    private val mapsSettingsRepository: MapsSettingsRepository,
    private val bleCoordinator: BleConnectionCoordinator,
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    private var bleCollectJob: Job? = null
    private var scanTimeoutJob: Job? = null
    private var previewPlayer: EarconPlayer? = null

    init {
        val savedKey = mapsSettingsRepository.getMapsApiKey()
        val audioSettings = audioSettingsRepository.getAudioSettings()
        _uiState.value = _uiState.value.copy(
            mapsApiKey = savedKey,
            earconVolume = audioSettings.earconVolume,
            voiceVerbosity = audioSettings.voiceVerbosity,
            enableVibration = audioSettings.enableVibration,
            enableHalfwayReminder = audioSettings.enableHalfwayReminder != false,
            enableKmSplits = audioSettings.enableKmSplits != false,
            enableWorkoutComplete = audioSettings.enableWorkoutComplete != false,
            enableInZoneConfirm = audioSettings.enableInZoneConfirm != false,
            maxHr = userProfileRepository.getMaxHr()
        )
        startBleCollectors()
        recomputeValidation()
    }

    private fun startBleCollectors() {
        bleCollectJob?.cancel()
        bleCollectJob = viewModelScope.launch {
            launch {
                bleCoordinator.discoveredDevices.collect { devices ->
                    _uiState.value = _uiState.value.copy(
                        discoveredDevices = devices,
                        isScanning = _uiState.value.isScanning && !_uiState.value.isHrConnected
                    )
                }
            }
            launch {
                bleCoordinator.isConnected.collect { connected ->
                    val currentState = _uiState.value
                    _uiState.value = currentState.copy(
                        isHrConnected = connected,
                        isScanning = if (connected) false else currentState.isScanning,
                        connectionError = if (connected) null else currentState.connectionError
                    )
                    recomputeValidation()
                }
            }
            launch {
                bleCoordinator.heartRate.collect { hr ->
                    _uiState.value = _uiState.value.copy(liveHr = hr)
                }
            }
        }
    }

    fun setMode(mode: WorkoutMode) {
        _uiState.value = _uiState.value.copy(mode = mode)
        recomputeValidation()
    }

    fun setSteadyStateHr(value: String) {
        _uiState.value = _uiState.value.copy(steadyStateHr = value)
        recomputeValidation()
    }

    fun setBufferBpm(value: String) {
        _uiState.value = _uiState.value.copy(bufferBpm = value)
        recomputeValidation()
    }

    fun setAlertDelaySec(value: String) {
        _uiState.value = _uiState.value.copy(alertDelaySec = value)
        recomputeValidation()
    }

    fun setAlertCooldownSec(value: String) {
        _uiState.value = _uiState.value.copy(alertCooldownSec = value)
        recomputeValidation()
    }

    fun setEarconVolume(value: Int) {
        _uiState.value = _uiState.value.copy(earconVolume = value.coerceIn(0, 100))
        saveAudioSettings()
        previewPlayer?.setVolume(_uiState.value.earconVolume)
    }

    fun setVoiceVerbosity(value: VoiceVerbosity) {
        _uiState.value = _uiState.value.copy(voiceVerbosity = value)
        saveAudioSettings()
    }

    fun setEnableVibration(value: Boolean) {
        _uiState.value = _uiState.value.copy(enableVibration = value)
        saveAudioSettings()
    }

    fun setMapsApiKey(value: String) {
        _uiState.value = _uiState.value.copy(
            mapsApiKey = value,
            mapsApiKeySaved = false
        )
    }

    fun saveMapsApiKey() {
        mapsSettingsRepository.setMapsApiKey(_uiState.value.mapsApiKey)
        _uiState.value = _uiState.value.copy(mapsApiKeySaved = true)
    }

    fun setEnableHalfwayReminder(value: Boolean) {
        _uiState.value = _uiState.value.copy(enableHalfwayReminder = value)
        saveAudioSettings()
    }

    fun setEnableKmSplits(value: Boolean) {
        _uiState.value = _uiState.value.copy(enableKmSplits = value)
        saveAudioSettings()
    }

    fun setEnableWorkoutComplete(value: Boolean) {
        _uiState.value = _uiState.value.copy(enableWorkoutComplete = value)
        saveAudioSettings()
    }

    fun setEnableInZoneConfirm(value: Boolean) {
        _uiState.value = _uiState.value.copy(enableInZoneConfirm = value)
        saveAudioSettings()
    }

    fun saveAudioSettings() {
        audioSettingsRepository.saveAudioSettings(
            AudioSettings(
                earconVolume = _uiState.value.earconVolume,
                voiceVerbosity = _uiState.value.voiceVerbosity,
                enableVibration = _uiState.value.enableVibration,
                enableHalfwayReminder = _uiState.value.enableHalfwayReminder,
                enableKmSplits = _uiState.value.enableKmSplits,
                enableWorkoutComplete = _uiState.value.enableWorkoutComplete,
                enableInZoneConfirm = _uiState.value.enableInZoneConfirm,
            )
        )
    }

    fun toggleAdvancedSettings() {
        _uiState.value = _uiState.value.copy(
            showAdvancedSettings = !_uiState.value.showAdvancedSettings
        )
    }

    fun toggleAppSettings() {
        _uiState.value = _uiState.value.copy(
            showAppSettings = !_uiState.value.showAppSettings
        )
    }

    fun selectPreset(presetId: String) {
        val maxHr = _uiState.value.maxHr
        if (maxHr == null) {
            _uiState.value = _uiState.value.copy(
                pendingPresetId = presetId,
                showHrMaxDialog = true,
                maxHrInput = ""
            )
        } else {
            _uiState.value = _uiState.value.copy(selectedPresetId = presetId)
            recomputeValidation()
        }
    }

    fun setMaxHrInput(value: String) {
        _uiState.value = _uiState.value.copy(maxHrInput = value)
    }

    fun confirmMaxHr(): Boolean {
        val value = _uiState.value.maxHrInput.toIntOrNull() ?: return false
        if (value !in 100..220) return false
        userProfileRepository.setMaxHr(value)
        val pendingId = _uiState.value.pendingPresetId
        _uiState.value = _uiState.value.copy(
            maxHr = value,
            showHrMaxDialog = false,
            pendingPresetId = null,
            selectedPresetId = pendingId,
            maxHrInput = ""
        )
        recomputeValidation()
        return true
    }

    fun dismissHrMaxDialog() {
        _uiState.value = _uiState.value.copy(
            showHrMaxDialog = false,
            pendingPresetId = null,
            maxHrInput = ""
        )
    }

    fun previewEarcon(event: CoachingEvent) {
        val player = previewPlayer ?: EarconPlayer(appContext).also {
            previewPlayer = it
        }
        player.setVolume(_uiState.value.earconVolume)
        player.play(event)
    }

    fun addSegment() {
        _uiState.value = _uiState.value.copy(
            segments = _uiState.value.segments + SegmentInput()
        )
        recomputeValidation()
    }

    fun updateSegmentDistance(index: Int, value: String) {
        val current = _uiState.value.segments.toMutableList()
        if (index !in current.indices) return
        current[index] = current[index].copy(distanceKm = value)
        _uiState.value = _uiState.value.copy(segments = current)
        recomputeValidation()
    }

    fun updateSegmentTarget(index: Int, value: String) {
        val current = _uiState.value.segments.toMutableList()
        if (index !in current.indices) return
        current[index] = current[index].copy(targetHr = value)
        _uiState.value = _uiState.value.copy(segments = current)
        recomputeValidation()
    }

    fun removeSegment(index: Int) {
        if (_uiState.value.segments.size <= 1) return
        val current = _uiState.value.segments.toMutableList()
        if (index !in current.indices) return
        current.removeAt(index)
        _uiState.value = _uiState.value.copy(segments = current)
        recomputeValidation()
    }

    fun startScan() {
        runCatching {
            bleCoordinator.startScan()
        }.onSuccess {
            scanTimeoutJob?.cancel()
            scanTimeoutJob = viewModelScope.launch {
                delay(16_000)
                if (!_uiState.value.isHrConnected) {
                    _uiState.value = _uiState.value.copy(isScanning = false)
                }
            }
            _uiState.value = _uiState.value.copy(
                isScanning = true,
                discoveredDevices = emptyList(),
                connectionError = null
            )
        }.onFailure {
            _uiState.value = _uiState.value.copy(
                isScanning = false,
                connectionError = "Unable to scan. Check Bluetooth and permissions."
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        runCatching {
            bleCoordinator.connectToDevice(device)
        }.onSuccess {
            scanTimeoutJob?.cancel()
            _uiState.value = _uiState.value.copy(
                isScanning = false,
                connectedDeviceName = device.name ?: device.address,
                connectedDeviceAddress = device.address,
                connectionError = null
            )
        }.onFailure {
            _uiState.value = _uiState.value.copy(
                connectionError = "Unable to connect to selected device."
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnectDevice() {
        bleCoordinator.disconnect()
        _uiState.value = _uiState.value.copy(
            isHrConnected = false,
            connectedDeviceName = "",
            connectedDeviceAddress = "",
            liveHr = 0
        )
        recomputeValidation()
    }

    @SuppressLint("MissingPermission")
    fun handoffConnectedDeviceAddress(): String? {
        val address = _uiState.value.connectedDeviceAddress.takeIf { it.isNotBlank() }
        val activeAddress = bleCoordinator.handoffConnectedDeviceAddress(address)
        _uiState.value = _uiState.value.copy(
            isScanning = false,
            connectedDeviceAddress = activeAddress.orEmpty(),
            connectedDeviceName = if (activeAddress.isNullOrBlank()) {
                ""
            } else {
                _uiState.value.connectedDeviceName
            }
        )
        return activeAddress
    }

    fun buildConfigOrNull(): WorkoutConfig? {
        val state = _uiState.value
        if (state.mode == WorkoutMode.FREE_RUN) {
            return WorkoutConfig(mode = WorkoutMode.FREE_RUN)
        }
        val buffer = state.bufferBpm.toIntOrNull() ?: return null
        val delay = state.alertDelaySec.toIntOrNull() ?: return null
        val cooldown = state.alertCooldownSec.toIntOrNull() ?: return null
        if (buffer !in 1..30 || delay !in 5..300 || cooldown !in 5..300) return null

        // Fast path: preset is selected
        val presetId = state.selectedPresetId
        val maxHr = state.maxHr
        if (presetId != null && presetId != "custom" && maxHr != null) {
            val preset = PresetLibrary.ALL.firstOrNull { it.id == presetId } ?: return null
            return preset.buildConfig(maxHr).copy(
                bufferBpm = buffer,
                alertDelaySec = delay,
                alertCooldownSec = cooldown
            )
        }

        return when (state.mode) {
            WorkoutMode.STEADY_STATE -> {
                val hr = state.steadyStateHr.toIntOrNull() ?: return null
                if (hr !in 60..230) return null
                WorkoutConfig(
                    mode = WorkoutMode.STEADY_STATE,
                    steadyStateTargetHr = hr,
                    bufferBpm = buffer,
                    alertDelaySec = delay,
                    alertCooldownSec = cooldown
                )
            }

            WorkoutMode.DISTANCE_PROFILE -> {
                var previousDistanceMeters = 0f
                val mappedSegments = state.segments.map { segment ->
                    val km = segment.distanceKm.toFloatOrNull() ?: return null
                    val hr = segment.targetHr.toIntOrNull() ?: return null
                    val meters = km * 1000f
                    if (km <= 0f || hr !in 60..230 || meters <= previousDistanceMeters) return null
                    previousDistanceMeters = meters
                    HrSegment(distanceMeters = meters, targetHr = hr)
                }
                if (mappedSegments.isEmpty()) return null
                WorkoutConfig(
                    mode = WorkoutMode.DISTANCE_PROFILE,
                    segments = mappedSegments,
                    bufferBpm = buffer,
                    alertDelaySec = delay,
                    alertCooldownSec = cooldown
                )
            }

            WorkoutMode.FREE_RUN -> null // unreachable: handled by early return above
        }
    }

    fun buildConfigJsonOrNull(): String? {
        val config = buildConfigOrNull() ?: return null
        return JsonCodec.gson.toJson(config)
    }

    private fun recomputeValidation() {
        val state = _uiState.value

        // Preset shortcut: if a valid preset is selected and maxHr is known, always valid
        val presetId = state.selectedPresetId
        val maxHr = state.maxHr
        if (presetId != null && presetId != "custom" && maxHr != null) {
            _uiState.value = state.copy(
                validation = SetupValidationState(canStartWorkout = true)
            )
            return
        }

        val segmentErrors = MutableList(state.segments.size) { SegmentInputError() }

        val bufferError = validateInt(
            raw = state.bufferBpm,
            min = 1,
            max = 30,
            emptyMessage = "Enter buffer",
            rangeMessage = "Use 1 to 30 bpm"
        )

        val delayError = validateInt(
            raw = state.alertDelaySec,
            min = 5,
            max = 300,
            emptyMessage = "Enter grace period",
            rangeMessage = "Use 5 to 300 sec"
        )
        val cooldownError = validateInt(
            raw = state.alertCooldownSec,
            min = 5,
            max = 300,
            emptyMessage = "Enter repeat interval",
            rangeMessage = "Use 5 to 300 sec"
        )

        val steadyStateError = if (state.mode == WorkoutMode.STEADY_STATE) {
            validateInt(
                raw = state.steadyStateHr,
                min = 60,
                max = 230,
                emptyMessage = "Enter target HR",
                rangeMessage = "Use 60 to 230 bpm"
            )
        } else {
            null
        }

        if (state.mode == WorkoutMode.DISTANCE_PROFILE) {
            var previousDistanceMeters = 0f
            state.segments.forEachIndexed { index, segment ->
                val distanceError = validatePositiveFloat(
                    raw = segment.distanceKm,
                    emptyMessage = "Enter distance",
                    invalidMessage = "Use a value > 0 km"
                )

                val hrError = validateInt(
                    raw = segment.targetHr,
                    min = 60,
                    max = 230,
                    emptyMessage = "Enter target",
                    rangeMessage = "Use 60 to 230 bpm"
                )

                var cumulativeError: String? = null
                val km = segment.distanceKm.toFloatOrNull()
                if (distanceError == null && km != null) {
                    val meters = km * 1000f
                    if (meters <= previousDistanceMeters) {
                        cumulativeError = "Distance must be greater than previous segment"
                    } else {
                        previousDistanceMeters = meters
                    }
                }

                segmentErrors[index] = SegmentInputError(
                    distanceKm = distanceError ?: cumulativeError,
                    targetHr = hrError
                )
            }
        }

        val hasModeSpecificErrors = when (state.mode) {
            WorkoutMode.STEADY_STATE -> steadyStateError != null
            WorkoutMode.DISTANCE_PROFILE -> segmentErrors.any { it.distanceKm != null || it.targetHr != null }
            WorkoutMode.FREE_RUN -> false
        }
        val hasGlobalErrors = bufferError != null || delayError != null || cooldownError != null

        val hasTarget = when (state.mode) {
            WorkoutMode.STEADY_STATE -> state.steadyStateHr.toIntOrNull() != null
            WorkoutMode.DISTANCE_PROFILE -> state.segments.isNotEmpty()
            WorkoutMode.FREE_RUN -> true
        }

        val canStartWorkout = if (state.mode == WorkoutMode.FREE_RUN) {
            true
        } else {
            !hasModeSpecificErrors && !hasGlobalErrors && hasTarget && buildConfigOrNull() != null
        }

        val blockedReason = when {
            hasModeSpecificErrors || hasGlobalErrors -> "Fix highlighted fields to continue."
            !hasTarget -> "Add at least one valid target zone."
            else -> null
        }

        _uiState.value = state.copy(
            validation = SetupValidationState(
                steadyStateHr = steadyStateError,
                segments = segmentErrors,
                bufferBpm = bufferError,
                alertDelaySec = delayError,
                alertCooldownSec = cooldownError,
                canStartWorkout = canStartWorkout,
                startBlockedReason = blockedReason
            )
        )
    }

    private fun validateInt(
        raw: String,
        min: Int,
        max: Int,
        emptyMessage: String,
        rangeMessage: String
    ): String? {
        if (raw.isBlank()) return emptyMessage
        val parsed = raw.toIntOrNull() ?: return rangeMessage
        if (parsed !in min..max) return rangeMessage
        return null
    }

    private fun validatePositiveFloat(
        raw: String,
        emptyMessage: String,
        invalidMessage: String
    ): String? {
        if (raw.isBlank()) return emptyMessage
        val parsed = raw.toFloatOrNull() ?: return invalidMessage
        if (parsed <= 0f) return invalidMessage
        return null
    }

    override fun onCleared() {
        scanTimeoutJob?.cancel()
        bleCollectJob?.cancel()
        previewPlayer?.destroy()
        previewPlayer = null
        super.onCleared()
    }
}
