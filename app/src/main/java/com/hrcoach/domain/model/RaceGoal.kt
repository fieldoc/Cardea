package com.hrcoach.domain.model

/**
 * The four race-distance goals presented as next-program chips on the Home
 * GraduateHero. Maps 1:1 to a subset of [BootcampGoal] (excluding the
 * evergreen Cardio Health track, which is shown separately as a Tier-2
 * choice row above the chips).
 */
enum class RaceGoal(val shortLabel: String, val bootcampGoal: BootcampGoal) {
    FIVE_K("5K", BootcampGoal.RACE_5K),
    TEN_K("10K", BootcampGoal.RACE_10K),
    HALF("Half", BootcampGoal.HALF_MARATHON),
    FULL("Full", BootcampGoal.MARATHON);
}
