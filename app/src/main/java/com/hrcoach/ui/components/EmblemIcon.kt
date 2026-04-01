package com.hrcoach.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hrcoach.domain.emblem.Emblem
import com.hrcoach.ui.emblem.drawEmblem
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientCyan
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.GradientRed

// Build the canonical Cardea 135-degree gradient sized to the canvas
private fun cardeaGradient(w: Float, h: Float) = Brush.linearGradient(
    colorStops = arrayOf(
        0.00f to GradientRed,
        0.35f to GradientPink,
        0.65f to GradientBlue,
        1.00f to GradientCyan
    ),
    start = Offset.Zero,
    end = Offset(w, h)
)

@Composable
fun EmblemIcon(
    emblem: Emblem,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val gradient = cardeaGradient(w, h)
        val center = Offset(w / 2, h / 2)
        val radius = this.size.minDimension / 2 * 0.75f
        drawEmblem(emblem, center, radius, gradient)
    }
}

@Composable
fun EmblemIconWithRing(
    emblem: Emblem,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    ringWidth: Dp = 3.dp
) {
    val bgColor = CardeaTheme.colors.bgPrimary

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val gradient = cardeaGradient(w, h)
        val center = Offset(w / 2, h / 2)
        val outerRadius = this.size.minDimension / 2
        val ringW = ringWidth.toPx()

        drawCircle(brush = gradient, radius = outerRadius, center = center, style = Stroke(width = ringW))
        drawCircle(color = bgColor, radius = outerRadius - ringW, center = center)

        val emblemRadius = (outerRadius - ringW * 2) * 0.8f
        drawEmblem(emblem, center, emblemRadius, gradient)
    }
}
