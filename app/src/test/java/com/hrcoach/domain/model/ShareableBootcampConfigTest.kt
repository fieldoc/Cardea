package com.hrcoach.domain.model

import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.domain.bootcamp.DayPreference
import com.hrcoach.domain.bootcamp.DaySelectionLevel
import org.junit.Assert.*
import org.junit.Test

class ShareableBootcampConfigTest {

    private val sample = ShareableBootcampConfig(
        goalType = "ENDURANCE",
        targetMinutesPerRun = 30,
        runsPerWeek = 3,
        preferredDays = listOf(
            DayPreference(1, DaySelectionLevel.AVAILABLE),
            DayPreference(3, DaySelectionLevel.LONG_RUN_BIAS),
            DayPreference(6, DaySelectionLevel.AVAILABLE)
        ),
        tierIndex = 1,
        sharerUserId = "abc-123",
        sharerDisplayName = "Alice"
    )

    @Test
    fun `toJson produces valid JSON with day preferences as objects`() {
        val json = sample.toJson()
        assertTrue(json.has("goalType"))
        assertEquals("ENDURANCE", json.getString("goalType"))
        assertEquals(30, json.getInt("targetMinutesPerRun"))
        assertEquals(3, json.getInt("runsPerWeek"))
        assertEquals(1, json.getInt("tierIndex"))
        assertEquals("abc-123", json.getString("sharerUserId"))
        assertEquals("Alice", json.getString("sharerDisplayName"))

        val days = json.getJSONArray("preferredDays")
        assertEquals(3, days.length())
        assertEquals(1, days.getJSONObject(0).getInt("day"))
        assertEquals("AVAILABLE", days.getJSONObject(0).getString("level"))
        assertEquals("LONG_RUN_BIAS", days.getJSONObject(1).getString("level"))
    }

    @Test
    fun `fromJson round-trips correctly`() {
        val json = sample.toJson()
        val restored = ShareableBootcampConfig.fromJson(json)
        assertEquals(sample, restored)
    }

    @Test
    fun `fromJson handles empty preferredDays`() {
        val empty = sample.copy(preferredDays = emptyList())
        val restored = ShareableBootcampConfig.fromJson(empty.toJson())
        assertEquals(emptyList<DayPreference>(), restored.preferredDays)
    }

    @Test
    fun `toShareable maps enrollment fields correctly`() {
        val enrollment = BootcampEnrollmentEntity(
            id = 1,
            goalType = "ENDURANCE",
            targetMinutesPerRun = 30,
            runsPerWeek = 3,
            preferredDays = listOf(
                DayPreference(1, DaySelectionLevel.AVAILABLE),
                DayPreference(3, DaySelectionLevel.LONG_RUN_BIAS)
            ),
            startDate = System.currentTimeMillis(),
            tierIndex = 2
        )
        val shareable = enrollment.toShareable(userId = "user-1", displayName = "Bob")
        assertEquals("ENDURANCE", shareable.goalType)
        assertEquals(30, shareable.targetMinutesPerRun)
        assertEquals(3, shareable.runsPerWeek)
        assertEquals(2, shareable.tierIndex)
        assertEquals("user-1", shareable.sharerUserId)
        assertEquals("Bob", shareable.sharerDisplayName)
        assertEquals(2, shareable.preferredDays.size)
    }
}
