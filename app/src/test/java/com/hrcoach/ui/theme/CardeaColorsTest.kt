package com.hrcoach.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CardeaColorsTest {

    @Test
    fun `dark and light palettes have different backgrounds`() {
        assertNotEquals(DarkCardeaColors.bgPrimary, LightCardeaColors.bgPrimary)
        assertNotEquals(DarkCardeaColors.bgSecondary, LightCardeaColors.bgSecondary)
    }

    @Test
    fun `dark and light palettes have different text colors`() {
        assertNotEquals(DarkCardeaColors.textPrimary, LightCardeaColors.textPrimary)
        assertNotEquals(DarkCardeaColors.textSecondary, LightCardeaColors.textSecondary)
    }

    @Test
    fun `gradients are identical in both palettes`() {
        assertEquals(DarkCardeaColors.gradient, LightCardeaColors.gradient)
        assertEquals(DarkCardeaColors.ctaGradient, LightCardeaColors.ctaGradient)
        assertEquals(DarkCardeaColors.navGradient, LightCardeaColors.navGradient)
    }

    @Test
    fun `zone colors are identical in both palettes`() {
        assertEquals(DarkCardeaColors.zoneGreen, LightCardeaColors.zoneGreen)
        assertEquals(DarkCardeaColors.zoneAmber, LightCardeaColors.zoneAmber)
        assertEquals(DarkCardeaColors.zoneRed, LightCardeaColors.zoneRed)
    }

    @Test
    fun `onGradient is always white`() {
        assertEquals(Color.White, DarkCardeaColors.onGradient)
        assertEquals(Color.White, LightCardeaColors.onGradient)
    }

    @Test
    fun `dark palette isDark is true, light palette isDark is false`() {
        assertEquals(true, DarkCardeaColors.isDark)
        assertEquals(false, LightCardeaColors.isDark)
    }

    @Test
    fun `light mode glass elevation is non-zero`() {
        assert(LightCardeaColors.glassElevation.value > 0f)
        assertEquals(0f, DarkCardeaColors.glassElevation.value, 0.001f)
    }
}
