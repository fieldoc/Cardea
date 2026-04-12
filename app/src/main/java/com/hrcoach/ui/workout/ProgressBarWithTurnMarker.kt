package com.hrcoach.ui.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaTextTertiary
import com.hrcoach.ui.theme.GlassBorder

@Composable
fun ProgressBarWithTurnMarker(
    progress: Float,
    showTurnMarker: Boolean,
    turnLabel: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),  // Prevent overlap with HR ring below
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Progress bar canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
        ) {
            val cornerRadius = CornerRadius(3.dp.toPx())

            // Track
            drawRoundRect(
                color = GlassBorder,
                cornerRadius = cornerRadius
            )

            // Fill
            if (progress > 0f) {
                drawRoundRect(
                    brush = CardeaGradient,
                    size = Size(size.width * progress.coerceIn(0f, 1f), size.height),
                    cornerRadius = cornerRadius
                )
            }

            // Halfway turn marker
            if (showTurnMarker) {
                val markerX = size.width * 0.5f
                val markerHeight = 20.dp.toPx()
                val markerWidth = 2.dp.toPx()
                val markerTop = (size.height - markerHeight) / 2f
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.20f),
                    topLeft = Offset(markerX - markerWidth / 2f, markerTop),
                    size = Size(markerWidth, markerHeight),
                    cornerRadius = CornerRadius(1.dp.toPx())
                )
            }
        }

        // Turn label below the bar
        if (showTurnMarker && turnLabel != null) {
            Text(
                text = turnLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp
                ),
                color = CardeaTextTertiary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
