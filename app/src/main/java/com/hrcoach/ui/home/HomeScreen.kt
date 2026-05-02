package com.hrcoach.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.domain.coaching.CoachingInsight
import com.hrcoach.domain.model.RaceGoal
import com.hrcoach.ui.components.ActiveSessionCard
import com.hrcoach.ui.components.CardeaButton
import com.hrcoach.ui.components.CardeaButtonEmphasis
import com.hrcoach.ui.theme.CardeaAmber
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientCyan
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.GradientRed
import com.hrcoach.ui.theme.ZoneGreen
import com.hrcoach.domain.education.ContentDensity
import com.hrcoach.domain.education.ZoneEducationProvider
import com.hrcoach.util.metersToUnit
import java.time.LocalDate

// ── Home Identity Tokens ────────────────────────────────────────
// A small, deliberate set of constants that keep every card on Home
// speaking the same visual language. Polish, not reinvention — one
// radius, one padding, one label treatment, one signature mark.

private val HomeCardRadius = RoundedCornerShape(16.dp)
private val HomeCardPadding = 16.dp
private val HomeGutter = 20.dp
private val HomeCardGap = 12.dp

/**
 * Cardea's signature "pulse dot" — a single cyan glyph the size of the
 * ECG line's QRS peak, echoed on every uppercase section label. It's
 * the only identity accent on the Home screen and is the thing that
 * reads as "Cardea" before any other pixel on the page does.
 */
@Composable
private fun PulseDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(4.dp)
            .clip(CircleShape)
            .background(GradientCyan)
    )
}

/**
 * Standard section label — uppercase, tight tracking, tertiary text,
 * prefixed with the pulse dot. Used on every card header on Home.
 */
@Composable
private fun PulseLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = CardeaTheme.colors.textTertiary,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PulseDot()
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                fontSize = 10.sp,
            ),
            color = color,
        )
    }
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
            color = CardeaTheme.colors.textTertiary
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
    maxHr: Int?,
    restHr: Int?,
    modifier: Modifier = Modifier
) {
    val dayEpoch = remember { LocalDate.now().toEpochDay() }
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
                // Vertical wash — fades toward the background at the bottom
                // so the hero dissolves into the CTA rather than sitting as
                // a separate island above it.
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to GradientRed.copy(alpha = 0.10f * heroAlpha),
                        0.55f to GradientPink.copy(alpha = 0.05f * heroAlpha),
                        1f to Color.Transparent
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.padding(start = HomeGutter, end = HomeGutter, top = 18.dp, bottom = 8.dp)
        ) {
            PulseLabel(text = headerText)
            Spacer(Modifier.height(6.dp))
            val heroGradient = CardeaTheme.colors.gradient
            Text(
                text = sessionLabel,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                    letterSpacing = (-0.5).sp
                ),
                color = CardeaTheme.colors.textPrimary,
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
            ZoneEducationProvider.forSessionType(
                session.sessionType,
                ContentDensity.ONE_LINER,
                maxHr = maxHr,
                restHr = restHr,
                dayEpoch = dayEpoch
            )?.let { oneLiner ->
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Signature accent — 2dp vertical stripe in cyan to
                    // echo the ECG/PulseDot identity. Faded so it reads
                    // as "quiet presence" not "primary CTA".
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .width(2.dp)
                            .height(30.dp)
                            .background(
                                GradientCyan.copy(alpha = 0.55f),
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
                alpha = if (isToday) 0.70f else 0.50f
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

        // Gradient distribution is biased early: red lives where the
        // first QRS spike fires, pink holds the dense middle beats,
        // blue/cyan tail off into the quiet baseline. The line should
        // feel like a signal decaying into the page, not a rainbow
        // stripe wiped across it.
        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0.00f to GradientRed,
                    0.30f to GradientPink,
                    0.65f to GradientBlue,
                    1.00f to GradientCyan
                )
            ),
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
            .padding(horizontal = HomeGutter, vertical = 0.dp)
    ) {
        CardeaButton(
            text = if (hasActiveBootcamp) "Start Session" else "Set Up Bootcamp",
            onClick = if (hasActiveBootcamp) onStartSession else onSetupBootcamp,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            emphasis = CardeaButtonEmphasis.Tonal,
        )
    }
}

