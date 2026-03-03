package com.hrcoach.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientCyan
import com.hrcoach.ui.theme.GradientPink

data class BarEntry(val label: String, val value: Float)

@Composable
fun BarChart(bars: List<BarEntry>, color: Color, modifier: Modifier = Modifier) {
    val maxValue = bars.maxOfOrNull { it.value } ?: 0f

    if (bars.isEmpty() || maxValue == 0f) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(text = "No data")
        }
        return
    }

    val gridColor = Color(0x0AFFFFFF)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current
    val axisTextSizePx = with(density) { 12.sp.toPx() }

    val textPaint = remember(axisTextSizePx) {
        android.graphics.Paint().apply {
            textSize = axisTextSizePx
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    val yLabelPaint = remember(axisTextSizePx) {
        android.graphics.Paint().apply {
            textSize = axisTextSizePx
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
    }

    // Measure label widths once per bars/paint change, not inside the draw loop
    val maxLabelWidth = remember(bars, textPaint) {
        bars.maxOfOrNull { textPaint.measureText(it.label) } ?: 0f
    }

    Canvas(modifier = modifier) {
        val paddingLeft = 48.dp.toPx()
        val paddingBottom = 32.dp.toPx()
        val paddingTop = 8.dp.toPx()
        val paddingRight = 8.dp.toPx()

        val chartLeft = paddingLeft
        val chartTop = paddingTop
        val chartRight = size.width - paddingRight
        val chartBottom = size.height - paddingBottom

        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        val gridLineColor = gridColor
        val onSurfaceVariantArgb = labelColor.toArgb()
        textPaint.color = onSurfaceVariantArgb
        yLabelPaint.color = onSurfaceVariantArgb

        // Draw 4 horizontal dashed grid lines with Y-axis labels
        val gridLineCount = 4
        for (i in 0..gridLineCount) {
            val fraction = i.toFloat() / gridLineCount
            val y = chartBottom - fraction * chartHeight
            val gridValue = fraction * maxValue

            // Dashed grid line
            val dashWidth = 12.dp.toPx()
            val dashGap = 6.dp.toPx()
            var x = chartLeft
            while (x < chartRight) {
                val endX = minOf(x + dashWidth, chartRight)
                drawLine(
                    color = gridLineColor,
                    start = Offset(x, y),
                    end = Offset(endX, y),
                    strokeWidth = 1.dp.toPx()
                )
                x += dashWidth + dashGap
            }

            // Y-axis label
            val labelText = "%.0f".format(gridValue)
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    labelText,
                    chartLeft - 6.dp.toPx(),
                    y + (yLabelPaint.textSize / 3f),
                    yLabelPaint
                )
            }
        }

        // Draw bars
        val totalBarSlotWidth = chartWidth / bars.size
        val gapFraction = 0.20f
        val barWidth = totalBarSlotWidth * (1f - gapFraction)
        val halfGap = totalBarSlotWidth * gapFraction / 2f
        val cornerRadius = 4.dp.toPx()

        // Only draw X-axis labels when they won't overlap
        val labelStep = if (maxLabelWidth > 0f) {
            kotlin.math.ceil((maxLabelWidth + 4.dp.toPx()) / totalBarSlotWidth).toInt().coerceAtLeast(1)
        } else 1

        bars.forEachIndexed { index, entry ->
            val isLast = index == bars.size - 1
            val barColor = if (isLast) color else color.copy(alpha = 0.55f)

            val slotLeft = chartLeft + index * totalBarSlotWidth
            val barLeft = slotLeft + halfGap
            val barRight = barLeft + barWidth
            val barHeight = (entry.value / maxValue) * chartHeight
            val barTop = chartBottom - barHeight
            val barCenterX = barLeft + barWidth / 2f

            val barGradient = Brush.verticalGradient(
                colors = listOf(GradientCyan, GradientBlue, GradientPink),
                startY = barTop,
                endY = chartBottom
            )
            drawRoundRect(
                brush = barGradient,
                topLeft = Offset(barLeft, barTop),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                alpha = if (isLast) 1f else 0.55f
            )

            // X-axis label centered below each bar — skipped when labels would overlap
            if (index % labelStep == 0) {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(
                        entry.label,
                        barCenterX,
                        chartBottom + 24.dp.toPx(),
                        textPaint
                    )
                }
            }
        }
    }
}
