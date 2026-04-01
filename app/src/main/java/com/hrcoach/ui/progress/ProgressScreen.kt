package com.hrcoach.ui.progress

import android.graphics.BlurMaskFilter
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hrcoach.R
import com.hrcoach.ui.charts.BarChart
import com.hrcoach.ui.charts.CalendarHeatmap
import com.hrcoach.ui.charts.PieChart
import com.hrcoach.ui.charts.ProgressChartCard
import com.hrcoach.ui.charts.ScatterPlot
import com.hrcoach.ui.charts.SectionHeader
import com.hrcoach.ui.charts.TrendInfo
import com.hrcoach.ui.components.CardeaButton
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.components.cardeaSegmentedButtonColors
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientCyan
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.GradientRed
import com.hrcoach.domain.education.ContentDensity
import com.hrcoach.domain.education.ZoneEducationProvider
import com.hrcoach.domain.education.ZoneId
import com.hrcoach.ui.theme.ZoneGreen
import com.hrcoach.ui.theme.ZoneRed
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    onStartWorkout: () -> Unit,
    onGoToLog: (() -> Unit)? = null,
    viewModel: ProgressViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CardeaTheme.colors.bgPrimary)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Trends",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = CardeaTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(14.dp))
            if (onGoToLog != null) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        selected = false,
                        onClick = onGoToLog,
                        colors = cardeaSegmentedButtonColors()
                    ) { Text("Log") }
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        selected = true,
                        onClick = {},
                        colors = cardeaSegmentedButtonColors()
                    ) { Text("Trends") }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            FilterRow(
                selected = uiState.filter,
                onFilterSelected = viewModel::setFilter,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        // ── Content ─────────────────────────────────────────────────────────
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GradientRed)
                }
            }

            uiState.errorMessage != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = uiState.errorMessage!!,
                        color = CardeaTheme.colors.textSecondary,
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
                            color = CardeaTheme.colors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        CardeaButton(
                            text = stringResource(R.string.button_start_workout),
                            onClick = onStartWorkout,
                            modifier = Modifier
                                .height(48.dp)
                                .fillMaxWidth(0.7f),
                            innerPadding = PaddingValues(horizontal = 16.dp)
                        )
                    }
                }
            }

            else -> DashboardContent(uiState)
        }
    }
}

