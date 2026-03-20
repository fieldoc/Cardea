package com.hrcoach.domain.coaching

enum class CoachingIcon {
    LIGHTBULB,
    CHART_UP,
    TROPHY,
    WARNING,
    HEART
}

data class CoachingInsight(
    val title: String,
    val subtitle: String,
    val icon: CoachingIcon = CoachingIcon.LIGHTBULB
)
