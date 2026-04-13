package com.hrcoach.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.domain.coaching.CoachingInsight
import com.hrcoach.ui.components.ActiveSessionCard
import com.hrcoach.ui.components.CardeaButton
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientCyan
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.GradientRed
import com.hrcoach.ui.theme.ZoneGreen
import com.hrcoach.domain.education.ContentDensity
import com.hrcoach.domain.education.ZoneEducationProvider
import com.hrcoach.util.metersToUnit

// ── Zone pill color mapping ─────────────────────────────────────

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

// ── Greeting Row ────────────────────────────────────────────────

@Composable
private fun GreetingRow(
    greeting: String,
    sensorName: String?,
    isSessionRunning: Boolean,
    onSensorClick: () -> Unit,
    onAccountClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            ),
            color = CardeaTheme.colors.textSecondary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconButton32(onClick = onSensorClick) {
                SensorIcon(
                    isConnected = isSessionRunning,
                    hasName = sensorName != null
                )
            }
            IconButton32(onClick = onAccountClick) {
                ProfileIcon()
            }
        }
    }
}

@Composable
private fun IconButton32(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .border(1.dp, CardeaTheme.colors.glassBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun SensorIcon(isConnected: Boolean, hasName: Boolean) {
    val color = when {
        isConnected -> CardeaTheme.colors.zoneGreen
        hasName -> CardeaTheme.colors.textSecondary
        else -> CardeaTheme.colors.textTertiary
    }
    Canvas(modifier = Modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        drawCircle(
            color = color,
            radius = w * 0.15f,
            center = center,
            style = Stroke(width = 1.5f * density)
        )
        drawArc(
            color = color.copy(alpha = 0.4f),
            startAngle = 135f,
            sweepAngle = 90f,
            useCenter = false,
            style = Stroke(width = 1.5f * density, cap = StrokeCap.Round),
            topLeft = Offset(w * 0.08f, h * 0.2f),
            size = Size(w * 0.35f, h * 0.6f)
        )
        drawArc(
            color = color.copy(alpha = 0.4f),
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            style = Stroke(width = 1.5f * density, cap = StrokeCap.Round),
            topLeft = Offset(w * 0.57f, h * 0.2f),
            size = Size(w * 0.35f, h * 0.6f)
        )
    }
}

@Composable
private fun ProfileIcon() {
    val color = CardeaTheme.colors.textTertiary
    Canvas(modifier = Modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        drawCircle(
            color = color,
            radius = w * 0.19f,
            center = Offset(w * 0.5f, h * 0.35f),
            style = Stroke(width = 1.5f * density)
        )
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = -180f,
            useCenter = false,
            style = Stroke(width = 1.5f * density, cap = StrokeCap.Round),
            topLeft = Offset(w * 0.15f, h * 0.55f),
            size = Size(w * 0.7f, h * 0.5f)
        )
    }
}

// ── Pulse Hero ──────────────────────────────────────────────────

@Composable
private fun PulseHero(
    session: BootcampSessionEntity,
    weekNumber: Int,
    isToday: Boolean,
    dayLabel: String,
    modifier: Modifier = Modifier
) {
    val (pillBg, pillText) = zonePillColors(session.sessionType)
    val sessionLabel = session.sessionType
        .replace("_", " ")
        .split(" ")
        .joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() } }

    val headerText = if (isToday) {
        "TODAY\u2019S SESSION \u00B7 WEEK $weekNumber"
    } else {
        "NEXT RUN \u00B7 $dayLabel"
    }

    val heroAlpha = if (isToday) 1f else 0.5f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to GradientRed.copy(alpha = 0.08f * heroAlpha),
                        0.5f to GradientBlue.copy(alpha = 0.08f * heroAlpha),
                        1f to GradientCyan.copy(alpha = 0.03f * heroAlpha)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp)
        ) {
            Text(
                text = headerText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontSize = 10.sp
                ),
                color = CardeaTheme.colors.textTertiary
            )
            Spacer(Modifier.height(6.dp))
            val heroGradient = CardeaTheme.colors.gradient
            Text(
                text = sessionLabel,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                    letterSpacing = (-0.5).sp
                ),
                color = if (isToday) CardeaTheme.colors.textPrimary else CardeaTheme.colors.textSecondary,
                modifier = if (isToday) {
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(brush = heroGradient, blendMode = BlendMode.SrcIn)
                        }
                } else {
                    Modifier
                }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${session.targetMinutes} min \u00B7 $sessionLabel",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = CardeaTheme.colors.textSecondary
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .background(
                        color = pillBg,
                        shape = RoundedCornerShape(100.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    text = session.sessionType.replace("_", " "),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        fontSize = 12.sp
                    ),
                    color = pillText
                )
            }
            ZoneEducationProvider.forSessionType(
                session.sessionType, ContentDensity.ONE_LINER
            )?.let { oneLiner ->
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .width(2.dp)
                            .height(30.dp)
                            .background(
                                CardeaTheme.colors.textTertiary,
                                RoundedCornerShape(1.dp)
                            )
                    )
                    Text(
                        text = oneLiner,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        ),
                        color = CardeaTheme.colors.textTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // ECG glow + line sit below the text content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(85.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-15).dp)
                    .size(width = 180.dp, height = 60.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                GradientPink.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
            EcgLine(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(85.dp),
                alpha = if (isToday) 0.45f else 0.20f
            )
        }
    }
}

