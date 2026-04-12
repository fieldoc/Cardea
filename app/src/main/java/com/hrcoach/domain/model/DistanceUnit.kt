package com.hrcoach.domain.model

enum class DistanceUnit {
    KM, MI;

    companion object {
        const val METERS_PER_KM = 1000f
        const val METERS_PER_MILE = 1609.344f

        fun fromString(s: String): DistanceUnit =
            if (s.equals("mi", ignoreCase = true)) MI else KM
    }
}
