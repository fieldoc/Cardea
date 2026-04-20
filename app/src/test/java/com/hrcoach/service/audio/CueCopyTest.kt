package com.hrcoach.service.audio

import com.hrcoach.domain.model.CoachingEvent
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CueCopyTest {

    @Test
    fun every_coaching_event_has_a_copy_entry() {
        CoachingEvent.values().forEach { event ->
            val entry = CueCopy.forEvent(event)
            assertNotNull("No CueCopy entry for $event", entry)
            assertTrue("Blank title for $event", entry.title.isNotBlank())
            assertTrue("Blank subtitle for $event", entry.subtitle.isNotBlank())
        }
    }

    @Test
    fun all_entries_exposed_in_displayOrder() {
        val ordered = CueCopy.displayOrder.map { it }.toSet()
        val all = CoachingEvent.values().toSet()
        assertTrue("displayOrder missing events: ${all - ordered}", ordered.containsAll(all))
    }

    @Test
    fun sections_cover_all_events() {
        val covered = CueCopy.sections.flatMap { it.events }.toSet()
        val all = CoachingEvent.values().toSet()
        assertTrue("Section coverage mismatch. Missing: ${all - covered}", covered == all)
    }
}
