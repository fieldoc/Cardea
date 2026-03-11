package com.hrcoach.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.HandlerThread
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Minimum acceptable horizontal accuracy (metres). Fixes worse than this are discarded. */
private const val MIN_ACCURACY_METERS = 20f

class GpsDistanceTracker(context: Context) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private var lastLocation: Location? = null
    private var isRunning: Boolean = false

    /**
     * When false, location fixes still update [lastLocation] and emit [currentLocation],
     * but do NOT accumulate [distanceMeters]. Prevents distance inflation during auto-pause.
     * Always update lastLocation even when paused so there is no distance spike on resume.
     */
    @Volatile
    private var isMoving: Boolean = true

    private val _distanceMeters = MutableStateFlow(0f)
    val distanceMeters: StateFlow<Float> = _distanceMeters

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation

    private val _currentSpeed = MutableStateFlow<Float?>(null)
    val currentSpeed: StateFlow<Float?> = _currentSpeed

    /** Background thread that receives location callbacks off the main thread. */
    private var locationThread: HandlerThread? = null

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        2_000L
    ).setMinUpdateDistanceMeters(3f).build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            // Discard fixes with poor horizontal accuracy to prevent GPS drift accumulation.
            if (location.accuracy > MIN_ACCURACY_METERS) return
            _currentLocation.value = location
            _currentSpeed.value = if (location.hasSpeed()) location.speed else null
            val previous = lastLocation
            // Always update lastLocation so there is no distance spike on auto-pause resume.
            lastLocation = location
            if (isMoving && previous != null) {
                _distanceMeters.value += previous.distanceTo(location)
            }
        }
    }

    /** Call with false when auto-paused; true when moving again. */
    fun setMoving(moving: Boolean) {
        isMoving = moving
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning) return
        isRunning = true
        isMoving = true
        lastLocation = null
        _distanceMeters.value = 0f
        _currentLocation.value = null
        _currentSpeed.value = null

        // Run location callbacks on a dedicated background thread, not the main looper.
        val thread = HandlerThread("gps-distance-tracker").also {
            it.start()
            locationThread = it
        }

        val started = runCatching {
            fusedClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                thread.looper
            )
        }.isSuccess
        if (!started) {
            isRunning = false
            thread.quitSafely()
            locationThread = null
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        runCatching {
            fusedClient.removeLocationUpdates(locationCallback)
        }
        locationThread?.quitSafely()
        locationThread = null
    }
}
