package com.hrcoach.service.workout

import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.HrSegment
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.domain.model.ZoneStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CoachingEventRouterInfoCuesTest {

    private lateinit var router: CoachingEventRouter
    private val emitted = mutableListOf<Pair<CoachingEvent, String?>>()

    private val distanceConfig = WorkoutConfig(
        mode = WorkoutMode.DISTANCE_PROFILE,
        segments = listOf(
            HrSegment(distanceMeters = 5000f, targetHr = 140)
        ),
        bufferBpm = 5,
        alertDelaySec = 15,
        alertCooldownSec = 30
    )

    private val freeRunConfig = WorkoutConfig(mode = WorkoutMode.FREE_RUN)

    @Before
    fun setUp() {
        router = CoachingEventRouter()
        emitted.clear()
    }

    private fun emit(event: CoachingEvent, text: String?) {
        emitted.add(event to text)
    }

    private fun route(
        config: WorkoutConfig = distanceConfig,
        distanceMeters: Float = 0f,
        elapsedSeconds: Long = 0L,
        zoneStatus: ZoneStatus = ZoneStatus.IN_ZONE,
        nowMs: Long = System.currentTimeMillis()
    ) {
        router.route(
            workoutConfig = config,
            connected = true,
            distanceMeters = distanceMeters,
            elapsedSeconds = elapsedSeconds,
            zoneStatus = zoneStatus,
            adaptiveResult = null,
            guidance = "HOLD THIS PACE",
            nowMs = nowMs,
            emitEvent = ::emit
        )
    }

    // ── KM_SPLIT ────────────────────────────────────────────────

    @Test
    fun `KM_SPLIT fires at each km boundary`() {
        route(distanceMeters = 500f)
        assertTrue(emitted.none { it.first == CoachingEvent.KM_SPLIT })

        route(distanceMeters = 1000f)
        val splits = emitted.filter { it.first == CoachingEvent.KM_SPLIT }
        assertEquals(1, splits.size)
        assertEquals("1", splits[0].second)
    }

    @Test
    fun `KM_SPLIT does not re-fire for same km`() {
        route(distanceMeters = 1100f)
        route(distanceMeters = 1500f)
        val splits = emitted.filter { it.first == CoachingEvent.KM_SPLIT }
        assertEquals(1, splits.size)
    }

    @Test
    fun `KM_SPLIT fires for km 2 after km 1`() {
        route(distanceMeters = 1000f)
        route(distanceMeters = 2000f)
        val splits = emitted.filter { it.first == CoachingEvent.KM_SPLIT }
        assertEquals(2, splits.size)
        assertEquals("1", splits[0].second)
        assertEquals("2", splits[1].second)
    }

    // ── HALFWAY ─────────────────────────────────────────────────

    @Test
    fun `HALFWAY fires at 50% of target distance`() {
        route(distanceMeters = 2000f)
        assertTrue(emitted.none { it.first == CoachingEvent.HALFWAY })

        route(distanceMeters = 2500f)
        assertEquals(1, emitted.count { it.first == CoachingEvent.HALFWAY })
    }

    @Test
    fun `HALFWAY fires only once`() {
        route(distanceMeters = 2500f)
        route(distanceMeters = 3000f)
        assertEquals(1, emitted.count { it.first == CoachingEvent.HALFWAY })
    }

    @Test
    fun `HALFWAY does not fire in FREE_RUN`() {
        route(config = freeRunConfig, distanceMeters = 5000f)
        assertTrue(emitted.none { it.first == CoachingEvent.HALFWAY })
    }

    // ── WORKOUT_COMPLETE ────────────────────────────────────────

    @Test
    fun `WORKOUT_COMPLETE fires at target distance`() {
        route(distanceMeters = 4999f)
        assertTrue(emitted.none { it.first == CoachingEvent.WORKOUT_COMPLETE })

        route(distanceMeters = 5000f)
        assertEquals(1, emitted.count { it.first == CoachingEvent.WORKOUT_COMPLETE })
    }

    @Test
    fun `WORKOUT_COMPLETE fires only once`() {
        route(distanceMeters = 5000f)
        route(distanceMeters = 5500f)
        assertEquals(1, emitted.count { it.first == CoachingEvent.WORKOUT_COMPLETE })
    }

    // ── IN_ZONE_CONFIRM ─────────────────────────────────────────

    @Test
    fun `IN_ZONE_CONFIRM fires after 3 min of silence in zone`() {
        val t0 = 1_000_000L
        // First route sets baseline
        route(zoneStatus = ZoneStatus.IN_ZONE, nowMs = t0)
        assertTrue(emitted.none { it.first == CoachingEvent.IN_ZONE_CONFIRM })

        // 2 minutes: no fire
        route(zoneStatus = ZoneStatus.IN_ZONE, nowMs = t0 + 120_000L)
        assertTrue(emitted.none { it.first == CoachingEvent.IN_ZONE_CONFIRM })

        // 3 minutes: fires
        route(zoneStatus = ZoneStatus.IN_ZONE, nowMs = t0 + 180_000L)
        assertEquals(1, emitted.count { it.first == CoachingEvent.IN_ZONE_CONFIRM })
    }

    @Test
    fun `IN_ZONE_CONFIRM does not fire when out of zone`() {
        val t0 = 1_000_000L
        route(zoneStatus = ZoneStatus.IN_ZONE, nowMs = t0)
        route(zoneStatus = ZoneStatus.ABOVE_ZONE, nowMs = t0 + 200_000L)
        assertTrue(emitted.none { it.first == CoachingEvent.IN_ZONE_CONFIRM })
    }

    @Test
    fun `IN_ZONE_CONFIRM timer resets after a voice cue`() {
        val t0 = 1_000_000L
        route(zoneStatus = ZoneStatus.IN_ZONE, nowMs = t0)

        // At 2 min, a KM_SPLIT fires (resets voice cue time)
        route(distanceMeters = 1000f, zoneStatus = ZoneStatus.IN_ZONE, nowMs = t0 + 120_000L)
        assertTrue(emitted.any { it.first == CoachingEvent.KM_SPLIT })

        // At 4 min (2 min after split): no confirm yet
        route(distanceMeters = 1500f, zoneStatus = ZoneStatus.IN_ZONE, nowMs = t0 + 240_000L)
        assertTrue(emitted.none { it.first == CoachingEvent.IN_ZONE_CONFIRM })

        // At 5 min (3 min after split): fires
        route(distanceMeters = 1800f, zoneStatus = ZoneStatus.IN_ZONE, nowMs = t0 + 300_000L)
        assertEquals(1, emitted.count { it.first == CoachingEvent.IN_ZONE_CONFIRM })
    }

    // ── reset() ─────────────────────────────────────────────────

    @Test
    fun `reset clears all informational cue state`() {
        route(distanceMeters = 2500f) // triggers HALFWAY + KM splits
        router.reset()
        emitted.clear()

        // After reset, HALFWAY can fire again
        route(distanceMeters = 2500f)
        assertEquals(1, emitted.count { it.first == CoachingEvent.HALFWAY })
    }
}
