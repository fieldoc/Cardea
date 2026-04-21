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

    // SplitMix64 finalizer constants — Stafford, "Better Bit Mixing" (2011)
    // https://zimbry.blogspot.com/2011/09/better-bit-mixing-improving-on.html
    // Used because plain Long.hashCode() collapses 64→32 bits too aggressively
    // for short seedKeys, producing visible collisions when only the low bits
    // differ across consecutive days.
    private val GOLDEN_GAMMA: Long = 0x9E3779B97F4A7C15uL.toLong()
    private val SM64_MIX1: Long = 0xBF58476D1CE4E5B9uL.toLong()
    private val SM64_MIX2: Long = 0x94D049BB133111EBuL.toLong()

    /**
     * @param poolSize number of variants in the pool; must be > 0.
     * @param seedKey distinguishes surfaces/zones/branches on the same day.
     * @param dayEpoch days since epoch (e.g. `LocalDate.toEpochDay()`).
     * @return index in `0 until poolSize`.
     */
    fun selectIndex(poolSize: Int, seedKey: String, dayEpoch: Long): Int {
        require(poolSize > 0) { "poolSize must be > 0" }
        var z = (seedKey.hashCode().toLong() * 1_000_003L) xor (dayEpoch * GOLDEN_GAMMA)
        z = (z xor (z ushr 30)) * SM64_MIX1
        z = (z xor (z ushr 27)) * SM64_MIX2
        z = z xor (z ushr 31)
        val nonNeg = (z and Int.MAX_VALUE.toLong()).toInt()
        return nonNeg % poolSize
    }
}