// ── Bottom Half ─────────────────────────────────────────────────

@Composable
private fun BottomHalf(
    state: HomeUiState,
    onNudgeTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (state.hasActiveBootcamp) Modifier.verticalScroll(scrollState) else Modifier)
            .padding(horizontal = HomeGutter, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(HomeCardGap)
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
                timeMinutes = state.totalTimeThisWeekMinutes,
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
        state.nudgeBanner?.let { nudge ->
            PartnerNudgeBanner(state = nudge, onTap = onNudgeTap)
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
            .clip(HomeCardRadius)
            .background(CardeaTheme.colors.glassHighlight)
            .border(1.dp, CardeaTheme.colors.glassBorder, HomeCardRadius)
            .padding(HomeCardPadding),
        verticalArrangement = Arrangement.Center
    ) {
        PulseLabel(text = "WEEKLY GOAL")
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
            .clip(HomeCardRadius)
            .background(CardeaTheme.colors.glassHighlight)
            .border(1.dp, CardeaTheme.colors.glassBorder, HomeCardRadius)
            .padding(HomeCardPadding),
        verticalArrangement = Arrangement.Center
    ) {
        PulseLabel(text = "STREAK")
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
            .clip(HomeCardRadius)
            .background(CardeaTheme.colors.glassHighlight)
            .border(1.dp, CardeaTheme.colors.glassBorder, HomeCardRadius)
            .padding(HomeCardPadding),
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
                PulseLabel(text = "THIS WEEK")
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
    timeMinutes: Long,
    distanceLabel: String = "km",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(HomeCardRadius)
            .background(CardeaTheme.colors.glassHighlight)
            .border(1.dp, CardeaTheme.colors.glassBorder, HomeCardRadius)
            .padding(HomeCardPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PulseLabel(text = "VOLUME")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VolumeMetric(
                value = "%.1f $distanceLabel".format(distanceKm),
                label = "DISTANCE"
            )
            VolumeMetric(
                value = "$timeMinutes min",
                label = "TIME"
            )
        }
    }
}

@Composable
private fun VolumeMetric(value: String, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = CardeaTheme.colors.textPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Medium
            ),
            color = CardeaTheme.colors.textTertiary
        )
    }
}

// ── Tier 3: Coaching Strip ──────────────────────────────────────

@Composable
private fun CoachingStrip(insight: CoachingInsight, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(HomeCardRadius)
            .background(CardeaTheme.colors.glassHighlight)
            .border(1.dp, CardeaTheme.colors.glassBorder, HomeCardRadius)
            .padding(horizontal = HomeCardPadding, vertical = 12.dp),
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
                shape = HomeCardRadius
            )
            .background(
                color = CardeaTheme.colors.glassHighlight,
                shape = HomeCardRadius
            )
            .padding(HomeCardPadding + 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PulseLabel(
            text = "THIS WEEK\u2019S SESSIONS",
            color = CardeaTheme.colors.textSecondary,
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
    Column(modifier = modifier.padding(horizontal = HomeGutter)) {
        // ── Feature showcase hero ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(HomeCardRadius)
                .border(
                    width = 1.dp,
                    color = CardeaTheme.colors.glassBorder,
                    shape = HomeCardRadius
                )
                .background(
                    color = CardeaTheme.colors.glassHighlight,
                    shape = HomeCardRadius
                )
                .padding(HomeCardPadding)
        ) {
            PulseLabel(text = "YOUR PERSONAL RUNNING COACH")
            Spacer(Modifier.height(10.dp))

            // Title \u2014 gradient via BlendMode.SrcIn
            Text(
                text = "Train smarter,\nnot harder.",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = CardeaTheme.colors.textPrimary,
                modifier = Modifier
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(brush = heroGradient, blendMode = BlendMode.SrcIn)
                    }
            )
            Spacer(Modifier.height(8.dp))

            // Subtitle \u2014 refreshed copy
            Text(
                text = "An adaptive plan that knows your week, your heart, and your pace.",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textSecondary
            )
            Spacer(Modifier.height(22.dp))

            // Feature trio nested glass container
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        width = 1.dp,
                        color = CardeaTheme.colors.glassBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .background(Color.White.copy(alpha = 0.02f))
                    .padding(horizontal = 14.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                FeatureItem(
                    icon = Icons.Default.FavoriteBorder,
                    iconTint = GradientRed,
                    iconBg = GradientRed.copy(alpha = 0.12f),
                    title = "HR zone coaching",
                    description = "Live alerts keep you in the right zone."
                )
                FeatureItem(
                    icon = Icons.Default.Timer,
                    iconTint = GradientBlue,
                    iconBg = GradientBlue.copy(alpha = 0.12f),
                    title = "Life-aware scheduling",
                    description = "Adapts to your week \u2014 block days, pick your long run."
                )
                FeatureItem(
                    icon = Icons.Default.Mic,
                    iconTint = GradientCyan,
                    iconBg = GradientCyan.copy(alpha = 0.12f),
                    title = "Voice coaching",
                    description = "Spoken pace, zone, and distance cues, in your ear."
                )
            }
            Spacer(Modifier.height(22.dp))

            // CTA button
            CardeaButton(
                text = "Set up bootcamp",
                onClick = onSetupBootcamp,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                cornerRadius = 16.dp,
                innerPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
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
            .clip(HomeCardRadius)
            .border(
                width = 1.dp,
                color = CardeaTheme.colors.glassBorder,
                shape = HomeCardRadius
            )
            .background(
                color = CardeaTheme.colors.glassHighlight,
                shape = HomeCardRadius
            )
            .clickable(onClick = onClick)
            .padding(horizontal = HomeCardPadding, vertical = 12.dp),
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

// ── Hero state primitives (Graduate + Resume) ──────────────────

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Black,
                fontSize = 30.sp,
                lineHeight = 30.sp,
                letterSpacing = (-0.8).sp
            ),
            color = CardeaTheme.colors.textPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = label.uppercase(),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
            color = CardeaTheme.colors.textTertiary
        )
    }
}

