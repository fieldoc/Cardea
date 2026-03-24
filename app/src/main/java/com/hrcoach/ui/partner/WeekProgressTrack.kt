package com.hrcoach.ui.partner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.domain.sharing.DayState

private val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val completedColor = Color(0xFF4ADE80)
private val bonusColor = Color(0xFF00E5FF)
private val pendingColor = Color(0xFFFACC15)
private val trackBgColor = Color(0x10FFFFFF)
private const val NODE_RADIUS = 10f
private const val GLOW_RADIUS = 16f

@Composable
fun WeekProgressTrack(
    dayStates: List<DayState>,
    modifier: Modifier = Modifier
) {
    require(dayStates.size == 7) { "dayStates must have 7 elements" }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
        ) {
            val totalWidth = size.width
            val spacing = totalWidth / 6f
            val centerY = size.height / 2f

            // Background track line
            drawLine(
                color = trackBgColor,
                start = Offset(0f, centerY),
                end = Offset(totalWidth, centerY),
                strokeWidth = 2f
            )

            // Green progress line up to last completed day
            val lastCompletedIdx = dayStates.indexOfLast {
                it == DayState.COMPLETED || it == DayState.BONUS
            }
            if (lastCompletedIdx >= 0) {
                drawLine(
                    color = completedColor.copy(alpha = 0.6f),
                    start = Offset(0f, centerY),
                    end = Offset(lastCompletedIdx * spacing, centerY),
                    strokeWidth = 2f
                )
            }

            // Draw nodes
            for (i in 0..6) {
                val cx = i * spacing
                drawDayNode(dayStates[i], cx, centerY)
            }
        }

        Spacer(Modifier.height(4.dp))

        // Day labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            dayStates.forEachIndexed { i, state ->
                val color = when (state) {
                    DayState.COMPLETED -> completedColor.copy(alpha = 0.7f)
                    DayState.BONUS -> bonusColor.copy(alpha = 0.7f)
                    DayState.TODAY -> pendingColor.copy(alpha = 0.8f)
                    DayState.DEFERRED -> Color.White.copy(alpha = 0.2f)
                    else -> Color.White.copy(alpha = 0.35f)
                }
                Text(
                    text = dayLabels[i],
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = if (state == DayState.TODAY) FontWeight.Bold
                        else FontWeight.Normal
                    ),
                    color = color
                )
            }
        }
    }
}

private fun DrawScope.drawDayNode(state: DayState, cx: Float, cy: Float) {
    when (state) {
        DayState.COMPLETED -> {
            // Glow
            drawCircle(completedColor.copy(alpha = 0.2f), GLOW_RADIUS, Offset(cx, cy))
            // Filled circle
            drawCircle(completedColor, NODE_RADIUS, Offset(cx, cy))
            // Checkmark
            val path = Path().apply {
                moveTo(cx - 4f, cy)
                lineTo(cx - 1f, cy + 3f)
                lineTo(cx + 5f, cy - 3f)
            }
            drawPath(path, Color(0xFF0F1623), style = Stroke(width = 2f))
        }

        DayState.DEFERRED -> {
            drawCircle(
                Color.White.copy(alpha = 0.04f), NODE_RADIUS, Offset(cx, cy)
            )
            drawCircle(
                Color.White.copy(alpha = 0.15f), NODE_RADIUS, Offset(cx, cy),
                style = Stroke(
                    width = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                )
            )
        }

        DayState.BONUS -> {
            drawCircle(bonusColor.copy(alpha = 0.15f), GLOW_RADIUS, Offset(cx, cy))
            drawCircle(
                bonusColor.copy(alpha = 0.5f), NODE_RADIUS, Offset(cx, cy),
                style = Stroke(2f)
            )
            drawCircle(bonusColor, 4f, Offset(cx, cy))
        }

        DayState.TODAY -> {
            drawCircle(pendingColor.copy(alpha = 0.1f), GLOW_RADIUS, Offset(cx, cy))
            drawCircle(
                pendingColor.copy(alpha = 0.5f), NODE_RADIUS + 1f, Offset(cx, cy),
                style = Stroke(2f)
            )
            drawCircle(pendingColor, 3f, Offset(cx, cy))
        }

        DayState.REST, DayState.FUTURE -> {
            drawCircle(Color.White.copy(alpha = 0.04f), NODE_RADIUS, Offset(cx, cy))
            drawCircle(
                Color.White.copy(alpha = 0.06f), NODE_RADIUS, Offset(cx, cy),
                style = Stroke(1f)
            )
        }
    }
}
