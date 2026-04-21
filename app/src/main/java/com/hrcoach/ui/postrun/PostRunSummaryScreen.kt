package com.hrcoach.ui.postrun

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.data.db.AchievementEntity
import androidx.hilt.navigation.compose.hiltViewModel
import com.hrcoach.R
import com.hrcoach.ui.components.AchievementCard
import com.hrcoach.ui.components.CardeaButton
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.components.SectionHeader
import com.hrcoach.ui.theme.CardeaBgPrimary
import com.hrcoach.ui.theme.CardeaBgSecondary
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.GlassBorder
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.ZoneGreen
import com.hrcoach.ui.theme.ZoneRed
import kotlinx.coroutines.delay

private enum class PostRunContentState {
    LOADING,
    ERROR,
    CONTENT
}

@Composable
private fun RunCompleteHero(
    visible: Boolean,
    distanceText: String,
    modifier: Modifier = Modifier
) {
    val gradient = CardeaTheme.colors.gradient  // CardeaGradient 4-stop
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(500)) +
            scaleIn(initialScale = 0.92f, animationSpec = tween(500)),
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = "RUN COMPLETE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontSize = 11.sp
                ),
                color = CardeaTheme.colors.textTertiary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = distanceText,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 44.sp,
                    letterSpacing = (-0.5).sp
                ),
                color = CardeaTheme.colors.textPrimary,
                modifier = Modifier
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(brush = gradient, blendMode = BlendMode.SrcIn)
                    }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostRunSummaryScreen(
    workoutId: Long,
    onDone: () -> Unit,
    onBack: () -> Unit,
    onNavigateToSoundLibrary: () -> Unit = {},
    viewModel: PostRunSummaryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCelebration by remember { mutableStateOf(false) }

    // Dynamic HRR gate: shows the card if within 3 minutes of workout end, then auto-hides.
    // Computed here (not in VM) so it re-evaluates if the user navigates away and returns
    // while the ViewModel is still alive.
    val hrrWindowMs = 180_000L
    var isHrrActive by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.workoutEndTimeMs) {
        val endMs = uiState.workoutEndTimeMs
        if (endMs <= 0L) return@LaunchedEffect
        val remaining = hrrWindowMs - (System.currentTimeMillis() - endMs)
        if (remaining > 0L) {
            isHrrActive = true
            delay(remaining)
            isHrrActive = false
        }
    }

    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading) {
            delay(120L)
            showCelebration = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(CardeaBgSecondary, CardeaBgPrimary),
                    center = Offset(x = 0f, y = 0f),
                    radius = 1800f
                )
            )
    ) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(uiState.titleText) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
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
                            .padding(padding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                PostRunContentState.ERROR -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)  // inter-section
                        ) {
                            // ── Section 1: Status (HRR + HRmax delta) ──
                            // Shown only when at least one status card has content. Status goes first
                            // because HRR is time-sensitive and HRmax is the most notable change.
                            val hasStatus = isHrrActive || uiState.hrMaxDelta != null
                            if (hasStatus) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SectionHeader("STATUS")
                                    if (isHrrActive) {
                                        HrrCooldownCard(endTimeMs = uiState.workoutEndTimeMs)
                                    }
                                    uiState.hrMaxDelta?.let { (oldMax, newMax) ->
                                        HrMaxUpdatedCard(oldMax = oldMax, newMax = newMax)
                                    }
                                }
                            }

                            // ── Section 2: Your Run (hero + 2 stat cards) ──
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                RunCompleteHero(
                                    visible = showCelebration,
                                    distanceText = uiState.distanceText
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    SummaryStatCard(
                                        title = stringResource(R.string.label_duration),
                                        value = uiState.durationText,
                                        icon = Icons.Default.Timer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    SummaryStatCard(
                                        title = stringResource(R.string.label_avg_hr),
                                        value = uiState.avgHrText,
                                        icon = Icons.Default.Favorite,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // ── Section 3: Compared to Similar Runs ──
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SectionHeader(
                                    title = "COMPARED",
                                    subtitle = if (uiState.comparisons.isNotEmpty()) {
                                        "vs. ${uiState.similarRunCount} similar sessions"
                                    } else null
                                )
                                if (uiState.comparisons.isEmpty()) {
                                    GlassCard {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Insights,
                                                contentDescription = null,
                                                tint = CardeaTheme.colors.textSecondary
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
                                                    color = CardeaTheme.colors.textSecondary
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    uiState.comparisons.forEach { item -> ComparisonRow(item) }
                                }
                            }

                            // ── Section 4: Extras (bootcamp, achievements, sounds recap) ──
                            val hasExtras = uiState.newAchievements.isNotEmpty() ||
                                !uiState.bootcampProgressLabel.isNullOrBlank() ||
                                uiState.showSoundsRecap
                            if (hasExtras) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SectionHeader("MORE")
                                    uiState.bootcampProgressLabel
                                        ?.takeIf { it.isNotBlank() }
                                        ?.let { progressLabel ->
                                            BootcampContextCard(
                                                progressLabel = progressLabel,
                                                weekComplete = uiState.bootcampWeekComplete
                                            )
                                        }
                                    if (uiState.newAchievements.isNotEmpty()) {
                                        NewAchievementsSection(achievements = uiState.newAchievements)
                                    }
                                    if (uiState.showSoundsRecap) {
                                        SoundsHeardSection(
                                            counts = uiState.cueCounts,
                                            onSeeLibrary = onNavigateToSoundLibrary
                                        )
                                    }
                                }
                            }
                        }

                        // Pinned action footer — sits below the scroll area, always visible.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CardeaBgPrimary.copy(alpha = 0.92f))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            CardeaButton(
                                text = stringResource(R.string.button_done),
                                onClick = onDone,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                cornerRadius = 14.dp
                            )
                        }
                    }
                }
            }
        }
    }
    } // end Box
}

