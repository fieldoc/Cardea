package com.hrcoach.domain.model

enum class TrainingPhase(val weeksRange: IntRange) {
    BASE(3..6),
    BUILD(4..6),
    PEAK(2..3),
    TAPER(1..2)
}

enum class BootcampGoal(
    val tier: Int,
    val suggestedMinMinutes: Int,
    val warnBelowMinutes: Int,
    val neverPrescribeBelowMinutes: Int,
    val phaseArc: List<TrainingPhase>
) {
    CARDIO_HEALTH(
        tier = 1,
        suggestedMinMinutes = 20,
        warnBelowMinutes = 15,
        neverPrescribeBelowMinutes = 10,
        phaseArc = listOf(TrainingPhase.BASE, TrainingPhase.BUILD)
    ),
    RACE_5K_10K(
        tier = 2,
        suggestedMinMinutes = 25,
        warnBelowMinutes = 20,
        neverPrescribeBelowMinutes = 15,
        phaseArc = listOf(TrainingPhase.BASE, TrainingPhase.BUILD, TrainingPhase.PEAK, TrainingPhase.TAPER)
    ),
    HALF_MARATHON(
        tier = 3,
        suggestedMinMinutes = 30,
        warnBelowMinutes = 25,
        neverPrescribeBelowMinutes = 20,
        phaseArc = listOf(TrainingPhase.BASE, TrainingPhase.BUILD, TrainingPhase.PEAK, TrainingPhase.TAPER)
    ),
    MARATHON(
        tier = 4,
        suggestedMinMinutes = 45,
        warnBelowMinutes = 30,
        neverPrescribeBelowMinutes = 20,
        phaseArc = listOf(TrainingPhase.BASE, TrainingPhase.BUILD, TrainingPhase.PEAK, TrainingPhase.TAPER)
    )
}
