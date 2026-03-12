package com.hrcoach.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.data.db.AchievementEntity
import com.hrcoach.ui.components.AchievementCard
import com.hrcoach.ui.theme.CardeaTextPrimary
import com.hrcoach.ui.theme.CardeaTextSecondary

@Composable
fun AchievementGallery(
    achievements: List<AchievementEntity>,
    modifier: Modifier = Modifier
) {
    if (achievements.isEmpty()) return

    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Achievements",
                color = CardeaTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${achievements.size} earned",
                color = CardeaTextSecondary,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(12.dp))
        achievements.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { achievement ->
                    AchievementCard(
                        achievement = achievement,
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
