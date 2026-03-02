package com.hrcoach.ui.charts

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

data class PieSlice(val label: String, val fraction: Float, val color: Color)

@Composable
fun PieChart(slices: List<PieSlice>, modifier: Modifier = Modifier) {
    val validSlices = remember(slices) {
        slices.filter { it.fraction >= 0.001f }
    }

    val dominantSlice = remember(validSlices) {
        validSlices.maxByOrNull { it.fraction }
    }

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (validSlices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No data",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurfaceColor
                )
            }
        } else {
            val onSurfaceArgb = onSurfaceColor.toArgb()

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val outerRadius = min(canvasWidth, canvasHeight) * 0.42f
                val innerRadius = outerRadius * 0.40f
                val strokeWidth = outerRadius - innerRadius

                val centerX = canvasWidth / 2f
                val centerY = canvasHeight / 2f

                val topLeft = Offset(centerX - outerRadius, centerY - outerRadius)
                val arcSize = Size(outerRadius * 2f, outerRadius * 2f)

                var startAngle = -90f

                for (slice in validSlices) {
                    val sweepAngle = slice.fraction * 360f

                    drawArc(
                        color = slice.color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth)
                    )

                    startAngle += sweepAngle + 0.5f
                }

                // Draw center text for dominant zone
                dominantSlice?.let { dominant ->
                    val percentage = (dominant.fraction * 100).toInt()
                    val text = "$percentage%"

                    val paint = Paint().apply {
                        isAntiAlias = true
                        color = onSurfaceArgb
                        textSize = 28.sp.toPx()
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        textAlign = Paint.Align.CENTER
                    }

                    val textY = centerY - (paint.descent() + paint.ascent()) / 2f

                    drawContext.canvas.nativeCanvas.drawText(text, centerX, textY, paint)
                }
            }

            // Legend
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                validSlices.forEach { slice ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Canvas(modifier = Modifier.size(8.dp)) {
                            drawCircle(color = slice.color)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = slice.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Normal,
                            fontSize = 11.sp,
                            color = onSurfaceColor
                        )
                    }
                }
            }
        }
    }
}
