package com.hrcoach.ui.account

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import com.hrcoach.service.WorkoutForegroundService
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import com.hrcoach.data.db.AchievementDao
import com.hrcoach.data.db.AchievementEntity
import com.hrcoach.data.db.BootcampDao
import com.hrcoach.data.db.TrackPointDao
import com.hrcoach.data.db.WorkoutMetricsDao
import com.hrcoach.data.firebase.CloudBackupManager
import com.hrcoach.data.firebase.CloudRestoreManager
import com.hrcoach.data.firebase.FirebaseAuthManager
import com.hrcoach.data.firebase.RestoreResult
import com.hrcoach.data.firebase.FirebasePartnerRepository
import com.hrcoach.data.firebase.FcmTokenManager
import com.hrcoach.data.repository.AdaptiveProfileRepository
import com.hrcoach.data.repository.AudioSettingsRepository
import com.hrcoach.data.repository.AutoPauseSettingsRepository
import com.hrcoach.data.repository.MapsSettingsRepository
import com.hrcoach.data.repository.UserProfileRepository
import com.hrcoach.data.repository.WorkoutRepository
import com.hrcoach.domain.model.AudioSettings
import com.hrcoach.domain.model.DistanceUnit
import com.hrcoach.domain.model.PartnerActivity
import com.hrcoach.domain.model.VoiceVerbosity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountUiState(
    val displayName: String = "Runner",
    val emblemId: String = "pulse",
    val totalWorkouts: Int = 0,
    val mapsApiKey: String = "",
    val mapsApiKeySaved: Boolean = false,
    val earconVolume: Int = 80,
    val voiceVolume: Int = 80,
    val voiceVerbosity: VoiceVerbosity = VoiceVerbosity.MINIMAL,
    val enableVibration: Boolean = true,
    val enableHalfwayReminder: Boolean = true,
    val enableKmSplits: Boolean = true,
    val enableWorkoutComplete: Boolean = true,
    val enableInZoneConfirm: Boolean = true,
    val maxHr: Int? = null,
    val maxHrInput: String = "",
    val maxHrSaved: Boolean = false,
    val maxHrError: String? = null,
    val hrMaxIsCalibrated: Boolean = false,
    val hrMaxCalibratedAtMs: Long? = null,
    val autoPauseEnabled: Boolean = true,
    val achievements: List<AchievementEntity> = emptyList(),
    val partners: List<PartnerActivity> = emptyList(),
    val partnerCount: Int = 0,
    val partnerNudgesEnabled: Boolean = true,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val isGoogleLinked: Boolean = false,
    val linkedEmail: String? = null,
    val isLinking: Boolean = false,
    val linkError: String? = null,
    val isRestoring: Boolean = false,
    val restoreResult: RestoreResult? = null,
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioRepo: AudioSettingsRepository,
    private val mapsRepo: MapsSettingsRepository,
    private val workoutRepo: WorkoutRepository,
    private val userProfileRepo: UserProfileRepository,
    private val autoPauseRepo: AutoPauseSettingsRepository,
    private val achievementDao: AchievementDao,
    private val adaptiveProfileRepo: AdaptiveProfileRepository,
    private val partnerRepository: FirebasePartnerRepository,
    private val firebaseAuthManager: FirebaseAuthManager,
    private val fcmTokenManager: FcmTokenManager,
    private val cloudBackupManager: CloudBackupManager,
    private val cloudRestoreManager: CloudRestoreManager,
    private val trackPointDao: TrackPointDao,
    private val workoutMetricsDao: WorkoutMetricsDao,
    private val bootcampDao: BootcampDao,
) : ViewModel() {

    private val _mapsKey      = MutableStateFlow("")
    private val _mapsKeySaved = MutableStateFlow(false)
    private val _volume       = MutableStateFlow(80)
    private val _voiceVolume  = MutableStateFlow(80)
    private val _verbosity    = MutableStateFlow(VoiceVerbosity.MINIMAL)
    private val _vibration    = MutableStateFlow(true)
    private val _halfwayReminder   = MutableStateFlow(true)
    private val _kmSplits          = MutableStateFlow(true)
    private val _workoutComplete   = MutableStateFlow(true)
    private val _inZoneConfirm     = MutableStateFlow(true)

    private val _maxHr              = MutableStateFlow<Int?>(null)
    private val _maxHrInput         = MutableStateFlow("")
    private val _maxHrSaved         = MutableStateFlow(false)
    private val _maxHrError         = MutableStateFlow<String?>(null)
    private val _hrMaxIsCalibrated  = MutableStateFlow(false)
    private val _hrMaxCalibratedAtMs = MutableStateFlow<Long?>(null)

    private val _distanceUnit = MutableStateFlow(DistanceUnit.KM)

    private val _autoPauseEnabled = MutableStateFlow(true)

    private val _displayName = MutableStateFlow("Runner")
    private val _emblemId = MutableStateFlow("pulse")

    private val _isGoogleLinked = MutableStateFlow(false)
    private val _linkedEmail = MutableStateFlow<String?>(null)
    private val _isLinking = MutableStateFlow(false)
    private val _linkError = MutableStateFlow<String?>(null)
    private val _isRestoring = MutableStateFlow(false)
    private val _restoreResult = MutableStateFlow<RestoreResult?>(null)

    private val _partners = partnerRepository.observePartners()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _partnerNudgesEnabled = MutableStateFlow(true)

    init {
        viewModelScope.launch {
            _mapsKey.value = mapsRepo.getMapsApiKey()
            val settings = audioRepo.getAudioSettings()
            _volume.value      = settings.earconVolume
            _voiceVolume.value = settings.voiceVolume
            _verbosity.value   = settings.voiceVerbosity
            _vibration.value = settings.enableVibration
            _halfwayReminder.value = settings.enableHalfwayReminder != false
            _kmSplits.value = settings.enableKmSplits != false
            _workoutComplete.value = settings.enableWorkoutComplete != false
            _inZoneConfirm.value = settings.enableInZoneConfirm != false
            _autoPauseEnabled.value = autoPauseRepo.isAutoPauseEnabled()
            _distanceUnit.value = DistanceUnit.fromString(userProfileRepo.getDistanceUnit())
            _displayName.value = userProfileRepo.getDisplayName()
            _emblemId.value = userProfileRepo.getEmblemId()
        }
        _maxHr.value = userProfileRepo.getMaxHr()
        _maxHrInput.value = _maxHr.value?.toString() ?: ""
        val adaptiveProfile = adaptiveProfileRepo.getProfile()
        _hrMaxIsCalibrated.value = adaptiveProfile.hrMaxIsCalibrated
        _hrMaxCalibratedAtMs.value = adaptiveProfile.hrMaxCalibratedAtMs
        _partnerNudgesEnabled.value = userProfileRepo.isPartnerNudgesEnabled()
        initFirebase()
        _isGoogleLinked.value = firebaseAuthManager.isGoogleLinked()
        _linkedEmail.value = firebaseAuthManager.getLinkedEmail()
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
        )
    }.combine(_voiceVolume) { base, voiceVol ->
        base.copy(voiceVolume = voiceVol)
    }.combine(_vibration) { base, vib ->
        base.copy(enableVibration = vib)
    }.combine(
        combine(_halfwayReminder, _kmSplits, _workoutComplete, _inZoneConfirm) { hw, km, wc, iz ->
            listOf(hw, km, wc, iz)
        }
    ) { base, cues ->
        base.copy(
            enableHalfwayReminder = cues[0],
            enableKmSplits = cues[1],
            enableWorkoutComplete = cues[2],
            enableInZoneConfirm = cues[3]
        )
    }.combine(
        combine(_maxHr, _maxHrInput, _maxHrSaved, _maxHrError) { hr, hrInput, hrSaved, hrError ->
            listOf<Any?>(hr, hrInput, hrSaved, hrError)
        }
    ) { base, parts ->
        base.copy(
            maxHr      = parts[0] as Int?,
            maxHrInput = parts[1] as String,
            maxHrSaved = parts[2] as Boolean,
            maxHrError = parts[3] as String?
        )
    }.combine(
        combine(_hrMaxIsCalibrated, _hrMaxCalibratedAtMs) { calibrated, atMs -> calibrated to atMs }
    ) { base, (calibrated, atMs) ->
        base.copy(hrMaxIsCalibrated = calibrated, hrMaxCalibratedAtMs = atMs)
    }.combine(_autoPauseEnabled) { base, autoPause ->
        base.copy(autoPauseEnabled = autoPause)
    }.combine(_distanceUnit) { base, unit ->
        base.copy(distanceUnit = unit)
    }.combine(
        combine(_displayName, _emblemId) { name, emblemId -> name to emblemId }
    ) { base, (name, emblemId) ->
        base.copy(displayName = name, emblemId = emblemId)
    }.combine(achievementDao.getAllAchievements()) { base, achievements ->
        base.copy(achievements = achievements)
    }.combine(
        combine(_partners, _partnerNudgesEnabled) { partners, nudgesEnabled -> partners to nudgesEnabled }
    ) { base, (partners, nudgesEnabled) ->
        base.copy(
            partners = partners,
            partnerCount = partners.size,
            partnerNudgesEnabled = nudgesEnabled,
        )
    }.combine(
        combine(_isGoogleLinked, _linkedEmail, _isLinking) { linked, email, linking ->
            Triple(linked, email, linking)
        }
    ) { base, (linked, email, linking) ->
        base.copy(isGoogleLinked = linked, linkedEmail = email, isLinking = linking)
    }.combine(
        combine(_linkError, _isRestoring, _restoreResult) { error, restoring, result ->
            Triple(error, restoring, result)
        }
    ) { base, (error, restoring, result) ->
        base.copy(linkError = error, isRestoring = restoring, restoreResult = result)
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
    fun setVoiceVolume(v: Float) { _voiceVolume.value = ((v / 5f).toInt() * 5).coerceIn(0, 100) }
    fun setVerbosity(v: VoiceVerbosity) { _verbosity.value = v }
    fun setVibration(v: Boolean) { _vibration.value = v }
    fun setEnableHalfwayReminder(v: Boolean) { _halfwayReminder.value = v; saveAudioSettings() }
    fun setEnableKmSplits(v: Boolean) { _kmSplits.value = v; saveAudioSettings() }
    fun setEnableWorkoutComplete(v: Boolean) { _workoutComplete.value = v; saveAudioSettings() }
    fun setEnableInZoneConfirm(v: Boolean) { _inZoneConfirm.value = v; saveAudioSettings() }

    fun setDistanceUnit(unit: DistanceUnit) {
        _distanceUnit.value = unit
        userProfileRepo.setDistanceUnit(if (unit == DistanceUnit.MI) "mi" else "km")
        viewModelScope.launch { cloudBackupManager.syncProfile() }
    }

    fun setAutoPauseEnabled(enabled: Boolean) {
        _autoPauseEnabled.value = enabled
        autoPauseRepo.setAutoPauseEnabled(enabled)
        viewModelScope.launch { cloudBackupManager.syncSettings() }
    }

    fun saveAudioSettings() {
        viewModelScope.launch {
            audioRepo.saveAudioSettings(
                AudioSettings(
                    earconVolume    = _volume.value,
                    voiceVolume     = _voiceVolume.value,
                    voiceVerbosity  = _verbosity.value,
                    enableVibration = _vibration.value,
                    enableHalfwayReminder = _halfwayReminder.value,
                    enableKmSplits = _kmSplits.value,
                    enableWorkoutComplete = _workoutComplete.value,
                    enableInZoneConfirm = _inZoneConfirm.value,
                )
            )
            // Notify the running workout service so settings take effect immediately.
            // startService is a no-op when the service isn't running.
            runCatching {
                context.startService(
                    Intent(context, WorkoutForegroundService::class.java).apply {
                        action = WorkoutForegroundService.ACTION_RELOAD_AUDIO_SETTINGS
                    }
                )
            }
            cloudBackupManager.syncSettings()
        }
    }

    fun setMaxHrInput(value: String) {
        _maxHrInput.value = value
        _maxHrSaved.value = false
        _maxHrError.value = null
    }

    fun setDisplayName(name: String) {
        _displayName.value = name.take(20)
    }

    fun setEmblemId(id: String) {
        _emblemId.value = id
    }

    fun saveProfile() {
        userProfileRepo.setDisplayName(_displayName.value)
        userProfileRepo.setEmblemId(_emblemId.value)
        viewModelScope.launch { cloudBackupManager.syncProfile() }
    }

    fun discardProfileChanges() {
        _displayName.value = userProfileRepo.getDisplayName()
        _emblemId.value = userProfileRepo.getEmblemId()
    }

    fun saveMaxHr() {
        val value = _maxHrInput.value.toIntOrNull()
        if (value == null) {
            _maxHrError.value = "Please enter a valid number"
            return
        }
        if (value !in 100..220) {
            _maxHrError.value = "Max HR must be between 100 and 220"
            return
        }
        _maxHrError.value = null
        userProfileRepo.setMaxHr(value)
        val profile = adaptiveProfileRepo.getProfile()
        adaptiveProfileRepo.saveProfile(profile.copy(hrMax = value))
        _maxHr.value = value
        _maxHrSaved.value = true
        viewModelScope.launch { cloudBackupManager.syncProfile() }
    }

    fun initFirebase() {
        viewModelScope.launch {
            firebaseAuthManager.ensureSignedIn()
            fcmTokenManager.refreshToken()
            partnerRepository.syncProfile()
        }
    }

    suspend fun createInviteCode(): String {
        return partnerRepository.createInviteCode()
    }

    suspend fun redeemInviteCode(code: String): PartnerActivity? {
        return partnerRepository.redeemInviteCode(code)
    }

    fun removePartner(partnerId: String) {
        viewModelScope.launch { partnerRepository.removePartner(partnerId) }
    }

    fun setPartnerNudgesEnabled(enabled: Boolean) {
        _partnerNudgesEnabled.value = enabled
        userProfileRepo.setPartnerNudgesEnabled(enabled)
    }

    fun linkGoogleAccount() {
        viewModelScope.launch {
            _isLinking.value = true
            _linkError.value = null
            try {
                when (firebaseAuthManager.linkGoogleAccount()) {
                    FirebaseAuthManager.LinkResult.FreshLink -> {
                        _isGoogleLinked.value = true
                        _linkedEmail.value = firebaseAuthManager.getLinkedEmail()
                        performFullBackup()
                    }
                    FirebaseAuthManager.LinkResult.ExistingAccount -> {
                        // User's Google account was already linked to a Cardea profile
                        // (e.g. from another device). Sign-in succeeded — restore data.
                        _isGoogleLinked.value = true
                        _linkedEmail.value = firebaseAuthManager.getLinkedEmail()
                        _isLinking.value = false
                        _isRestoring.value = true
                        runCatching {
                            val result = cloudRestoreManager.restore()
                            _restoreResult.value = result
                        }.onFailure { e ->
                            _linkError.value = "Signed in, but restore failed: ${e.message}"
                        }
                        _isRestoring.value = false
                        return@launch
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _linkError.value = "Failed to link: ${e.message}"
            }
            _isLinking.value = false
        }
    }

    fun restoreFromCloud() {
        viewModelScope.launch {
            _isRestoring.value = true
            runCatching {
                val result = cloudRestoreManager.restore()
                _restoreResult.value = result
            }.onFailure { e ->
                _linkError.value = "Restore failed: ${e.message}"
            }
            _isRestoring.value = false
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { firebaseAuthManager.signOut() }
            _isGoogleLinked.value = false
            _linkedEmail.value = null
        }
    }

    fun clearRestoreResult() {
        _restoreResult.value = null
    }

    fun clearLinkError() {
        _linkError.value = null
    }

    private suspend fun performFullBackup() {
        val workouts = workoutRepo.getAllWorkoutsOnce()
        val trackPointsByWorkout = workouts.associate { w ->
            w.id to trackPointDao.getPointsForWorkout(w.id)
        }
        val metrics = workoutMetricsDao.getAllMetricsOnce()
        val enrollment = bootcampDao.getActiveEnrollmentOnce()
        val sessions = enrollment?.let { bootcampDao.getSessionsForEnrollmentOnce(it.id) } ?: emptyList()
        val achievements = achievementDao.getAllAchievementsOnce()
        cloudBackupManager.performFullBackup(workouts, trackPointsByWorkout, metrics, enrollment, sessions, achievements)
    }
}
