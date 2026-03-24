package com.hrcoach.ui.components

import org.junit.Assert.*
import org.junit.Test

class EmblemRegistryTest {

    @Test
    fun `registry contains exactly 24 emblems`() {
        assertEquals(24, EmblemRegistry.allIds.size)
    }

    @Test
    fun `all expected emblem IDs are present`() {
        val expected = listOf(
            "pulse", "bolt", "summit", "flame", "compass", "shield",
            "ascent", "crown", "orbit", "infinity", "diamond", "nova",
            "heart", "wave", "spiral", "trident", "comet", "prism",
            "ripple", "crescent", "wings", "helix", "focus", "laurel"
        )
        expected.forEach { id ->
            assertTrue("Missing emblem: $id", EmblemRegistry.allIds.contains(id))
        }
    }

    @Test
    fun `each emblem has a display name`() {
        EmblemRegistry.allIds.forEach { id ->
            assertNotNull("Missing display name for: $id", EmblemRegistry.displayName(id))
            assertTrue(EmblemRegistry.displayName(id)!!.isNotBlank())
        }
    }

    @Test
    fun `unknown emblem ID returns null display name`() {
        assertNull(EmblemRegistry.displayName("nonexistent"))
    }
}
