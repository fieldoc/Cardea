package com.hrcoach.ui.progress

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrcoach.data.db.TrackPointEntity
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.data.repository.WorkoutMetricsRepository
import com.hrcoach.data.repository.WorkoutRepository
import com.hrcoach.domain.engine.MetricsCalculator
import com.hrcoach.domain.model.WorkoutAdaptiveMetrics
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.ui.charts.BarEntry
import com.hrcoach.ui.charts.CalendarDay
import com.hrcoach.ui.charts.PieSlice
import com.hrcoach.ui.charts.ScatterPoint
import com.hrcoach.ui.charts.TrendInfo
import com.hrcoach.util.JsonCodec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.abs

enum class ProgressFilter { ALL, STEADY_STATE, DISTANCE_PROFILE }

data class ProgressTrendPoint(
    val workoutId: Long,
    val timestampMs: Long,
    val value: Float
)

data class ProgressUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val filter: ProgressFilter = ProgressFilter.ALL,
    val hasData: Boolean = false,

    // Section 1 – Killer Metric
    val heartbeatsPerKmBars: List<BarEntry> = emptyList(),
    val heartbeatsPerKmTrend: TrendInfo? = null,
    val heartbeatsPerKmHero: String = "--",

    // Section 2 – Performance Fingerprint
    val paceAtFixedHrSeries: List<ProgressTrendPoint> = emptyList(),
    val paceReferenceHrBpm: Int = 150,
    val speedVsHrPoints: List<ScatterPoint> = emptyList(),

    // Section 3 – Cardiovascular Fitness
    val aerobicEfficiencySeries: List<ProgressTrendPoint> = emptyList(),
    val restingHrSeries: List<ProgressTrendPoint> = emptyList(),
    val hrRecoverySeries: List<ProgressTrendPoint> = emptyList(),
    val vo2MaxSeries: List<ProgressTrendPoint> = emptyList(),

    // Section 4 – Training Load
    val weeklyDistanceBars: List<BarEntry> = emptyList(),
    val weeklyLoadBars: List<BarEntry> = emptyList(),
    val zoneDistribution: List<PieSlice> = emptyList(),

    // Section 5 – Consistency
    val calendarDays: List<CalendarDay> = emptyList()
)

