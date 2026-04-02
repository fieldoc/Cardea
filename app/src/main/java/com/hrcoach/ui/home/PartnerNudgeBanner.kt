package com.hrcoach.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.domain.emblem.Emblem
import com.hrcoach.ui.components.EmblemIconWithRing
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientCyan
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.GradientRed

private val NudgeGreen = Color(0xFF4ADE80)

private val NudgeBackground = Brush.linearGradient(
    colors = listOf(
        Color(0xFF4ADE80).copy(alpha = 0.06f),
        Color(0xFF00D1FF).copy(alpha = 0.04f),
    )
)

private val GoGradient = Brush.linearGradient(
    colorStops = arrayOf(
        0.00f to GradientRed,
        0.35f to GradientPink,
        0.65f to GradientBlue,
        1.00f to GradientCyan,
    )
)

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
            .background(NudgeBackground)
            .drawBehind { drawNudgeLeftBorder() }
            .clickable { onTap() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Stacked partner avatar emblems
        val emblems = state.partners
            .take(3)
            .map { Emblem.fromId(it.emblemId) }

        StackedEmblems(emblems = emblems)

        Spacer(modifier = Modifier.width(12.dp))

        // Partner name text + subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = NudgeGreen, fontWeight = FontWeight.SemiBold)) {
                        append(state.text)
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
                    color = Color(0xFFA1A1AA),
                    lineHeight = 15.sp,
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // "Go →" gradient CTA
        Text(
            text = "Go \u2192",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }.drawBehind {
                drawRect(brush = GoGradient, size = this.size)
            },
            color = Color.Transparent,
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

private fun DrawScope.drawNudgeLeftBorder() {
    drawRect(
        color = Color(0xFF4ADE80),
        topLeft = Offset.Zero,
        size = Size(width = 3.dp.toPx(), height = size.height),
    )
}
