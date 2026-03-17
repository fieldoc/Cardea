package com.hrcoach.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionGate {

    fun requiredRuntimePermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }

        return permissions
    }

    fun missingRuntimePermissions(context: Context): List<String> {
        return requiredRuntimePermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasAllRuntimePermissions(context: Context): Boolean {
        return missingRuntimePermissions(context).isEmpty()
    }

    /** Permissions required for workout functionality (BLE + Location). */
    fun workoutPermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        }
        return permissions
    }

    /** Has all permissions needed for a workout (BLE + Location). */
    fun hasWorkoutPermissions(context: Context): Boolean {
        return workoutPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** Human-readable description for a denied permission. */
    fun describePermission(permission: String): String = when (permission) {
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION -> "Location (for GPS tracking)"
        Manifest.permission.BLUETOOTH_SCAN -> "Bluetooth Scan (for HR monitor)"
        Manifest.permission.BLUETOOTH_CONNECT -> "Bluetooth Connect (for HR monitor)"
        Manifest.permission.POST_NOTIFICATIONS -> "Notifications (for workout alerts)"
        else -> permission.substringAfterLast('.')
    }
}
