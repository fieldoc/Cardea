package com.hrcoach.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun DebugSimulationScreen(
    onNavigateToSetup: () -> Unit,
    onBack: () -> Unit,
    viewModel: DebugSimulationViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F17))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Text("Simulation", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(24.dp))

        // Status
        if (state.isSimActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x33FF5A5F))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SIMULATION ACTIVE", color = Color(0xFFFF5A5F), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text("Go to Workout tab to start", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
            Spacer(Modifier.height(16.dp))
        }

        // Scenario Picker
        Text("SCENARIO", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        state.scenarios.forEachIndexed { index, scenario ->
            val isSelected = index == state.selectedScenarioIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Color(0x33FF5A5F) else Color(0x14FFFFFF))
                    .border(
                        1.dp,
                        if (isSelected) Color(0xFFFF5A5F) else Color(0x0FFFFFFF),
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { viewModel.selectScenario(index) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(scenario.name, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(
                        "${scenario.durationSeconds / 60} min | ${scenario.events.size} events",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
                if (isSelected) {
                    Text("Selected", color = Color(0xFFFF5A5F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Speed
        Text("SPEED", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(1f, 5f, 10f, 50f).forEach { speed ->
                val isSelected = state.speedMultiplier == speed
                Text(
                    text = "${speed.toInt()}x",
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color(0x33FF5A5F) else Color(0x14FFFFFF))
                        .clickable { viewModel.setSpeed(speed) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // Toggle
        Button(
            onClick = {
                viewModel.toggleSimulation()
                if (!state.isSimActive) {
                    onNavigateToSetup()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.isSimActive) Color(0xFF333333) else Color(0xFFFF5A5F)
            )
        ) {
            Text(
                if (state.isSimActive) "Disable Simulation" else "Enable Simulation",
                fontWeight = FontWeight.Bold
            )
        }
    }
}