@Composable
private fun EcgLine(modifier: Modifier = Modifier, alpha: Float = 0.45f) {
    Canvas(modifier = modifier.graphicsLayer { this.alpha = alpha }) {
        val w = size.width
        val h = size.height

        // ECG path points normalized from mockup SVG viewBox 393x85
        val points = listOf(
            0f to 0.612f,
            0.191f to 0.612f,
            0.242f to 0.612f,
            0.285f to 0.212f,
            0.326f to 0.871f,
            0.366f to 0.118f,
            0.407f to 0.706f,
            0.445f to 0.494f,
            0.489f to 0.612f,
            0.682f to 0.612f,
            0.733f to 0.612f,
            0.776f to 0.235f,
            0.817f to 0.847f,
            0.857f to 0.165f,
            0.898f to 0.682f,
            0.936f to 0.518f,
            0.975f to 0.612f,
            1f to 0.612f
        )

        val path = Path().apply {
            points.forEachIndexed { i, (nx, ny) ->
                val x = nx * w
                val y = ny * h
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            brush = Brush.horizontalGradient(listOf(GradientRed, GradientPink, GradientBlue, GradientCyan)),
            style = Stroke(
                width = 2.5f * density,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

// ── CTA Row ─────────────────────────────────────────────────────

@Composable
private fun CtaRow(
    hasActiveBootcamp: Boolean,
    isNextSessionToday: Boolean,
    onStartSession: () -> Unit,
    onSetupBootcamp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        CardeaButton(
            text = if (hasActiveBootcamp) "Start Session" else "Set Up Bootcamp",
            onClick = if (hasActiveBootcamp) onStartSession else onSetupBootcamp,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        )
    }
}

// ── Bottom Half ─────────────────────────────────────────────────

@Composable
private fun BottomHalf(state: HomeUiState, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (state.hasActiveBootcamp) Modifier.verticalScroll(scrollState) else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.hasActiveBootcamp) {
            // Bootcamp enrolled: ring goal card + full-width volume tile
            WeekGoalRingCard(
                completedRuns = state.workoutsThisWeek,
                totalRuns = state.weeklyTarget,
                weekNumber = state.currentWeekNumber
            )
            VolumeTile(
                distanceKm = metersToUnit(state.totalDistanceThisWeekMeters.toFloat(), state.distanceUnit),
                distanceTargetKm = state.weeklyDistanceTargetKm,
                timeMinutes = state.totalTimeThisWeekMinutes,
                timeTargetMinutes = state.weeklyTimeTargetMinutes,
                distanceLabel = if (state.distanceUnit == com.hrcoach.domain.model.DistanceUnit.MI) "mi" else "km",
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            // No bootcamp: goal + streak tiles, optional coaching strip
            val hasLowerContent = state.coachingInsight != null
            PrimaryRow(
                state = state,
                modifier = if (hasLowerContent) Modifier.weight(1f)
                           else Modifier.fillMaxWidth().height(160.dp)
            )
            state.coachingInsight?.let { insight ->
                CoachingStrip(insight = insight)
            }
        }
    }
}

// ── Tier 1: Goal + Streak ───────────────────────────────────────

@Composable
private fun PrimaryRow(state: HomeUiState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GoalTile(
            current = state.workoutsThisWeek,
            target = state.weeklyTarget,
            modifier = Modifier.weight(1.2f)
        )
        StreakTile(
            streak = state.sessionStreak,
            modifier = Modifier.weight(0.8f)
        )
    }
}

@Composable
private fun GoalTile(current: Int, target: Int, modifier: Modifier = Modifier) {
    val remaining = (target - current).coerceAtLeast(0)
    val subText = when {
        remaining == 0 -> "goal hit!"
        remaining == 1 -> "one more run"
        else -> "$remaining more runs"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(CardeaTheme.colors.glassHighlight)
            .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(18.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "WEEKLY GOAL",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                fontSize = 10.sp
            ),
            color = CardeaTheme.colors.textTertiary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "$current/$target",
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 28.sp
            ),
            color = CardeaTheme.colors.textPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subText,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = CardeaTheme.colors.textTertiary
        )
    }
}

