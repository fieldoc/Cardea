package com.hrcoach.ui.training

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hrcoach.ui.bootcamp.BootcampScreen
import com.hrcoach.ui.setup.SetupScreen
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.GradientPink

enum class TrainingSegment { Bootcamp, FreeRun }

@Composable
fun TrainingScreen(
    initialSegment: TrainingSegment,
    isWideLayout: Boolean,
    onStartWorkout: (configJson: String, deviceAddress: String?) -> Unit,
    onBack: () -> Unit,
    onGoToBootcampSettings: () -> Unit,
    onGoToSoundLibrary: () -> Unit,
) {
    // Seeded from the route argument; hoisted unconditionally so segment swaps
    // don't tear down TabRow state. Key on initialSegment so nav-level deep links
    // override the saved state.
    var selected by rememberSaveable(initialSegment) { mutableStateOf(initialSegment) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CardeaTheme.colors.bgPrimary)
    ) {
        TabRow(
            selectedTabIndex = selected.ordinal,
            containerColor = Color.Transparent,
            contentColor = CardeaTheme.colors.textPrimary,
            divider = {},
            indicator = { tabPositions ->
                with(TabRowDefaults) {
                    Box(
                        Modifier
                            .tabIndicatorOffset(tabPositions[selected.ordinal])
                            .height(2.dp)
                            .background(GradientPink)
                    )
                }
            }
        ) {
            TrainingSegment.values().forEach { seg ->
                val isActive = selected == seg
                val label = when (seg) {
                    TrainingSegment.Bootcamp -> "Bootcamp"
                    TrainingSegment.FreeRun -> "Free Run"
                }
                Tab(
                    selected = isActive,
                    onClick = { selected = seg },
                    text = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium
                            ),
                            color = if (isActive)
                                CardeaTheme.colors.textPrimary
                            else
                                CardeaTheme.colors.textSecondary
                        )
                    }
                )
            }
        }

        when (selected) {
            TrainingSegment.Bootcamp -> BootcampScreen(
                onStartWorkout = onStartWorkout,
                onBack = onBack,
                onGoToSettings = onGoToBootcampSettings,
                onGoToManualSetup = { selected = TrainingSegment.FreeRun },
                onGoToSoundLibrary = onGoToSoundLibrary,
            )
            TrainingSegment.FreeRun -> SetupScreen(
                isWideLayout = isWideLayout,
                onStartWorkout = onStartWorkout,
                onGoToBootcamp = { selected = TrainingSegment.Bootcamp },
                onGoToSoundLibrary = onGoToSoundLibrary,
            )
        }
    }
}
