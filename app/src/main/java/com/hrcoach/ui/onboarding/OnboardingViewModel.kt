package com.hrcoach.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrcoach.data.repository.OnboardingRepository
import com.hrcoach.data.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class WeightUnit { LBS, KG }

data class OnboardingUiState(
    val currentPage: Int = 0,
    val age: String = "",
    val weight: String = "",
    val weightUnit: WeightUnit = WeightUnit.LBS,
    val estimatedHrMax: Int? = null,
    val hrMaxOverride: String = "",
    val isHrMaxOverrideExpanded: Boolean = false,
    val bluetoothPermissionGranted: Boolean = false,
    val locationPermissionGranted: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingRepository: OnboardingRepository,
    private val userProfileRepository: UserProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onAgeChanged(value: String) {
        val age = value.toIntOrNull()
        val estimated = if (age != null && age in 13..99) 220 - age else null
        _uiState.update { it.copy(age = value, estimatedHrMax = estimated) }
    }

    fun onWeightChanged(value: String) {
        _uiState.update { it.copy(weight = value) }
    }

    fun toggleWeightUnit() {
        _uiState.update {
            it.copy(weightUnit = if (it.weightUnit == WeightUnit.LBS) WeightUnit.KG else WeightUnit.LBS)
        }
    }

    fun onHrMaxOverrideChanged(value: String) {
        _uiState.update { it.copy(hrMaxOverride = value) }
    }

    fun toggleHrMaxOverride() {
        _uiState.update { it.copy(isHrMaxOverrideExpanded = !it.isHrMaxOverrideExpanded) }
    }

    fun effectiveHrMax(): Int? {
        val override = _uiState.value.hrMaxOverride.toIntOrNull()
        if (override != null && override in 120..220) return override
        return _uiState.value.estimatedHrMax
    }

    fun isAgeValid(): Boolean {
        val age = _uiState.value.age.toIntOrNull() ?: return false
        return age in 13..99
    }

    fun saveProfile() {
        viewModelScope.launch {
            val state = _uiState.value
            val age = state.age.toIntOrNull() ?: return@launch
            if (age !in 13..99) return@launch

            userProfileRepository.setAge(age)

            val weight = state.weight.toIntOrNull()
            if (weight != null && weight in 27..400) {
                userProfileRepository.setWeight(weight)
            }
            userProfileRepository.setWeightUnit(
                if (state.weightUnit == WeightUnit.LBS) "lbs" else "kg"
            )

            val hrMax = effectiveHrMax()
            if (hrMax != null && hrMax in 100..220) {
                userProfileRepository.setMaxHr(hrMax)
            }
        }
    }

    fun onPermissionResult(type: PermissionType, granted: Boolean) {
        _uiState.update {
            when (type) {
                PermissionType.BLUETOOTH -> it.copy(bluetoothPermissionGranted = granted)
                PermissionType.LOCATION -> it.copy(locationPermissionGranted = granted)
                PermissionType.NOTIFICATION -> it.copy(notificationPermissionGranted = granted)
            }
        }
    }

    fun completeOnboarding() {
        onboardingRepository.setOnboardingCompleted()
    }
}

enum class PermissionType { BLUETOOTH, LOCATION, NOTIFICATION }

@HiltViewModel
class OnboardingSplashViewModel @Inject constructor(
    val onboardingRepository: OnboardingRepository,
) : ViewModel()
