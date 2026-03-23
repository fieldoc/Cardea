package com.hrcoach.ui.partner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrcoach.data.firebase.PartnerRepository
import com.hrcoach.data.firebase.RunCompletionPayload
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PartnerDetailUiState(
    val isLoading: Boolean = true,
    val distanceMeters: Double = 0.0,
    val routePolyline: String = "",
    val streakCount: Int = 0,
    val programPhase: String? = null,
    val sessionLabel: String? = null,
    val partnerName: String = "Partner",
    val notFound: Boolean = false
)

@HiltViewModel
class PartnerDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val partnerRepository: PartnerRepository
) : ViewModel() {

    private val completionId: String? = savedStateHandle["completionId"]

    private val _uiState = MutableStateFlow(PartnerDetailUiState())
    val uiState: StateFlow<PartnerDetailUiState> = _uiState

    init {
        loadPartnerDetail()
    }

    private fun loadPartnerDetail() {
        viewModelScope.launch {
            try {
                // If we have a specific completion ID, we'd fetch it directly.
                // For now, get the partner's most recent completion.
                val partnerId = partnerRepository.observePartnerId()
                    .let { flow ->
                        var id: String? = null
                        flow.collect { id = it; return@collect }
                        id
                    }

                if (partnerId == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false, notFound = true)
                    return@launch
                }

                val info = partnerRepository.getPartnerInfo(partnerId)
                val completions = partnerRepository.getPartnerCompletions(partnerId)
                val latest = completions.firstOrNull()

                _uiState.value = PartnerDetailUiState(
                    isLoading = false,
                    distanceMeters = latest?.distanceMeters ?: 0.0,
                    routePolyline = latest?.routePolyline ?: "",
                    streakCount = latest?.streakCount ?: 0,
                    programPhase = latest?.programPhase,
                    sessionLabel = latest?.sessionLabel,
                    partnerName = info?.displayName ?: "Partner",
                    notFound = latest == null
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, notFound = true)
            }
        }
    }
}
