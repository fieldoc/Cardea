package com.hrcoach.ui.postrun

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrcoach.data.db.AchievementDao
import com.hrcoach.data.db.AchievementEntity
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.data.repository.AdaptiveProfileRepository
import com.hrcoach.data.repository.WorkoutRepository
import com.hrcoach.data.repository.WorkoutMetricsRepository
import com.hrcoach.domain.achievement.AchievementEvaluator
import com.hrcoach.domain.bootcamp.BootcampSessionCompleter
import com.hrcoach.domain.engine.MetricsCalculator
import com.hrcoach.domain.engine.TuningDirection
import com.hrcoach.service.WorkoutState
import com.hrcoach.domain.model.WorkoutAdaptiveMetrics
import com.hrcoach.util.formatDurationSeconds
import com.hrcoach.util.formatWorkoutDate
import com.hrcoach.data.repository.UserProfileRepository
import com.hrcoach.domain.model.DistanceUnit
import com.hrcoach.util.distanceUnitLabel
import com.hrcoach.util.metersToUnit
import com.hrcoach.util.recordedAtMs
import com.hrcoach.ui.common.MetricLabels
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.util.Log
import javax.inject.Inject
import kotlin.math.abs

data class PostRunComparison(
    val title: String,
    val value: String,
    val delta: String? = null,
    val insight: String? = null,
    val positive: Boolean? = null
)

data class PostRunSummaryUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val titleText: String = "Post-run Summary",
    val distanceText: String = "--",
    val durationText: String = "--",
    val avgHrText: String = "--",
    val similarRunCount: Int = 0,
    val comparisons: List<PostRunComparison> = emptyList(),
    val bootcampProgressLabel: String? = null,
    val bootcampWeekComplete: Boolean = false,
    val isBootcampRun: Boolean = false,
    // workoutEndTimeMs > 0 on fresh runs; Screen derives HRR visibility dynamically from this
    val workoutEndTimeMs: Long = 0L,
    val newAchievements: List<AchievementEntity> = emptyList(),
    // Non-null when HRmax was auto-calibrated during this session: Pair(oldMax, newMax)
    val hrMaxDelta: Pair<Int, Int>? = null,
    // Per-workout counts of coaching cues fired, parsed from WorkoutMetrics.cueCountsJson.
    // Rendered by SoundsHeardSection on the first three runs only.
    val cueCounts: Map<com.hrcoach.domain.model.CoachingEvent, Int> = emptyMap(),
    val showSoundsRecap: Boolean = false,
    // ── New fields for inline route map ──
    val trackPoints: List<com.hrcoach.data.db.TrackPointEntity> = emptyList(),
    val workoutConfig: com.hrcoach.domain.model.WorkoutConfig? = null,
    val isMapsEnabled: Boolean = false,
)

