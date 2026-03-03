package com.hrcoach.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.components.StatItem
import com.hrcoach.ui.theme.HrCoachThemeTokens
import com.hrcoach.ui.theme.SubtleText
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    onStartWorkout: () -> Unit,
    viewModel: ProgressViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_progress_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF0F1623), Color(0xFF0B0F17)),
                        center = Offset.Zero,
                        radius = 1800f
                    )
                )
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
                            color = HrCoachThemeTokens.subtleText,
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
                                color = HrCoachThemeTokens.subtleText,
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { KeyMetricsStrip(uiState) }
        item { SectionHeader("Performance", "Your highest-signal trends, surfaced first.", Modifier.padding(horizontal = 16.dp)) }
        item { HeartbeatsPerKmCard(uiState, Modifier.padding(horizontal = 16.dp)) }
        item { Vo2MaxCard(uiState, Modifier.padding(horizontal = 16.dp)) }
        item { WeeklyDistanceCard(uiState, Modifier.padding(horizontal = 16.dp)) }
        item { SectionHeader("Details", "Fingerprint, load, and consistency charts.", Modifier.padding(horizontal = 16.dp)) }
        item { PaceAtFixedHrCard(uiState, Modifier.padding(horizontal = 16.dp)) }
        item { SpeedVsHrCard(uiState, Modifier.padding(horizontal = 16.dp)) }
        item { AerobicEfficiencyCard(uiState, Modifier.padding(horizontal = 16.dp)) }
        item { RestingHrCard(uiState, Modifier.padding(horizontal = 16.dp)) }
        item { HrRecoveryCard(uiState, Modifier.padding(horizontal = 16.dp)) }
        item { WeeklyLoadCard(uiState, Modifier.padding(horizontal = 16.dp)) }
        item { ZoneDistributionCard(uiState, Modifier.padding(horizontal = 16.dp)) }
        item { ConsistencyCalendarCard(uiState, Modifier.padding(horizontal = 16.dp)) }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun KeyMetricsStrip(uiState: ProgressUiState) {
    val currentHbKm = uiState.heartbeatsPerKmBars.lastOrNull { it.value > 0f }?.value
    val previousHbKm = uiState.heartbeatsPerKmBars.dropLast(1).lastOrNull { it.value > 0f }?.value
    val weeklyDistance = uiState.weeklyDistanceBars.lastOrNull()?.value ?: 0f
    val vo2Estimate = uiState.vo2MaxSeries.lastOrNull()?.value

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassCard(modifier = Modifier.width(180.dp)) {
            StatItem(
                label = "Heartbeats/km",
                value = currentHbKm?.let { it.toInt().toString() } ?: "--"
            )
            Text(
                text = if (currentHbKm != null && previousHbKm != null) {
                    "vs last month ${deltaLabel(currentHbKm - previousHbKm, lowerIsBetter = true, suffix = " beats")}"
                } else {
                    "Current month vs last"
                },
                style = MaterialTheme.typography.bodySmall,
                color = HrCoachThemeTokens.subtleText
            )
        }
        GlassCard(modifier = Modifier.width(180.dp)) {
            StatItem(
                label = "Estimated VO2 Max",
                value = vo2Estimate?.let { String.format("%.1f", it) } ?: "--"
            )
            Text(
                text = "(estimated +/-10%)",
                style = MaterialTheme.typography.bodySmall,
                color = HrCoachThemeTokens.subtleText
            )
        }
        GlassCard(modifier = Modifier.width(180.dp)) {
            StatItem(
                label = "Weekly Distance",
                value = String.format("%.1f km", weeklyDistance)
            )
            Text(
                text = "Last 7 days",
                style = MaterialTheme.typography.bodySmall,
                color = HrCoachThemeTokens.subtleText
            )
        }
    }
}

