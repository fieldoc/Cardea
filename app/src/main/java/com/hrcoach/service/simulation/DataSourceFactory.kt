package com.hrcoach.service.simulation

import android.content.Context
import com.hrcoach.domain.simulation.HrDataSource
import com.hrcoach.domain.simulation.LocationDataSource
import com.hrcoach.domain.simulation.RealClock
import com.hrcoach.domain.simulation.SimulationScenario
import com.hrcoach.domain.simulation.WorkoutClock
import com.hrcoach.service.BleConnectionCoordinator
import com.hrcoach.service.GpsDistanceTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface DataSourceFactory {
    fun createHrSource(): HrDataSource
    fun createLocationSource(): LocationDataSource
    fun getClock(): WorkoutClock
}

@Singleton
class RealDataSourceFactory @Inject constructor(
    private val bleCoordinator: BleConnectionCoordinator,
    @ApplicationContext private val context: Context
) : DataSourceFactory {
    override fun createHrSource(): HrDataSource = bleCoordinator.managerForWorkout()
    override fun createLocationSource(): LocationDataSource = GpsDistanceTracker(context)
    override fun getClock(): WorkoutClock = RealClock()
}

class SimulatedDataSourceFactory(
    private val scenario: SimulationScenario,
    private val clock: SimulationClock
) : DataSourceFactory {
    override fun createHrSource(): HrDataSource = SimulatedHrSource(clock, scenario)
    override fun createLocationSource(): LocationDataSource = SimulatedLocationSource(clock, scenario)
    override fun getClock(): WorkoutClock = clock
}
