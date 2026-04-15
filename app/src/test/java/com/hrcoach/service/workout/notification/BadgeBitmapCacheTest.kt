package com.hrcoach.service.workout.notification

import com.hrcoach.domain.model.ZoneStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Uses a fake "bitmap" (a simple wrapper object) so the test runs on plain JUnit without Android.
 * A wrapper class (not Int) is used so assertSame / assertNotSame check object identity reliably —
 * raw Int values above 127 are not pooled by the JVM and would give false failures.
 */
class BadgeBitmapCacheTest {

    /** Thin wrapper that gives us stable reference identity without Android. */
    private data class FakeBitmap(val value: Int)

    private class FakeRenderer : (Int, ZoneStatus, Boolean) -> FakeBitmap {
        var calls = 0
            private set
        override fun invoke(hr: Int, zone: ZoneStatus, paused: Boolean): FakeBitmap {
            calls++
            return FakeBitmap(hr * 1000 + zone.ordinal * 10 + (if (paused) 1 else 0))
        }
    }

    private fun cache(renderer: FakeRenderer) =
        BadgeBitmapCache<FakeBitmap>(maxEntries = 4) { hr, zone, paused ->
            renderer(hr, zone, paused)
        }

    @Test fun `first lookup renders and returns`() {
        val renderer = FakeRenderer()
        val cache = cache(renderer)
        val bmp = cache.get(152, ZoneStatus.IN_ZONE, paused = false)
        assertEquals(1, renderer.calls)
        assertEquals(FakeBitmap(152 * 1000 + ZoneStatus.IN_ZONE.ordinal * 10), bmp)
    }

    @Test fun `identical lookup returns cached bitmap without re-rendering`() {
        val renderer = FakeRenderer()
        val cache = cache(renderer)
        val a = cache.get(152, ZoneStatus.IN_ZONE, paused = false)
        val b = cache.get(152, ZoneStatus.IN_ZONE, paused = false)
        assertEquals(1, renderer.calls)
        assertSame(a, b)
    }

    @Test fun `different hr re-renders`() {
        val renderer = FakeRenderer()
        val cache = cache(renderer)
        val a = cache.get(152, ZoneStatus.IN_ZONE, paused = false)
        val b = cache.get(153, ZoneStatus.IN_ZONE, paused = false)
        assertEquals(2, renderer.calls)
        assertNotSame(a, b)
    }

    @Test fun `different zone re-renders`() {
        val renderer = FakeRenderer()
        val cache = cache(renderer)
        cache.get(152, ZoneStatus.IN_ZONE, paused = false)
        cache.get(152, ZoneStatus.ABOVE_ZONE, paused = false)
        assertEquals(2, renderer.calls)
    }

    @Test fun `paused and unpaused are different cache entries`() {
        val renderer = FakeRenderer()
        val cache = cache(renderer)
        cache.get(152, ZoneStatus.IN_ZONE, paused = false)
        cache.get(152, ZoneStatus.IN_ZONE, paused = true)
        assertEquals(2, renderer.calls)
    }

    @Test fun `LRU evicts oldest entry when over capacity`() {
        val renderer = FakeRenderer()
        val cache = cache(renderer) // max 4
        cache.get(150, ZoneStatus.IN_ZONE, paused = false)
        cache.get(151, ZoneStatus.IN_ZONE, paused = false)
        cache.get(152, ZoneStatus.IN_ZONE, paused = false)
        cache.get(153, ZoneStatus.IN_ZONE, paused = false)
        // All four render calls
        assertEquals(4, renderer.calls)
        // This should evict 150
        cache.get(154, ZoneStatus.IN_ZONE, paused = false)
        assertEquals(5, renderer.calls)
        // Re-fetching 150 must re-render
        cache.get(150, ZoneStatus.IN_ZONE, paused = false)
        assertEquals(6, renderer.calls)
        // But 154 is still cached
        cache.get(154, ZoneStatus.IN_ZONE, paused = false)
        assertEquals(6, renderer.calls)
    }

    @Test fun `clear empties the cache`() {
        val renderer = FakeRenderer()
        val cache = cache(renderer)
        cache.get(152, ZoneStatus.IN_ZONE, paused = false)
        cache.clear()
        cache.get(152, ZoneStatus.IN_ZONE, paused = false)
        assertEquals(2, renderer.calls)
    }
}
