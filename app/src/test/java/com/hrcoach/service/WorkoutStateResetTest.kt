package com.hrcoach.service

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WorkoutStateResetTest {

    @After
    fun tearDown() {
        WorkoutState.set(WorkoutSnapshot())
    }

    @Test
    fun resetPreservesPendingBootcampSessionId() {
        WorkoutState.set(
            WorkoutSnapshot(
                isRunning = true,
                pendingBootcampSessionId = 42L,
                completedWorkoutId = null
            )
        )

        WorkoutState.reset()

        assertEquals(42L, WorkoutState.snapshot.value.pendingBootcampSessionId)
    }

    @Test
    fun resetPreservesCompletedWorkoutId() {
        WorkoutState.set(
            WorkoutSnapshot(
                isRunning = true,
                completedWorkoutId = 99L
            )
        )

        WorkoutState.reset()

        assertEquals(99L, WorkoutState.snapshot.value.completedWorkoutId)
    }

    @Test
    fun resetClearsIsRunning() {
        WorkoutState.set(WorkoutSnapshot(isRunning = true))

        WorkoutState.reset()

        assertFalse(WorkoutState.snapshot.value.isRunning)
    }
}
