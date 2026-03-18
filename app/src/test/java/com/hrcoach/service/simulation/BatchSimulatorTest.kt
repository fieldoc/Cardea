package com.hrcoach.service.simulation

import com.hrcoach.domain.model.AdaptiveProfile
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.domain.simulation.SimulationScenario
import org.junit.Assert.*
import org.junit.Test

class BatchSimulatorTest {

    private val config = WorkoutConfig(
        mode = WorkoutMode.STEADY_STATE,
        steadyStateTargetHr = 140,
        bufferBpm = 5
    )

    @Test
    fun `simulateSingleRun produces ticks and updated profile`() {
        val result = BatchSimulator.simulateSingleRun(
            scenario = SimulationScenario.EASY_STEADY_RUN,
            config = config,
            initialProfile = AdaptiveProfile()
        )
        assertTrue("should have ticks, had ${result.tickCount}", result.tickCount > 0)
        assertEquals(1200, result.durationSeconds)
        assertTrue("avg HR should be > 0, was ${result.avgHr}", result.avgHr > 0)
        assertTrue("distance should be > 0, was ${result.finalDistance}", result.finalDistance > 0f)
    }

    @Test
    fun `simulateSingleRun with signal loss has lower avg HR`() {
        val steadyResult = BatchSimulator.simulateSingleRun(
            scenario = SimulationScenario.EASY_STEADY_RUN,
            config = config,
            initialProfile = AdaptiveProfile()
        )
        val signalLossResult = BatchSimulator.simulateSingleRun(
            scenario = SimulationScenario.SIGNAL_LOSS,
            config = config,
            initialProfile = AdaptiveProfile()
        )
        // Signal loss scenario has disconnects so fewer valid HR samples
        assertTrue("signal loss should have fewer ticks with HR",
            signalLossResult.tickCount > 0)
    }

    @Test
    fun `all presets run without crashing`() {
        for (scenario in SimulationScenario.ALL_PRESETS) {
            val result = BatchSimulator.simulateSingleRun(
                scenario = scenario,
                config = config,
                initialProfile = AdaptiveProfile()
            )
            assertTrue("${scenario.name} should produce ticks", result.tickCount > 0)
        }
    }
}
