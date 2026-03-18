package com.hrcoach.domain.simulation

import android.location.Location
import kotlinx.coroutines.flow.StateFlow

interface LocationDataSource {
    val distanceMeters: StateFlow<Float>
    val currentLocation: StateFlow<Location?>
    val currentSpeed: StateFlow<Float?>
    fun start()
    fun stop()
    fun setMoving(moving: Boolean)
}
