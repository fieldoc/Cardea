package com.hrcoach.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientCyan
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.GradientRed
import kotlin.math.abs
import kotlin.math.min

data class ScatterPoint(
    val x: Float,          // HR (bpm)
    val y: Float,          // pace (min/km) — lower = faster
    val ageFraction: Float // 0f = oldest, 1f = newest
)

@Composable
fun ScatterPlot(points: List<ScatterPoint>, modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    if (points.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(text = "Not enough data")
        }
        return
    }

    // Precompute regression outside Canvas to avoid recomputing every frame
    val regressionResult = remember(points) { computeLinearRegression(points) }
    val density = LocalDensity.current
    val axisTextSizePx = with(density) { 12.sp.toPx() }

    val chartGridColor = CardeaTheme.colors.chartGrid
    Canvas(modifier = modifier.fillMaxSize()) {
        val leftPadding = 52.dp.toPx()
        val bottomPadding = 36.dp.toPx()
        val topPadding = 8.dp.toPx()
        val rightPadding = 8.dp.toPx()
        val pointRadius = 5.dp.toPx()
        val dashOn = 8.dp.toPx()
        val dashOff = 4.dp.toPx()

        val plotLeft = leftPadding
        val plotTop = topPadding
        val plotRight = size.width - rightPadding
        val plotBottom = size.height - bottomPadding
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop

        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }

        val rangeX = if (abs(maxX - minX) < 1e-6f) 1f else maxX - minX
        val rangeY = if (abs(maxY - minY) < 1e-6f) 1f else maxY - minY

        fun toScreenX(value: Float): Float =
            plotLeft + (value - minX) / rangeX * plotWidth

        // Y-axis inverted: lower pace = higher on screen
        fun toScreenY(value: Float): Float =
            plotTop + plotHeight - (value - minY) / rangeY * plotHeight

        val gridColor = chartGridColor
        val textPaint = android.graphics.Paint().apply {
            color = onSurfaceColor.toArgb()
            textSize = axisTextSizePx
            isAntiAlias = true
        }
        val labelPaint = android.graphics.Paint().apply {
            color = onSurfaceColor.toArgb()
            textSize = axisTextSizePx
            isAntiAlias = true
        }

        // Draw grid lines and X-axis ticks (4 evenly spaced)
        val tickCount = 4
        for (i in 0..tickCount) {
            val fraction = i.toFloat() / tickCount

            // Vertical grid line
            val gx = plotLeft + fraction * plotWidth
            val xValue = minX + fraction * rangeX
            drawDashedLine(
                start = Offset(gx, plotTop),
                end = Offset(gx, plotBottom),
                color = gridColor,
                dashOn = dashOn,
                dashOff = dashOff
            )
            // X-axis tick label
            val label = "%.0f".format(xValue)
            val labelWidth = textPaint.measureText(label)
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    label,
                    gx - labelWidth / 2f,
                    plotBottom + 14.dp.toPx(),
                    textPaint
                )
            }

            // Horizontal grid line
            val gy = plotTop + fraction * plotHeight
            // Y value corresponding to this screen position (inverted)
            val yValue = minY + (1f - fraction) * rangeY
            drawDashedLine(
                start = Offset(plotLeft, gy),
                end = Offset(plotRight, gy),
                color = gridColor,
                dashOn = dashOn,
                dashOff = dashOff
            )
            // Y-axis tick label
            val yLabel = "%.1f".format(yValue)
            val yLabelWidth = textPaint.measureText(yLabel)
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    yLabel,
                    plotLeft - yLabelWidth - 4.dp.toPx(),
                    gy + textPaint.textSize / 3f,
                    textPaint
                )
            }
        }

        // Draw scatter points with gradient colors
        val scatterGradientColors = listOf(GradientRed, GradientPink, GradientBlue, GradientCyan)
        for (point in points) {
            val cx = toScreenX(point.x)
            val cy = toScreenY(point.y)
            val color = lerp(
                Color.Gray.copy(alpha = 0.3f),
                scatterGradientColors[(point.ageFraction * (scatterGradientColors.size - 1)).toInt().coerceIn(0, scatterGradientColors.size - 1)],
                point.ageFraction
            )
            drawCircle(color = color, radius = pointRadius, center = Offset(cx, cy))
        }

        // Draw dashed linear regression line with gradient color
        val (slope, intercept) = regressionResult
        val yAtLeft = slope * minX + intercept
        val yAtRight = slope * maxX + intercept
        val regStartX = plotLeft
        val regStartY = toScreenY(yAtLeft)
        val regEndX = plotRight
        val regEndY = toScreenY(yAtRight)
        drawDashedLine(
            start = Offset(regStartX, regStartY),
            end = Offset(regEndX, regEndY),
            color = GradientCyan,
            dashOn = dashOn,
            dashOff = dashOff
        )

        // X-axis label: "Heart Rate (bpm)" centered below the chart
        val xAxisLabel = "Heart Rate (bpm)"
        val xAxisLabelWidth = labelPaint.measureText(xAxisLabel)
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText(
                xAxisLabel,
                plotLeft + plotWidth / 2f - xAxisLabelWidth / 2f,
                size.height - 4.dp.toPx(),
                labelPaint
            )
        }

        // Y-axis label: "Pace" rotated 90° on the left side
        val yAxisLabel = "Pace"
        val yAxisLabelWidth = labelPaint.measureText(yAxisLabel)
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.apply {
                save()
                rotate(
                    -90f,
                    labelPaint.textSize,
                    plotTop + plotHeight / 2f
                )
                drawText(
                    yAxisLabel,
                    labelPaint.textSize - yAxisLabelWidth / 2f,
                    plotTop + plotHeight / 2f + labelPaint.textSize / 3f,
                    labelPaint
                )
                restore()
            }
        }
    }
}

private fun computeLinearRegression(points: List<ScatterPoint>): Pair<Float, Float> {
    val n = points.size.toFloat()
    val sumX = points.sumOf { it.x.toDouble() }.toFloat()
    val sumY = points.sumOf { it.y.toDouble() }.toFloat()
    val sumXY = points.sumOf { (it.x * it.y).toDouble() }.toFloat()
    val sumX2 = points.sumOf { (it.x * it.x).toDouble() }.toFloat()
    val denominator = n * sumX2 - sumX * sumX
    return if (abs(denominator) < 1e-6f) {
        Pair(0f, sumY / n)
    } else {
        val slope = (n * sumXY - sumX * sumY) / denominator
        val intercept = (sumY - slope * sumX) / n
        Pair(slope, intercept)
    }
}

private fun DrawScope.drawDashedLine(
    start: Offset,
    end: Offset,
    color: Color,
    dashOn: Float,
    dashOff: Float
) {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = kotlin.math.sqrt(dx * dx + dy * dy)
    if (length < 1e-6f) return
    val ux = dx / length
    val uy = dy / length

    var drawn = 0f
    var drawing = true
    while (drawn < length) {
        val segLen = if (drawing) min(dashOn, length - drawn) else min(dashOff, length - drawn)
        if (drawing) {
            val sx = start.x + ux * drawn
            val sy = start.y + uy * drawn
            val ex = start.x + ux * (drawn + segLen)
            val ey = start.y + uy * (drawn + segLen)
            drawLine(color = color, start = Offset(sx, sy), end = Offset(ex, ey), strokeWidth = 2.dp.toPx())
        }
        drawn += segLen
        drawing = !drawing
    }
}
