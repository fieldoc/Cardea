package com.hrcoach.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.hilt.navigation.compose.hiltViewModel
import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.domain.coaching.CoachingIcon
import com.hrcoach.domain.coaching.CoachingInsight
import com.hrcoach.ui.components.ActiveSessionCard
import com.hrcoach.ui.components.CardeaButton
import com.hrcoach.ui.components.CardeaLogo
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.util.metersToKm

@Composable
private fun zonePillColors(sessionType: String): Pair<Color, Color> = when {
    sessionType.contains("Z2", ignoreCase = true) ||
    sessionType.contains("EASY", ignoreCase = true) ||
    sessionType.contains("AEROBIC", ignoreCase = true) ->
        Color(0xFF4D61FF).copy(alpha = 0.2f) to Color(0xFF7B8FFF)
    sessionType.contains("Z4", ignoreCase = true) ||
    sessionType.contains("TEMPO", ignoreCase = true) ||
    sessionType.contains("INTERVAL", ignoreCase = true) ->
        Color(0xFFFF4D5A).copy(alpha = 0.2f) to Color(0xFFFF7B84)
    else ->
        CardeaTheme.colors.glassBorder to CardeaTheme.colors.textSecondary
}

@Composable
private fun StatChip(value: String, label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                color = CardeaTheme.colors.glassHighlight,
                shape = RoundedCornerShape(14.dp)
            )
            .border(
                width = 1.dp,
                color = CardeaTheme.colors.glassBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    lineHeight = 20.sp
                ),
                color = CardeaTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = CardeaTheme.colors.textSecondary
            )
        }
    }
}

@Composable
private fun StatChipsRow(state: HomeUiState, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.hasActiveBootcamp) {
            val lastKm = state.lastWorkout?.let { "%.1f".format(metersToKm(it.totalDistanceMeters)) } ?: "—"
            StatChip(
                value = "${state.workoutsThisWeek}/${state.weeklyTarget}",
                label = "GOAL",
                modifier = Modifier.weight(1f)
            )
            StatChip(
                value = if (state.lastWorkout != null) "$lastKm km" else "—",
                label = "LAST RUN",
                modifier = Modifier.weight(1f)
            )
            StatChip(
                value = state.sessionStreak.toString(),
                label = "NO MISSES",
                modifier = Modifier.weight(1f)
            )
        } else {
            val lastKm = state.lastWorkout?.let { "%.1f".format(metersToKm(it.totalDistanceMeters)) } ?: "—"
            val lastMin = state.lastWorkout?.let {
                val mins = ((it.endTime - it.startTime) / 60_000L).coerceAtLeast(0)
                if (mins == 0L) "< 1 min" else "$mins min"
            } ?: "—"
            StatChip(
                value = state.workoutsThisWeek.toString(),
                label = "THIS WEEK",
                modifier = Modifier.weight(1f)
            )
            StatChip(
                value = if (state.lastWorkout != null) "$lastKm km" else "—",
                label = "LAST RUN",
                modifier = Modifier.weight(1f)
            )
            StatChip(
                value = lastMin,
                label = "DURATION",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BootcampProgressRing(
    currentWeek: Int,
    totalWeeks: Int,
    percentComplete: Float,
    modifier: Modifier = Modifier
) {
    val gradientPink = Color(0xFFFF2DA6)
    val gradientPurple = Color(0xFF5B5BFF)
    val textPrimary = CardeaTheme.colors.textPrimary
    val textSecondary = CardeaTheme.colors.textSecondary
    val trackColor = CardeaTheme.colors.glassBorder

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardeaTheme.colors.glassHighlight)
            .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "BOOTCAMP",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            ),
            color = textSecondary
        )
        Spacer(Modifier.height(12.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(88.dp)) {
            Canvas(modifier = Modifier.size(88.dp)) {
                val strokeWidth = 6.dp.toPx()
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(gradientPink, gradientPurple)
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * percentComplete.coerceIn(0f, 1f),
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "W$currentWeek",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        lineHeight = 20.sp
                    ),
                    color = textPrimary
                )
                Text(
                    text = "of $totalWeeks",
                    style = MaterialTheme.typography.labelSmall,
                    color = textSecondary
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${(percentComplete * 100).toInt()}% complete",
            style = MaterialTheme.typography.labelSmall,
            color = textSecondary
        )
    }
}

