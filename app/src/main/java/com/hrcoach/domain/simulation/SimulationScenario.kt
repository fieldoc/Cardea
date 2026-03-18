package com.hrcoach.domain.simulation

data class SimulationScenario(
    val name: String,
    val durationSeconds: Int,
    val hrProfile: List<HrDataPoint>,
    val paceProfile: List<PaceDataPoint>,
    val events: List<SimEvent> = emptyList()
) {
    fun interpolateHr(timeSeconds: Float): Int =
        interpolate(hrProfile, timeSeconds, default = 0f, time = { it.timeSeconds.toFloat() }, value = { it.hr.toFloat() }).toInt()

    fun interpolatePace(timeSeconds: Float): Float =
        interpolate(paceProfile, timeSeconds, default = 6.0f, time = { it.timeSeconds.toFloat() }, value = { it.paceMinPerKm })

    fun isSignalLost(timeSeconds: Float): Boolean = hasActiveEvent<SimEvent.SignalLoss>(timeSeconds)

    fun isGpsDropout(timeSeconds: Float): Boolean = hasActiveEvent<SimEvent.GpsDropout>(timeSeconds)

    fun isStopped(timeSeconds: Float): Boolean = hasActiveEvent<SimEvent.Stop>(timeSeconds)

    private inline fun <reified T : SimEvent> hasActiveEvent(timeSeconds: Float): Boolean =
        events.any { it is T && timeSeconds >= it.atSeconds && timeSeconds < it.atSeconds + it.durationSeconds }

    private fun <T> interpolate(
        points: List<T>,
        timeSeconds: Float,
        default: Float,
        time: (T) -> Float,
        value: (T) -> Float
    ): Float {
        if (points.isEmpty()) return default
        if (timeSeconds <= time(points.first())) return value(points.first())
        if (timeSeconds >= time(points.last())) return value(points.last())
        val idx = points.indexOfLast { time(it) <= timeSeconds }
        val a = points[idx]
        val b = points.getOrNull(idx + 1) ?: return value(a)
        val t = (timeSeconds - time(a)) / (time(b) - time(a))
        return value(a) + t * (value(b) - value(a))
    }

    companion object {
        val EASY_STEADY_RUN = SimulationScenario(
            name = "Easy Steady Run",
            durationSeconds = 1200,
            hrProfile = listOf(
                HrDataPoint(0, 70),
                HrDataPoint(180, 140),
                HrDataPoint(1200, 142)
            ),
            paceProfile = listOf(PaceDataPoint(0, 6.0f))
        )

        val ZONE_DRIFT = SimulationScenario(
            name = "Zone Drift",
            durationSeconds = 1200,
            hrProfile = listOf(
                HrDataPoint(0, 70),
                HrDataPoint(120, 140),
                HrDataPoint(300, 155),
                HrDataPoint(420, 138),
                HrDataPoint(600, 158),
                HrDataPoint(720, 140),
                HrDataPoint(900, 160),
                HrDataPoint(1020, 142),
                HrDataPoint(1200, 140)
            ),
            paceProfile = listOf(PaceDataPoint(0, 5.5f))
        )

        val INTERVAL_SESSION = SimulationScenario(
            name = "Interval Session",
            durationSeconds = 1500,
            hrProfile = listOf(
                HrDataPoint(0, 70),
                HrDataPoint(120, 130),
                HrDataPoint(180, 170), HrDataPoint(300, 172),
                HrDataPoint(360, 120), HrDataPoint(420, 118),
                HrDataPoint(480, 170), HrDataPoint(600, 174),
                HrDataPoint(660, 122), HrDataPoint(720, 118),
                HrDataPoint(780, 172), HrDataPoint(900, 175),
                HrDataPoint(960, 120), HrDataPoint(1020, 116),
                HrDataPoint(1080, 170), HrDataPoint(1200, 176),
                HrDataPoint(1260, 125),
                HrDataPoint(1500, 100)
            ),
            paceProfile = listOf(
                PaceDataPoint(0, 6.0f),
                PaceDataPoint(180, 4.5f), PaceDataPoint(300, 4.5f),
                PaceDataPoint(360, 6.5f), PaceDataPoint(420, 6.5f),
                PaceDataPoint(480, 4.5f), PaceDataPoint(600, 4.5f),
                PaceDataPoint(660, 6.5f), PaceDataPoint(720, 6.5f),
                PaceDataPoint(780, 4.5f), PaceDataPoint(900, 4.5f),
                PaceDataPoint(960, 6.5f), PaceDataPoint(1020, 6.5f),
                PaceDataPoint(1080, 4.5f), PaceDataPoint(1200, 4.5f),
                PaceDataPoint(1260, 6.0f), PaceDataPoint(1500, 7.0f)
            )
        )

        val SIGNAL_LOSS = SimulationScenario(
            name = "Signal Loss",
            durationSeconds = 900,
            hrProfile = listOf(
                HrDataPoint(0, 70),
                HrDataPoint(120, 140),
                HrDataPoint(900, 145)
            ),
            paceProfile = listOf(PaceDataPoint(0, 5.5f)),
            events = listOf(
                SimEvent.SignalLoss(atSeconds = 240, durationSeconds = 30),
                SimEvent.SignalLoss(atSeconds = 540, durationSeconds = 60)
            )
        )

        val GPS_DROPOUT = SimulationScenario(
            name = "GPS Dropout",
            durationSeconds = 900,
            hrProfile = listOf(
                HrDataPoint(0, 70),
                HrDataPoint(120, 140),
                HrDataPoint(900, 145)
            ),
            paceProfile = listOf(PaceDataPoint(0, 5.5f)),
            events = listOf(
                SimEvent.GpsDropout(atSeconds = 300, durationSeconds = 45)
            )
        )

        val ALL_PRESETS = listOf(EASY_STEADY_RUN, ZONE_DRIFT, INTERVAL_SESSION, SIGNAL_LOSS, GPS_DROPOUT)
    }
}

data class HrDataPoint(
    val timeSeconds: Int,
    val hr: Int
)

data class PaceDataPoint(
    val timeSeconds: Int,
    val paceMinPerKm: Float
)

sealed class SimEvent(val atSeconds: Int, val durationSeconds: Int) {
    class SignalLoss(atSeconds: Int, durationSeconds: Int) : SimEvent(atSeconds, durationSeconds)
    class GpsDropout(atSeconds: Int, durationSeconds: Int) : SimEvent(atSeconds, durationSeconds)
    class Stop(atSeconds: Int, durationSeconds: Int) : SimEvent(atSeconds, durationSeconds)
}
