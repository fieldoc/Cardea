package com.hrcoach.service.audio

import com.hrcoach.domain.model.CoachingEvent

enum class VoiceEventPriority {
    CRITICAL,      // Zone alerts: SPEED_UP, SLOW_DOWN, SIGNAL_LOST
    NORMAL,        // Transitions: RETURN_TO_ZONE, SEGMENT_CHANGE, PREDICTIVE_WARNING, SIGNAL_REGAINED
    INFORMATIONAL; // Non-urgent: HALFWAY, KM_SPLIT, WORKOUT_COMPLETE, IN_ZONE_CONFIRM

    companion object {
        fun of(event: CoachingEvent): VoiceEventPriority = when (event) {
            CoachingEvent.SPEED_UP,
            CoachingEvent.SLOW_DOWN,
            CoachingEvent.SIGNAL_LOST         -> CRITICAL
            CoachingEvent.RETURN_TO_ZONE,
            CoachingEvent.SEGMENT_CHANGE,
            CoachingEvent.PREDICTIVE_WARNING,
            CoachingEvent.SIGNAL_REGAINED     -> NORMAL
            CoachingEvent.HALFWAY,
            CoachingEvent.KM_SPLIT,
            CoachingEvent.WORKOUT_COMPLETE,
            CoachingEvent.IN_ZONE_CONFIRM     -> INFORMATIONAL
        }
    }
}
