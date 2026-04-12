package com.hrcoach.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.ui.components.CardeaButton
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.components.cardeaSegmentedButtonColors
import com.hrcoach.ui.theme.CardeaCtaGradient
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientCyan
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.GradientRed
import com.hrcoach.ui.theme.ZoneGreen
import com.hrcoach.ui.theme.ZoneRed
import com.hrcoach.domain.model.DistanceUnit
import com.hrcoach.util.asModeLabel
import com.hrcoach.util.distanceUnitLabel
import com.hrcoach.util.formatDuration
import com.hrcoach.util.formatPace
import com.hrcoach.util.metersToUnit
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ── Week grouping model ──────────────────────────────────────────────────────

private data class WeekGroup(
    val label: String,
    val runCount: Int,
    val totalDistance: Float,
    val distanceLabel: String,
    val workouts: List<WorkoutEntity>
)

private fun groupWorkoutsByWeek(workouts: List<WorkoutEntity>, distanceUnit: DistanceUnit): List<WeekGroup> {
    if (workouts.isEmpty()) return emptyList()
    val cal = Calendar.getInstance()
    val nowYear = cal.get(Calendar.YEAR)
    val nowWeek = cal.get(Calendar.WEEK_OF_YEAR)
    val unitLabel = distanceUnitLabel(distanceUnit)

    return workouts
        .groupBy { w ->
            cal.timeInMillis = w.startTime
            cal.get(Calendar.YEAR) * 100 + cal.get(Calendar.WEEK_OF_YEAR)
        }
        .entries
        .sortedByDescending { it.key }
        .map { (key, items) ->
            val year = key / 100
            val week = key % 100
            val isThisWeek = year == nowYear && week == nowWeek
            val label = if (isThisWeek) "This Week" else weekRangeLabel(items)
            val totalDistance = metersToUnit(items.sumOf { it.totalDistanceMeters.toDouble() }.toFloat(), distanceUnit)
            WeekGroup(
                label = label,
                runCount = items.size,
                totalDistance = totalDistance,
                distanceLabel = unitLabel,
                workouts = items.sortedByDescending { it.startTime }
            )
        }
}

private fun weekRangeLabel(workouts: List<WorkoutEntity>): String {
    if (workouts.isEmpty()) return ""
    val cal = Calendar.getInstance()
    cal.timeInMillis = workouts.first().startTime
    cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
    val weekStart = cal.time
    cal.add(Calendar.DAY_OF_WEEK, 6)
    val weekEnd = cal.time
    val monthFmt = SimpleDateFormat("MMM", Locale.getDefault())
    val dayFmt = SimpleDateFormat("d", Locale.getDefault())
    return "${monthFmt.format(weekStart)} ${dayFmt.format(weekStart)} – ${dayFmt.format(weekEnd)}"
}

// ── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryListScreen(
    onWorkoutClick: (Long) -> Unit,
    onStartWorkout: () -> Unit,
    onGoToTrends: (() -> Unit)? = null,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val workouts by viewModel.workouts.collectAsStateWithLifecycle()
    val distanceUnit = viewModel.distanceUnit
    val weekGroups = remember(workouts, distanceUnit) { groupWorkoutsByWeek(workouts, distanceUnit) }
    var deleteModeId by remember { mutableStateOf<Long?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CardeaTheme.colors.bgPrimary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // ── Header ──────────────────────────────────────────────────────
            Text(
                text = "Activity",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = CardeaTheme.colors.textPrimary
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (onGoToTrends != null) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        selected = true,
                        onClick = {},
                        colors = cardeaSegmentedButtonColors()
                    ) { Text("Log") }
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        selected = false,
                        onClick = onGoToTrends,
                        colors = cardeaSegmentedButtonColors()
                    ) { Text("Trends") }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Content ─────────────────────────────────────────────────────
            if (workouts.isEmpty()) {
                HistoryEmptyState(onStartWorkout = onStartWorkout)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    weekGroups.forEach { group ->
                        item(key = "header_${group.label}") {
                            WeekHeader(group = group)
                        }
                        items(group.workouts, key = { it.id }) { workout ->
                            WeekWorkoutCard(
                                workout = workout,
                                distanceUnit = distanceUnit,
                                isDeleteMode = deleteModeId == workout.id,
                                onClick = {
                                    if (deleteModeId != null) {
                                        deleteModeId = null
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
                        item(key = "spacer_${group.label}") {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = {
                    showDeleteDialog = false
                    deleteModeId = null
                },
                title = { Text("Delete this run?") },
                text = { Text("This will permanently remove all route data and stats.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            deleteModeId?.let { viewModel.deleteWorkout(it) }
                            showDeleteDialog = false
                            deleteModeId = null
                        }
                    ) {
                        Text("Delete", color = ZoneRed)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDeleteDialog = false
                        deleteModeId = null
                    }) { Text("Cancel") }
                },
                containerColor = CardeaTheme.colors.bgPrimary,
                titleContentColor = CardeaTheme.colors.textPrimary,
                textContentColor = CardeaTheme.colors.textSecondary
            )
        }
    }
}

// ── Week header ──────────────────────────────────────────────────────────────

@Composable
private fun WeekHeader(group: WeekGroup) {
    val isThisWeek = group.label == "This Week"

    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(top = 8.dp, bottom = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = group.label,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.sp
                ),
                color = if (isThisWeek) CardeaTheme.colors.textPrimary else CardeaTheme.colors.textSecondary
            )
            Text(
                text = "${group.runCount} run${if (group.runCount != 1) "s" else ""} · ${"%.1f".format(group.totalDistance)} ${group.distanceLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textTertiary
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    brush = if (isThisWeek) {
                        Brush.horizontalGradient(
                            listOf(GradientRed.copy(alpha = 0.5f), GradientPink.copy(alpha = 0.2f), Color.Transparent)
                        )
                    } else {
                        Brush.horizontalGradient(
                            listOf(CardeaTheme.colors.glassBorder, Color.Transparent)
                        )
                    }
                )
        )
        Spacer(modifier = Modifier.height(6.dp))
    }
}

