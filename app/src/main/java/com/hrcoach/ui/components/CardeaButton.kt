package com.hrcoach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hrcoach.ui.theme.CardeaTheme

/** Visual emphasis level for [CardeaButton]. */
enum class CardeaButtonEmphasis {
    /** Saturated gradient fill — loud. Use sparingly, inside cards. */
    Filled,

    /**
     * Tonal variant: glass fill, gradient border, gradient text (via SrcIn).
     * Preserves the brand signature without neon-slab dominance — use when
     * the button would otherwise steal the eye from a nearby protagonist
     * (e.g. the Home hero).
     */
    Tonal,
}

/**
 * Primary CTA button using the Cardea gradient.
 *
 * Callers control size and width via [modifier] (e.g. fillMaxWidth + height, or wrapContentWidth).
 * Use [cornerRadius] for pill-style variants (e.g. 50.dp).
 * Use [innerPadding] when the button should size to its content with internal horizontal padding.
 * Use [emphasis] to swap between loud gradient fill and quiet tonal variant.
 */
@Composable
fun CardeaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = 14.dp,
    innerPadding: PaddingValues = PaddingValues(0.dp),
    emphasis: CardeaButtonEmphasis = CardeaButtonEmphasis.Filled,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val ctaGradient = CardeaTheme.colors.ctaGradient
    val glassHighlight = CardeaTheme.colors.glassHighlight

    val baseModifier = when (emphasis) {
        CardeaButtonEmphasis.Filled -> modifier
            .clip(shape)
            .background(if (enabled) ctaGradient else SolidColor(glassHighlight))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(innerPadding)

        CardeaButtonEmphasis.Tonal -> modifier
            .clip(shape)
            .background(SolidColor(glassHighlight))
            .then(
                if (enabled) Modifier.border(width = 1.5.dp, brush = ctaGradient, shape = shape)
                else Modifier.border(width = 1.dp, color = CardeaTheme.colors.glassBorder, shape = shape)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(innerPadding)
    }

    Box(
        modifier = baseModifier,
        contentAlignment = Alignment.Center
    ) {
        when (emphasis) {
            CardeaButtonEmphasis.Filled -> Text(
                text = text,
                color = if (enabled) CardeaTheme.colors.onGradient else CardeaTheme.colors.textTertiary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            CardeaButtonEmphasis.Tonal -> {
                if (enabled) {
                    Text(
                        text = text,
                        color = CardeaTheme.colors.onGradient,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                drawRect(brush = ctaGradient, blendMode = BlendMode.SrcIn)
                            }
                    )
                } else {
                    Text(
                        text = text,
                        color = CardeaTheme.colors.textTertiary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
