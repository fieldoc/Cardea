package com.hrcoach.domain.bootcamp

import com.hrcoach.data.db.BootcampEnrollmentEntity
import org.junit.Assert.*
import org.junit.Test

class DayPreferenceTest {
    @Test fun blackout_serializes_and_roundtrips() {
        val prefs = listOf(
            DayPreference(1, DaySelectionLevel.AVAILABLE),
            DayPreference(3, DaySelectionLevel.LONG_RUN_BIAS),
            DayPreference(5, DaySelectionLevel.BLACKOUT)
        )
        val encoded = BootcampEnrollmentEntity.serializeDayPreferences(prefs)
        val decoded = BootcampEnrollmentEntity.parseDayPreferences(encoded)
        assertEquals(prefs, decoded)
    }

    @Test fun blackout_not_in_tap_cycle() {
        assertEquals(DaySelectionLevel.AVAILABLE, DaySelectionLevel.NONE.next())
        assertEquals(DaySelectionLevel.LONG_RUN_BIAS, DaySelectionLevel.AVAILABLE.next())
        assertEquals(DaySelectionLevel.NONE, DaySelectionLevel.LONG_RUN_BIAS.next())
    }
}
