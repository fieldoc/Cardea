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
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hrcoach.R
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.CardeaBgPrimary
import com.hrcoach.ui.theme.CardeaBgSecondary
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.GlassBorder
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.ZoneGreen
import com.hrcoach.ui.theme.ZoneRed
import com.hrcoach.ui.components.CardeaButton
import kotlinx.coroutines.delay

private enum class PostRunContentState {
    LOADING,
    ERROR,
    CONTENT
}

@OptIn(ExperimentalMaterial3Api::class)
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
    var showCelebration by remember { mutableStateOf(false) }

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
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AnimatedVisibility(
                            visible = showCelebration,
                            enter = scaleIn(animationSpec = tween(400)) + fadeIn(animationSpec = tween(400))
                        ) {
                            Text(
                                text = "Run Complete",
                                style = MaterialTheme.typography.headlineMedium,
                                color = GradientPink
                            )
                        }

                        if (uiState.isHrrActive) {
                            HrrCooldownCard(endTimeMs = uiState.workoutEndTimeMs)
                        }

                        uiState.bootcampProgressLabel
                            ?.takeIf { it.isNotBlank() }
                            ?.let { progressLabel ->
                                BootcampContextCard(
                                    progressLabel = progressLabel,
                                    weekComplete = uiState.bootcampWeekComplete
                                )
                            }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SummaryStatCard(
                                title = stringResource(R.string.label_distance),
                                value = uiState.distanceText,
                                icon = Icons.AutoMirrored.Filled.DirectionsRun,
                                modifier = Modifier.weight(1f)
                            )
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

                        Text(
                            text = "Compared to Similar Runs",
                            style = MaterialTheme.typography.titleLarge,
                            color = CardeaTheme.colors.textPrimary
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
                            Text(
                                text = "Based on ${uiState.similarRunCount} similar sessions",
                                style = MaterialTheme.typography.bodySmall,
                                color = CardeaTheme.colors.textSecondary
                            )
                            uiState.comparisons.forEach { item ->
                                GlassCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = CardeaTheme.colors.textPrimary
                                            )
                                            Text(
                                                text = item.value,
                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                                color = CardeaTheme.colors.textPrimary
                                            )
                                            item.insight?.let { insight ->
                                                Text(
                                                    text = insight,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = CardeaTheme.colors.textSecondary
                                                )
                                            }
                                        }
                                        item.delta?.let { delta ->
                                            Text(
                                                text = delta,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = when (item.positive) {
                                                    true -> ZoneGreen
                                                    false -> ZoneRed
                                                    null -> CardeaTheme.colors.textSecondary
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onViewHistory,
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, CardeaTheme.colors.glassBorder)
                            ) {
                                Text("View Route", color = CardeaTheme.colors.textPrimary)
                            }
                            CardeaButton(
                                text = stringResource(R.string.button_done),
                                onClick = onDone,
                                modifier = Modifier.weight(1f).height(40.dp),
                                cornerRadius = 50.dp
                            )
                        }
                        TextButton(
                            onClick = onViewProgress,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(stringResource(R.string.button_view_progress))
                        }
                    }
                }
            }
        }
    }
    } // end Box
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
