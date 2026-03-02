package com.hrcoach.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class GpsDistanceTracker(context: Context) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private var lastLocation: Location? = null
    private var isRunning: Boolean = false

    private val _distanceMeters = MutableStateFlow(0f)
    val distanceMeters: StateFlow<Float> = _distanceMeters

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        2_000L
    ).setMinUpdateDistanceMeters(3f).build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            _currentLocation.value = location
            val previous = lastLocation
            if (previous != null) {
                _distanceMeters.value += previous.distanceTo(location)
            }
            lastLocation = location
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning) return
        isRunning = true
        lastLocation = null
        _distanceMeters.value = 0f
        _currentLocation.value = null
        val started = runCatching {
            fusedClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }.isSuccess
        if (!started) {
            isRunning = false
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        runCatching {
            fusedClient.removeLocationUpdates(locationCallback)
        }
    }
}
