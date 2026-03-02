package com.hrcoach.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class HomeUiState(
    val greeting: String = "Good morning",
    val lastWorkout: WorkoutEntity? = null,
    val workoutsThisWeek: Int = 0,
    val weeklyTarget: Int = 4,
    val efficiencyPercent: Int = 0
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = workoutRepository.getAllWorkouts()
        .map { workouts ->
            val zone = ZoneId.systemDefault()
            val now = Instant.now().atZone(zone)
            val weekStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            val thisWeek = workouts.count { it.startTime >= weekStart }
            val target = 4
            val pct = ((thisWeek.toFloat() / target) * 100).toInt().coerceIn(0, 100)
            val greeting = when (now.hour) {
                in 0..11  -> "Good morning"
                in 12..17 -> "Good afternoon"
                else      -> "Good evening"
            }
            HomeUiState(
                greeting = greeting,
                lastWorkout = workouts.firstOrNull(),
                workoutsThisWeek = thisWeek,
                weeklyTarget = target,
                efficiencyPercent = pct
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
