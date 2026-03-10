package com.hrcoach.ui.bootcamp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.repository.BootcampRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Lightweight ViewModel that exposes only the enrollment presence flag.
 * Used by NavGraph to decide whether the Training tab should land on
 * BootcampScreen (enrolled) or SetupScreen (not enrolled), without
 * incurring the full load cost of [BootcampViewModel].
 */
@HiltViewModel
class BootcampStatusViewModel @Inject constructor(
    bootcampRepository: BootcampRepository
) : ViewModel() {

    val hasActiveEnrollment: StateFlow<Boolean> =
        bootcampRepository.getActiveEnrollment()
            .map { it?.status == BootcampEnrollmentEntity.STATUS_ACTIVE }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}