@Composable
private fun StreakTile(streak: Int, modifier: Modifier = Modifier) {
    val subText = if (streak > 0) "no misses" else "start today"

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(CardeaTheme.colors.glassHighlight)
            .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(18.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "STREAK",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                fontSize = 10.sp
            ),
            color = CardeaTheme.colors.textTertiary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = streak.toString(),
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 28.sp
            ),
            color = CardeaTheme.colors.textPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subText,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = CardeaTheme.colors.textTertiary
        )
    }
}

// ── Week Goal Ring Card (bootcamp-enrolled home) ─────────────────

@Composable
private fun WeekGoalRingCard(
    completedRuns: Int,
    totalRuns: Int,
    weekNumber: Int,
    modifier: Modifier = Modifier
) {
    val gradientBrush = CardeaTheme.colors.gradient  // CardeaGradient 4-stop
    val trackColor = CardeaTheme.colors.glassBorder
    val sweepAngle = if (totalRuns > 0) (completedRuns.toFloat() / totalRuns) * 360f else 0f
    val isWeekIncomplete = completedRuns < totalRuns

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardeaTheme.colors.glassHighlight)
            .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Progress ring — 84dp, 6dp stroke, CardeaGradient arc on glassBorder track
            Box(
                modifier = Modifier.size(84.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(84.dp)) {
                    val strokePx = 6.dp.toPx()
                    val inset = strokePx / 2f
                    val arcSize = Size(size.width - strokePx, size.height - strokePx)
                    // Track arc — always visible, even at 0%
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                    // Progress arc — CardeaGradient, skipped at exactly 0
                    if (sweepAngle > 0f) {
                        drawArc(
                            brush = gradientBrush,
                            startAngle = -90f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                    }
                }
                // Center label: "N / of M"
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$completedRuns",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = CardeaTheme.colors.textPrimary,
                        lineHeight = 22.sp
                    )
                    Text(
                        text = "of $totalRuns",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = CardeaTheme.colors.textSecondary,
                        lineHeight = 14.sp
                    )
                }
            }

            // Right column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "THIS WEEK",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                    color = CardeaTheme.colors.textTertiary
                )
                Text(
                    text = if (completedRuns == 0)
                        "$totalRuns ${if (totalRuns == 1) "run" else "runs"} scheduled"
                    else
                        "$completedRuns of $totalRuns ${if (totalRuns == 1) "run" else "runs"} done",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CardeaTheme.colors.textPrimary
                )
                if (weekNumber >= 2) {
                    Text(
                        text = "Week $weekNumber",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal,
                        color = CardeaTheme.colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Tagline — shown until the week goal is complete
        if (isWeekIncomplete) {
            Text(
                text = "$totalRuns ${if (totalRuns == 1) "run" else "runs"} this week. You've got this.",
                fontSize = 13.sp,
                color = CardeaTheme.colors.textSecondary,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ── Volume Tile ─────────────────────────────────────────────────

@Composable
private fun VolumeTile(
    distanceKm: Float,
    distanceTargetKm: Double,
    timeMinutes: Long,
    timeTargetMinutes: Long,
    distanceLabel: String = "km",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CardeaTheme.colors.glassHighlight)
            .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = "THIS WEEK",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
                fontSize = 10.sp
            ),
            color = CardeaTheme.colors.textTertiary
        )
        VolumeRow(
            label = "DIST",
            value = "%.0f / %.0f %s".format(distanceKm, distanceTargetKm, distanceLabel),
            progress = (distanceKm / distanceTargetKm.toFloat()).coerceIn(0f, 1f)
        )
        VolumeRow(
            label = "TIME",
            value = "$timeMinutes / $timeTargetMinutes min",
            progress = (timeMinutes.toFloat() / timeTargetMinutes.coerceAtLeast(1)).coerceIn(0f, 1f)
        )
    }
}

