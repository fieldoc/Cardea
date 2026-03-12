package com.hrcoach.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class UserProfileSanitizationTest {

    @Test
    fun `sanitizeName returns trimmed input`() {
        assertEquals("Bob", sanitizeDisplayName("  Bob  "))
    }

    @Test
    fun `sanitizeName truncates to 20 chars`() {
        assertEquals("A".repeat(20), sanitizeDisplayName("A".repeat(25)))
    }

    @Test
    fun `sanitizeName falls back to Runner for blank`() {
        assertEquals("Runner", sanitizeDisplayName("   "))
    }

    @Test
    fun `sanitizeName falls back to Runner for empty`() {
        assertEquals("Runner", sanitizeDisplayName(""))
    }

    @Test
    fun `sanitizeName preserves normal input`() {
        assertEquals("Alice", sanitizeDisplayName("Alice"))
    }
}
