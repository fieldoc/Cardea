package com.hrcoach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.data.db.AchievementEntity
import com.hrcoach.data.db.AchievementType
import com.hrcoach.ui.theme.AchievementGold
import com.hrcoach.ui.theme.AchievementSky
import com.hrcoach.ui.theme.AchievementSlate
import com.hrcoach.ui.theme.CardeaTheme

@Composable
fun AchievementCard(
    achievement: AchievementEntity,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (accentColor, borderColor, bgColor) = prestigeColors(achievement.prestigeLevel)
    val shape = RoundedCornerShape(if (compact) 12.dp else 16.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(bgColor, bgColor.copy(alpha = bgColor.alpha * 0.25f))
                )
            )
            .border(1.dp, borderColor, shape)
            .padding(if (compact) 12.dp else 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = achievementIcon(achievement),
                fontSize = if (compact) 24.sp else 32.sp
            )
            Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
            Text(
                text = achievementTitle(achievement),
                color = CardeaTheme.colors.textPrimary,
                fontSize = if (compact) 13.sp else 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            if (achievement.goal != null) {
                Text(
                    text = goalDisplayName(achievement.goal),
                    color = CardeaTheme.colors.textTertiary,
                    fontSize = if (compact) 11.sp else 13.sp
                )
            } else {
                Text(
                    text = categoryLabel(achievement.type),
                    color = CardeaTheme.colors.textTertiary,
                    fontSize = if (compact) 11.sp else 13.sp
                )
            }
            Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
            PrestigeDots(level = achievement.prestigeLevel, color = accentColor, compact = compact)
        }
    }
}

@Composable
private fun PrestigeDots(level: Int, color: Color, compact: Boolean) {
    val size = if (compact) 6.dp else 8.dp
    val gap = if (compact) 3.dp else 4.dp
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        repeat(3) { i ->
            Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(if (i < level) color else color.copy(alpha = 0.2f))
            )
        }
    }
}

@Composable
private fun prestigeColors(level: Int): Triple<Color, Color, Color> = when (level) {
    1 -> Triple(AchievementSlate, CardeaTheme.colors.achievementSlateBorder, CardeaTheme.colors.achievementSlateBg)
    2 -> Triple(AchievementSky, CardeaTheme.colors.achievementSkyBorder, CardeaTheme.colors.achievementSkyBg)
    3 -> Triple(AchievementGold, CardeaTheme.colors.achievementGoldBorder, CardeaTheme.colors.achievementGoldBg)
    else -> Triple(AchievementSlate, CardeaTheme.colors.achievementSlateBorder, CardeaTheme.colors.achievementSlateBg)
}

private fun achievementIcon(a: AchievementEntity): String = when (a.type) {
    AchievementType.TIER_GRADUATION.name -> goalIcon(a.goal)
    AchievementType.BOOTCAMP_GRADUATION.name -> "\uD83C\uDF93"
    AchievementType.DISTANCE_MILESTONE.name -> "\uD83C\uDFC3"
    AchievementType.STREAK_MILESTONE.name -> "\uD83D\uDD25"
    AchievementType.WEEKLY_GOAL_STREAK.name -> "\uD83C\uDFAF"
    else -> "\uD83C\uDFC6"
}

private fun goalIcon(goal: String?): String = when (goal) {
    "CARDIO_HEALTH" -> "\u2764\uFE0F"
    "RACE_5K" -> "\uD83D\uDC5F"
    "RACE_10K" -> "\uD83D\uDC5F"
    "RACE_5K_10K" -> "\uD83D\uDC5F"
    "HALF_MARATHON" -> "\uD83D\uDEE3\uFE0F"
    "MARATHON" -> "\uD83C\uDFC5"
    else -> "\uD83C\uDFC6"
}

private fun achievementTitle(a: AchievementEntity): String = when (a.type) {
    AchievementType.TIER_GRADUATION.name -> when (a.tier) {
        1 -> "Intermediate"
        2 -> "Advanced"
        else -> "Tier Up"
    }
    AchievementType.BOOTCAMP_GRADUATION.name -> "Program Complete"
    AchievementType.DISTANCE_MILESTONE.name -> a.milestone.replace("km", " km")
    AchievementType.STREAK_MILESTONE.name -> a.milestone
        .replace("_sessions", " Sessions")
        .replaceFirstChar { it.uppercase() }
    AchievementType.WEEKLY_GOAL_STREAK.name -> a.milestone
        .replace("_weeks", " Weeks On Target")
        .replaceFirstChar { it.uppercase() }
    else -> a.milestone
}

private fun goalDisplayName(goal: String?): String = when (goal) {
    "CARDIO_HEALTH" -> "Cardio Health"
    "RACE_5K" -> "5K"
    "RACE_10K" -> "10K"
    "RACE_5K_10K" -> "5K / 10K"
    "HALF_MARATHON" -> "Half Marathon"
    "MARATHON" -> "Marathon"
    else -> ""
}

private fun categoryLabel(type: String): String = when (type) {
    AchievementType.DISTANCE_MILESTONE.name -> "Distance"
    AchievementType.STREAK_MILESTONE.name -> "No Misses"
    AchievementType.WEEKLY_GOAL_STREAK.name -> "Weekly Goal"
    else -> ""
}