private data class WorkoutWithMetrics(
    val workout: WorkoutEntity,
    val metrics: WorkoutAdaptiveMetrics?,
    val config: WorkoutConfig?
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val workoutMetricsRepository: WorkoutMetricsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProgressUiState())
    val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()

    private var allWorkouts: List<WorkoutWithMetrics> = emptyList()
    private val restingHrCache = mutableMapOf<Long, Float?>()
    private val trackPointCache = mutableMapOf<Long, List<TrackPointEntity>>()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                val workouts = workoutRepository.getAllWorkoutsOnce().sortedBy { it.startTime }
                val workoutIds = workouts.map { it.id }
                workoutMetricsRepository.pruneWorkoutMetrics(workoutIds.toSet())
                val storedMetrics = workoutMetricsRepository.getWorkoutMetrics(workoutIds)

                val enriched = workouts.map { workout ->
                    val config = parseConfig(workout.targetConfig)
                    val targetHr = extractTargetHr(config)
                    val metrics = storedMetrics[workout.id]
                        ?: deriveAndPersistMetrics(workout, targetHr)
                    WorkoutWithMetrics(workout = workout, metrics = metrics, config = config)
                }
                allWorkouts = enriched
                rebuildUi()
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Unable to load progress."
                )
            }
        }
    }

    fun setFilter(filter: ProgressFilter) {
        if (_uiState.value.filter == filter) return
        _uiState.value = _uiState.value.copy(filter = filter)
        viewModelScope.launch { rebuildUi() }
    }

    private suspend fun deriveAndPersistMetrics(
        workout: WorkoutEntity,
        targetHr: Float?
    ): WorkoutAdaptiveMetrics? {
        val points = workoutRepository.getTrackPoints(workout.id)
        trackPointCache[workout.id] = points
        val recordedAtMs = if (workout.endTime > workout.startTime) workout.endTime else workout.startTime
        val derived = MetricsCalculator.deriveFullMetrics(
            workoutId = workout.id,
            recordedAtMs = recordedAtMs,
            trackPoints = points,
            targetHr = targetHr
        )
        if (derived != null) workoutMetricsRepository.saveWorkoutMetrics(derived)
        return derived
    }

    private suspend fun rebuildUi() {
        val zone = ZoneId.systemDefault()
        val currentMonth = YearMonth.now()
        val today = LocalDate.now(zone)

        val filtered = allWorkouts
            .filter { item ->
                when (_uiState.value.filter) {
                    ProgressFilter.ALL -> true
                    ProgressFilter.STEADY_STATE -> item.workout.mode == WorkoutMode.STEADY_STATE.name
                    ProgressFilter.DISTANCE_PROFILE -> item.workout.mode == WorkoutMode.DISTANCE_PROFILE.name
                }
            }
            .sortedBy { it.workout.startTime }

        val recent30 = filtered.takeLast(30)

        // Fetch missing track points for zone distribution + resting HR
        for (item in recent30) {
            if (!restingHrCache.containsKey(item.workout.id)) {
                val pts = trackPointCache[item.workout.id]
                    ?: workoutRepository.getTrackPoints(item.workout.id)
                        .also { trackPointCache[item.workout.id] = it }
                restingHrCache[item.workout.id] = MetricsCalculator.computeRestingHrProxy(pts)
            }
        }

        // Pace reference HR = mode of targetHr values
        val paceRefHr = filtered
            .mapNotNull { extractTargetHr(it.config)?.toInt() }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }?.key ?: 150

        // ── Section 1: Heartbeats/km monthly bars (last 6 months) ──────────
        val hbkmBars = (5 downTo 0).map { i ->
            val month = currentMonth.minusMonths(i.toLong())
            val avg = filtered
                .filter { item ->
                    val ym = YearMonth.from(
                        Instant.ofEpochMilli(item.workout.startTime).atZone(zone).toLocalDate()
                    )
                    ym == month
                }
                .mapNotNull { it.metrics?.heartbeatsPerKm }
                .takeIf { it.isNotEmpty() }
                ?.average()?.toFloat()
            BarEntry(label = month.month.name.take(3), value = avg ?: 0f)
        }
        // Indices: 0=5mo ago, 1=4mo, 2=3mo, 3=2mo, 4=1mo, 5=now
        val lastMonthHbkm = hbkmBars.lastOrNull { it.value > 0f }?.value
        val threeMonthsAgoHbkm = hbkmBars.getOrNull(2)?.value?.takeIf { it > 0f }
        val hbkmTrend = if (lastMonthHbkm != null && threeMonthsAgoHbkm != null) {
            val pct = ((lastMonthHbkm - threeMonthsAgoHbkm) / threeMonthsAgoHbkm) * 100f
            val label = when {
                abs(pct) < 2f -> "Flat"
                else -> "${if (pct < 0f) "▼" else "▲"} ${String.format("%.0f", abs(pct))}% in 90d"
            }
            TrendInfo(label = label, positive = if (abs(pct) < 2f) null else pct < 0f)
        } else null
        val hbkmHero = lastMonthHbkm?.let { it.toInt().toString() } ?: "--"

        // ── Section 2: Performance Fingerprint ─────────────────────────────
        val paceAtFixedHrSeries = filtered.mapNotNull { item ->
            item.metrics?.paceAtRefHrMinPerKm?.let { v ->
                ProgressTrendPoint(item.workout.id, item.workout.startTime, v)
            }
        }
        val totalFiltered = filtered.size.coerceAtLeast(1)
        val speedVsHrPoints = filtered.mapIndexedNotNull { idx, item ->
            val hr = item.metrics?.avgHr ?: return@mapIndexedNotNull null
            val pace = item.metrics.avgPaceMinPerKm ?: return@mapIndexedNotNull null
            ScatterPoint(x = hr, y = pace, ageFraction = idx.toFloat() / totalFiltered)
        }

        // ── Section 3: Cardiovascular Fitness ──────────────────────────────
        val aerobicEfficiencySeries = filtered.mapNotNull { item ->
            item.metrics?.efficiencyFactor?.let { v ->
                ProgressTrendPoint(item.workout.id, item.workout.startTime, v)
            }
        }
        val restingHrSeries = recent30.mapNotNull { item ->
            restingHrCache[item.workout.id]?.let { v ->
                ProgressTrendPoint(item.workout.id, item.workout.startTime, v)
            }
        }
        val hrRecoverySeries = filtered.mapNotNull { item ->
            val settle = listOfNotNull(item.metrics?.settleDownSec, item.metrics?.settleUpSec)
            settle.takeIf { it.isNotEmpty() }?.average()?.toFloat()?.let { v ->
                ProgressTrendPoint(item.workout.id, item.workout.startTime, v)
            }
        }
        val vo2MaxSeries = filtered.mapNotNull { item ->
            item.metrics?.efficiencyFactor?.let { ef ->
                val vo2 = ((ef - 0.010f) / 0.015f * 40f + 30f).coerceIn(15f, 85f)
                ProgressTrendPoint(item.workout.id, item.workout.startTime, vo2)
            }
        }

        // ── Section 4: Training Load (last 12 weeks) ────────────────────────
        val weekStarts = (11 downTo 0).map { w ->
            val d = today.minusWeeks(w.toLong())
            d.minusDays(d.dayOfWeek.value.toLong() - 1) // Monday
        }
        val weeklyDistanceMap = weekStarts.associateWith { 0f }.toMutableMap()
        val weeklyLoadMap = weekStarts.associateWith { 0f }.toMutableMap()
        filtered.forEach { item ->
            val date = Instant.ofEpochMilli(item.workout.startTime).atZone(zone).toLocalDate()
            val weekStart = date.minusDays(date.dayOfWeek.value.toLong() - 1)
            if (weeklyDistanceMap.containsKey(weekStart)) {
                weeklyDistanceMap[weekStart] = weeklyDistanceMap.getValue(weekStart) +
                    item.workout.totalDistanceMeters / 1000f
                val durationMin = if (item.workout.endTime > item.workout.startTime)
                    (item.workout.endTime - item.workout.startTime) / 60_000f else 0f
                val avgHr = item.metrics?.avgHr ?: 0f
                weeklyLoadMap[weekStart] = weeklyLoadMap.getValue(weekStart) +
                    durationMin * avgHr / 100f
            }
        }
        val weeklyDistanceBars = weekStarts.map { ws ->
            BarEntry("${ws.month.name.take(3)} ${ws.dayOfMonth}", weeklyDistanceMap.getValue(ws))
        }
        val weeklyLoadBars = weekStarts.map { ws ->
            BarEntry("${ws.month.name.take(3)} ${ws.dayOfMonth}", weeklyLoadMap.getValue(ws))
        }

        // Zone distribution from track points of last 30 workouts
        val zoneCounts = mutableMapOf(
            "Easy" to 0L, "Moderate" to 0L, "Target" to 0L, "Hard" to 0L, "Max" to 0L
        )
        recent30.forEach { item ->
            val refHr = extractTargetHr(item.config) ?: return@forEach
            val pts = trackPointCache[item.workout.id] ?: return@forEach
            pts.forEach pt@{ pt ->
                if (pt.heartRate <= 0) return@pt
                val hr = pt.heartRate.toFloat()
                val zoneName = when {
                    hr < refHr - 20f -> "Easy"
                    hr < refHr - 5f -> "Moderate"
                    hr <= refHr + 5f -> "Target"
                    hr <= refHr + 20f -> "Hard"
                    else -> "Max"
                }
                zoneCounts[zoneName] = zoneCounts.getValue(zoneName) + 1L
            }
        }
        val totalPts = zoneCounts.values.sum().toFloat().coerceAtLeast(1f)
        val zoneOrder = listOf("Easy", "Moderate", "Target", "Hard", "Max")
        val zoneColors = listOf(
            Color(0xFF34D399),
            Color(0xFF4F8EF7),
            Color(0xFF34D399),
            Color(0xFFF59E0B),
            Color(0xFFEF4444)
        )
        val zoneDistribution = zoneOrder.zip(zoneColors).mapNotNull { (name, color) ->
            val count = zoneCounts.getValue(name)
            if (count == 0L) null
            else PieSlice(label = name, fraction = count / totalPts, color = color)
        }

        // ── Section 5: Consistency (last 365 days) ──────────────────────────
        val oneYearAgo = today.minusDays(365)
        val calendarMap = mutableMapOf<LocalDate, Float>()
        filtered.forEach { item ->
            val date = Instant.ofEpochMilli(item.workout.startTime).atZone(zone).toLocalDate()
            if (!date.isBefore(oneYearAgo)) {
                calendarMap[date] = calendarMap.getOrDefault(date, 0f) +
                    item.workout.totalDistanceMeters / 1000f
            }
        }
        val calendarDays = calendarMap.map { (date, km) ->
            CalendarDay(epochDay = date.toEpochDay().toInt(), distanceKm = km)
        }

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            errorMessage = null,
            hasData = filtered.isNotEmpty(),
            heartbeatsPerKmBars = hbkmBars,
            heartbeatsPerKmTrend = hbkmTrend,
            heartbeatsPerKmHero = hbkmHero,
            paceAtFixedHrSeries = paceAtFixedHrSeries,
            paceReferenceHrBpm = paceRefHr,
            speedVsHrPoints = speedVsHrPoints,
            aerobicEfficiencySeries = aerobicEfficiencySeries,
            restingHrSeries = restingHrSeries,
            hrRecoverySeries = hrRecoverySeries,
            vo2MaxSeries = vo2MaxSeries,
            weeklyDistanceBars = weeklyDistanceBars,
            weeklyLoadBars = weeklyLoadBars,
            zoneDistribution = zoneDistribution,
            calendarDays = calendarDays
        )
    }

    private fun parseConfig(json: String): WorkoutConfig? = try {
        JsonCodec.gson.fromJson(json, WorkoutConfig::class.java)
    } catch (_: Exception) { null }

    private fun extractTargetHr(config: WorkoutConfig?): Float? =
        (config?.steadyStateTargetHr ?: config?.segments?.firstOrNull()?.targetHr)?.toFloat()
}
