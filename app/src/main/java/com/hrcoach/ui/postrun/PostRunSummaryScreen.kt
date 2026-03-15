package com.hrcoach.ui.postrun

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hrcoach.R
import com.hrcoach.service.WorkoutState
import com.hrcoach.ui.components.AchievementCard
import com.hrcoach.ui.components.CardeaButton
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.AchievementGold
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientCyan
import com.hrcoach.ui.theme.ZoneAmber
import com.hrcoach.ui.theme.ZoneGreen
import com.hrcoach.ui.theme.ZoneRed
import kotlinx.coroutines.delay

private enum class PostRunContentState { LOADING, ERROR, CONTENT }

/** Constant hero gradient — hoisted to avoid re-allocation on every recomposition. */
private val HeroGradient = Brush.verticalGradient(
    colorStops = arrayOf(
        0f to Color(0x28FF4D5A),
        0.55f to Color(0x124D61FF),
        1f to Color.Transparent
    )
)

@Composable
fun PostRunSummaryScreen(
    workoutId: Long,
    onViewProgress: () -> Unit,
    onViewHistory: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: PostRunSummaryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        WorkoutState.clearCompletedWorkoutId()
    }

    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading) {
            delay(120L)
            showContent = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CardeaTheme.colors.bgPrimary)
    ) {
        val contentState = when {
            uiState.isLoading -> PostRunContentState.LOADING
            uiState.errorMessage != null -> PostRunContentState.ERROR
            else -> PostRunContentState.CONTENT
        }

        Crossfade(targetState = contentState, label = "post-run-content") { state ->
            when (state) {
                PostRunContentState.LOADING -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = ZoneGreen)
                    }
                }

                PostRunContentState.ERROR -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "Unable to load summary.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = CardeaTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onDone) {
                            Text(stringResource(R.string.button_done))
                        }
                    }
                }

                PostRunContentState.CONTENT -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // ── Hero — full-bleed gradient, no side padding ──────
                        AnimatedVisibility(
                            visible = showContent,
                            enter = slideInVertically(
                                initialOffsetY = { -it / 4 },
                                animationSpec = tween(400)
                            ) + fadeIn(tween(400))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(HeroGradient)
                                    .statusBarsPadding()
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 10.dp, bottom = 20.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    // Back nav row
                                    Row(
                                        modifier = Modifier.clickable(onClick = onBack),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back",
                                            tint = CardeaTheme.colors.textTertiary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "History",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = CardeaTheme.colors.textTertiary
                                        )
                                    }
                                    // Eyebrow
                                    Text(
                                        text = "RUN COMPLETE",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            letterSpacing = 3.sp,
                                            fontWeight = FontWeight.Black
                                        ),
                                        color = CardeaTheme.colors.textTertiary
                                    )
                                    // Motivational title
                                    Text(
                                        text = uiState.titleText.ifBlank { "Well done." },
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.ExtraBold
                                        ),
                                        color = CardeaTheme.colors.textPrimary
                                    )
                                    // Giant gradient distance
                                    Text(
                                        text = uiState.distanceText,
                                        style = MaterialTheme.typography.displayLarge.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            brush = CardeaGradient,
                                            lineHeight = 76.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = CardeaTheme.colors.glassBorder, thickness = 1.dp)

                        // ── Scrollable content below hero ───────────────────
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp)
                                .padding(top = 16.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Stat strip: Duration | Avg HR
                            AnimatedVisibility(
                                visible = showContent,
                                enter = fadeIn(tween(400, delayMillis = 80))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    SmallStatCard(
                                        label = stringResource(R.string.label_duration),
                                        value = uiState.durationText,
                                        modifier = Modifier.weight(1f)
                                    )
                                    SmallStatCard(
                                        label = stringResource(R.string.label_avg_hr),
                                        value = uiState.avgHrText,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // HRR cooldown (priority card — shown first if active)
                            if (uiState.isHrrActive) {
                                HrrCooldownCard(secondsRemaining = uiState.hrrSecondsRemaining)
                            }

                            // Bootcamp context
                            uiState.bootcampProgressLabel
                                ?.takeIf { it.isNotBlank() }
                                ?.let { progressLabel ->
                                    BootcampContextCard(
                                        progressLabel = progressLabel,
                                        weekComplete = uiState.bootcampWeekComplete
                                    )
                                }

                            // Achievement cards
                            if (uiState.achievements.isNotEmpty()) {
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "ACHIEVEMENT UNLOCKED",
                                    color = AchievementGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 3.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(12.dp))
                                uiState.achievements.forEach { achievement ->
                                    AchievementCard(
                                        achievement = achievement,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                            }

                            // Comparisons / assessment
                            AnimatedVisibility(
                                visible = showContent,
                                enter = fadeIn(tween(500, delayMillis = 160))
                            ) {
                                if (uiState.comparisons.isEmpty()) {
                                    GlassCard {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Insights,
                                                contentDescription = null,
                                                tint = CardeaTheme.colors.textTertiary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = "Not enough data yet.",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = CardeaTheme.colors.textPrimary
                                                )
                                                Text(
                                                    text = "Complete a few similar sessions to unlock this view.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = CardeaTheme.colors.textTertiary
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "vs ${uiState.similarRunCount} similar sessions",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                letterSpacing = 1.sp
                                            ),
                                            color = CardeaTheme.colors.textTertiary
                                        )
                                        AssessmentCard(comparisons = uiState.comparisons)
                                    }
                                }
                            }

                            // Action buttons
                            AnimatedVisibility(
                                visible = showContent,
                                enter = fadeIn(tween(400, delayMillis = 240))
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Progress — glass outlined
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .clip(RoundedCornerShape(50.dp))
                                                .background(CardeaTheme.colors.glassHighlight)
                                                .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(50.dp))
                                                .clickable(onClick = onViewProgress),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = stringResource(R.string.button_view_progress),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = CardeaTheme.colors.textSecondary
                                            )
                                        }
                                        // Done — gradient
                                        CardeaButton(
                                            text = stringResource(R.string.button_done),
                                            onClick = onDone,
                                            modifier = Modifier
                                                .weight(2f)
                                                .height(48.dp),
                                            cornerRadius = 50.dp
                                        )
                                    }
                                    TextButton(
                                        onClick = onViewHistory,
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    ) {
                                        Text(
                                            text = "View Route",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = CardeaTheme.colors.textTertiary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardeaTheme.colors.glassHighlight)
            .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = CardeaTheme.colors.textTertiary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = CardeaTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AssessmentCard(comparisons: List<PostRunComparison>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardeaTheme.colors.glassHighlight)
            .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Session Overview",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
            color = CardeaTheme.colors.textTertiary
        )
        comparisons.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = CardeaTheme.colors.textPrimary
                    )
                    item.insight?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = CardeaTheme.colors.textTertiary
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = item.value,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = CardeaTheme.colors.textPrimary
                    )
                    item.delta?.let { delta ->
                        Text(
                            text = delta,
                            style = MaterialTheme.typography.labelSmall,
                            color = when (item.positive) {
                                true -> ZoneGreen
                                false -> ZoneAmber
                                null -> CardeaTheme.colors.textTertiary
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HrrCooldownCard(secondsRemaining: Int) {
    val progress = secondsRemaining / 120f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "hrrProgress"
    )

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(GradientCyan.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                        contentDescription = null,
                        tint = GradientCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Recovery Walk",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = CardeaTheme.colors.textPrimary
                    )
                    Text(
                        text = "Stay moving at a slow pace",
                        style = MaterialTheme.typography.bodySmall,
                        color = CardeaTheme.colors.textSecondary
                    )
                }
                Text(
                    text = "${secondsRemaining}s",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Black,
                        fontFeatureSettings = "tnum"
                    ),
                    color = GradientCyan
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CardeaTheme.colors.glassBorder)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(listOf(GradientCyan, GradientBlue))
                        )
                )
            }
            Text(
                text = "Measuring how quickly your heart rate drops to calibrate your fitness level.",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textSecondary,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun BootcampContextCard(
    progressLabel: String,
    weekComplete: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardeaTheme.colors.glassHighlight)
            .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(44.dp)
                    .background(ZoneGreen.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (weekComplete) "Bootcamp Week Complete" else "Bootcamp Progress",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    color = if (weekComplete) ZoneGreen else CardeaTheme.colors.textTertiary
                )
                Text(
                    text = progressLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = CardeaTheme.colors.textPrimary
                )
                Text(
                    text = if (weekComplete) {
                        "Strong finish. Next week will adapt from this session."
                    } else {
                        "This run has been applied to your bootcamp week."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTheme.colors.textTertiary
                )
            }
        }
    }
}