@Composable
private fun VolumeRow(label: String, value: String, progress: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp,
                fontSize = 10.sp
            ),
            color = CardeaTheme.colors.textTertiary,
            modifier = Modifier.width(30.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(CardeaTheme.colors.glassBorder)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceAtLeast(0.01f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                GradientRed.copy(alpha = 0.55f),
                                GradientPink.copy(alpha = 0.55f),
                                GradientBlue.copy(alpha = 0.55f)
                            )
                        )
                    )
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = CardeaTheme.colors.textSecondary
        )
    }
}

// ── Tier 3: Coaching Strip ──────────────────────────────────────

@Composable
private fun CoachingStrip(insight: CoachingInsight, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardeaTheme.colors.glassHighlight)
            .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CoachingEcgIcon(modifier = Modifier.size(18.dp))
        Text(
            text = insight.title,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                lineHeight = 18.sp
            ),
            color = CardeaTheme.colors.textSecondary,
            maxLines = 1
        )
    }
}

@Composable
private fun CoachingEcgIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.graphicsLayer { alpha = 0.5f }) {
        val w = size.width
        val h = size.height
        // ECG polyline normalized from mockup SVG viewBox 0 0 18 18
        val points = listOf(
            0.056f to 0.5f,
            0.25f to 0.5f,
            0.333f to 0.194f,
            0.417f to 0.806f,
            0.528f to 0.306f,
            0.611f to 0.611f,
            0.694f to 0.5f,
            0.944f to 0.5f
        )
        val path = Path().apply {
            points.forEachIndexed { i, (nx, ny) ->
                val x = nx * w
                val y = ny * h
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(GradientRed, GradientPink, GradientBlue)
            ),
            style = Stroke(
                width = 1.8f * density,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

// ── All Caught Up Card ──────────────────────────────────────────

@Composable
private fun AllCaughtUpCard(onGoToTraining: () -> Unit, modifier: Modifier = Modifier) {
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
            text = "THIS WEEK\u2019S SESSIONS",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            ),
            color = CardeaTheme.colors.textSecondary
        )
        Spacer(Modifier.height(8.dp))
        // 7a: Celebration ring
        val checkColor = ZoneGreen
        val ringTrack = CardeaTheme.colors.glassBorder
        val animatedSweep by animateFloatAsState(
            targetValue = 360f,
            animationSpec = tween(durationMillis = 800),
            label = "checkRing"
        )
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(40.dp)) {
                val strokeW = 3.dp.toPx()
                val radius = (size.minDimension - strokeW) / 2f
                drawCircle(
                    color = ringTrack,
                    radius = radius,
                    style = Stroke(width = strokeW)
                )
                drawArc(
                    color = checkColor,
                    startAngle = -90f,
                    sweepAngle = animatedSweep,
                    useCenter = false,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
            }
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = checkColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        // 7b: Elevated title (headlineMedium instead of titleLarge)
        Text(
            text = "All done for now",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = CardeaTheme.colors.textPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Next week\u2019s plan is on the way. Rest well.",
            style = MaterialTheme.typography.bodySmall,
            color = CardeaTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        CardeaButton(
            text = "View Training",
            onClick = onGoToTraining,
            innerPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
        )
    }
}

// ── No-Bootcamp Card ────────────────────────────────────────────

@Composable
private fun NoBootcampCard(
    onSetupBootcamp: () -> Unit,
    onStartRun: () -> Unit,
    modifier: Modifier = Modifier
) {
    val heroGradient = CardeaTheme.colors.gradient
    Column(modifier = modifier.padding(horizontal = 20.dp)) {
        // ── Feature showcase hero ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(
                    width = 1.dp,
                    color = CardeaTheme.colors.glassBorder,
                    shape = RoundedCornerShape(20.dp)
                )
                .background(
                    color = CardeaTheme.colors.glassHighlight,
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(20.dp)
        ) {
            // Badge
            Text(
                text = "YOUR PERSONAL RUNNING COACH",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                ),
                color = CardeaTheme.colors.textTertiary
            )
            Spacer(Modifier.height(10.dp))

            // Title
            Text(
                text = "Train smarter, not harder.",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = CardeaTheme.colors.textPrimary,
                modifier = Modifier
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(brush = heroGradient, blendMode = BlendMode.SrcIn)
                    }
            )
            Spacer(Modifier.height(6.dp))

            // Subtitle
            Text(
                text = "Cardea builds an adaptive plan around your life, fitness, and heart rate data.",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textSecondary
            )
            Spacer(Modifier.height(20.dp))

            // Feature items
            FeatureItem(
                icon = Icons.Default.FavoriteBorder,
                iconTint = GradientRed,
                iconBg = GradientRed.copy(alpha = 0.12f),
                title = "HR zone coaching",
                description = "Real-time alerts keep you in the right zone"
            )
            Spacer(Modifier.height(18.dp))
            FeatureItem(
                icon = Icons.Default.Timer,
                iconTint = GradientBlue,
                iconBg = GradientBlue.copy(alpha = 0.12f),
                title = "Life-aware scheduling",
                description = "Adapts to your week \u2014 block days, pick your long run"
            )
            Spacer(Modifier.height(18.dp))
            FeatureItem(
                icon = Icons.Default.Mic,
                iconTint = GradientCyan,
                iconBg = GradientCyan.copy(alpha = 0.12f),
                title = "Voice coaching",
                description = "Spoken pace, zone, and distance cues in your ear"
            )
            Spacer(Modifier.height(20.dp))

            // CTA button
            CardeaButton(
                text = "Set Up Bootcamp",
                onClick = onSetupBootcamp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ── Just Run strip ──
        Spacer(Modifier.height(12.dp))
        JustRunStrip(onClick = onStartRun)
    }
}

