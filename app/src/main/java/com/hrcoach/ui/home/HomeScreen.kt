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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.hrcoach.ui.components.ActiveSessionCard
import com.hrcoach.ui.components.CardeaButton
import com.hrcoach.ui.components.CardeaLogo
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.components.StatItem
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
    onGoToBootcamp: () -> Unit,
    onGoToWorkout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Active session banner — shows when workout is in progress
                if (state.isSessionRunning) {
                    ActiveSessionCard(onClick = onGoToWorkout)
                }

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "CARDEA",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 3.sp
                            ),
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        Text(
                            text = state.greeting,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Sensor status shortcut — tap to go to Training tab for device setup
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GlassHighlight)
                                .clickable(onClick = onStartRun),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = "Sensor setup",
                                tint = CardeaTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(GlassHighlight)
                                .clickable(onClick = onGoToAccount),
                            contentAlignment = Alignment.Center
                        ) {
                            CardeaLogo(size = 28.dp, animate = false)
                        }
                    }
                }

                // Efficiency Ring Card (Athletic Refinement)
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "WEEKLY ACTIVITY",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = CardeaTextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${state.workoutsThisWeek} of ${state.weeklyTarget}",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = "SESSIONS COMPLETED",
                                style = MaterialTheme.typography.labelSmall,
                                color = CardeaTextSecondary
                            )
                        }
                        EfficiencyRing(percent = 0)
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
                            StatItem("Date", dateStr)
                            StatItem("Distance", "%.2f km".format(distKm))
                            StatItem("Duration", "${durationMin}m")
                        }
                    }
                }

                // Bootcamp Card — elevated entry point for structured training
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onGoToBootcamp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "STRUCTURED TRAINING",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = CardeaTextSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Bootcamp",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = "Adaptive program — phases, HR zones, life-aware",
                                style = MaterialTheme.typography.bodySmall,
                                color = CardeaTextSecondary
                            )
                        }
                        CardeaButton(
                            text = "Jump back in",
                            onClick = onGoToBootcamp,
                            modifier = Modifier.height(36.dp),
                            innerPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
                            cornerRadius = 10.dp
                        )
                    }
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
                val stroke = 4.dp.toPx()
                val segments = 40
                val sweep = 360f / segments
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val targetSweep = (percent / 100f) * 360f

                onDrawWithContent {
                    // Background segments
                    for (i in 0 until segments) {
                        drawArc(
                            color = Color.White.copy(alpha = 0.05f),
                            startAngle = i * sweep - 90f + 1f,
                            sweepAngle = sweep - 2f,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Butt),
                            topLeft = Offset(inset, inset), size = arcSize
                        )
                    }
                    
                    // Progress segments
                    val activeSegments = (percent / 100f * segments).toInt()
                    for (i in 0 until activeSegments) {
                        drawArc(
                            brush = gradientBrush,
                            startAngle = i * sweep - 90f + 1f,
                            sweepAngle = sweep - 2f,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Butt),
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
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            ),
            color = Color.White
        )
    }
}