@Composable
private fun TrophyChip(weeksCompleted: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(CardeaAmber.copy(alpha = 0.10f))
            .border(1.dp, CardeaAmber.copy(alpha = 0.28f), RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.EmojiEvents,
            contentDescription = null,
            tint = CardeaAmber,
            modifier = Modifier.size(10.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "$weeksCompleted WEEKS",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.2.sp,
            color = CardeaAmber
        )
    }
}

@Composable
private fun PauseGlyph(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, CardeaTheme.colors.glassBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 12.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(CardeaTheme.colors.textSecondary)
            )
            Box(
                Modifier
                    .size(width = 3.dp, height = 12.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(CardeaTheme.colors.textSecondary)
            )
        }
    }
}

@Composable
private fun LaurelDecoration(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .width(220.dp)
            .height(28.dp)
            .graphicsLayer { alpha = 0.55f },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(GradientPink)
        )
        Spacer(Modifier.width(28.dp))
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(CardeaAmber)
        )
        Spacer(Modifier.width(28.dp))
        Box(
            Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(GradientCyan)
        )
    }
}

@Composable
private fun ProgressThread(
    sessionsDone: Int,
    sessionsTotal: Int,
    pausedAtWeek: Int,
    totalWeeks: Int,
    modifier: Modifier = Modifier
) {
    val progress = if (sessionsTotal > 0) {
        (sessionsDone.toFloat() / sessionsTotal).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.025f))
            .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PulseLabel(text = "PROGRESS")
            Text(
                text = "$sessionsDone / $sessionsTotal sessions · week $pausedAtWeek of $totalWeeks",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = CardeaTheme.colors.textSecondary
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.06f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.85f))
                        )
                    )
            )
        }
    }
}

@Composable
private fun WhilePausedReassurance(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.015f))
            .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        PulseLabel(text = "WHILE YOU'RE PAUSED")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "No streak penalty. No nagging. Manual runs still log to History — they just don't count toward this program until you resume.",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.5.sp,
                lineHeight = 17.sp
            ),
            color = CardeaTheme.colors.textSecondary
        )
    }
}

