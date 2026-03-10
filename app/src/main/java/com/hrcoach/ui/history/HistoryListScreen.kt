package com.hrcoach.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hrcoach.R
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.ui.components.CardeaButton
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.components.StatItem
import com.hrcoach.ui.theme.CardeaTextSecondary
import com.hrcoach.util.formatDuration
import com.hrcoach.util.formatWorkoutDate
import kotlin.math.floor

private val HistoryBackdrop = Brush.radialGradient(
    colors = listOf(
        Color(0xFF0F1623),
        Color(0xFF0B0F17)
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryListScreen(
    onWorkoutClick: (Long) -> Unit,
    onStartWorkout: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val workouts by viewModel.workouts.collectAsState()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_history_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HistoryBackdrop)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (workouts.isEmpty()) {
                        "Every workout you save shows up here."
                    } else {
                        "${workouts.size} recorded sessions ready to review."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = CardeaTextSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (workouts.isEmpty()) {
                    HistoryEmptyState(onStartWorkout = onStartWorkout)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(workouts, key = { it.id }) { workout ->
                            WorkoutCard(
                                workout = workout,
                                onClick = { onWorkoutClick(workout.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryEmptyState(onStartWorkout: () -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp)
    ) {
        Text(
            text = "Your run archive is still empty",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Start a workout to unlock route replay, post-run insights, and long-term progress trends.",
            style = MaterialTheme.typography.bodyLarge,
            color = CardeaTextSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        CardeaButton(
            text = stringResource(R.string.button_start_workout),
            onClick = onStartWorkout,
            modifier = Modifier.height(44.dp),
            innerPadding = PaddingValues(horizontal = 24.dp)
        )
    }
}

@Composable
private fun WorkoutCard(
    workout: WorkoutEntity,
    onClick: () -> Unit
) {
    val date = formatWorkoutDate(workout.startTime)
    val duration = formatDuration(workout.startTime, workout.endTime)
    val distanceKm = workout.totalDistanceMeters / 1000f
    val distanceLabel = String.format("%.2f km", distanceKm)
    val paceLabel = averagePaceLabel(workout, distanceKm)

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = date,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = workout.mode.asModeLabel(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = distanceLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Tap for route and stats",
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTextSecondary
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatItem(
                label = "Duration",
                value = duration,
                modifier = Modifier.weight(1f)
            )
            StatItem(
                label = "Avg pace",
                value = paceLabel,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun averagePaceLabel(workout: WorkoutEntity, distanceKm: Float): String {
    if (distanceKm <= 0f || workout.endTime <= workout.startTime) return "--"
    val durationMinutes = (workout.endTime - workout.startTime) / 60_000f
    if (durationMinutes <= 0f) return "--"
    val pace = durationMinutes / distanceKm
    val wholeMinutes = floor(pace).toInt()
    val seconds = ((pace - wholeMinutes) * 60f).toInt().coerceIn(0, 59)
    return String.format("%d:%02d /km", wholeMinutes, seconds)
}

private fun String.asModeLabel(): String =
    lowercase()
        .split('_')
        .joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
