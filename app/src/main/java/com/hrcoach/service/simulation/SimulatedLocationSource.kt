package com.hrcoach.service.simulation

import android.location.Location
import com.hrcoach.domain.simulation.LocationDataSource
import com.hrcoach.domain.simulation.SimulationScenario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Random

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

    private val random = Random()
    private var bearingDeg: Double = 0.0

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

        val jitteredSpeed = if (speedMs > 0f) {
            (speedMs * (1f + (random.nextFloat() - 0.5f) * 0.06f)).coerceAtLeast(0f)
        } else 0f

        _currentSpeed.value = jitteredSpeed

        val dt = elapsedSeconds - lastUpdateSeconds
        if (dt > 0f && isMoving && jitteredSpeed > 0f) {
            _distanceMeters.value += jitteredSpeed * dt
        }
        lastUpdateSeconds = elapsedSeconds

        // Sinusoidal bearing: ±15° over 400m wavelength
        bearingDeg = 15.0 * kotlin.math.sin(_distanceMeters.value / 400.0 * 2.0 * Math.PI)
        val bearingRad = Math.toRadians(bearingDeg)
        val totalDist = _distanceMeters.value.toDouble()
        val latOffset = totalDist * kotlin.math.cos(bearingRad) / 111_111.0
        val lonOffset = totalDist * kotlin.math.sin(bearingRad) / (111_111.0 * kotlin.math.cos(Math.toRadians(startLat)))

        val loc = Location("simulation").apply {
            latitude = startLat + latOffset
            longitude = startLon + lonOffset
            accuracy = 3f + random.nextFloat() * 4f  // 3–7m jitter
            time = clock.now()
            speed = jitteredSpeed
        }
        _currentLocation.value = loc
    }
}
