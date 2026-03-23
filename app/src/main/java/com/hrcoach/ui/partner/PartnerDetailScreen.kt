package com.hrcoach.ui.partner

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.CardeaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerDetailScreen(
    onBack: () -> Unit,
    viewModel: PartnerDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = CardeaTheme.colors.bgPrimary,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = state.partnerName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = CardeaTheme.colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = CardeaTheme.colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = CardeaTheme.colors.bgPrimary
                )
            )
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = CardeaTheme.colors.textSecondary)
                }
            }

            state.notFound -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recent runs from your partner yet.",
                        color = CardeaTheme.colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Route map placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardeaTheme.colors.glassHighlight),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.routePolyline.isNotEmpty()) {
                            // TODO: Render Google Maps with polyline (same pattern as HistoryDetailScreen)
                            Text(
                                text = "Route Map",
                                color = CardeaTheme.colors.textSecondary
                            )
                        } else {
                            Text(
                                text = "No route recorded",
                                color = CardeaTheme.colors.textTertiary
                            )
                        }
                    }

                    // Stats
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        StatRow(
                            label = "Distance",
                            value = "%.1f km".format(state.distanceMeters / 1000.0)
                        )
                        StatRow(
                            label = "Streak",
                            value = "${state.streakCount} days"
                        )
                        if (state.programPhase != null) {
                            StatRow(label = "Phase", value = state.programPhase!!)
                        }
                        if (state.sessionLabel != null) {
                            StatRow(label = "Session", value = state.sessionLabel!!)
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = CardeaTheme.colors.textSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = CardeaTheme.colors.textPrimary
        )
    }
}
