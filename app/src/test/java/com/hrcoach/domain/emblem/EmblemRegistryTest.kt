package com.hrcoach.domain.emblem

import org.junit.Assert.*
import org.junit.Test

class EmblemRegistryTest {

    @Test
    fun `all 24 emblems are registered`() {
        assertEquals(24, Emblem.entries.size)
    }

    @Test
    fun `each emblem has a unique id`() {
        val ids = Emblem.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `each emblem has a non-blank display name`() {
        Emblem.entries.forEach { emblem ->
            assertTrue("${emblem.name} has blank displayName", emblem.displayName.isNotBlank())
        }
    }

    @Test
    fun `fromId returns correct emblem`() {
        assertEquals(Emblem.PULSE, Emblem.fromId("pulse"))
        assertEquals(Emblem.BOLT, Emblem.fromId("bolt"))
    }

    @Test
    fun `fromId returns default for unknown id`() {
        assertEquals(Emblem.PULSE, Emblem.fromId("nonexistent"))
    }

    @Test
    fun `fromId handles legacy unicode avatar symbols`() {
        assertEquals(Emblem.PULSE, Emblem.fromId("\u2665"))
        assertEquals(Emblem.BOLT, Emblem.fromId("\u26A1"))
        assertEquals(Emblem.DIAMOND, Emblem.fromId("\u25C6"))
    }
}
