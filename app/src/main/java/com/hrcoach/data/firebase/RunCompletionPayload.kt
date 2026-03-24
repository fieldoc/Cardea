package com.hrcoach.data.firebase

data class RunCompletionPayload(
    val userId: String,
    val timestamp: Long,
    val distanceMeters: Double,
    val routePolyline: String,
    val streakCount: Int,
    val programPhase: String?,
    val sessionLabel: String?,
    val wasScheduled: Boolean,
    val originalScheduledWeekDay: Int?,
    val weekDay: Int
) {
    fun toMap(): Map<String, Any> = buildMap {
        put("userId", userId)
        put("timestamp", timestamp)
        put("distanceMeters", distanceMeters)
        put("routePolyline", routePolyline)
        put("streakCount", streakCount)
        programPhase?.let { put("programPhase", it) }
        sessionLabel?.let { put("sessionLabel", it) }
        put("wasScheduled", wasScheduled)
        originalScheduledWeekDay?.let { put("originalScheduledWeekDay", it) }
        put("weekDay", weekDay)
    }
}
