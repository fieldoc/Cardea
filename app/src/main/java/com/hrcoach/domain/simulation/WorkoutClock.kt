package com.hrcoach.domain.simulation

interface WorkoutClock {
    fun now(): Long
}

class RealClock : WorkoutClock {
    override fun now(): Long = System.currentTimeMillis()
}