@Composable
private fun WeeklyVolumeCard(state: HomeUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardeaTheme.colors.glassHighlight)
            .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "THIS WEEK",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            ),
            color = CardeaTheme.colors.textSecondary
        )
        Spacer(Modifier.height(14.dp))
        VolumeBar(
            label = "Distance",
            value = "%.1f / %.0f km".format(
                metersToKm(state.totalDistanceThisWeekMeters.toFloat()),
                state.weeklyDistanceTargetKm
            ),
            progress = (metersToKm(state.totalDistanceThisWeekMeters.toFloat()) / state.weeklyDistanceTargetKm).toFloat(),
            gradientColors = listOf(Color(0xFFFF5A5F), Color(0xFFFF2DA6))
        )
        Spacer(Modifier.height(14.dp))
        VolumeBar(
            label = "Time",
            value = "${state.totalTimeThisWeekMinutes} / ${state.weeklyTimeTargetMinutes} min",
            progress = (state.totalTimeThisWeekMinutes.toFloat() / state.weeklyTimeTargetMinutes.coerceAtLeast(1)).coerceIn(0f, 1f),
            gradientColors = listOf(Color(0xFF5B5BFF), Color(0xFF00D1FF))
        )
        Spacer(Modifier.height(14.dp))
        VolumeBar(
            label = "Runs",
            value = "${state.workoutsThisWeek} / ${state.weeklyTarget}",
            progress = (state.workoutsThisWeek.toFloat() / state.weeklyTarget.coerceAtLeast(1)).coerceIn(0f, 1f),
            gradientColors = listOf(Color(0xFF00D1FF), Color(0xFF4DFF88))
        )
    }
}

@Composable
private fun VolumeBar(
    label: String,
    value: String,
    progress: Float,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = CardeaTheme.colors.textSecondary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = CardeaTheme.colors.textPrimary
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(CardeaTheme.colors.glassBorder)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.linearGradient(gradientColors))
            )
        }
    }
}

@Composable
private fun CoachingInsightCard(insight: CoachingInsight, modifier: Modifier = Modifier) {
    val iconEmoji = when (insight.icon) {
        CoachingIcon.LIGHTBULB -> "\uD83D\uDCA1"
        CoachingIcon.CHART_UP  -> "\uD83D\uDCC8"
        CoachingIcon.TROPHY    -> "\uD83C\uDFC6"
        CoachingIcon.WARNING   -> "\u26A0\uFE0F"
        CoachingIcon.HEART     -> "\u2764\uFE0F"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardeaTheme.colors.glassHighlight)
            .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF5B5BFF).copy(alpha = 0.3f),
                            Color(0xFF00D1FF).copy(alpha = 0.2f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = iconEmoji, fontSize = 18.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = insight.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = CardeaTheme.colors.textPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = insight.subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = CardeaTheme.colors.textSecondary
            )
        }
    }
}