@Composable
private fun HeartbeatsPerKmCard(uiState: ProgressUiState, modifier: Modifier = Modifier) {
    ProgressChartCard(
        title = "Heartbeats / km",
        subtitle = "Lower suggests better aerobic efficiency.",
        trendInfo = uiState.heartbeatsPerKmTrend,
        modifier = modifier
    ) {
        if (uiState.heartbeatsPerKmBars.none { it.value > 0f }) {
            NotEnoughDataText()
        } else {
            BarChart(
                bars = uiState.heartbeatsPerKmBars,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .height(200.dp)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PaceAtFixedHrCard(uiState: ProgressUiState, modifier: Modifier = Modifier) {
    ProgressChartCard(
        title = "Pace at ${uiState.paceReferenceHrBpm} bpm",
        subtitle = "Lower means faster at the same cardiac cost.",
        trendInfo = seriesTrendInfo(uiState.paceAtFixedHrSeries, lowerIsBetter = true) {
            "${if (it >= 0f) "+" else ""}${String.format("%.2f", it)} min/km"
        },
        modifier = modifier
    ) {
        TrendLineChart(
            series = uiState.paceAtFixedHrSeries,
            yFormatter = { String.format("%.2f", it) }
        )
    }
}

@Composable
private fun SpeedVsHrCard(uiState: ProgressUiState, modifier: Modifier = Modifier) {
    ProgressChartCard(
        title = "Speed vs HR",
        subtitle = "Older sessions fade back, newer sessions move forward.",
        modifier = modifier
    ) {
        if (uiState.speedVsHrPoints.size < 2) {
            NotEnoughDataText()
        } else {
            ScatterPlot(
                points = uiState.speedVsHrPoints,
                modifier = Modifier
                    .height(220.dp)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AerobicEfficiencyCard(uiState: ProgressUiState, modifier: Modifier = Modifier) {
    ProgressChartCard(
        title = "Aerobic Efficiency",
        subtitle = "Speed-to-HR ratio. Higher is stronger.",
        trendInfo = seriesTrendInfo(uiState.aerobicEfficiencySeries, lowerIsBetter = false) {
            "${if (it >= 0f) "+" else ""}${String.format("%.3f", it)}"
        },
        modifier = modifier
    ) {
        TrendLineChart(
            series = uiState.aerobicEfficiencySeries,
            yFormatter = { String.format("%.2f", it) }
        )
    }
}

@Composable
private fun RestingHrCard(uiState: ProgressUiState, modifier: Modifier = Modifier) {
    ProgressChartCard(
        title = "Resting HR Proxy",
        subtitle = "Estimated from warm-up trends. Lower is better.",
        trendInfo = seriesTrendInfo(uiState.restingHrSeries, lowerIsBetter = true) {
            "${if (it >= 0f) "+" else ""}${it.toInt()} bpm"
        },
        modifier = modifier
    ) {
        TrendLineChart(
            series = uiState.restingHrSeries,
            yFormatter = { "${it.toInt()} bpm" }
        )
    }
}

@Composable
private fun HrRecoveryCard(uiState: ProgressUiState, modifier: Modifier = Modifier) {
    ProgressChartCard(
        title = "HR Recovery",
        subtitle = "Time to settle back in-zone after drift.",
        trendInfo = seriesTrendInfo(uiState.hrRecoverySeries, lowerIsBetter = true) {
            "${if (it >= 0f) "+" else ""}${it.toInt()} s"
        },
        modifier = modifier
    ) {
        TrendLineChart(
            series = uiState.hrRecoverySeries,
            yFormatter = { "${it.toInt()} s" }
        )
    }
}

@Composable
private fun Vo2MaxCard(uiState: ProgressUiState, modifier: Modifier = Modifier) {
    ProgressChartCard(
        title = "VO2 Max Estimate",
        subtitle = "Current estimate with expected model variance.",
        trendInfo = seriesTrendInfo(uiState.vo2MaxSeries, lowerIsBetter = false) {
            "${if (it >= 0f) "+" else ""}${String.format("%.1f", it)} ml/kg/min"
        },
        modifier = modifier
    ) {
        TrendLineChart(
            series = uiState.vo2MaxSeries,
            yFormatter = { String.format("%.0f", it) }
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "(estimated +/-10%)",
            style = MaterialTheme.typography.bodySmall,
            color = HrCoachThemeTokens.subtleText
        )
    }
}

@Composable
private fun WeeklyDistanceCard(uiState: ProgressUiState, modifier: Modifier = Modifier) {
    ProgressChartCard(
        title = "Weekly Distance",
        subtitle = "Last 12 weeks",
        modifier = modifier
    ) {
        BarChart(
            bars = uiState.weeklyDistanceBars,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .height(200.dp)
                .fillMaxWidth()
        )
    }
}

@Composable
private fun WeeklyLoadCard(uiState: ProgressUiState, modifier: Modifier = Modifier) {
    ProgressChartCard(
        title = "Weekly Training Load",
        subtitle = "Duration x avg HR / 100",
        modifier = modifier
    ) {
        BarChart(
            bars = uiState.weeklyLoadBars,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier
                .height(200.dp)
                .fillMaxWidth()
        )
    }
}

@Composable
private fun ZoneDistributionCard(uiState: ProgressUiState, modifier: Modifier = Modifier) {
    ProgressChartCard(
        title = "HR Zone Distribution",
        subtitle = "Last 30 workouts",
        modifier = modifier
    ) {
        if (uiState.zoneDistribution.isEmpty()) {
            NotEnoughDataText()
        } else {
            PieChart(
                slices = uiState.zoneDistribution,
                modifier = Modifier
                    .height(180.dp)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ConsistencyCalendarCard(uiState: ProgressUiState, modifier: Modifier = Modifier) {
    ProgressChartCard(
        title = "Running Calendar",
        subtitle = "Last 365 days. Brighter days mean more distance.",
        modifier = modifier
    ) {
        CalendarHeatmap(
            days = uiState.calendarDays,
            modifier = Modifier
                .height(160.dp)
                .fillMaxWidth()
        )
    }
}

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
                    color = SubtleText,
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
                color = HrCoachThemeTokens.subtleText
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = yFormatter(values.last()),
                style = MaterialTheme.typography.bodySmall,
                color = HrCoachThemeTokens.subtleText
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
            color = HrCoachThemeTokens.subtleText
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

private fun deltaLabel(delta: Float, lowerIsBetter: Boolean, suffix: String): String {
    if (delta == 0f) return "flat"
    val improved = if (lowerIsBetter) delta < 0f else delta > 0f
    val sign = if (improved) "" else "+"
    return "$sign${String.format("%.0f", delta)}$suffix"
}
