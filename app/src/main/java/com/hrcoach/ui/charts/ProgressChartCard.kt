package com.hrcoach.ui.charts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class TrendInfo(val label: String, val positive: Boolean?)

@Composable
fun TrendBadge(trendInfo: TrendInfo, modifier: Modifier = Modifier) {
    val backgroundColor = when (trendInfo.positive) {
        true -> Color(0xFF2E7D32)
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when (trendInfo.positive) {
        true -> Color.White
        false -> MaterialTheme.colorScheme.onError
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier.height(24.dp),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = trendInfo.label,
                color = textColor,
                fontSize = 11.sp
            )
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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