@Composable
private fun DashboardContent(uiState: ProgressUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { KeyMetricsGrid(uiState, Modifier.padding(horizontal = 16.dp)) }
        item { CoachTakeCard(uiState, Modifier.padding(horizontal = 16.dp)) }

        // Efficiency — how much effort each kilometer costs
        item { SectionHeader("Efficiency", "How economically you convert effort into speed.", Modifier.padding(horizontal = 16.dp)) }
        item { HeartbeatsPerKmCard(uiState, Modifier.padding(horizontal = 16.dp)) }
        item { Vo2MaxCard(uiState, Modifier.padding(horizontal = 16.dp)) }
        item { PaceAtFixedHrCard(uiState, Modifier.padding(horizontal = 16.dp)) }
        item { AerobicEfficiencyCard(uiState, Modifier.padding(horizontal = 16.dp)) }

        // Load — volume, intensity, and speed profile
        item { SectionHeader("Load", "Volume, intensity distribution, and pace fingerprint.", Modifier.padding(horizontal = 16.dp)) }
        item { WeeklyDistanceCard(uiState, Modifier.padding(horizontal = 16.dp)) }
        item { WeeklyLoadCard(uiState, Modifier.padding(horizontal = 16.dp)) }
        item { ZoneDistributionCard(uiState, Modifier.padding(horizontal = 16.dp)) }
        item { SpeedVsHrCard(uiState, Modifier.padding(horizontal = 16.dp)) }

        // Health — recovery markers and consistency
        item { SectionHeader("Health", "Recovery quality and training consistency.", Modifier.padding(horizontal = 16.dp)) }
        item { RestingHrCard(uiState, Modifier.padding(horizontal = 16.dp)) }
        item { HrRecoveryCard(uiState, Modifier.padding(horizontal = 16.dp)) }
        item { ConsistencyCalendarCard(uiState, Modifier.padding(horizontal = 16.dp)) }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

/**
 * Coach's Take — a plain-language insight at the top of the dashboard.
 * Computes from existing [ProgressUiState] without any extra ViewModel logic.
 */
@Composable
private fun CoachTakeCard(uiState: ProgressUiState, modifier: Modifier = Modifier) {
    val lines = buildCoachTakeLines(uiState)
    if (lines.isEmpty()) return

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "COACH'S TAKE",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = GradientPink
        )
        Spacer(modifier = Modifier.height(6.dp))
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = CardeaTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

private fun buildCoachTakeLines(uiState: ProgressUiState): List<String> {
    val lines = mutableListOf<String>()

    // Efficiency trend: TrendInfo.positive == true means improving (lower hbkm = positive for this metric)
    val hbkmTrend = uiState.heartbeatsPerKmTrend
    if (hbkmTrend?.positive != null) {
        lines += if (hbkmTrend.positive == true)
            "Your aerobic efficiency is trending in the right direction — keep your next runs at a comfortable effort."
        else
            "Your heart is working harder per km lately. A few easy Zone 2 runs will help rebuild your aerobic base."
    }

    // Resting HR trend
    val restingHrRecent = uiState.restingHrSeries.takeLast(4)
    if (restingHrRecent.size >= 2) {
        val delta = restingHrRecent.last().value - restingHrRecent.first().value
        if (delta < -2f) lines += "Resting HR is dropping — a strong sign of improving cardiovascular fitness."
        else if (delta > 3f) lines += "Resting HR has crept up slightly. Prioritise sleep and an easy week."
    }

    // Consistency check — calendarDays has distanceKm; > 0 means a workout happened
    val recentDays = uiState.calendarDays.takeLast(7)
    val recentConsistency = recentDays.count { it.distanceKm > 0f }
    if (recentConsistency >= 3) lines += "Strong week — $recentConsistency sessions in the last 7 days."
    else if (recentConsistency == 0 && uiState.calendarDays.isNotEmpty())
        lines += "No sessions detected this week. Getting back out, even briefly, resets your recovery clock."

    return lines.take(2) // limit to 2 insights to keep it scannable
}

@Composable
private fun KeyMetricsGrid(uiState: ProgressUiState, modifier: Modifier = Modifier) {
    val currentHbKm = uiState.heartbeatsPerKmBars.lastOrNull { it.value > 0f }?.value
    val previousHbKm = uiState.heartbeatsPerKmBars.dropLast(1).lastOrNull { it.value > 0f }?.value
    val weeklyDistance = uiState.weeklyDistanceBars.lastOrNull()?.value ?: 0f
    val vo2Estimate = uiState.vo2MaxSeries.lastOrNull()?.value

    val hbkmDelta = if (currentHbKm != null && previousHbKm != null) currentHbKm - previousHbKm else null
    val vo2Trend = seriesTrendInfo(uiState.vo2MaxSeries, lowerIsBetter = false) {
        "${if (it >= 0f) "+" else ""}${String.format("%.1f", it)}"
    }

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCell(
            value = currentHbKm?.toInt()?.toString() ?: "--",
            label = "HB / km",
            trendLabel = hbkmDelta?.let { d ->
                val i = d.toInt()
                if (i == 0) "flat" else "${if (i > 0) "+" else ""}$i"
            },
            trendPositive = hbkmDelta?.let { it < 0f },
            valueBrush = CardeaGradient,
            modifier = Modifier.weight(1f)
        )
        MetricCell(
            value = vo2Estimate?.let { String.format("%.1f", it) } ?: "--",
            label = "VO₂ est.",
            trendLabel = vo2Trend?.label,
            trendPositive = vo2Trend?.positive,
            valueColor = GradientBlue,
            modifier = Modifier.weight(1f)
        )
        MetricCell(
            value = String.format("%.1f", weeklyDistance),
            label = "km / wk",
            trendLabel = null,
            trendPositive = null,
            valueColor = ZoneGreen,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MetricCell(
    value: String,
    label: String,
    trendLabel: String?,
    trendPositive: Boolean?,
    valueBrush: Brush? = null,
    valueColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    val resolvedColor = if (valueColor == Color.Unspecified) CardeaTheme.colors.textPrimary else valueColor
    GlassCard(modifier = modifier, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                brush = valueBrush
            ),
            color = if (valueBrush != null) Color.Unspecified else resolvedColor
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.04.sp
            ),
            color = CardeaTheme.colors.textTertiary
        )
        if (trendLabel != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = trendLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = when (trendPositive) {
                    true -> ZoneGreen
                    false -> ZoneRed
                    null -> CardeaTheme.colors.textTertiary
                }
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
                color = GradientRed,
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
            color = CardeaTheme.colors.textSecondary
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
            color = GradientBlue,
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
            color = GradientPink,
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
            Spacer(modifier = Modifier.height(12.dp))
            ZoneEducationLegend()
        }
    }
}

