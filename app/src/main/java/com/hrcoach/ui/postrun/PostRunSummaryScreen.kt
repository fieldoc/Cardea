package com.hrcoach.ui.postrun

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.domain.engine.TuningDirection
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
    onOpenFullMap: () -> Unit,
    onOpenMapsSetup: () -> Unit,
    viewModel: PostRunSummaryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCelebration by remember { mutableStateOf(false) }

    // Dynamic HRR gate: shows the card while within the visibility window, then auto-hides.
    // Computed here (not in VM) so it re-evaluates if the user navigates away and returns
    // while the ViewModel is still alive.
    //
    // Two timers run here:
    //   - HRR_MEASUREMENT_COMPLETE_MS (120_000): when the HR recovery measurement is done.
    //     Audio "Recovery measurement complete" TTS fires at this point. The card's own
    //     internal countdown also ends here and flips into its "you can stop walking" text.
    //   - hrrWindowMs (180_000): how long the card stays visually pinned after that.
    //     Keeping it visible for another minute lets a tired runner who glances at their
    //     phone late still see the measurement result before it disappears.
    val hrrWindowMs = 180_000L
    val hrrMeasurementCompleteMs = 120_000L
    var isHrrActive by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.workoutEndTimeMs) {
        val endMs = uiState.workoutEndTimeMs
        if (endMs <= 0L) return@LaunchedEffect
        val elapsed = System.currentTimeMillis() - endMs
        if (elapsed >= hrrWindowMs) return@LaunchedEffect  // past visibility — leave hidden

        // Fresh entry = within the first few seconds of the window. Returning to this screen
        // mid-window (e.g. after popping back from HistoryDetail) must NOT re-announce.
        val isFreshEntry = elapsed < 5_000L
        isHrrActive = true
        if (isFreshEntry) viewModel.onHrrWindowStarted()

        // Fire "measurement complete" TTS at t = 120s (or immediately if we opened the
        // screen after that point but still inside the visibility window).
        val timeToComplete = (hrrMeasurementCompleteMs - elapsed).coerceAtLeast(0L)
        if (timeToComplete > 0L) {
            delay(timeToComplete)
            viewModel.onHrrWindowEnded()
        }
        // Keep the card visible until the visibility window closes.
        val remainingVisibility = hrrWindowMs - maxOf(elapsed, hrrMeasurementCompleteMs)
        if (remainingVisibility > 0L) delay(remainingVisibility)
        isHrrActive = false
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
                            // ── Section 1: Status (HRR + HRmax delta + Training Signal) ──
                            // Shown only when at least one status card has content. Status goes first
                            // because HRR is time-sensitive, HRmax is the most notable change, and the
                            // Training Signal explains how this run reshapes next week's plan.
                            val showSignalCard = uiState.isBootcampRun &&
                                uiState.tuningDirection != null
                            val hasStatus = isHrrActive ||
                                uiState.hrMaxDelta != null ||
                                showSignalCard
                            if (hasStatus) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SectionHeader("STATUS")
                                    if (isHrrActive) {
                                        HrrCooldownCard(endTimeMs = uiState.workoutEndTimeMs)
                                    }
                                    uiState.hrMaxDelta?.let { (oldMax, newMax) ->
                                        HrMaxUpdatedCard(oldMax = oldMax, newMax = newMax)
                                    }
                                    if (showSignalCard) {
                                        TuningDirectionCard(
                                            direction = uiState.tuningDirection!!,
                                            runEffort = uiState.runEffort,
                                            typicalEffort = uiState.typicalEffort,
                                            environmentAffected = uiState.runEnvironmentAffected,
                                        )
                                    }
                                }
                            }

                            // ── Section 2: Your Run (hero + 2 stat cards + route map) ──
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
                                // ── Route map preview ──
                                // GoogleMap consumes touch gestures for pan/zoom, so a wrapping
                                // `Modifier.clickable` on the map surface never fires. The
                                // explicit "See full route" TextButton below is the only
                                // working tap affordance; it's only shown when there IS a
                                // route to see (≥2 points + Maps enabled).
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                ) {
                                    com.hrcoach.ui.components.RouteMap(
                                        trackPoints = uiState.trackPoints,
                                        workoutConfig = uiState.workoutConfig,
                                        isMapsEnabled = uiState.isMapsEnabled,
                                        onOpenMapsSetup = onOpenMapsSetup,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                if (uiState.trackPoints.size >= 2 && uiState.isMapsEnabled) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable(onClick = onOpenFullMap)
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "See full route",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = CardeaTheme.colors.textPrimary
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = CardeaTheme.colors.textPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            // ── Section 3: Compared to Similar Runs ──
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SectionHeader(
                                    title = "COMPARED",
                                    subtitle = if (uiState.similarRunCount > 0) {
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

    // Tiered-gradient rule (CLAUDE.md): only ONE CardeaGradient accent per screen.
    // RunCompleteHero already owns Tier 1, so the HRR card uses a ZoneGreen border as
    // a recovery-health cue instead. Radius matches GlassCard's inner 18dp shape.
    GlassCard(
        borderColor = ZoneGreen.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
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
    GlassCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
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
    // Editorial line ("Strong finish. Next week will adapt…") removed 2026-05-05 —
    // the Training Signal card in STATUS now owns "what this run does to next week".
    // This card stays for pure bookkeeping: title + week-progress label.
    GlassCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
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
            }
        }
    }
}

/**
 * Training Signal card — surfaces the adaptive engine's plan-tuning decision in plain
 * English with a single earned number. Bootcamp runs only.
 *
 * Layout matches the 2026-05-05 design handoff (`Training Signal Card v2.html`):
 *   ┌────────────────────────────────────────────────────────────┐
 *   │ Pushing harder next week         ︿  EFFORT 110 · typical 90 │
 *   │ Your efficiency is trending up and you're recovered…       │
 *   └────────────────────────────────────────────────────────────┘
 *
 * Env-flag muted variant: this run's Effort number gets line-through, comparator
 * dropped, italic footer added below a 1px divider explaining the exclusion. The
 * headline still reflects the engine's actual decision — env-affected only excludes
 * THIS run from the fitness signal; if other recent reliable runs drove the engine
 * to PUSH/EASE, that decision still applies and the user must hear about it.
 * Headline reverts to the "hot weather ignored" copy ONLY when the engine actually
 * landed on HOLD, since that is the one case where the plan really did stay put.
 * Direction is communicated by copy + a small directional glyph; no color encoding
 * (greens/yellows/reds reserved elsewhere).
 */
/**
 * Pure decision table for the Training Signal card's copy + headline weight. Extracted
 * from [TuningDirectionCard] so the env-affected interactions (which are easy to get
 * wrong) can be unit-tested without spinning up Compose.
 */
internal data class TuningSignalCopy(
    val headline: String,
    val driver: String,
    val mutedHeadline: Boolean,
)

internal fun tuningSignalCopy(
    direction: TuningDirection,
    environmentAffected: Boolean,
): TuningSignalCopy {
    val (headline, driver) = when {
        environmentAffected && direction == TuningDirection.HOLD ->
            "Holding the plan — hot weather ignored" to "Next week stays as prescribed."
        direction == TuningDirection.PUSH_HARDER ->
            "Pushing harder next week" to
                "Your efficiency is trending up and you're recovered enough to step it up."
        direction == TuningDirection.EASE_BACK ->
            "Easing back next week" to
                "You're carrying fatigue — next week eases off."
        else ->
            "Holding the plan" to
                "Solid session — keeping next week as prescribed."
    }
    // Muted headline styling only when the headline itself is the muted "hot weather
    // ignored" copy. When the engine pushed/eased on the strength of other runs, the
    // headline conveys real news and should read with full weight.
    return TuningSignalCopy(
        headline = headline,
        driver = driver,
        mutedHeadline = environmentAffected && direction == TuningDirection.HOLD,
    )
}

@Composable
private fun TuningDirectionCard(
    direction: TuningDirection,
    runEffort: Int?,
    typicalEffort: Int?,
    environmentAffected: Boolean,
) {
    val copy = tuningSignalCopy(direction, environmentAffected)
    val headline = copy.headline
    val driver = copy.driver
    val headlineColor =
        if (copy.mutedHeadline) CardeaTheme.colors.textSecondary
        else CardeaTheme.colors.textPrimary
    val headlineWeight =
        if (copy.mutedHeadline) FontWeight.SemiBold else FontWeight.Bold

    GlassCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = headlineWeight,
                    fontSize = 16.sp,
                    lineHeight = 20.sp
                ),
                color = headlineColor,
                modifier = Modifier.weight(1f)
            )
            if (runEffort != null) {
                EffortMeta(
                    runEffort = runEffort,
                    typicalEffort = typicalEffort,
                    direction = direction,
                    muted = environmentAffected,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = driver,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                lineHeight = 18.sp
            ),
            color = CardeaTheme.colors.textSecondary
        )
        if (environmentAffected) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.06f))
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "This run was unusually hot — not counted toward fitness signal.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontStyle = FontStyle.Italic
                ),
                color = CardeaTheme.colors.textTertiary
            )
        }
    }
}

