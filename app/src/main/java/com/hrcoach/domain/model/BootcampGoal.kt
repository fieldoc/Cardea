package com.hrcoach.domain.model

enum class TrainingPhase(val weeksRange: IntRange) {
    BASE(3..6),
    BUILD(4..6),
    PEAK(2..3),
    TAPER(1..2);

    val displayName: String get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

enum class BootcampGoal(
    val suggestedMinMinutes: Int,
    val warnBelowMinutes: Int,
    val neverPrescribeBelowMinutes: Int,
    val minLongRunMinutes: Int,
    val maxLongRunMinutes: Int,
    val phaseArc: List<TrainingPhase>
) {
    CARDIO_HEALTH(
        suggestedMinMinutes = 20,
        warnBelowMinutes = 15,
        neverPrescribeBelowMinutes = 10,
        minLongRunMinutes = 20,
        maxLongRunMinutes = 60,
        phaseArc = listOf(TrainingPhase.BASE, TrainingPhase.BUILD)
    ),
    RACE_5K(
        suggestedMinMinutes = 25,
        warnBelowMinutes = 20,
        neverPrescribeBelowMinutes = 15,
        minLongRunMinutes = 30,
        maxLongRunMinutes = 60,
        phaseArc = listOf(TrainingPhase.BASE, TrainingPhase.BUILD, TrainingPhase.PEAK, TrainingPhase.TAPER)
    ),
    RACE_10K(
        suggestedMinMinutes = 30,
        warnBelowMinutes = 20,
        neverPrescribeBelowMinutes = 15,
        minLongRunMinutes = 40,
        maxLongRunMinutes = 75,
        phaseArc = listOf(TrainingPhase.BASE, TrainingPhase.BUILD, TrainingPhase.PEAK, TrainingPhase.TAPER)
    ),
    HALF_MARATHON(
        suggestedMinMinutes = 30,
        warnBelowMinutes = 25,
        neverPrescribeBelowMinutes = 20,
        minLongRunMinutes = 60,
        maxLongRunMinutes = 120,
        phaseArc = listOf(TrainingPhase.BASE, TrainingPhase.BUILD, TrainingPhase.PEAK, TrainingPhase.TAPER)
    ),
    MARATHON(
        suggestedMinMinutes = 45,
        warnBelowMinutes = 30,
        neverPrescribeBelowMinutes = 20,
        minLongRunMinutes = 90,
        maxLongRunMinutes = 150,
        phaseArc = listOf(TrainingPhase.BASE, TrainingPhase.BUILD, TrainingPhase.PEAK, TrainingPhase.TAPER)
    )
}
