package com.hrcoach.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.service.simulation.SimulationController

internal val SPEED_OPTIONS = listOf(1f, 5f, 10f, 50f)

@Composable
fun SimulationOverlay(modifier: Modifier = Modifier) {
    val simState by SimulationController.state.collectAsState()
    if (!simState.isActive) return

    var speedIndex by remember {
        mutableIntStateOf(SPEED_OPTIONS.indexOf(simState.speedMultiplier).coerceAtLeast(0))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x14FFFFFF))
            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "SIM: ${simState.scenario?.name ?: "Unknown"}",
            color = Color(0xFFFF5A5F),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "${simState.speedMultiplier.toInt()}x",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x33FF5A5F))
                .clickable {
                    speedIndex = (speedIndex + 1) % SPEED_OPTIONS.size
                    SimulationController.setSpeed(SPEED_OPTIONS[speedIndex])
                }
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}
