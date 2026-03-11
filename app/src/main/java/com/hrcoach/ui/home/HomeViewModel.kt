package com.hrcoach.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.data.repository.BootcampRepository
import com.hrcoach.data.repository.WorkoutRepository
import com.hrcoach.service.WorkoutState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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
    val isSessionRunning: Boolean = false,
    val hasActiveBootcamp: Boolean = false,
    val nextSession: BootcampSessionEntity? = null,
    val currentWeekNumber: Int = 1,
    val sessionStreak: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val bootcampRepository: BootcampRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        workoutRepository.getAllWorkouts(),
        bootcampRepository.getActiveEnrollment(),
        WorkoutState.snapshot
    ) { workouts, enrollment, snapshot ->
        Triple(workouts, enrollment, snapshot)
    }
    .flatMapLatest { (workouts, enrollment, snapshot) ->
        flow {
            val zone = ZoneId.systemDefault()
            val now = Instant.now().atZone(zone)
            val weekStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            val thisWeek = workouts.count { it.startTime >= weekStart }

            val greeting = when (now.hour) {
                in 0..11  -> "Good morning"
                in 12..17 -> "Good afternoon"
                else      -> "Good evening"
            }

            // Only ACTIVE enrollment gets the hero card — PAUSED/GRADUATED fall back to no-bootcamp UI
            val activeEnrollment = enrollment?.takeIf {
                it.status == BootcampEnrollmentEntity.STATUS_ACTIVE
            }

            val nextSession = activeEnrollment?.let {
                bootcampRepository.getNextSession(it.id)
            }

            val sessionStreak = if (activeEnrollment != null) {
                val allSessions = bootcampRepository.getSessionsForEnrollmentOnce(activeEnrollment.id)
                computeSessionStreak(allSessions, activeEnrollment.startDate)
            } else 0

            emit(HomeUiState(
                greeting = greeting,
                lastWorkout = workouts.firstOrNull(),
                workoutsThisWeek = thisWeek,
                weeklyTarget = activeEnrollment?.runsPerWeek ?: 4,
                isSessionRunning = snapshot.isRunning,
                hasActiveBootcamp = activeEnrollment != null,
                nextSession = nextSession,
                currentWeekNumber = activeEnrollment?.let {
                    (it.currentPhaseIndex * 4) + it.currentWeekInPhase + 1
                } ?: 1,
                sessionStreak = sessionStreak,
            ))
        }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
