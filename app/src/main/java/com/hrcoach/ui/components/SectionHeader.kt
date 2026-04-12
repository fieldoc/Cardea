package com.hrcoach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.ui.theme.CardeaTheme

/**
 * Unified section divider used across all screens.
 *
 * Thin glass-border rule + UPPERCASE label in textPrimary.
 * The rule provides structural separation; the label stays compact.
 * Optional subtitle in bodySmall / textTertiary.
 */
@Composable
fun SectionHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        // ── Thin rule for structural separation ──
        Box(
            modifier = Modifier
                .fillMaxWidth(0.25f)
                .height(1.dp)
                .background(CardeaTheme.colors.glassBorder)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            ),
            color = CardeaTheme.colors.textPrimary
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
