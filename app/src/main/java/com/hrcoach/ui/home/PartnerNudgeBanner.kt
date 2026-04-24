package com.hrcoach.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.domain.emblem.Emblem
import com.hrcoach.ui.components.EmblemIconWithRing
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.PartnerTeal

@Composable
fun PartnerNudgeBanner(
    state: NudgeBannerState,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardeaTheme.colors.glassHighlight)
            .border(1.dp, CardeaTheme.colors.glassBorder, shape)
            .clickable { onTap() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Stacked partner avatar emblems
        val emblems = state.partners
            .take(3)
            .map { Emblem.fromId(it.emblemId) }

        StackedEmblems(emblems = emblems)

        Spacer(modifier = Modifier.width(12.dp))

        // Partner name text + subtitle. PartnerTeal accents only the
        // "just finished" phrase so the card reads as a quiet presence
        // rather than a block of coloured text.
        val baseStyle = SpanStyle(
            color = CardeaTheme.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        val accentStyle = SpanStyle(
            color = PartnerTeal,
            fontWeight = FontWeight.SemiBold,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildAnnotatedString {
                    val source = state.text
                    val phrase = "just finished"
                    val idx = source.indexOf(phrase, ignoreCase = true)
                    if (idx >= 0) {
                        withStyle(baseStyle) { append(source.substring(0, idx)) }
                        withStyle(accentStyle) { append(source.substring(idx, idx + phrase.length)) }
                        withStyle(baseStyle) { append(source.substring(idx + phrase.length)) }
                    } else {
                        withStyle(baseStyle) { append(source) }
                    }
                },
                fontSize = 13.sp,
                lineHeight = 17.sp,
                maxLines = 2,
            )
            if (state.subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = state.subtitle,
                    fontSize = 11.sp,
                    color = CardeaTheme.colors.textSecondary,
                    lineHeight = 15.sp,
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // "Go →" CTA — solid PartnerTeal so PulseHero remains the sole
        // Tier 1 gradient on the Home screen.
        Text(
            text = "Go \u2192",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = PartnerTeal,
        )
    }
}

@Composable
private fun StackedEmblems(emblems: List<Emblem>) {
    val avatarSize = 32.dp
    val overlap = 12.dp
    val totalWidth = avatarSize + (overlap * (emblems.size - 1).coerceAtLeast(0))

    Box(modifier = Modifier.size(width = totalWidth, height = avatarSize)) {
        emblems.forEachIndexed { index, emblem ->
            EmblemIconWithRing(
                emblem = emblem,
                size = avatarSize,
                ringWidth = 2.dp,
                modifier = Modifier.offset(x = overlap * index),
            )
        }
    }
}
