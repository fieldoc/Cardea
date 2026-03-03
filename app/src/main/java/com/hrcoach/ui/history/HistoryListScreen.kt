package com.hrcoach.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.hrcoach.R
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaTextPrimary
import com.hrcoach.ui.theme.ZoneRed
import com.hrcoach.util.formatDuration
import com.hrcoach.util.formatWorkoutDate
import kotlin.math.floor

private val HistoryBackdrop = Brush.radialGradient(
    colors = listOf(
        Color(0xFF0F1623),
        Color(0xFF0B0F17)
    )
)

private val HistoryGlass = Color(0x0FFFFFFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryListScreen(
    onWorkoutClick: (Long) -> Unit,
    onStartWorkout: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val workouts by viewModel.workouts.collectAsState()
    var deleteModeId by remember { mutableStateOf<Long?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val dismissDelete: () -> Unit = { showDeleteDialog = false; deleteModeId = null }

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
                    color = Color(0xFFB6C2D1)
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
                                isDeleteMode = deleteModeId == workout.id,
                                onClick = {
                                    if (deleteModeId != null) {
                                        val wasThisCard = deleteModeId == workout.id
                                        deleteModeId = null
                                        if (!wasThisCard) onWorkoutClick(workout.id)
                                    } else {
                                        onWorkoutClick(workout.id)
                                    }
                                },
                                onLongClick = { deleteModeId = workout.id },
                                onDeleteClick = {
                                    deleteModeId = workout.id
                                    showDeleteDialog = true
                                }
                            )
                        }
                    }
                    if (showDeleteDialog) {
                        AlertDialog(
                            onDismissRequest = dismissDelete,
                            title = { Text("Delete this run?") },
                            text = { Text("This will permanently remove all route data and stats.") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        deleteModeId?.let { viewModel.deleteWorkout(it) }
                                        dismissDelete()
                                    }
                                ) {
                                    Text("Delete", color = ZoneRed)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = dismissDelete) { Text("Cancel") }
                            },
                            containerColor = Color(0xFF141B27),
                            titleContentColor = Color(0xFFF5F7FB),
                            textContentColor = Color(0xFFB6C2D1)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryEmptyState(onStartWorkout: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        colors = CardDefaults.cardColors(containerColor = HistoryGlass)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Your run archive is still empty",
                style = MaterialTheme.typography.headlineSmall,
                color = Color(0xFFF5F7FB),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Start a workout to unlock route replay, post-run insights, and long-term progress trends.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFB8C5D3)
            )
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardeaGradient)
                    .clickable(onClick = onStartWorkout)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.button_start_workout),
                    color = CardeaTextPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkoutCard(
    workout: WorkoutEntity,
    isDeleteMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val date = formatWorkoutDate(workout.startTime)
    val duration = formatDuration(workout.startTime, workout.endTime)
    val distanceKm = workout.totalDistanceMeters / 1000f
    val distanceLabel = String.format("%.2f km", distanceKm)
    val paceLabel = averagePaceLabel(workout, distanceKm)

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(
                1.dp,
                if (isDeleteMode) ZoneRed.copy(alpha = 0.33f) else Color.White.copy(alpha = 0.08f)
            ),
            colors = CardDefaults.cardColors(containerColor = HistoryGlass)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = date,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFF5F7FB),
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
                                color = Color(0xFFD8E8FF)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = distanceLabel,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color(0xFFFDFEFF),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Tap for route and stats",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8FA4B7)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HistoryMetricChip(
                        label = "Duration",
                        value = duration,
                        modifier = Modifier.weight(1f)
                    )
                    HistoryMetricChip(
                        label = "Avg pace",
                        value = paceLabel,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // X badge — bleeds past top-right corner
        AnimatedVisibility(
            visible = isDeleteMode,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-6).dp)
                .zIndex(1f),
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(color = ZoneRed, shape = androidx.compose.foundation.shape.CircleShape)
                    .clickable(onClick = onDeleteClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Delete workout",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun HistoryMetricChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8EA4B8)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFF4F7FB),
                fontWeight = FontWeight.Medium
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
