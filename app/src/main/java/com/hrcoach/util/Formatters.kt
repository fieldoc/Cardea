package com.hrcoach.util

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
    return String.format(Locale.getDefault(), "%.2f km", distanceMeters / 1000f)
}

fun formatPaceMinPerKm(paceMinPerKm: Float): String {
    if (paceMinPerKm <= 0f || paceMinPerKm > 30f) return "-- /km"
    val totalSec = (paceMinPerKm * 60f).toInt()
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.getDefault(), "%d:%02d /km", min, sec)
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

fun String.asModeLabel(): String =
    split("_").joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { it.uppercase() }
    }
