package com.hrcoach.ui.emblem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import com.hrcoach.domain.emblem.Emblem
import org.junit.Test

class EmblemRendererTest {

    /**
     * Crash-safety test: verifies that drawEmblem dispatches all 24 enum values
     * without throwing. Uses a MockK DrawScope so no Android bitmap allocation is needed.
     */
    @Test
    fun `all 24 emblems draw without crashing`() {
        val gradient = Brush.linearGradient(
            colors = listOf(Color.Red, Color.Blue),
            start = Offset.Zero, end = Offset(100f, 100f)
        )
        val scope = mockk<DrawScope>(relaxed = true)

        // Stub size to avoid NPE from any size-dependent calls
        every { scope.size } returns androidx.compose.ui.geometry.Size(100f, 100f)

        Emblem.entries.forEach { emblem ->
            scope.drawEmblem(emblem, Offset(50f, 50f), 40f, gradient)
        }
        // If we reach here all 24 branches executed without throwing
    }
}