@Composable
private fun BootcampHeroCard(
    session: BootcampSessionEntity,
    weekNumber: Int,
    isToday: Boolean,
    dayLabel: String,
    onStartSession: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (pillBg, pillText) = zonePillColors(session.sessionType)
    val sessionLabel = session.sessionType
        .replace("_", " ")
        .split(" ")
        .joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() } }

    // Mute visual intensity when the session isn't today
    val gradientAlpha = if (isToday) 1f else 0.45f
    val textPrimary = if (isToday) CardeaTheme.colors.textPrimary else CardeaTheme.colors.textSecondary
    val textSecondary = if (isToday) CardeaTheme.colors.textSecondary else CardeaTheme.colors.textTertiary
    val borderColor = if (isToday) CardeaTheme.colors.glassSurface else CardeaTheme.colors.glassBorder

    val headerText = if (isToday) {
        "TODAY'S SESSION · WEEK $weekNumber"
    } else {
        "NEXT RUN · $dayLabel"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color(0x33FF2DA6).copy(alpha = 0x33 / 255f * gradientAlpha),
                        1f to Color(0x14E5FFFF).copy(alpha = 0x14 / 255f * gradientAlpha)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        // Radial glow — top right corner (hidden when muted)
        if (isToday) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .offset(x = (-30).dp, y = (-30).dp)
                    .align(Alignment.TopEnd)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x40FF2DA6), Color.Transparent)
                        ),
                        shape = CircleShape
                    )
            )
        }

        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = headerText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                ),
                color = textSecondary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = sessionLabel,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    lineHeight = 24.sp
                ),
                color = textPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${session.targetMinutes} min · $sessionLabel",
                style = MaterialTheme.typography.bodySmall,
                color = textSecondary
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .background(
                        color = if (isToday) pillBg else pillBg.copy(alpha = pillBg.alpha * 0.5f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = session.sessionType.replace("_", " "),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = if (isToday) pillText else pillText.copy(alpha = 0.6f)
                )
            }
            Spacer(Modifier.height(16.dp))
            if (isToday) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CardeaButton(
                        text = "Start Session",
                        onClick = onStartSession,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = CardeaTheme.colors.glassSurface,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onDetails)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Details",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = CardeaTheme.colors.textSecondary
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = CardeaTheme.colors.glassBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onDetails)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Preview",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = CardeaTheme.colors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun NoBootcampCard(onSetupBootcamp: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = CardeaTheme.colors.glassBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .background(
                color = CardeaTheme.colors.glassHighlight,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "STRUCTURED TRAINING",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            ),
            color = CardeaTheme.colors.textSecondary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Start Bootcamp",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = CardeaTheme.colors.textPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Adaptive program — HR zones, life-aware scheduling",
            style = MaterialTheme.typography.bodySmall,
            color = CardeaTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        CardeaButton(
            text = "Set Up Bootcamp",
            onClick = onSetupBootcamp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

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
    val bgSecondary = CardeaTheme.colors.bgSecondary
    val bgPrimary = CardeaTheme.colors.bgPrimary
    val backgroundBrush = remember(bgSecondary, bgPrimary) {
        Brush.radialGradient(
            colors = listOf(bgSecondary, bgPrimary),
            center = Offset.Zero,
            radius = 1800f
        )
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Active session banner — unchanged
                if (state.isSessionRunning) {
                    ActiveSessionCard(onClick = onGoToWorkout)
                }

                // Header — greeting + icons (CARDEA text label removed)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = state.greeting,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = CardeaTheme.colors.textPrimary
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardeaTheme.colors.glassHighlight)
                                .clickable(onClick = onStartRun)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            val sensorColor = when {
                                state.isSessionRunning -> CardeaTheme.colors.zoneGreen
                                state.sensorName != null -> CardeaTheme.colors.textSecondary
                                else -> CardeaTheme.colors.textTertiary
                            }
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = "Sensor setup",
                                tint = sensorColor,
                                modifier = Modifier.size(16.dp)
                            )
                            val sensorName = state.sensorName
                            if (sensorName != null) {
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = sensorName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CardeaTheme.colors.textSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(CardeaTheme.colors.glassHighlight)
                                .clickable(onClick = onGoToAccount),
                            contentAlignment = Alignment.Center
                        ) {
                            CardeaLogo(size = 28.dp, animate = false)
                        }
                    }
                }

                // Hero section
                val nextSession = state.nextSession
                if (state.hasActiveBootcamp && nextSession != null) {
                    BootcampHeroCard(
                        session = nextSession,
                        weekNumber = state.currentWeekNumber,
                        isToday = state.isNextSessionToday,
                        dayLabel = state.nextSessionDayLabel,
                        onStartSession = onGoToBootcamp,
                        onDetails = onGoToBootcamp,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    NoBootcampCard(
                        onSetupBootcamp = onGoToBootcamp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Stat chips
                StatChipsRow(state = state)

                // Progress ring + Weekly volume side by side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.hasActiveBootcamp) {
                        BootcampProgressRing(
                            currentWeek = state.currentWeekNumber,
                            totalWeeks = state.bootcampTotalWeeks,
                            percentComplete = state.bootcampPercentComplete,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    WeeklyVolumeCard(
                        state = state,
                        modifier = if (state.hasActiveBootcamp) Modifier.weight(1f) else Modifier.fillMaxWidth()
                    )
                }

                // Coaching insight
                state.coachingInsight?.let { insight ->
                    CoachingInsightCard(insight = insight)
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
