package com.hrcoach.service.workout

import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.domain.model.ZoneStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CoachingEventRouterKmTest {

    private lateinit var router: CoachingEventRouter
    private val emitted = mutableListOf<Pair<CoachingEvent, String?>>()
    private val config = WorkoutConfig(mode = WorkoutMode.FREE_RUN)

    @Before fun setUp() { router = CoachingEventRouter(); emitted.clear() }

    private fun route(distanceMeters: Float, nowMs: Long = 1_000_000L) {
        router.route(
            workoutConfig = config,
            connected = true,
            distanceMeters = distanceMeters,
            elapsedSeconds = 0L,
            zoneStatus = ZoneStatus.NO_DATA,
            adaptiveResult = null,
            guidance = "",
            nowMs = nowMs,
            emitEvent = { event, text -> emitted.add(event to text) }
        )
    }

    @Test
    fun `km 51 fires KM_SPLIT with guidance text 51`() {
        route(51_001f)  // just past 51km
        val split = emitted.firstOrNull { it.first == CoachingEvent.KM_SPLIT }
        assertEquals(CoachingEvent.KM_SPLIT, split?.first)
        assertEquals("51", split?.second)
    }

    @Test
    fun `km 100 fires KM_SPLIT with guidance text 100`() {
        repeat(99) { km -> route((km + 1) * 1000f, nowMs = (km + 1) * 1000L) }
        emitted.clear()
        route(100_001f, nowMs = 100_001_000L)
        val split = emitted.firstOrNull { it.first == CoachingEvent.KM_SPLIT }
        assertEquals("100", split?.second)
    }

    @Test
    fun `km 50 still fires normally`() {
        route(50_001f)
        assertTrue(emitted.any { it.first == CoachingEvent.KM_SPLIT })
    }

    @Test
    fun `successive km announcements past 50 each fire separately`() {
        // Cross km 51
        route(51_001f, nowMs = 1_000L)
        val first = emitted.firstOrNull { it.first == CoachingEvent.KM_SPLIT }
        assertEquals("51", first?.second)
        emitted.clear()
        // Cross km 52
        route(52_001f, nowMs = 2_000L)
        val second = emitted.firstOrNull { it.first == CoachingEvent.KM_SPLIT }
        assertEquals("52", second?.second)
    }
}
