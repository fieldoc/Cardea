package com.hrcoach.data.firebase

import org.junit.Assert.*
import org.junit.Test

class RunCompletionPayloadTest {

    @Test
    fun `payload from bootcamp run includes phase and session label`() {
        val payload = RunCompletionPayload(
            userId = "uid1",
            timestamp = 1000L,
            distanceMeters = 3200.0,
            routePolyline = "encodedPoly",
            streakCount = 5,
            programPhase = "Week 3 · BUILD",
            sessionLabel = "Day 2 of 4",
            wasScheduled = true,
            originalScheduledWeekDay = null,
            weekDay = 3
        )
        assertEquals("Week 3 · BUILD", payload.programPhase)
        assertTrue(payload.wasScheduled)
        assertNull(payload.originalScheduledWeekDay)
    }

    @Test
    fun `payload from free run has null phase and session label`() {
        val payload = RunCompletionPayload(
            userId = "uid1",
            timestamp = 2000L,
            distanceMeters = 5000.0,
            routePolyline = "poly2",
            streakCount = 3,
            programPhase = null,
            sessionLabel = null,
            wasScheduled = false,
            originalScheduledWeekDay = null,
            weekDay = 6
        )
        assertNull(payload.programPhase)
        assertFalse(payload.wasScheduled)
    }

    @Test
    fun `makeup run carries originalScheduledWeekDay`() {
        val payload = RunCompletionPayload(
            userId = "uid1",
            timestamp = 3000L,
            distanceMeters = 4000.0,
            routePolyline = "poly3",
            streakCount = 2,
            programPhase = "Week 2 · BASE",
            sessionLabel = "Day 1 of 3",
            wasScheduled = true,
            originalScheduledWeekDay = 2,
            weekDay = 3
        )
        assertEquals(2, payload.originalScheduledWeekDay)
        assertEquals(3, payload.weekDay)
    }

    @Test
    fun `toMap produces correct Firestore map`() {
        val payload = RunCompletionPayload(
            userId = "uid1",
            timestamp = 1000L,
            distanceMeters = 3200.0,
            routePolyline = "poly",
            streakCount = 5,
            programPhase = "Week 3 · BUILD",
            sessionLabel = "Day 2 of 4",
            wasScheduled = true,
            originalScheduledWeekDay = null,
            weekDay = 3
        )
        val map = payload.toMap()
        assertEquals("uid1", map["userId"])
        assertEquals(1000L, map["timestamp"])
        assertEquals(3200.0, map["distanceMeters"])
        assertEquals(5, map["streakCount"])
        assertEquals(3, map["weekDay"])
        assertFalse(map.containsKey("originalScheduledWeekDay"))
    }
}