@HiltViewModel
class PostRunSummaryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val workoutMetricsRepository: WorkoutMetricsRepository,
    private val bootcampSessionCompleter: BootcampSessionCompleter,
    private val achievementEvaluator: AchievementEvaluator,
    private val achievementDao: AchievementDao,
    private val adaptiveProfileRepository: AdaptiveProfileRepository,
    private val userProfileRepository: UserProfileRepository,
    private val mapsSettingsRepository: com.hrcoach.data.repository.MapsSettingsRepository,
    private val hrrAudio: HrrAudio
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostRunSummaryUiState())
    val uiState: StateFlow<PostRunSummaryUiState> = _uiState.asStateFlow()

    private val workoutId: Long? = savedStateHandle.get<Long>("workoutId")

    init {
        load()
    }

    /** Called by the Screen when the 120s HRR cooldown window begins on a fresh run. */
    fun onHrrWindowStarted() {
        hrrAudio.playHrrStart()
    }

    /** Called by the Screen when the 120s HRR cooldown window ends. */
    fun onHrrWindowEnded() {
        hrrAudio.playHrrComplete()
    }

    private fun load() {
        val id = workoutId
        if (id == null) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Workout summary unavailable.") }
            return
        }

        val fresh = savedStateHandle.get<Boolean>("fresh") ?: false

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // --- 1. Essential: load workout summary (must succeed for screen to display) ---
            // Fetch all workouts once here and pass down — avoids a second full-table scan
            // in the achievement block (fix for double-query audit finding).
            val allWorkouts = workoutRepository.getAllWorkoutsOnce()

            val summaryLoaded = runCatching {
                val workout = workoutRepository.getWorkoutById(id)
                    ?: error("Workout not found.")
                val currentMetrics = getOrDeriveMetrics(workout)
                val similar = loadSimilarWorkouts(workout, allWorkouts)
                val comparisons = buildComparisons(
                    currentMetrics = currentMetrics,
                    similarMetrics = similar.mapNotNull { it.second }
                )
                // Load track points ONCE — shared for avgHr fallback and for map rendering.
                val allTrackPoints = workoutRepository.getTrackPoints(workout.id)
                val avgHr = currentMetrics?.avgHr ?: allTrackPoints
                    .map { it.heartRate }
                    .takeIf { it.isNotEmpty() }
                    ?.average()
                    ?.toFloat()
                val isMapsEnabled = hasAnyMapsApiKey()
                val parsedConfig = runCatching {
                    com.hrcoach.util.JsonCodec.gson.fromJson(
                        workout.targetConfig,
                        com.hrcoach.domain.model.WorkoutConfig::class.java
                    )
                }.getOrNull()

                // Parse cue counts from the current workout's metrics, and gate the recap
                // to the user's first 3 non-simulated workouts. The counts live in
                // WorkoutMetrics.cueCountsJson (populated by WFS at stop time).
                val cueCounts = parseCueCounts(currentMetrics?.cueCountsJson)
                val lifetimeWorkoutCount = runCatching {
                    workoutRepository.countNonSimulated()
                }.getOrDefault(0)
                val showSoundsRecap = cueCounts.isNotEmpty() && lifetimeWorkoutCount in 1..3

                _uiState.value = PostRunSummaryUiState(
                    isLoading = false,
                    errorMessage = null,
                    titleText = formatWorkoutDate(workout.startTime),
                    distanceText = run {
                        val unit = DistanceUnit.fromString(userProfileRepository.getDistanceUnit())
                        String.format("%.2f %s", metersToUnit(workout.totalDistanceMeters, unit), distanceUnitLabel(unit))
                    },
                    durationText = formatDurationSeconds(activeDurationSecondsOf(workout)),
                    avgHrText = avgHr?.let { "${it.toInt()} bpm" } ?: "--",
                    similarRunCount = similar.size,
                    comparisons = comparisons,
                    workoutEndTimeMs = workout.endTime,
                    cueCounts = cueCounts,
                    showSoundsRecap = showSoundsRecap,
                    trackPoints = allTrackPoints,
                    workoutConfig = parsedConfig,
                    isMapsEnabled = isMapsEnabled,
                )
            }.onFailure {
                Log.e("PostRunSummaryVM", "Failed to load workout summary", it)
                _uiState.update { s ->
                    s.copy(isLoading = false, errorMessage = it.message ?: "Unable to load post-run summary.")
                }
            }.isSuccess

            // --- Side effects below run even if summary display failed ---
            if (fresh) {
                // 2a. HRmax delta — only consume when the summary loaded successfully so the
                //     card is actually visible. On failure, leave the delta in WorkoutState
                //     rather than silently discarding it.
                if (summaryLoaded) {
                    val hrMaxDelta = WorkoutState.snapshot.value.hrMaxUpdatedDelta
                    WorkoutState.clearHrMaxUpdatedDelta()
                    if (hrMaxDelta != null) {
                        _uiState.update { it.copy(hrMaxDelta = hrMaxDelta) }
                    }
                }

                // 2b. Best-effort: complete bootcamp session
                val pendingId = WorkoutState.snapshot.value.pendingBootcampSessionId
                if (pendingId != null) {
                    runCatching {
                        val tuningDirection = adaptiveProfileRepository.getProfile().lastTuningDirection
                            ?: TuningDirection.HOLD
                        val result = bootcampSessionCompleter.complete(
                            workoutId = id,
                            pendingSessionId = pendingId,
                            tuningDirection = tuningDirection
                        )
                        if (result.completed) {
                            _uiState.update { it.copy(
                                isBootcampRun = true,
                                bootcampProgressLabel = result.progressLabel,
                                bootcampWeekComplete = result.weekComplete
                            ) }
                        }
                    }.onFailure {
                        Log.e("PostRunSummaryVM", "Bootcamp session completion failed", it)
                    }
                    // Always clear pending ID — the session either completed or is
                    // unrecoverable (WorkoutState is in-memory; it won't survive an app restart
                    // regardless). Leaving it set causes stale-state bugs on the next run.
                    WorkoutState.setPendingBootcampSessionId(null)
                }

                // 3. Best-effort: evaluate achievements (reuses allWorkouts fetched above)
                if (summaryLoaded) {
                    runCatching {
                        val totalKm = allWorkouts.sumOf { it.totalDistanceMeters.toDouble() } / 1000.0
                        achievementEvaluator.evaluateDistance(totalKm, id)

                        val streak = computeWorkoutStreak(allWorkouts)
                        achievementEvaluator.evaluateStreak(streak, id)

                        val newAchievements = achievementDao.getUnshownAchievements()
                        if (newAchievements.isNotEmpty()) {
                            achievementDao.markShown(newAchievements.map { it.id })
                            _uiState.update { it.copy(newAchievements = newAchievements) }
                        }
                    }.onFailure {
                        Log.e("PostRunSummaryVM", "Achievement evaluation failed", it)
                    }
                }

                // 4. Always: clear stale completedWorkoutId so the next workout start
                //    triggers a clean LaunchedEffect transition in NavGraph.
                WorkoutState.clearCompletedWorkoutId()
            }
        }
    }

    private suspend fun getOrDeriveMetrics(workout: WorkoutEntity): WorkoutAdaptiveMetrics? {
        val stored = workoutMetricsRepository.getWorkoutMetrics(workout.id)
        if (stored != null) return stored

        val trackPoints = workoutRepository.getTrackPoints(workout.id)
        val derived = MetricsCalculator.deriveFullMetrics(
            workoutId = workout.id,
            recordedAtMs = workout.recordedAtMs,
            trackPoints = trackPoints
        )
        if (derived != null) {
            workoutMetricsRepository.saveWorkoutMetrics(derived)
        }
        return derived
    }

    private suspend fun loadSimilarWorkouts(
        workout: WorkoutEntity,
        allWorkouts: List<WorkoutEntity>
    ): List<Pair<WorkoutEntity, WorkoutAdaptiveMetrics?>> {
        if (workout.totalDistanceMeters <= 0f) return emptyList()
        val similar = allWorkouts.filter { candidate ->
            candidate.id != workout.id &&
                candidate.mode == workout.mode &&
                candidate.totalDistanceMeters > 0f &&
                isSimilarDistance(workout.totalDistanceMeters, candidate.totalDistanceMeters)
        }
        return similar.map { candidate ->
            candidate to getOrDeriveMetrics(candidate)
        }
    }

    private fun isSimilarDistance(baseMeters: Float, candidateMeters: Float): Boolean {
        if (baseMeters <= 0f) return false
        val ratio = abs(candidateMeters - baseMeters) / baseMeters
        return ratio <= 0.2f
    }

    private fun buildComparisons(
        currentMetrics: WorkoutAdaptiveMetrics?,
        similarMetrics: List<WorkoutAdaptiveMetrics>
    ): List<PostRunComparison> {
        if (currentMetrics == null) return emptyList()
        val items = mutableListOf<PostRunComparison>()

        val similarHrAtSix = similarMetrics.mapNotNull { it.hrAtSixMinPerKm }
        currentMetrics.hrAtSixMinPerKm?.let { current ->
            val delta = similarHrAtSix.takeIf { it.isNotEmpty() }?.let { current - it.average().toFloat() }
            val deltaText = delta?.takeIf { abs(it) >= 2f }?.let {
                val absValue = abs(it).toInt()
                if (it < 0f) "$absValue bpm lower than similar average" else "$absValue bpm higher than similar average"
            }
            items += PostRunComparison(
                title = "HR @ 6:00/km",
                value = "${current.toInt()} bpm",
                delta = deltaText,
                positive = delta?.let { it < 0f }
            )
        }

        val similarEf = similarMetrics.mapNotNull { it.efficiencyFactor }
        currentMetrics.efficiencyFactor?.let { current ->
            val delta = similarEf.takeIf { it.isNotEmpty() }?.let { current - it.average().toFloat() }
            val deltaText = delta?.takeIf { abs(it) >= 0.03f }?.let {
                val sign = if (it >= 0f) "+" else ""
                "$sign${String.format("%.2f", it)} vs similar average"
            }
            items += PostRunComparison(
                title = MetricLabels.EFFICIENCY_FACTOR,
                value = String.format("%.2f", current),
                delta = deltaText,
                positive = delta?.let { it >= 0f }
            )
        }

        val similarDecoupling = similarMetrics.mapNotNull { it.aerobicDecoupling }
        currentMetrics.aerobicDecoupling?.let { current ->
            val delta = similarDecoupling.takeIf { it.isNotEmpty() }?.let { current - it.average().toFloat() }
            val deltaText = delta?.takeIf { abs(it) >= 0.8f }?.let {
                val sign = if (it >= 0f) "+" else ""
                "$sign${String.format("%.1f", it)}% vs similar average"
            }
            val insight = if (current < 5f) {
                "Good aerobic endurance"
            } else {
                "Aerobic endurance can improve"
            }
            items += PostRunComparison(
                title = MetricLabels.AEROBIC_DECOUPLING,
                value = String.format("%.1f%%", current),
                delta = deltaText,
                insight = insight,
                positive = current < 5f
            )
        }

        if (currentMetrics.responseLagSec > 0f) {
            items += PostRunComparison(
                title = "Recovery Lag",
                value = "${currentMetrics.responseLagSec.toInt()} s",
                insight = "Estimated delay between pace change and HR response."
            )
        }

        if (currentMetrics.longTermHrTrimBpm != 0f) {
            val trim = currentMetrics.longTermHrTrimBpm
            val sign = if (trim >= 0f) "+" else ""
            items += PostRunComparison(
                title = "Adaptive Trim",
                value = "$sign${String.format("%.1f", trim)} bpm",
                insight = "Long-term personalization offset currently applied to projections."
            )
        }

        if (currentMetrics.efFirstHalf != null && currentMetrics.efSecondHalf != null) {
            items += PostRunComparison(
                title = "Efficiency Split",
                value = "${String.format("%.2f", currentMetrics.efFirstHalf)} -> ${String.format("%.2f", currentMetrics.efSecondHalf)}",
                insight = "Efficiency by half helps explain drift and endurance consistency."
            )
        }

        return items
    }

    private fun hasAnyMapsApiKey(): Boolean {
        val runtimeKey = mapsSettingsRepository.getMapsApiKey()
        val builtIn = com.hrcoach.BuildConfig.MAPS_API_KEY
        return runtimeKey.isNotBlank() ||
            (builtIn.isNotBlank() && builtIn != "YOUR_API_KEY_HERE")
    }

    internal companion object {
        /**
         * Counts the number of most-recent consecutive calendar days on which the
         * user ran at least once. Same-day runs do not advance the streak; a
         * skipped calendar day breaks it. Uses the local-system timezone because
         * "consecutive days" is a human-wall-clock concept, not a UTC one.
         *
         * Raw hour-delta is insufficient: a Mon 11pm → Wed 1am run (26h gap)
         * would incorrectly count as a continuous streak even though Tuesday
         * was skipped.
         */
        fun computeWorkoutStreak(workouts: List<WorkoutEntity>): Int {
            if (workouts.isEmpty()) return 0
            val zone = java.time.ZoneId.systemDefault()
            val uniqueDays = workouts
                .map {
                    java.time.Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate()
                }
                .toSortedSet()
                .toList()
                .reversed() // newest day first
            var streak = 1
            for (i in 0 until uniqueDays.lastIndex) {
                val gapDays = java.time.temporal.ChronoUnit.DAYS
                    .between(uniqueDays[i + 1], uniqueDays[i])
                if (gapDays != 1L) break
                streak++
            }
            return streak
        }
    }
}

