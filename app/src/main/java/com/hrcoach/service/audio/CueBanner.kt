package com.hrcoach.service.audio

import com.hrcoach.domain.model.CoachingEvent

enum class CueBannerKind { ALERT, GUIDANCE, MILESTONE, INFO }

data class CueBanner(
    val event: CoachingEvent,
    val title: String,
    val subtitle: String,
    val kind: CueBannerKind,
    val firedAtMs: Long
)
