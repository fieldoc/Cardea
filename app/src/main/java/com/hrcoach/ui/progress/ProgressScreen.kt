package com.hrcoach.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hrcoach.R
import com.hrcoach.ui.charts.BarChart
import com.hrcoach.ui.charts.CalendarHeatmap
import com.hrcoach.ui.charts.PieChart
import com.hrcoach.ui.charts.ProgressChartCard
import com.hrcoach.ui.charts.ScatterPlot
import com.hrcoach.ui.charts.SectionHeader
import com.hrcoach.ui.charts.TrendInfo
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    onStartWorkout: () -> Unit,
    viewModel: ProgressViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.screen_progress_title)) })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            FilterRow(
                selected = uiState.filter,
                onFilterSelected = viewModel::setFilter,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.errorMessage != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = uiState.errorMessage!!,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                }
                !uiState.hasData -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Text(
                                text = "No workout data yet. Complete a few sessions to unlock your dashboard.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(onClick = onStartWorkout) {
                                Text(stringResource(R.string.button_start_workout))
                            }
                        }
                    }
                }
                else -> DashboardContent(uiState)
            }
        }
    }
}

@Composable
private fun DashboardContent(uiState: ProgressUiState) {
    LazyColumn {
        // ── Section 1: Killer Metric ──────────────────────────────────────
        item { SectionHeader("Heartbeats per Kilometer", "Lower = fitter") }
        item { HeartbeatsPerKmCard(uiState) }

        // ── Section 2: Performance Fingerprint ───────────────────────────
        item { SectionHeader("Performance Fingerprint") }
        item { PaceAtFixedHrCard(uiState) }
        item { SpeedVsHrCard(uiState) }

        // ── Section 3: Cardiovascular Fitness ────────────────────────────
        item { SectionHeader("Cardiovascular Fitness") }
        item { AerobicEfficiencyCard(uiState) }
        item { RestingHrCard(uiState) }
        item { HrRecoveryCard(uiState) }
        item { Vo2MaxCard(uiState) }

        // ── Section 4: Training Load ──────────────────────────────────────
        item { SectionHeader("Training Load") }
        item { WeeklyDistanceCard(uiState) }
        item { WeeklyLoadCard(uiState) }
        item { ZoneDistributionCard(uiState) }

        // ── Section 5: Consistency ────────────────────────────────────────
        item { SectionHeader("Consistency") }
        item { ConsistencyCalendarCard(uiState) }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ── Section card composables ──────────────────────────────────────────────────

@Composable
private fun HeartbeatsPerKmCard(uiState: ProgressUiState) {
    ProgressChartCard(
        title = "Heartbeats / km",
        subtitle = "Hero: ${uiState.heartbeatsPerKmHero} beats/km",
        trendInfo = uiState.heartbeatsPerKmTrend
    ) {
        if (uiState.heartbeatsPerKmBars.none { it.value > 0f }) {
            NotEnoughDataText()
        } else {
            BarChart(
                bars = uiState.heartbeatsPerKmBars,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(200.dp).fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PaceAtFixedHrCard(uiState: ProgressUiState) {
    ProgressChartCard(
        title = "Pace at ${uiState.paceReferenceHrBpm} bpm",
        subtitle = "Lower = faster at your target HR",
        trendInfo = seriesTrendInfo(uiState.paceAtFixedHrSeries, lowerIsBetter = true) {
            "${if (it >= 0f) "+" else ""}${String.format("%.2f", it)} min/km"
        }
    ) {
        TrendLineChart(
            series = uiState.paceAtFixedHrSeries,
            yFormatter = { String.format("%.2f", it) }
        )
    }
}

@Composable
private fun SpeedVsHrCard(uiState: ProgressUiState) {
    ProgressChartCard(
        title = "Speed vs HR",
        subtitle = "Color: older → newer workouts"
    ) {
        if (uiState.speedVsHrPoints.size < 2) {
            NotEnoughDataText()
        } else {
            ScatterPlot(
                points = uiState.speedVsHrPoints,
                modifier = Modifier.height(200.dp).fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AerobicEfficiencyCard(uiState: ProgressUiState) {
    ProgressChartCard(
        title = "Aerobic Efficiency",
        subtitle = "Speed-to-HR ratio — higher is fitter",
        trendInfo = seriesTrendInfo(uiState.aerobicEfficiencySeries, lowerIsBetter = false) {
            "${if (it >= 0f) "+" else ""}${String.format("%.3f", it)}"
        }
    ) {
        TrendLineChart(
            series = uiState.aerobicEfficiencySeries,
            yFormatter = { String.format("%.2f", it) }
        )
    }
}

@Composable
private fun RestingHrCard(uiState: ProgressUiState) {
    ProgressChartCard(
        title = "Resting HR Proxy",
        subtitle = "Estimated from workout warm-up — lower is fitter",
        trendInfo = seriesTrendInfo(uiState.restingHrSeries, lowerIsBetter = true) {
            "${if (it >= 0f) "+" else ""}${it.toInt()} bpm"
        }
    ) {
        TrendLineChart(
            series = uiState.restingHrSeries,
            yFormatter = { "${it.toInt()} bpm" }
        )
    }
}

@Composable
private fun HrRecoveryCard(uiState: ProgressUiState) {
    ProgressChartCard(
        title = "HR Recovery",
        subtitle = "Time to settle in-zone after drift — lower is better",
        trendInfo = seriesTrendInfo(uiState.hrRecoverySeries, lowerIsBetter = true) {
            "${if (it >= 0f) "+" else ""}${it.toInt()} s"
        }
    ) {
        TrendLineChart(
            series = uiState.hrRecoverySeries,
            yFormatter = { "${it.toInt()} s" }
        )
    }
}

@Composable
private fun Vo2MaxCard(uiState: ProgressUiState) {
    ProgressChartCard(
        title = "VO₂ Max Estimate",
        subtitle = "VO₂ estimate (±10%) — higher is fitter",
        trendInfo = seriesTrendInfo(uiState.vo2MaxSeries, lowerIsBetter = false) {
            "${if (it >= 0f) "+" else ""}${String.format("%.1f", it)} ml/kg/min"
        }
    ) {
        TrendLineChart(
            series = uiState.vo2MaxSeries,
            yFormatter = { "${String.format("%.0f", it)} ml/kg/min" }
        )
    }
}

@Composable
private fun WeeklyDistanceCard(uiState: ProgressUiState) {
    ProgressChartCard(
        title = "Weekly Distance",
        subtitle = "Last 12 weeks"
    ) {
        BarChart(
            bars = uiState.weeklyDistanceBars,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.height(200.dp).fillMaxWidth()
        )
    }
}

@Composable
private fun WeeklyLoadCard(uiState: ProgressUiState) {
    ProgressChartCard(
        title = "Weekly Training Load",
        subtitle = "Duration × avg HR / 100 — last 12 weeks"
    ) {
        BarChart(
            bars = uiState.weeklyLoadBars,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.height(200.dp).fillMaxWidth()
        )
    }
}

@Composable
private fun ZoneDistributionCard(uiState: ProgressUiState) {
    ProgressChartCard(
        title = "HR Zone Distribution",
        subtitle = "Last 30 workouts"
    ) {
        if (uiState.zoneDistribution.isEmpty()) {
            NotEnoughDataText()
        } else {
            PieChart(
                slices = uiState.zoneDistribution,
                modifier = Modifier.height(180.dp).fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ConsistencyCalendarCard(uiState: ProgressUiState) {
    ProgressChartCard(
        title = "Running Calendar",
        subtitle = "Last 365 days — darker = more km"
    ) {
        CalendarHeatmap(
            days = uiState.calendarDays,
            modifier = Modifier.height(140.dp).fillMaxWidth()
        )
    }
}

// ── Shared chart helpers ──────────────────────────────────────────────────────

@Composable
private fun TrendLineChart(
    series: List<ProgressTrendPoint>,
    yFormatter: (Float) -> String,
    modifier: Modifier = Modifier,
    threshold: Float? = null
) {
    if (series.size < 2) {
        NotEnoughDataText()
        return
    }
    val values = series.map { it.value }
    val rawMin = values.minOrNull() ?: 0f
    val rawMax = values.maxOrNull() ?: rawMin
    val chartMin = threshold?.let { minOf(rawMin, it) } ?: rawMin
    val chartMax = threshold?.let { maxOf(rawMax, it) } ?: rawMax
    val range = (chartMax - chartMin).takeIf { it > 0f } ?: 1f
    val lineColor = MaterialTheme.colorScheme.primary
    val thresholdColor = MaterialTheme.colorScheme.outline

    Column {
        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            val w = size.width
            val h = size.height
            val stepX = if (series.size > 1) w / (series.size - 1) else w

            threshold?.let { tv ->
                val ty = h - ((tv - chartMin) / range * h)
                drawLine(
                    color = thresholdColor,
                    start = Offset(0f, ty),
                    end = Offset(w, ty),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                )
            }

            val path = Path()
            series.forEachIndexed { i, pt ->
                val x = stepX * i
                val y = h - ((pt.value - chartMin) / range * h)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, lineColor, style = Stroke(width = 5f, cap = StrokeCap.Round))

            series.forEachIndexed { i, pt ->
                val x = stepX * i
                val y = h - ((pt.value - chartMin) / range * h)
                drawCircle(lineColor, radius = 5f, center = Offset(x, y))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = yFormatter(values.first()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = yFormatter(values.last()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun NotEnoughDataText() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Not enough data yet",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun FilterRow(
    selected: ProgressFilter,
    onFilterSelected: (ProgressFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val filters = listOf(
        ProgressFilter.ALL to "All",
        ProgressFilter.STEADY_STATE to "Steady",
        ProgressFilter.DISTANCE_PROFILE to "Distance"
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        filters.forEachIndexed { index, (filter, label) ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = filters.size),
                selected = selected == filter,
                onClick = { onFilterSelected(filter) }
            ) { Text(label) }
        }
    }
}

// ── Pure logic helpers ────────────────────────────────────────────────────────

private fun seriesTrendInfo(
    series: List<ProgressTrendPoint>,
    lowerIsBetter: Boolean,
    formatter: (Float) -> String
): TrendInfo? {
    if (series.size < 2) return null
    val first = series.first().value
    val last = series.last().value
    val delta = last - first
    val relativeDelta = if (first != 0f) abs(delta / first) else abs(delta)
    if (relativeDelta < 0.02f) return TrendInfo("Flat", null)
    val positive = (delta < 0f) == lowerIsBetter
    return TrendInfo(formatter(delta), positive)
}
