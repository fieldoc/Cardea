package com.hrcoach.service.workout.notification

import com.hrcoach.domain.model.ZoneStatus

/**
 * LRU cache keyed by (currentHr, zoneStatus, paused) → T.
 *
 * Generic in T so the production type uses T = android.graphics.Bitmap
 * but tests can use T = Int (or any simple type) without needing Android.
 */
class BadgeBitmapCache<T>(
    private val maxEntries: Int = 16,
    private val factory: (Int, ZoneStatus, Boolean) -> T,
) {
    private data class Key(val hr: Int, val zone: ZoneStatus, val paused: Boolean)

    // LinkedHashMap with accessOrder = true gives us LRU semantics on read.
    private val entries = object : LinkedHashMap<Key, T>(
        /* initialCapacity = */ maxEntries + 1,
        /* loadFactor     = */ 0.75f,
        /* accessOrder    = */ true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, T>): Boolean =
            size > maxEntries
    }

    fun get(currentHr: Int, zoneStatus: ZoneStatus, paused: Boolean): T {
        val key = Key(currentHr, zoneStatus, paused)
        synchronized(entries) {
            entries[key]?.let { return it }
            val created = factory(currentHr, zoneStatus, paused)
            entries[key] = created
            return created
        }
    }

    fun clear() {
        synchronized(entries) {
            entries.clear()
        }
    }
}