@Composable
private fun FeatureItem(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = CardeaTheme.colors.textPrimary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = CardeaTheme.colors.textSecondary
            )
        }
    }
}

@Composable
private fun JustRunStrip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = CardeaTheme.colors.glassBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .background(
                color = CardeaTheme.colors.glassHighlight,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Play icon in glass circle
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(CardeaTheme.colors.glassSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = CardeaTheme.colors.textPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Just run",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = CardeaTheme.colors.textPrimary
            )
            Text(
                text = "Skip the plan \u2014 start a manual free run now",
                style = MaterialTheme.typography.labelSmall,
                color = CardeaTheme.colors.textSecondary
            )
        }

        // Chevron
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = CardeaTheme.colors.textSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ── Root HomeScreen ─────────────────────────────────────────────

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Active session banner
            if (state.isSessionRunning) {
                ActiveSessionCard(onClick = onGoToWorkout)
            }

            // Greeting row
            GreetingRow(
                greeting = state.greeting,
                sensorName = state.sensorName,
                isSessionRunning = state.isSessionRunning,
                onSensorClick = onStartRun,
                onAccountClick = onGoToAccount
            )

            // Hero section
            val nextSession = state.nextSession
            when {
                state.hasActiveBootcamp && nextSession != null -> {
                    PulseHero(
                        session = nextSession,
                        weekNumber = state.currentWeekNumber,
                        isToday = state.isNextSessionToday,
                        dayLabel = state.nextSessionDayLabel
                    )
                }
                state.hasActiveBootcamp -> {
                    AllCaughtUpCard(onGoToTraining = onGoToBootcamp)
                }
                else -> {
                    NoBootcampCard(
                        onSetupBootcamp = onGoToBootcamp,
                        onStartRun = onStartRun
                    )
                }
            }

            // Partner nudge banner — visible when a partner ran today and user hasn't
            if (state.nudgeBanner != null) {
                Spacer(modifier = Modifier.height(12.dp))
                PartnerNudgeBanner(
                    state = state.nudgeBanner!!,
                    onTap = onGoToBootcamp,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            // CTA — only when bootcamp is active (NoBootcampCard has its own button)
            if (state.hasActiveBootcamp) {
                CtaRow(
                    hasActiveBootcamp = true,
                    isNextSessionToday = state.isNextSessionToday,
                    onStartSession = onGoToBootcamp,
                    onSetupBootcamp = onGoToBootcamp
                )
            }

            // Bottom half fills remaining space
            BottomHalf(
                state = state,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