@Composable
private fun ZoneEducationLegend() {
    val zones = listOf(
        ZoneId.ZONE_2 to "Z2",
        ZoneId.ZONE_3 to "Z3",
        ZoneId.ZONE_4_5 to "Z4-5"
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((zoneId, label) in zones) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    color = CardeaTheme.colors.textSecondary,
                    modifier = Modifier.width(30.dp)
                )
                Text(
                    text = ZoneEducationProvider.getContent(zoneId, ContentDensity.ONE_LINER),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = CardeaTheme.colors.textTertiary,
                    maxLines = 2
                )
            }
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

    val thresholdLineColor = CardeaTheme.colors.textSecondary
    Column {
        Spacer(
            modifier = modifier
                .fillMaxWidth()
                .height(160.dp)
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val stepX = if (series.size > 1) w / (series.size - 1) else w

                    val path = Path()
                    series.forEachIndexed { i, pt ->
                        val x = stepX * i
                        val y = h - ((pt.value - chartMin) / range * h)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }

                    // Build gradient spanning the chart width — cached until size or series changes
                    val chartGradient = Brush.linearGradient(
                        colorStops = arrayOf(
                            0.00f to GradientRed,
                            0.35f to GradientPink,
                            0.65f to GradientBlue,
                            1.00f to GradientCyan
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(w, 0f)
                    )

                    onDrawBehind {
                        threshold?.let { tv ->
                            val ty = h - ((tv - chartMin) / range * h)
                            drawLine(
                                color = thresholdLineColor,
                                start = Offset(0f, ty),
                                end = Offset(w, ty),
                                strokeWidth = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                            )
                        }

                        // Glow pass — true neon glow via BlurMaskFilter
                        drawIntoCanvas { canvas ->
                            val glowPaint = Paint().apply {
                                asFrameworkPaint().apply {
                                    isAntiAlias = true
                                    style = android.graphics.Paint.Style.STROKE
                                    strokeWidth = 5f
                                    strokeCap = android.graphics.Paint.Cap.ROUND
                                    maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
                                    color = android.graphics.Color.argb(100, 0xFF, 0x2D, 0xA6)
                                }
                            }
                            canvas.drawPath(path, glowPaint)
                        }
                        // Main gradient line
                        drawPath(
                            path = path,
                            brush = chartGradient,
                            style = Stroke(width = 5f, cap = StrokeCap.Round)
                        )

                        series.forEachIndexed { i, pt ->
                            val x = stepX * i
                            val y = h - ((pt.value - chartMin) / range * h)
                            drawCircle(GradientCyan, radius = 5f, center = Offset(x, y))
                        }
                    }
                }
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = yFormatter(values.first()),
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textSecondary
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = yFormatter(values.last()),
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textSecondary
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
            color = CardeaTheme.colors.textSecondary
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
                onClick = { onFilterSelected(filter) },
                colors = cardeaSegmentedButtonColors()
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
