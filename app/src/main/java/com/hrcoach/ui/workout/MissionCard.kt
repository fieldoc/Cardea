package com.hrcoach.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.domain.model.ZoneStatus
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaTextPrimary
import com.hrcoach.ui.theme.CardeaTextSecondary
import com.hrcoach.ui.theme.CardeaTextTertiary
import com.hrcoach.ui.theme.GlassBorder
import com.hrcoach.ui.theme.GlassHighlight
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.ZoneAmber
import com.hrcoach.ui.theme.ZoneGreen
import com.hrcoach.ui.theme.ZoneRed
import com.hrcoach.util.formatDistanceKm
import com.hrcoach.util.formatDurationSeconds

@Composable
fun MissionCard(
    uiState: ActiveWorkoutUiState,
    modifier: Modifier = Modifier
) {
    val snapshot = uiState.snapshot
    val zoneColor = missionZoneColor(snapshot.isFreeRun, snapshot.zoneStatus)
    val zoneLabel = missionZoneLabel(snapshot.isFreeRun, snapshot.hrConnected, snapshot.zoneStatus)
    val sessionLabel = uiState.workoutTypeLabel
    val hasTimeGoal = uiState.totalDurationSeconds != null
    val hasDistanceGoal = uiState.totalDistanceMeters != null
    val hasGoal = hasTimeGoal || hasDistanceGoal

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassHighlight)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .drawBehind {
                // Cardea gradient left edge accent (3dp)
                drawRect(
                    brush = CardeaGradient,
                    topLeft = Offset.Zero,
                    size = Size(3.dp.toPx(), size.height)
                )
            }
            .padding(start = 7.dp, end = 14.dp, top = 14.dp, bottom = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Top row: session label (left) + zone badge (right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = sessionLabel ?: "Workout",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = CardeaTextPrimary
                    )
                    uiState.bootcampWeekNumber?.let { week ->
                        Text(
                            text = "Week $week",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = CardeaTextTertiary
                        )
                    }
                }

                // Zone badge pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(zoneColor.copy(alpha = 0.12f))
                        .border(1.dp, zoneColor.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(zoneColor, CircleShape)
                    )
                    Text(
                        text = zoneLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.2.sp
                        ),
                        color = zoneColor
                    )
                }
            }

            // Hero timer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom
            ) {
                if (hasTimeGoal) {
                    Text(
                        text = formatDurationSeconds(uiState.elapsedSeconds),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-1).sp
                        ),
                        color = Color.White
                    )
                    Text(
                        text = " / ",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = Color.White.copy(alpha = 0.15f)
                    )
                    Text(
                        text = formatDurationSeconds(uiState.totalDurationSeconds!!),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = Color.White.copy(alpha = 0.28f)
                    )
                } else if (hasDistanceGoal) {
                    Text(
                        text = formatDistanceKm(snapshot.distanceMeters),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-1).sp
                        ),
                        color = Color.White
                    )
                    Text(
                        text = " / ",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = Color.White.copy(alpha = 0.15f)
                    )
                    Text(
                        text = "${formatDistanceKm(uiState.totalDistanceMeters!!)} km",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = Color.White.copy(alpha = 0.28f)
                    )
                } else {
                    // Open-ended: just elapsed time
                    Text(
                        text = formatDurationSeconds(uiState.elapsedSeconds),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-1).sp
                        ),
                        color = Color.White
                    )
                }
            }

            // Bottom row: target HR pill (left) + remaining (right)
            if (hasGoal || (!snapshot.isFreeRun && snapshot.targetHr > 0)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Target HR pill
                    if (!snapshot.isFreeRun && snapshot.targetHr > 0) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(zoneColor.copy(alpha = 0.10f))
                                .border(
                                    1.dp,
                                    zoneColor.copy(alpha = 0.20f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "\u2665",
                                fontSize = 11.sp,
                                color = zoneColor
                            )
                            Text(
                                text = "Target ${snapshot.targetHr} bpm",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = zoneColor
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    // Remaining text
                    uiState.remainingSeconds?.let { remaining ->
                        Text(
                            text = "${formatDurationSeconds(remaining)} remaining",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = CardeaTextTertiary
                        )
                    }
                }
            }
        }
    }
}

private fun missionZoneColor(isFreeRun: Boolean, zoneStatus: ZoneStatus): Color {
    return if (isFreeRun) {
        GradientBlue
    } else {
        when (zoneStatus) {
            ZoneStatus.IN_ZONE -> ZoneGreen
            ZoneStatus.BELOW_ZONE -> ZoneAmber
            ZoneStatus.ABOVE_ZONE -> ZoneRed
            ZoneStatus.NO_DATA -> Color.White.copy(alpha = 0.35f)
        }
    }
}

private fun missionZoneLabel(
    isFreeRun: Boolean,
    hrConnected: Boolean,
    zoneStatus: ZoneStatus
): String = when {
    isFreeRun && hrConnected -> "FREE RUN"
    isFreeRun -> "NO SIGNAL"
    !hrConnected || zoneStatus == ZoneStatus.NO_DATA -> "NO SIGNAL"
    zoneStatus == ZoneStatus.IN_ZONE -> "IN ZONE"
    zoneStatus == ZoneStatus.ABOVE_ZONE -> "ABOVE ZONE"
    zoneStatus == ZoneStatus.BELOW_ZONE -> "BELOW ZONE"
    else -> "NO SIGNAL"
}
