package com.hrcoach.ui.partner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.domain.sharing.DayState
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientCyan
import com.hrcoach.ui.theme.GradientRed

data class PartnerCardState(
    val displayName: String = "Runner",
    val avatarSymbol: String = "\u2665",
    val statusText: String = "",
    val statusColor: Color = Color.White,
    val streakCount: Int = 0,
    val dayStates: List<DayState> = List(7) { DayState.REST },
    val programPhase: String? = null,
    val latestCompletionId: String? = null
)

private val cardShape = RoundedCornerShape(16.dp)

@Composable
fun PartnerHeroCard(
    state: PartnerCardState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xEB0F1623),
                        Color(0xF2141C2A)
                    )
                ),
                shape = cardShape
            )
            .border(1.dp, CardeaTheme.colors.glassBorder, cardShape)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column {
            // Header row: avatar + name/status + streak badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(GradientRed, GradientBlue)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.avatarSymbol,
                        fontSize = 20.sp
                    )
                }

                // Name + status
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.displayName,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = CardeaTheme.colors.textPrimary
                    )
                    Text(
                        text = state.statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = state.statusColor
                    )
                }

                // Streak badge
                StreakBadge(count = state.streakCount)
            }

            Spacer(Modifier.height(16.dp))

            // Week progress track
            WeekProgressTrack(dayStates = state.dayStates)

            // Phase label (bootcamp runners only)
            if (state.programPhase != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = state.programPhase,
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 0.3.sp
                    ),
                    color = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun StreakBadge(count: Int, modifier: Modifier = Modifier) {
    val badgeShape = RoundedCornerShape(10.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        GradientRed.copy(alpha = 0.10f),
                        GradientCyan.copy(alpha = 0.10f)
                    )
                ),
                shape = badgeShape
            )
            .border(1.dp, CardeaTheme.colors.glassBorder, badgeShape)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                brush = Brush.linearGradient(
                    colors = listOf(GradientRed, GradientCyan)
                )
            )
        )
        Text(
            text = "STREAK",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 8.sp,
                letterSpacing = 0.5.sp
            ),
            color = Color.White.copy(alpha = 0.4f)
        )
    }
}