@Composable
private fun EvergreenChoiceRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, CardeaTheme.colors.glassBorderStrong, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(GradientCyan.copy(alpha = 0.10f))
                .border(1.dp, GradientCyan.copy(alpha = 0.28f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.FavoriteBorder,
                contentDescription = null,
                tint = GradientCyan,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Cardio Health",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp
                    ),
                    color = CardeaTheme.colors.textPrimary
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(GradientCyan.copy(alpha = 0.12f))
                        .border(1.dp, GradientCyan.copy(alpha = 0.30f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "EVERGREEN",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        color = GradientCyan
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Maintain race fitness. Tier-aware — keeps you at your level.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                ),
                color = CardeaTheme.colors.textSecondary
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = CardeaTheme.colors.textTertiary,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun RaceChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.025f))
            .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp
            ),
            color = CardeaTheme.colors.textSecondary
        )
    }
}

// ── GraduateHero ────────────────────────────────────────────────

@Composable
private fun GraduateHero(
    enrollment: BootcampEnrollmentEntity,
    weeksCompleted: Int,
    sessionsCompleted: Int,
    totalKm: Double,
    onChooseEvergreen: () -> Unit,
    onChooseRace: (RaceGoal) -> Unit,
    onFreestyle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val heroGradient = CardeaTheme.colors.gradient
    val cardWash = Brush.verticalGradient(
        colorStops = arrayOf(
            0.00f to GradientPink.copy(alpha = 0.10f),
            0.55f to GradientBlue.copy(alpha = 0.04f),
            1.00f to CardeaTheme.colors.glassHighlight
        )
    )
    val goalLabel = enrollment.goalType
        .takeIf { it.isNotBlank() }
        ?.replace("_", " ")
        ?.uppercase()
        ?: "PROGRAM"

    Column(modifier = modifier.padding(horizontal = HomeGutter)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(HomeCardRadius)
                .border(1.dp, CardeaTheme.colors.glassBorderStrong, HomeCardRadius)
                .background(cardWash)
                .padding(HomeCardPadding)
        ) {
            LaurelDecoration(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-4).dp)
            )
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PulseLabel(text = "GRADUATED · $goalLabel")
                    TrophyChip(weeksCompleted = weeksCompleted)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Race-tier fitness,\nlocked in.",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 38.sp,
                        lineHeight = 40.sp,
                        letterSpacing = (-1.2).sp
                    ),
                    color = CardeaTheme.colors.textPrimary,
                    modifier = Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(brush = heroGradient, blendMode = BlendMode.SrcIn)
                        }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "You finished your training cycle. Every pace zone, every long run, banked. Here's where to take it next.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp
                    ),
                    color = CardeaTheme.colors.textSecondary
                )
                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatTile(
                        value = weeksCompleted.toString(),
                        label = "WEEKS",
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        value = sessionsCompleted.toString(),
                        label = "SESSIONS",
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        value = totalKm.toInt().toString(),
                        label = "KM",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(22.dp))
                PulseLabel(text = "CHOOSE YOUR NEXT PROGRAM")
                Spacer(Modifier.height(10.dp))
                EvergreenChoiceRow(onClick = onChooseEvergreen)
                Spacer(Modifier.height(18.dp))
                PulseLabel(text = "NEW RACE")
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RaceGoal.entries.forEach { goal ->
                        RaceChip(
                            label = goal.shortLabel,
                            onClick = { onChooseRace(goal) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(onClick = onFreestyle) {
                        Text(
                            text = "Just freestyle today →",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                            color = CardeaTheme.colors.textTertiary
                        )
                    }
                }
            }
        }
    }
}

// ── ResumeCard ──────────────────────────────────────────────────

