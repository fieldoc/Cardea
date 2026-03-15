package com.hrcoach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hrcoach.ui.theme.CardeaTheme

/**
 * Primary CTA button using the Cardea gradient fill.
 *
 * Callers control size and width via [modifier] (e.g. fillMaxWidth + height, or wrapContentWidth).
 * Use [cornerRadius] for pill-style variants (e.g. 50.dp).
 * Use [innerPadding] when the button should size to its content with internal horizontal padding.
 */
@Composable
fun CardeaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    innerPadding: PaddingValues = PaddingValues(0.dp)
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(CardeaTheme.colors.ctaGradient)
            .clickable(onClick = onClick)
            .padding(innerPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = CardeaTheme.colors.onGradient,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}
