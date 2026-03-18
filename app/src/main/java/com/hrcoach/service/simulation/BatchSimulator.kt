package com.hrcoach.service.simulation

import com.hrcoach.data.repository.AdaptiveProfileRepository
import com.hrcoach.domain.engine.AdaptivePaceController
import com.hrcoach.domain.engine.ZoneEngine
import com.hrcoach.domain.model.AdaptiveProfile
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.ZoneStatus
import com.hrcoach.domain.simulation.SimulationScenario
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

data class SingleRunResult(
    val tickCount: Int,
    val durationSeconds: Int,
    val updatedProfile: AdaptiveProfile,
    val avgHr: Int,
    val finalDistance: Float
)

data class BatchResult(
    val runs: List<SingleRunResult>,
    val finalProfile: AdaptiveProfile
)

class BatchSimulator @Inject constructor(
    private val adaptiveProfileRepository: AdaptiveProfileRepository
) {
    companion object {
        private const val TICK_INTERVAL_MS = 2_000L

        /** Pure function: simulate one run without DB access. For unit testing. */
        fun simulateSingleRun(
            scenario: SimulationScenario,
            config: WorkoutConfig,
            initialProfile: AdaptiveProfile
        ): SingleRunResult {
            val clock = SimulationClock(MutableStateFlow(1f))
            val hrSource = SimulatedHrSource(scenario)
            val locSource = SimulatedLocationSource(clock, scenario)

            val zoneEngine = ZoneEngine(config)
            val adaptive = AdaptivePaceController(config, initialProfile)

            locSource.start()
            val startMs = clock.now()
            var tickCount = 0
            var hrSum = 0L
            var hrCount = 0

            val totalTicks = (scenario.durationSeconds * 1000L) / TICK_INTERVAL_MS

            for (i in 0 until totalTicks) {
                clock.advanceBy(TICK_INTERVAL_MS)
                val elapsedSec = (clock.now() - startMs) / 1000f

                hrSource.updateForTime(elapsedSec)
                locSource.updateForTime(elapsedSec)

                val hr = hrSource.heartRate.value
                val connected = hrSource.isConnected.value
                val target = if (config.isTimeBased()) {
                    config.targetHrAtElapsedSeconds(elapsedSec.toLong())
                } else {
                    config.targetHrAtDistance(locSource.distanceMeters.value)
                }

                val zoneStatus = if (!connected || hr <= 0 || target == null || target == 0) {
                    ZoneStatus.NO_DATA
                } else {
                    zoneEngine.evaluate(hr, target)
                }

                adaptive.evaluateTick(
                    nowMs = clock.now(),
                    hr = hr,
                    connected = connected,
                    targetHr = target,
                    distanceMeters = locSource.distanceMeters.value,
                    actualZone = zoneStatus
                )

                if (connected && hr > 0) {
                    hrSum += hr
                    hrCount++
                }
                tickCount++
            }

            locSource.stop()
            val session = adaptive.finishSession(workoutId = 0, endedAtMs = clock.now())

            return SingleRunResult(
                tickCount = tickCount,
                durationSeconds = scenario.durationSeconds,
                updatedProfile = session?.updatedProfile ?: initialProfile,
                avgHr = if (hrCount > 0) (hrSum / hrCount).toInt() else 0,
                finalDistance = locSource.distanceMeters.value
            )
        }
    }

    suspend fun runBatch(
        scenarios: List<SimulationScenario>,
        config: WorkoutConfig,
        onProgress: (completed: Int, total: Int) -> Unit
    ): BatchResult {
        var profile = adaptiveProfileRepository.getProfile()
        val results = mutableListOf<SingleRunResult>()

        for ((index, scenario) in scenarios.withIndex()) {
            val result = simulateSingleRun(scenario, config, profile)
            profile = result.updatedProfile
            adaptiveProfileRepository.saveProfile(profile)
            results.add(result)
            onProgress(index + 1, scenarios.size)
        }

        return BatchResult(runs = results, finalProfile = profile)
    }
}
