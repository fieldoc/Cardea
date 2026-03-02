package com.hrcoach.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrcoach.service.WorkoutSnapshot
import com.hrcoach.service.WorkoutState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class WorkoutViewModel @Inject constructor() : ViewModel() {
    val uiState: StateFlow<WorkoutSnapshot> = WorkoutState.snapshot
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = WorkoutState.snapshot.value
        )
}
