package com.hrcoach.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientCyan
import java.util.Locale

data class CalendarDay(val epochDay: Int, val distanceKm: Float)

@Composable
fun CalendarHeatmap(days: List<CalendarDay>, modifier: Modifier = Modifier) {
    val cellSize = 10.dp
    val cellGap = 2.dp
    val cellStep = cellSize + cellGap

    val today = remember { LocalDate.now() }
    // Start from (today - 363 days) snapped to the Monday of that week
    val startRaw = today.minusDays(363)
    val startDate = remember(today) {
        val dow = startRaw.dayOfWeek
        val daysFromMonday = (dow.value - DayOfWeek.MONDAY.value + 7) % 7
        startRaw.minusDays(daysFromMonday.toLong())
    }

    // Build a map from epochDay -> distanceKm for O(1) lookup
    val dayMap = remember(days) { days.associate { it.epochDay to it.distanceKm } }

    // Build list of 52 week columns; each column is a list of 7 LocalDates (Mon..Sun)
    val weeks = remember(startDate) {
        (0 until 52).map { weekIndex ->
            (0 until 7).map { dayIndex ->
                startDate.plusDays((weekIndex * 7 + dayIndex).toLong())
            }
        }
    }

    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val heatLow = GradientBlue.copy(alpha = 0.4f)
    val heatHigh = GradientCyan

    // Day-of-week label strings: show text only at rows 0 (Mon), 2 (Wed), 4 (Fri)
    val dowLabels = listOf("M", "", "W", "", "F", "", "")

    Row(modifier = modifier) {
        // Left column: day-of-week labels
        // Offset by the height of the month label row above
        Column(
            modifier = Modifier.padding(top = 16.dp, end = 2.dp)
        ) {
            dowLabels.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .size(width = 10.dp, height = cellSize)
                        .padding(bottom = if (index < 6) cellGap else 0.dp)
                ) {
                    if (label.isNotEmpty()) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            lineHeight = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (index < 6) {
                    Spacer(modifier = Modifier.height(cellGap))
                }
            }
        }

        // Scrollable area: month labels row + week columns
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Column {
                // Month labels row
                Row {
                    weeks.forEachIndexed { weekIndex, week ->
                        val monday = week[0]
                        // Show month label if this is the first week of a new month
                        // (monday's day-of-month is in the first 7 days, meaning this is the first Mon of the month)
                        val showMonth = monday.dayOfMonth <= 7
                        Box(modifier = Modifier.width(cellStep)) {
                            if (showMonth) {
                                Text(
                                    text = monday.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                    fontSize = 12.sp,
                                    lineHeight = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Week columns
                Row {
                    weeks.forEachIndexed { weekIndex, week ->
                        Column {
                            week.forEachIndexed { dayIndex, date ->
                                val epochDay = date.toEpochDay().toInt()
                                val distanceKm = dayMap[epochDay]
                                val color = if (distanceKm != null && distanceKm > 0f) {
                                    val intensity = (distanceKm / 10f).coerceIn(0f, 1f)
                                    lerp(heatLow, heatHigh, intensity)
                                } else {
                                    surfaceVariant
                                }

                                Box(
                                    modifier = Modifier
                                        .size(cellSize)
                                        .background(
                                            color = color,
                                            shape = RoundedCornerShape(2.dp)
                                        )
                                )

                                if (dayIndex < 6) {
                                    Spacer(modifier = Modifier.height(cellGap))
                                }
                            }
                        }

                        if (weekIndex < 51) {
                            Spacer(modifier = Modifier.width(cellGap))
                        }
                    }
                }
            }
        }
    }
}
