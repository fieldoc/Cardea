package com.hrcoach.ui.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.ZoneGreen
import com.hrcoach.ui.theme.ZoneRed

data class TrendInfo(val label: String, val positive: Boolean?)

@Composable
fun TrendBadge(trendInfo: TrendInfo, modifier: Modifier = Modifier) {
    val backgroundColor = when (trendInfo.positive) {
        true -> ZoneGreen.copy(alpha = 0.18f)
        false -> ZoneRed.copy(alpha = 0.18f)
        null -> CardeaTheme.colors.textTertiary.copy(alpha = 0.18f)
    }
    val textColor = when (trendInfo.positive) {
        true -> ZoneGreen
        false -> ZoneRed
        null -> CardeaTheme.colors.textSecondary
    }

    Box(
        modifier = modifier
            .height(24.dp)
    ) {
        Surface(
            color = backgroundColor,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.height(24.dp)
            ) {
                Text(
                    text = trendInfo.label,
                    color = textColor,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .wrapContentHeight(Alignment.CenterVertically)
                        .padding(horizontal = 10.dp)
                )
            }
        }
    }
}

@Composable
fun ProgressChartCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trendInfo: TrendInfo? = null,
    content: @Composable () -> Unit
) {
    GlassCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = CardeaTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.weight(1f))
            if (trendInfo != null) {
                TrendBadge(trendInfo = trendInfo)
            }
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textSecondary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            ),
            color = CardeaTheme.colors.textSecondary
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textTertiary
            )
        }
    }
}
