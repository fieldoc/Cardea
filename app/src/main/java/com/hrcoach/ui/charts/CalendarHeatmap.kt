package com.hrcoach.ui.charts

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

data class CalendarDay(val epochDay: Int, val distanceKm: Float)

@Composable
fun CalendarHeatmap(days: List<CalendarDay>, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val cellSize = 12.dp
    val cellGap = 3.dp
    val monthLabelHeight = 20.dp

    val cellSizePx = with(density) { cellSize.toPx() }
    val cellGapPx = with(density) { cellGap.toPx() }
    val cellStepPx = cellSizePx + cellGapPx
    val monthLabelHeightPx = with(density) { monthLabelHeight.toPx() }
    val fontSizePx = with(density) { 10.sp.toPx() }

    val today = remember { LocalDate.now() }
    val todayEpoch = today.toEpochDay().toInt()

    val startDate = remember(today) {
        val startRaw = today.minusDays(363)
        val dow = startRaw.dayOfWeek
        val daysFromMonday = (dow.value - DayOfWeek.MONDAY.value + 7) % 7
        startRaw.minusDays(daysFromMonday.toLong())
    }

    val dayMap = remember(days) { days.associate { it.epochDay to it.distanceKm } }

    val weeks = remember(startDate) {
        (0 until 52).map { weekIndex ->
            (0 until 7).map { dayIndex ->
                startDate.plusDays((weekIndex * 7 + dayIndex).toLong())
            }
        }
    }

    val emptyColor = CardeaTheme.colors.surfaceVariant
    val heatLow = GradientBlue.copy(alpha = 0.5f)
    val heatHigh = GradientCyan
    val textSecondary = CardeaTheme.colors.textSecondary

    Row(modifier = modifier) {
        // Left column: day-of-week labels (M, W, F)
        Column(
            modifier = Modifier
                .width(16.dp)
                .padding(top = monthLabelHeight)
        ) {
            listOf("M", "", "W", "", "F", "", "").forEach { label ->
                Box(
                    modifier = Modifier.size(width = 16.dp, height = cellSize + cellGap),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (label.isNotEmpty()) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            color = textSecondary,
                            lineHeight = 10.sp
                        )
                    }
                }
            }
        }

        // Scrollable heatmap canvas
        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Canvas(
                modifier = Modifier
                    .width(with(density) { (52 * cellStepPx).toDp() })
                    .height(with(density) { (monthLabelHeightPx + 7 * cellStepPx).toDp() })
                    .drawWithCache {
                        val textPaint = Paint().apply {
                            isAntiAlias = true
                            textSize = fontSizePx
                            color = textSecondary.toArgb()
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                        }

                        onDrawBehind {
                            drawIntoCanvas { canvas ->
                                var lastMonthValue = -1
                                weeks.forEachIndexed { weekIndex, week ->
                                    val firstDay = week[0]

                                    // Month label: drawn at first week of each new month — no Box clipping
                                    if (firstDay.monthValue != lastMonthValue) {
                                        val monthText = firstDay.month.getDisplayName(
                                            TextStyle.SHORT, Locale.getDefault()
                                        )
                                        canvas.nativeCanvas.drawText(
                                            monthText,
                                            weekIndex * cellStepPx,
                                            fontSizePx * 1.2f,
                                            textPaint
                                        )
                                        lastMonthValue = firstDay.monthValue
                                    }

                                    // Draw each day cell
                                    week.forEachIndexed { dayIndex, date ->
                                        val epochDay = date.toEpochDay().toInt()
                                        val distanceKm = dayMap[epochDay]

                                        val x = weekIndex * cellStepPx
                                        val y = monthLabelHeightPx + dayIndex * cellStepPx

                                        val color = if (distanceKm != null && distanceKm > 0f) {
                                            val intensity = (distanceKm / 10f).coerceIn(0f, 1f)
                                            lerp(heatLow, heatHigh, intensity)
                                        } else {
                                            emptyColor
                                        }

                                        drawRoundRect(
                                            color = color,
                                            topLeft = Offset(x, y),
                                            size = Size(cellSizePx, cellSizePx),
                                            cornerRadius = CornerRadius(
                                                with(density) { 2.dp.toPx() },
                                                with(density) { 2.dp.toPx() }
                                            )
                                        )

                                        // Cyan border on today's cell
                                        if (epochDay == todayEpoch) {
                                            drawRoundRect(
                                                color = GradientCyan,
                                                topLeft = Offset(x, y),
                                                size = Size(cellSizePx, cellSizePx),
                                                cornerRadius = CornerRadius(
                                                    with(density) { 2.dp.toPx() },
                                                    with(density) { 2.dp.toPx() }
                                                ),
                                                style = Stroke(width = with(density) { 1.dp.toPx() })
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
            ) {}
        }
    }
}
