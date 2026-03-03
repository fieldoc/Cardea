package com.hrcoach.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hrcoach.ui.components.CardeaButton
import com.hrcoach.ui.components.CardeaLogo
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.CardeaBgPrimary
import com.hrcoach.ui.theme.CardeaBgSecondary
import com.hrcoach.ui.theme.CardeaTextSecondary
import com.hrcoach.ui.theme.GlassHighlight
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientCyan
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.GradientRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onStartRun: () -> Unit,
    onGoToProgress: () -> Unit,
    onGoToHistory: () -> Unit,
    onGoToAccount: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(CardeaBgSecondary, CardeaBgPrimary),
                        center = Offset.Zero,
                        radius = 1800f
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Cardea",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = GradientPink
                        )
                        Text(
                            text = state.greeting,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
                            color = Color.White
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(GlassHighlight)
                            .clickable(onClick = onGoToAccount),
                        contentAlignment = Alignment.Center
                    ) {
                        CardeaLogo(size = 24.dp)
                    }
                }

                // Efficiency Ring Card
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Weekly Activity",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = "${state.workoutsThisWeek} of ${state.weeklyTarget} this week",
                                style = MaterialTheme.typography.bodySmall,
                                color = CardeaTextSecondary
                            )
                        }
                        EfficiencyRing(percent = state.efficiencyPercent)
                    }
                }

                // Last Run Card
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Last Run",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    val workout = state.lastWorkout
                    if (workout == null) {
                        Text(
                            text = "No sessions recorded yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CardeaTextSecondary
                        )
                    } else {
                        val dateStr = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                            .format(Date(workout.startTime))
                        val distKm = workout.totalDistanceMeters / 1000f
                        val durationMs = workout.endTime - workout.startTime
                        val durationMin = (durationMs / 60_000).coerceAtLeast(0)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            LastRunStat("Date", dateStr)
                            LastRunStat("Distance", "%.2f km".format(distKm))
                            LastRunStat("Duration", "${durationMin}m")
                        }
                    }
                }

                // Start Run CTA
                CardeaButton(
                    text = "Start a Run",
                    onClick = onStartRun,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                )

                // Quick Links
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickLinkChip("Progress", Modifier.weight(1f), onGoToProgress)
                    QuickLinkChip("History", Modifier.weight(1f), onGoToHistory)
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun EfficiencyRing(percent: Int) {
    val gradientBrush = Brush.linearGradient(
        colorStops = arrayOf(
            0.00f to GradientRed,
            0.35f to GradientPink,
            0.65f to GradientBlue,
            1.00f to GradientCyan
        )
    )
    Box(
        modifier = Modifier
            .size(90.dp)
            .drawWithCache {
                val stroke = 6.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val sweepAngle = (percent / 100f) * 360f
                onDrawWithContent {
                    drawArc(
                        color = Color(0x14FFFFFF),
                        startAngle = -90f, sweepAngle = 360f, useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                        topLeft = Offset(inset, inset), size = arcSize
                    )
                    if (sweepAngle > 0f) {
                        drawArc(
                            brush = gradientBrush,
                            startAngle = -90f, sweepAngle = sweepAngle, useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                            topLeft = Offset(inset, inset), size = arcSize
                        )
                    }
                    drawContent()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 22.sp
            ),
            color = Color.White
        )
    }
}

@Composable
private fun LastRunStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = CardeaTextSecondary)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = Color.White
        )
    }
}

@Composable
private fun QuickLinkChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x14FFFFFF))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = CardeaTextSecondary)
    }
}