@Composable
private fun ResumeCard(
    enrollment: BootcampEnrollmentEntity,
    sessionsDone: Int,
    sessionsTotal: Int,
    pausedAtWeek: Int,
    totalWeeks: Int,
    onResume: () -> Unit,
    onSwitchProgram: () -> Unit,
    onManualRun: () -> Unit,
    modifier: Modifier = Modifier
) {
    val goalLabel = enrollment.goalType
        .takeIf { it.isNotBlank() }
        ?.replace("_", " ")
        ?.lowercase()
        ?: "program"

    val annotated = buildAnnotatedString {
        val bold = SpanStyle(
            color = CardeaTheme.colors.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
        append("You had ")
        withStyle(bold) { append("$sessionsDone of $sessionsTotal") }
        append(" sessions complete in the ")
        withStyle(bold) { append(goalLabel) }
        append(" program. Your place is held — pick up exactly where you left off.")
    }

    Column(modifier = modifier.padding(horizontal = HomeGutter)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(HomeCardRadius)
                .border(1.dp, CardeaTheme.colors.glassBorder, HomeCardRadius)
                .background(CardeaTheme.colors.glassHighlight)
                .padding(HomeCardPadding)
        ) {
            PauseGlyph(modifier = Modifier.align(Alignment.TopEnd))
            Column {
                PulseLabel(text = "PAUSED · WEEK $pausedAtWeek")
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Ready when\nyou are.",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 30.sp,
                        lineHeight = 32.sp,
                        letterSpacing = (-0.5).sp
                    ),
                    color = CardeaTheme.colors.textPrimary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        lineHeight = 21.sp
                    ),
                    color = CardeaTheme.colors.textSecondary
                )
                Spacer(Modifier.height(22.dp))
                ProgressThread(
                    sessionsDone = sessionsDone,
                    sessionsTotal = sessionsTotal,
                    pausedAtWeek = pausedAtWeek,
                    totalWeeks = totalWeeks
                )
                Spacer(Modifier.height(22.dp))
                CardeaButton(
                    text = "Resume training",
                    onClick = onResume,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    cornerRadius = 16.dp,
                    innerPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                    leadingIcon = Icons.Default.PlayArrow
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onSwitchProgram,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, CardeaTheme.colors.glassBorderStrong),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = CardeaTheme.colors.textPrimary
                    )
                ) {
                    Text(
                        text = "Start a different program",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(onClick = onManualRun) {
                        Text(
                            text = "Manual run →",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                            color = CardeaTheme.colors.textTertiary
                        )
                    }
                }
            }
        }
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

            // Hero section — branches on full bootcamp lifecycle state
            when (val bootcamp = state.bootcampState) {
                is HomeBootcampState.Active -> {
                    if (bootcamp.nextSession != null) {
                        PulseHero(
                            session = bootcamp.nextSession,
                            weekNumber = bootcamp.weekNumber,
                            isToday = bootcamp.isToday,
                            dayLabel = bootcamp.dayLabel,
                            maxHr = state.maxHr,
                            restHr = state.restHr
                        )
                    } else {
                        AllCaughtUpCard(onGoToTraining = onGoToBootcamp)
                    }
                }
                is HomeBootcampState.Paused -> {
                    ResumeCard(
                        enrollment = bootcamp.enrollment,
                        sessionsDone = bootcamp.sessionsDone,
                        sessionsTotal = bootcamp.sessionsTotal,
                        pausedAtWeek = bootcamp.pausedAtWeek,
                        totalWeeks = bootcamp.totalWeeks,
                        onResume = { viewModel.resumeBootcamp() },
                        onSwitchProgram = onGoToBootcamp,
                        onManualRun = onStartRun
                    )
                    Spacer(Modifier.height(18.dp))
                    Box(modifier = Modifier.padding(horizontal = HomeGutter)) {
                        WhilePausedReassurance()
                    }
                }
                is HomeBootcampState.Graduated -> {
                    GraduateHero(
                        enrollment = bootcamp.enrollment,
                        weeksCompleted = bootcamp.weeksCompleted,
                        sessionsCompleted = bootcamp.sessionsCompleted,
                        totalKm = bootcamp.totalKm,
                        onChooseEvergreen = onGoToBootcamp,
                        onChooseRace = { _ -> onGoToBootcamp() },
                        onFreestyle = onStartRun
                    )
                }
                HomeBootcampState.None -> {
                    NoBootcampCard(
                        onSetupBootcamp = onGoToBootcamp,
                        onStartRun = onStartRun
                    )
                }
            }

            // CTA — only when bootcamp is active (every other state hosts its own CTA)
            if (state.bootcampState is HomeBootcampState.Active) {
                CtaRow(
                    hasActiveBootcamp = true,
                    isNextSessionToday = state.isNextSessionToday,
                    onStartSession = onGoToBootcamp,
                    onSetupBootcamp = onGoToBootcamp
                )
            }

            // Bottom half fills remaining space. PartnerNudgeBanner lives
            // inside BottomHalf so it sits below the user's primary CTA and
            // progress content rather than fighting with them for hierarchy.
            BottomHalf(
                state = state,
                onNudgeTap = onGoToBootcamp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