@Composable
private fun EffortMeta(
    runEffort: Int,
    typicalEffort: Int?,
    direction: TuningDirection,
    muted: Boolean,
) {
    val labelColor = CardeaTheme.colors.textTertiary
    val numberColor =
        if (muted) CardeaTheme.colors.textTertiary
        else CardeaTheme.colors.textPrimary
    val glyphTint =
        if (muted) CardeaTheme.colors.textTertiary
        else CardeaTheme.colors.textSecondary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DirectionGlyph(direction = direction, tint = glyphTint)
        Text(
            text = "EFFORT",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = labelColor,
        )
        Text(
            text = runEffort.toString(),
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = numberColor,
            textDecoration = if (muted) TextDecoration.LineThrough else null,
        )
        if (!muted && typicalEffort != null) {
            Text(
                text = "· typical $typicalEffort",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = labelColor
            )
        }
    }
}

/**
 * 11dp polyline glyph for the Effort row: chevron-up for PUSH_HARDER, horizontal line
 * for HOLD, chevron-down for EASE_BACK. Drawn directly with Canvas so we don't have to
 * scale a 24dp Material icon down to a non-standard size — the design calls for a
 * tight 11dp glyph that sits inline with 11sp uppercase text.
 */
@Composable
private fun DirectionGlyph(direction: TuningDirection, tint: Color) {
    Canvas(modifier = Modifier.size(11.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        val points = when (direction) {
            TuningDirection.PUSH_HARDER -> listOf(
                Offset(w * 0.20f, h * 0.65f),
                Offset(w * 0.50f, h * 0.32f),
                Offset(w * 0.80f, h * 0.65f),
            )
            TuningDirection.EASE_BACK -> listOf(
                Offset(w * 0.20f, h * 0.35f),
                Offset(w * 0.50f, h * 0.68f),
                Offset(w * 0.80f, h * 0.35f),
            )
            TuningDirection.HOLD -> listOf(
                Offset(w * 0.18f, h * 0.50f),
                Offset(w * 0.82f, h * 0.50f),
            )
        }
        for (i in 0 until points.size - 1) {
            drawLine(
                color = tint,
                start = points[i],
                end = points[i + 1],
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
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
