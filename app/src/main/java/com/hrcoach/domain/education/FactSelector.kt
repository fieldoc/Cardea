package com.hrcoach.domain.education

/**
 * Deterministic variant selector for rotating educational content pools.
 *
 * Same `(poolSize, seedKey, dayEpoch)` triple always returns the same index, so
 * facts are stable within a day but vary across days and across distinct seeds
 * (zones, surfaces, branches) on the same day.
 *
 * Pure Kotlin — no Android dependencies. Caller supplies `dayEpoch` (typically
 * `LocalDate.now().toEpochDay()`) so the time dependency is explicit and testable.
 */
object FactSelector {

    /**
     * @param poolSize number of variants in the pool; must be > 0.
     * @param seedKey distinguishes surfaces/zones/branches on the same day.
     * @param dayEpoch days since epoch (e.g. `LocalDate.toEpochDay()`).
     * @return index in `0 until poolSize`.
     */
    fun selectIndex(poolSize: Int, seedKey: String, dayEpoch: Long): Int {
        require(poolSize > 0) { "poolSize must be > 0" }
        // Mix seedKey hash with day-scaled golden ratio, then run a SplitMix64-style
        // finalizer to fully avalanche the bits. Plain `Long.hashCode()` collapses
        // 64 bits to 32 too aggressively for short seedKeys, producing collisions
        // when only the low bits differ.
        var z = (seedKey.hashCode().toLong() * 1_000_003L) xor (dayEpoch * 0x9E3779B97F4A7C15uL.toLong())
        z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5B9uL.toLong()
        z = (z xor (z ushr 27)) * 0x94D049BB133111EBuL.toLong()
        z = z xor (z ushr 31)
        val nonNeg = (z and Int.MAX_VALUE.toLong()).toInt()
        return nonNeg % poolSize
    }
}