// ── Workout card ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeekWorkoutCard(
    workout: WorkoutEntity,
    distanceUnit: DistanceUnit,
    isDeleteMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val cal = Calendar.getInstance().apply { timeInMillis = workout.startTime }
    val dayNumber = SimpleDateFormat("d", Locale.getDefault()).format(cal.time)
    val dayName = SimpleDateFormat("EEE", Locale.getDefault()).format(cal.time).uppercase()

    val distanceInUnit = metersToUnit(workout.totalDistanceMeters, distanceUnit)
    val distanceText = "%.2f".format(distanceInUnit)
    val unitLabel = distanceUnitLabel(distanceUnit)
    val duration = formatDuration(workout.startTime, workout.endTime)
    val pace = averagePaceLabel(workout, distanceUnit)
    val modeLabel = workout.mode.asModeLabel()

    val modeBrush = if (workout.mode == "DISTANCE_PROFILE") {
        CardeaCtaGradient
    } else null
    val modeColor = when (workout.mode) {
        "FREE_RUN" -> GradientBlue
        "STEADY_STATE" -> ZoneGreen
        else -> Color.Unspecified
    }

    Box(modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 4.dp)
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Day column
                Column(
                    modifier = Modifier.width(36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = dayNumber,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp,
                            lineHeight = 20.sp
                        ),
                        color = CardeaTheme.colors.textPrimary
                    )
                    Text(
                        text = dayName,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.06.sp
                        ),
                        color = CardeaTheme.colors.textTertiary
                    )
                }

                // Divider
                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .width(1.dp)
                        .height(36.dp)
                        .background(CardeaTheme.colors.glassBorder)
                )

                // Center content
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Distance number
                        Text(
                            text = distanceText,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp,
                                fontSize = 20.sp,
                                brush = modeBrush
                            ),
                            color = if (modeBrush != null) Color.Unspecified else modeColor
                        )
                        Text(
                            text = " $unitLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = CardeaTheme.colors.textTertiary,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = modeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.06.sp
                            ),
                            color = CardeaTheme.colors.textSecondary,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = duration,
                            style = MaterialTheme.typography.bodySmall,
                            color = CardeaTheme.colors.textSecondary,
                            maxLines = 1
                        )
                        Text(
                            text = pace,
                            style = MaterialTheme.typography.bodySmall,
                            color = CardeaTheme.colors.textSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // X badge for delete mode
        AnimatedVisibility(
            visible = isDeleteMode,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-6).dp)
                .zIndex(1f),
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f)
        ) {
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .size(26.dp)
                    .background(color = CardeaTheme.colors.zoneRed, shape = androidx.compose.foundation.shape.CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Delete workout",
                    tint = CardeaTheme.colors.onGradient,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// ── Empty state ──────────────────────────────────────────────────────────────

@Composable
private fun EcgWaveIllustration(modifier: Modifier = Modifier) {
    val gradientColors = listOf(GradientRed, GradientPink, GradientBlue, GradientCyan)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val midY = h / 2f

        val path = Path().apply {
            moveTo(0f, midY)
            lineTo(w * 0.22f, midY)
            // P wave — small smooth bump
            cubicTo(w * 0.25f, midY - h * 0.10f, w * 0.30f, midY - h * 0.10f, w * 0.33f, midY)
            // PR segment
            lineTo(w * 0.38f, midY)
            // Q dip
            lineTo(w * 0.41f, midY + h * 0.14f)
            // R peak — tall spike, the heartbeat
            lineTo(w * 0.45f, midY - h * 0.80f)
            // S dip
            lineTo(w * 0.49f, midY + h * 0.18f)
            // ST segment
            lineTo(w * 0.54f, midY)
            // T wave — smooth dome
            cubicTo(w * 0.58f, midY - h * 0.18f, w * 0.65f, midY - h * 0.18f, w * 0.70f, midY)
            // Flat outro
            lineTo(w, midY)
        }

        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = gradientColors,
                start = Offset(w * 0.22f, midY),
                end = Offset(w * 0.70f, midY)
            ),
            style = Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
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
        EcgWaveIllustration(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Your run archive is still empty",
            style = MaterialTheme.typography.headlineSmall,
            color = CardeaTheme.colors.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Start a workout to unlock route replay, post-run insights, and long-term progress trends.",
            style = MaterialTheme.typography.bodyLarge,
            color = CardeaTheme.colors.textSecondary
        )
        Spacer(modifier = Modifier.height(20.dp))
        CardeaButton(
            text = "Start a Run",
            onClick = onStartWorkout,
            modifier = Modifier.height(44.dp),
            innerPadding = PaddingValues(horizontal = 24.dp)
        )
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun averagePaceLabel(workout: WorkoutEntity, unit: DistanceUnit): String {
    val distanceKm = workout.totalDistanceMeters / 1000f
    if (distanceKm <= 0f || workout.endTime <= workout.startTime) return "--"
    val durationMinutes = (workout.endTime - workout.startTime) / 60_000f
    if (durationMinutes <= 0f) return "--"
    return formatPace(durationMinutes / distanceKm, unit)
}

