package com.hrcoach.service.simulation

import android.location.Location
import com.hrcoach.domain.simulation.LocationDataSource
import com.hrcoach.domain.simulation.SimulationScenario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SimulatedLocationSource(
    private val clock: SimulationClock,
    private val scenario: SimulationScenario
) : LocationDataSource {

    private val _distanceMeters = MutableStateFlow(0f)
    override val distanceMeters: StateFlow<Float> = _distanceMeters

    private val _currentLocation = MutableStateFlow<Location?>(null)
    override val currentLocation: StateFlow<Location?> = _currentLocation

    private val _currentSpeed = MutableStateFlow<Float?>(null)
    override val currentSpeed: StateFlow<Float?> = _currentSpeed

    @Volatile
    private var isMoving: Boolean = true
    private var isRunning: Boolean = false
    private var lastUpdateSeconds: Float = 0f

    private val startLat = 40.7128
    private val startLon = -74.0060

    override fun start() {
        isRunning = true
        isMoving = true
        lastUpdateSeconds = 0f
        _distanceMeters.value = 0f
        _currentLocation.value = null
        _currentSpeed.value = null
    }

    override fun stop() {
        isRunning = false
        _currentLocation.value = null
        _currentSpeed.value = null
    }

    override fun setMoving(moving: Boolean) {
        isMoving = moving
    }

    fun updateForTime(elapsedSeconds: Float) {
        if (!isRunning) return

        if (scenario.isGpsDropout(elapsedSeconds)) {
            _currentLocation.value = null
            _currentSpeed.value = null
            lastUpdateSeconds = elapsedSeconds
            return
        }

        val paceMinPerKm = scenario.interpolatePace(elapsedSeconds)
        val speedMs = if (scenario.isStopped(elapsedSeconds)) {
            0f
        } else {
            if (paceMinPerKm > 0f) 1000f / (paceMinPerKm * 60f) else 0f
        }

        _currentSpeed.value = speedMs

        val dt = elapsedSeconds - lastUpdateSeconds
        if (dt > 0f && isMoving && speedMs > 0f) {
            _distanceMeters.value += speedMs * dt
        }
        lastUpdateSeconds = elapsedSeconds

        val totalDist = _distanceMeters.value
        val latOffset = totalDist / 111_111.0
        val loc = Location("simulation").apply {
            latitude = startLat + latOffset
            longitude = startLon
            accuracy = 5f
            time = clock.now()
            speed = speedMs
        }
        _currentLocation.value = loc
    }
}
