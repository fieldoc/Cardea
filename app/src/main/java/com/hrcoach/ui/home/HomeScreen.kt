package com.hrcoach.ui.home

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
import androidx.hilt.navigation.compose.hiltViewModel
import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.ui.components.ActiveSessionCard
import com.hrcoach.ui.components.CardeaButton
import com.hrcoach.ui.components.CardeaLogo
import com.hrcoach.ui.theme.CardeaBgPrimary
import com.hrcoach.ui.theme.CardeaBgSecondary
import com.hrcoach.ui.theme.CardeaTextPrimary
import com.hrcoach.ui.theme.CardeaTextSecondary
import com.hrcoach.ui.theme.CardeaTextTertiary
import com.hrcoach.ui.theme.GlassBorder
import com.hrcoach.ui.theme.GlassHighlight
import com.hrcoach.ui.theme.GlassSurface

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
        GlassBorder to CardeaTextSecondary
}

@Composable
private fun StatChip(value: String, label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                color = GlassHighlight,
                shape = RoundedCornerShape(14.dp)
            )
            .border(
                width = 1.dp,
                color = GlassBorder,
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
                color = CardeaTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = CardeaTextSecondary
            )
        }
    }
}

@Composable
private fun StatChipsRow(state: HomeUiState, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.hasActiveBootcamp) {
            val lastKm = state.lastWorkout?.let { "%.1f".format(it.totalDistanceMeters / 1000f) } ?: "—"
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
            val lastKm = state.lastWorkout?.let { "%.1f".format(it.totalDistanceMeters / 1000f) } ?: "—"
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
    val textPrimary = if (isToday) CardeaTextPrimary else CardeaTextSecondary
    val textSecondary = if (isToday) CardeaTextSecondary else CardeaTextTertiary
    val borderColor = if (isToday) GlassSurface else GlassBorder

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
                                color = GlassSurface,
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
                            color = CardeaTextSecondary
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = GlassBorder,
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
                        color = CardeaTextSecondary
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
                color = GlassBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .background(
                color = GlassHighlight,
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
            color = CardeaTextSecondary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Start Bootcamp",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = CardeaTextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Adaptive program — HR zones, life-aware scheduling",
            style = MaterialTheme.typography.bodySmall,
            color = CardeaTextSecondary,
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
    val backgroundBrush = remember {
        Brush.radialGradient(
            colors = listOf(CardeaBgSecondary, CardeaBgPrimary),
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
                        color = CardeaTextPrimary
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