private fun activeDurationSecondsOf(workout: WorkoutEntity): Long =
    workout.activeDurationSeconds.takeIf { it > 0L }
        ?: ((workout.endTime - workout.startTime).coerceAtLeast(0L) / 1000L)

/**
 * Parses the `cueCountsJson` blob written by WFS at stop time (see CoachingAudioManager
 * .consumeCueCounts). Returns a best-effort map, dropping any unknown enum keys.
 * Gson deserializes numeric values as Double by default, hence the `.toInt()`.
 */
internal fun parseCueCounts(json: String?): Map<com.hrcoach.domain.model.CoachingEvent, Int> {
    if (json.isNullOrBlank()) return emptyMap()
    return runCatching {
        @Suppress("UNCHECKED_CAST")
        val raw = com.google.gson.Gson()
            .fromJson(json, Map::class.java) as? Map<String, Any?>
            ?: return@runCatching emptyMap<com.hrcoach.domain.model.CoachingEvent, Int>()
        raw.mapNotNull { (k, v) ->
            val event = runCatching {
                com.hrcoach.domain.model.CoachingEvent.valueOf(k)
            }.getOrNull() ?: return@mapNotNull null
            val count = when (v) {
                is Number -> v.toInt()
                is String -> v.toIntOrNull() ?: return@mapNotNull null
                else -> return@mapNotNull null
            }
            if (count <= 0) null else event to count
        }.toMap()
    }.getOrDefault(emptyMap())
}
