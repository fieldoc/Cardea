package com.hrcoach.util

import com.hrcoach.domain.model.DistanceUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDuration(startTime: Long, endTime: Long): String {
    val safeEnd = if (endTime > startTime) endTime else startTime
    val totalSeconds = ((safeEnd - startTime).coerceAtLeast(0L) / 1000L).toInt()
    val hours = totalSeconds / 3600
    val minutes = totalSeconds / 60
    val minutesRemainder = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutesRemainder, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

fun formatDistanceKm(distanceMeters: Float): String {
    return formatDistance(distanceMeters, DistanceUnit.KM)
}

fun formatDistance(distanceMeters: Float, unit: DistanceUnit): String {
    val label = if (unit == DistanceUnit.MI) "mi" else "km"
    val divisor = if (unit == DistanceUnit.MI) DistanceUnit.METERS_PER_MILE else DistanceUnit.METERS_PER_KM
    return String.format(Locale.getDefault(), "%.2f %s", distanceMeters / divisor, label)
}

fun formatPaceMinPerKm(paceMinPerKm: Float): String {
    return formatPace(paceMinPerKm, DistanceUnit.KM)
}

fun formatPace(paceMinPerKm: Float, unit: DistanceUnit): String {
    val label = if (unit == DistanceUnit.MI) "mi" else "km"
    val pace = if (unit == DistanceUnit.MI) paceMinPerKm * (DistanceUnit.METERS_PER_MILE / DistanceUnit.METERS_PER_KM) else paceMinPerKm
    if (pace <= 0f || pace > 50f) return "-- /$label"
    val totalSec = (pace * 60f).toInt()
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.getDefault(), "%d:%02d /%s", min, sec, label)
}

fun formatWorkoutDate(timestampMs: Long): String {
    val format = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return format.format(Date(timestampMs))
}

fun formatDurationSeconds(totalSeconds: Long): String {
    val hours = totalSeconds / 3600L
    val minutesRemainder = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutesRemainder, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", totalSeconds / 60L, seconds)
    }
}

fun metersToKm(meters: Float): Float = meters / 1000f

fun metersToUnit(meters: Float, unit: DistanceUnit): Float =
    meters / if (unit == DistanceUnit.MI) DistanceUnit.METERS_PER_MILE else DistanceUnit.METERS_PER_KM

fun distanceUnitLabel(unit: DistanceUnit): String =
    if (unit == DistanceUnit.MI) "mi" else "km"

fun String.asModeLabel(): String =
    split("_").joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { it.uppercase() }
    }