@Composable
private fun NewAchievementsSection(achievements: List<AchievementEntity>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = if (achievements.size == 1) "Achievement Unlocked" else "${achievements.size} Achievements Unlocked",
            style = MaterialTheme.typography.titleSmall,
            color = GradientPink
        )
        achievements.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { achievement ->
                    AchievementCard(
                        achievement = achievement,
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = GradientPink,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = CardeaTheme.colors.textSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = CardeaTheme.colors.textPrimary
        )
    }
}

@Composable
private fun HrrCooldownCard(endTimeMs: Long) {
    val totalCooldownSec = 120
    var remainingSeconds by remember { mutableIntStateOf(totalCooldownSec) }

    LaunchedEffect(endTimeMs) {
        while (remainingSeconds > 0) {
            val elapsed = ((System.currentTimeMillis() - endTimeMs) / 1000).toInt()
            remainingSeconds = (totalCooldownSec - elapsed).coerceAtLeast(0)
            delay(1_000L)
        }
    }

    val isComplete = remainingSeconds <= 0

    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsWalk,
                contentDescription = null,
                tint = GradientPink,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isComplete) "Recovery measurement complete"
                           else "Calculating your 30-day recovery index\u2026",
                    style = MaterialTheme.typography.titleMedium,
                    color = CardeaTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (isComplete) {
                    Text(
                        text = "You can stop walking now.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CardeaTheme.colors.textSecondary
                    )
                } else {
                    Text(
                        text = "Walk slowly \u2014 ${remainingSeconds}s remaining",
                        style = MaterialTheme.typography.bodySmall,
                        color = CardeaTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { 1f - (remainingSeconds.toFloat() / totalCooldownSec) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = GradientPink,
                        trackColor = GlassBorder
                    )
                }
            }
        }
    }
}

@Composable
private fun HrMaxUpdatedCard(
    oldMax: Int,
    newMax: Int
) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.FavoriteBorder,
                contentDescription = null,
                tint = CardeaTheme.colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Max HR updated",
                    style = MaterialTheme.typography.labelMedium,
                    color = CardeaTheme.colors.textSecondary
                )
                Text(
                    text = "$oldMax → $newMax bpm",
                    style = MaterialTheme.typography.titleMedium,
                    color = CardeaTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Cardea measured a new personal ceiling and adjusted your training zones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTheme.colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun BootcampContextCard(
    progressLabel: String,
    weekComplete: Boolean
) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Insights,
                contentDescription = null,
                tint = GradientPink,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (weekComplete) "Bootcamp Week Complete" else "Bootcamp Progress",
                    style = MaterialTheme.typography.titleMedium,
                    color = CardeaTheme.colors.textPrimary
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
                        "This run has been applied to your current bootcamp week."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTheme.colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun ComparisonRow(item: PostRunComparison) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = CardeaTheme.colors.textSecondary
                )
                Text(
                    text = item.value,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = CardeaTheme.colors.textPrimary
                )
                // Normalised context line: prefer delta when present, else insight,
                // else nothing. No more mixed layouts across rows.
                val contextLine = item.delta ?: item.insight
                contextLine?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            item.delta != null -> when (item.positive) {
                                true -> ZoneGreen
                                false -> ZoneRed
                                null -> CardeaTheme.colors.textSecondary
                            }
                            else -> CardeaTheme.colors.textSecondary
                        }
                    )
                }
            }
        }
    }
}
