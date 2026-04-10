package com.hrcoach.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hrcoach.ui.theme.CardeaTheme

private val GlassShape = RoundedCornerShape(18.dp)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    borderColor: Color = CardeaTheme.colors.glassBorder,
    containerColor: Color = Color.Transparent,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = CardeaTheme.colors
    Card(
        modifier = modifier.wrapContentHeight(),
        shape = GlassShape,
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(
            containerColor = if (containerColor != Color.Transparent) containerColor
                             else if (colors.isDark) Color.Transparent else colors.bgSecondary
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = colors.glassElevation)
    ) {
        Box(modifier = Modifier.background(colors.glassFillBrush)) {
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .wrapContentHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                content = content
            )
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    unit: String? = null,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = CardeaTheme.colors.textSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = CardeaTheme.colors.textPrimary,
            textAlign = if (horizontalAlignment == Alignment.CenterHorizontally) TextAlign.Center else TextAlign.Start
        )
        if (unit != null) {
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = CardeaTheme.colors.textSecondary
            )
        }
    }
}
