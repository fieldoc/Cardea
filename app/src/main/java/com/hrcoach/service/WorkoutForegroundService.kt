package com.hrcoach.service

import android.content.Intent
import android.location.Location
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.data.repository.AdaptiveProfileRepository
import com.hrcoach.data.repository.AudioSettingsRepository
import com.hrcoach.data.repository.WorkoutMetricsRepository
import com.hrcoach.data.repository.WorkoutRepository
import com.hrcoach.domain.engine.AdaptivePaceController
import com.hrcoach.domain.engine.EnvironmentFlagDetector
import com.hrcoach.domain.engine.FitnessLoadCalculator
import com.hrcoach.domain.engine.FitnessSignalEvaluator
import com.hrcoach.domain.engine.HrArtifactDetector
import com.hrcoach.domain.engine.HrCalibrator
import com.hrcoach.domain.engine.MetricsCalculator
import com.hrcoach.domain.engine.ZoneEngine
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.domain.model.ZoneStatus
import com.hrcoach.service.audio.CoachingAudioManager
import com.hrcoach.service.workout.AlertPolicy
import com.hrcoach.service.workout.CoachingEventRouter
import com.hrcoach.service.workout.TrackPointRecorder
import com.hrcoach.service.workout.WorkoutNotificationHelper
import com.hrcoach.util.JsonCodec
import com.hrcoach.util.PermissionGate
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WorkoutForegroundService : LifecycleService() {

    companion object {
        const val ACTION_START = "com.hrcoach.ACTION_START"
        const val ACTION_STOP = "com.hrcoach.ACTION_STOP"
        const val ACTION_PAUSE = "com.hrcoach.ACTION_PAUSE"
        const val ACTION_RESUME = "com.hrcoach.ACTION_RESUME"
        const val ACTION_RESCAN_BLE = "com.hrcoach.ACTION_RESCAN_BLE"
        const val EXTRA_CONFIG_JSON = "config_json"
        const val EXTRA_DEVICE_ADDRESS = "device_address"

        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "workout_channel"
        private const val TRACK_POINT_INTERVAL_MS = 5_000L
    }

    @Inject
    lateinit var repository: WorkoutRepository

    @Inject
    lateinit var workoutMetricsRepository: WorkoutMetricsRepository

    @Inject
    lateinit var adaptiveProfileRepository: AdaptiveProfileRepository

    @Inject
    lateinit var audioSettingsRepository: AudioSettingsRepository

    @Inject
    lateinit var bleCoordinator: BleConnectionCoordinator

    private val gson = JsonCodec.gson
    private lateinit var notificationHelper: WorkoutNotificationHelper

    private var gpsTracker: GpsDistanceTracker? = null
    private var coachingAudioManager: CoachingAudioManager? = null
    private var zoneEngine: ZoneEngine? = null
    private var adaptiveController: AdaptivePaceController? = null

    private val alertPolicy = AlertPolicy()
    private val coachingEventRouter = CoachingEventRouter()
    private val trackPointRecorder = TrackPointRecorder(TRACK_POINT_INTERVAL_MS)

    private var observationJob: Job? = null
    private var stopJob: Job? = null
    private var startupJob: Job? = null

    private var workoutId: Long = 0L
    private var workoutStartMs: Long = 0L
    private var isStopping: Boolean = false
    private var latestTick: WorkoutTick? = null

    private var hrSampleSum: Long = 0L
    private var hrSampleCount: Int = 0

    private val hrSampleBuffer = ArrayDeque<Int>()      // rolling 120-sample window for artifact detection
    private val hrSessionSamples = mutableListOf<Int>() // full session for hrMax detection
    private var cadenceLockSuspected: Boolean = false

    override fun onCreate() {
        super.onCreate()
        notificationHelper = WorkoutNotificationHelper(this, CHANNEL_ID, NOTIFICATION_ID)
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (WorkoutState.snapshot.value.isRunning) return START_NOT_STICKY
                val configJson = intent.getStringExtra(EXTRA_CONFIG_JSON) ?: return START_NOT_STICKY
                val parsedConfig = runCatching {
                    gson.fromJson(configJson, WorkoutConfig::class.java)
                }.getOrNull() ?: return START_NOT_STICKY
                if (!PermissionGate.hasAllRuntimePermissions(this)) {
                    handleStartFailure("Missing required permissions.")
                    return START_NOT_STICKY
                }
                val deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                runCatching {
                    startWorkout(parsedConfig, deviceAddress)
                }.onFailure {
                    handleStartFailure("Unable to start workout.")
                }
                return START_NOT_STICKY
            }

            ACTION_PAUSE -> {
                pauseWorkout()
                return START_NOT_STICKY
            }

            ACTION_RESUME -> {
                resumeWorkout()
                return START_NOT_STICKY
            }

            ACTION_STOP -> {
                stopWorkout()
                return START_NOT_STICKY
            }

            ACTION_RESCAN_BLE -> {
                bleCoordinator.startScan()
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun startWorkout(workoutConfig: WorkoutConfig, deviceAddress: String?) {
        isStopping = false
        workoutId = 0L
        latestTick = null
        alertPolicy.reset()
        coachingEventRouter.reset()
        trackPointRecorder.reset()
        hrSampleBuffer.clear()
        hrSessionSamples.clear()
        cadenceLockSuspected = false

        notificationHelper.startForeground(this, "Starting workout...")

        gpsTracker = GpsDistanceTracker(this)
        coachingAudioManager = CoachingAudioManager(this, audioSettingsRepository.getAudioSettings())
        zoneEngine = ZoneEngine(workoutConfig)
        adaptiveController = AdaptivePaceController(
            config = workoutConfig,
            initialProfile = adaptiveProfileRepository.getProfile()
        )

        startupJob?.cancel()
        startupJob = lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                workoutId = repository.createWorkout(
                    WorkoutEntity(
                        startTime = System.currentTimeMillis(),
                        mode = workoutConfig.mode.name,
                        targetConfig = gson.toJson(workoutConfig)
                    )
                )
                workoutStartMs = System.currentTimeMillis()
                gpsTracker?.start()

                val alreadyConnected = bleCoordinator.isConnected.value
                if (!alreadyConnected) {
                    val connected = deviceAddress?.let { address ->
                        bleCoordinator.connectToAddress(address)
                    } ?: false
                    if (!connected) {
                        bleCoordinator.startScan()
                    }
                }

                WorkoutState.set(
                    WorkoutSnapshot(
                        isRunning = true,
                        isPaused = false,
                        targetHr = workoutConfig.targetHrAtDistance(0f) ?: 0,
                        guidanceText = "GET HR SIGNAL",
                        adaptiveLagSec = adaptiveController?.currentLagSec() ?: 0f
                    )
                )
                observeWorkoutTicks(workoutConfig)
            }.onFailure {
                handleStartFailure("Workout start failed. Check permissions and try again.")
            }
        }
    }

    private fun observeWorkoutTicks(workoutConfig: WorkoutConfig) {
        val hrManager = bleCoordinator.managerForWorkout()
        val tracker = gpsTracker ?: return

        observationJob?.cancel()
        observationJob = lifecycleScope.launch(Dispatchers.IO) {
            combine(
                hrManager.heartRate,
                hrManager.isConnected,
                tracker.distanceMeters,
                tracker.currentLocation
            ) { hr, connected, distance, location ->
                WorkoutTick(
                    hr = hr,
                    connected = connected,
                    distanceMeters = distance,
                    location = location
                )
            }.collect { tick ->
                processTick(workoutConfig, tick)
            }
        }
    }

    private suspend fun processTick(workoutConfig: WorkoutConfig, tick: WorkoutTick) {
        val engine = zoneEngine ?: return
        val adaptive = adaptiveController
        latestTick = tick
        val nowMs = System.currentTimeMillis()
        val elapsedSeconds = if (workoutStartMs > 0L) (nowMs - workoutStartMs) / 1000L else 0L
        val target = if (workoutConfig.isTimeBased()) {
            workoutConfig.targetHrAtElapsedSeconds(elapsedSeconds)
        } else {
            workoutConfig.targetHrAtDistance(tick.distanceMeters)
        }
        val isPaused = WorkoutState.snapshot.value.isPaused

        if (isPaused) {
            WorkoutState.update { current ->
                current.copy(
                    currentHr = if (tick.connected) tick.hr else 0,
                    targetHr = target ?: 0,
                    hrConnected = tick.connected,
                    guidanceText = "Workout paused",
                    projectionReady = false,
                    predictedHr = 0,
                    avgHr = WorkoutState.snapshot.value.avgHr
                )
            }
            notificationHelper.update("Workout paused")
            return
        }

        val zoneStatus = if (!tick.connected || tick.hr <= 0 || target == null || target == 0) {
            ZoneStatus.NO_DATA
        } else {
            engine.evaluate(tick.hr, target)
        }

        if (tick.connected && tick.hr > 0 && !isPaused) {
            hrSampleSum += tick.hr
            hrSampleCount++
            hrSampleBuffer.addLast(tick.hr)
            if (hrSampleBuffer.size > 120) hrSampleBuffer.removeFirst()
            hrSessionSamples.add(tick.hr)
            // Check for cadence lock every 10 new samples once buffer is 30+
            if (hrSampleBuffer.size >= 30 && hrSampleBuffer.size % 10 == 0) {
                cadenceLockSuspected = HrArtifactDetector.isArtifactSuspected(hrSampleBuffer.toList())
            }
        }
        val sessionAvgHr = if (hrSampleCount > 0) (hrSampleSum / hrSampleCount).toInt() else 0

        val adaptiveResult = adaptive?.evaluateTick(
            nowMs = nowMs,
            hr = tick.hr,
            connected = tick.connected,
            targetHr = target,
            distanceMeters = tick.distanceMeters,
            actualZone = zoneStatus
        )

        val guidance = adaptiveResult?.guidance ?: when (zoneStatus) {
            ZoneStatus.ABOVE_ZONE -> "SLOW DOWN NOW"
            ZoneStatus.BELOW_ZONE -> "SPEED UP NOW"
            ZoneStatus.IN_ZONE -> "HOLD THIS PACE"
            ZoneStatus.NO_DATA -> "GET HR SIGNAL"
        }

        WorkoutState.update { current ->
            current.copy(
                currentHr = if (tick.connected) tick.hr else 0,
                targetHr = target ?: 0,
                zoneStatus = zoneStatus,
                distanceMeters = tick.distanceMeters,
                hrConnected = tick.connected,
                paceMinPerKm = adaptiveResult?.currentPaceMinPerKm ?: current.paceMinPerKm,
                predictedHr = adaptiveResult?.predictedHr ?: 0,
                guidanceText = guidance,
                adaptiveLagSec = adaptive?.currentLagSec() ?: 0f,
                projectionReady = adaptiveResult?.hasProjectionConfidence ?: false,
                isFreeRun = workoutConfig.mode == WorkoutMode.FREE_RUN,
                avgHr = sessionAvgHr
            )
        }

        coachingEventRouter.route(
            workoutConfig = workoutConfig,
            connected = tick.connected,
            distanceMeters = tick.distanceMeters,
            elapsedSeconds = elapsedSeconds,
            zoneStatus = zoneStatus,
            adaptiveResult = adaptiveResult,
            guidance = guidance,
            nowMs = nowMs,
            emitEvent = { event, eventGuidance ->
                coachingAudioManager?.fireEvent(event, eventGuidance)
            }
        )
        alertPolicy.handle(
            status = zoneStatus,
            nowMs = nowMs,
            alertDelaySec = workoutConfig.alertDelaySec,
            alertCooldownSec = workoutConfig.alertCooldownSec,
            guidanceText = guidance,
            onResetEscalation = { coachingAudioManager?.resetEscalation() },
            onAlert = { event, eventGuidance ->
                coachingAudioManager?.fireEvent(event, eventGuidance)
            }
        )

        trackPointRecorder.saveIfNeeded(
            workoutId = workoutId,
            timestampMs = nowMs,
            latitude = tick.location?.latitude,
            longitude = tick.location?.longitude,
            heartRate = tick.hr,
            distanceMeters = tick.distanceMeters,
            force = false,
            save = repository::addTrackPoint
        )

        val notificationText = when {
            !tick.connected -> "HR monitor disconnected"
            tick.hr <= 0 -> "Connected. Waiting for heart rate..."
            target != null && target > 0 -> "$guidance - HR ${tick.hr} / $target"
            else -> "HR ${tick.hr} bpm"
        }
        notificationHelper.update(notificationText)
    }

    private fun pauseWorkout() {
        WorkoutState.update { current ->
            if (!current.isRunning || current.isPaused) current else {
                current.copy(isPaused = true, guidanceText = "Workout paused")
            }
        }
        notificationHelper.update("Workout paused")
    }

    private fun resumeWorkout() {
        WorkoutState.update { current ->
            if (!current.isRunning || !current.isPaused) current else {
                current.copy(isPaused = false)
            }
        }
        notificationHelper.update("Workout resumed")
    }

    private fun stopWorkout() {
        hrSampleSum = 0L
        hrSampleCount = 0
        if (isStopping) return
        isStopping = true

        stopJob?.cancel()
        stopJob = lifecycleScope.launch(Dispatchers.IO) {
            startupJob?.join()
            startupJob = null

            observationJob?.cancel()
            observationJob?.join()
            observationJob = null

            val finalTick = latestTick
            if (finalTick != null) {
                trackPointRecorder.saveIfNeeded(
                    workoutId = workoutId,
                    timestampMs = System.currentTimeMillis(),
                    latitude = finalTick.location?.latitude,
                    longitude = finalTick.location?.longitude,
                    heartRate = finalTick.hr,
                    distanceMeters = finalTick.distanceMeters,
                    force = true,
                    save = repository::addTrackPoint
                )
            }

            gpsTracker?.stop()
            bleCoordinator.disconnect()

            if (workoutId > 0L) {
                val now = System.currentTimeMillis()
                val session = adaptiveController?.finishSession(workoutId = workoutId, endedAtMs = now)

                // --- Calibration pass ---
                var currentProfile = session?.updatedProfile ?: adaptiveProfileRepository.getProfile()

                // hrMax: only update if cadence lock was NOT suspected
                val newHrMax = HrCalibrator.detectNewHrMax(
                    currentHrMax = currentProfile.hrMax ?: 180,
                    recentSamples = hrSessionSamples.toList(),
                    cadenceLockSuspected = cadenceLockSuspected
                )
                if (newHrMax != null) {
                    currentProfile = currentProfile.copy(hrMax = newHrMax, hrMaxIsCalibrated = true)
                }

                // Load track points once — shared by metrics calculation and hrRest calibration
                val trackPoints = repository.getTrackPoints(workoutId)

                // hrRest: lower only when a plausible early-session proxy is found
                val restingProxy = MetricsCalculator.computeRestingHrProxy(trackPoints)
                if (restingProxy != null) {
                    val updatedRest = HrCalibrator.updateHrRest(
                        currentHrRest = currentProfile.hrRest ?: restingProxy,
                        candidate = restingProxy
                    )
                    currentProfile = currentProfile.copy(hrRest = updatedRest)
                }

                adaptiveProfileRepository.saveProfile(currentProfile)
                // --------------------------------

                val currentWorkout = repository.getWorkoutById(workoutId)
                if (currentWorkout != null) {
                    repository.updateWorkout(
                        currentWorkout.copy(
                            endTime = now,
                            totalDistanceMeters = WorkoutState.snapshot.value.distanceMeters
                        )
                    )
                }

                val canonicalMetrics = MetricsCalculator.deriveFullMetrics(
                    workoutId = workoutId,
                    recordedAtMs = now,
                    trackPoints = trackPoints
                )
                val metricsToSave = when {
                    canonicalMetrics != null && session != null -> canonicalMetrics.copy(
                        settleDownSec = session.metrics.settleDownSec,
                        settleUpSec = session.metrics.settleUpSec,
                        longTermHrTrimBpm = session.metrics.longTermHrTrimBpm,
                        responseLagSec = session.metrics.responseLagSec
                    )
                    canonicalMetrics != null -> canonicalMetrics
                    session != null -> session.metrics
                    else -> null
                }
                // Mark unreliable if cadence lock was detected during the session
                val reliableMetrics = metricsToSave?.copy(trimpReliable = !cadenceLockSuspected)
                reliableMetrics?.let { workoutMetricsRepository.saveWorkoutMetrics(it) }

                WorkoutState.update { it.copy(completedWorkoutId = workoutId) }
            }

            cleanupManagers()
            WorkoutState.reset()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            isStopping = false
        }
    }

    private fun handleStartFailure(message: String) {
        runCatching {
            notificationHelper.update(message)
        }
        cleanupManagers()
        WorkoutState.reset()
        WorkoutState.clearCompletedWorkoutId()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    private fun cleanupManagers() {
        gpsTracker?.stop()
        gpsTracker = null
        coachingAudioManager?.destroy()
        coachingAudioManager = null
        zoneEngine = null
        adaptiveController = null
        alertPolicy.reset()
        coachingEventRouter.reset()
        trackPointRecorder.reset()
    }

    override fun onDestroy() {
        super.onDestroy()
        startupJob?.cancel()
        observationJob?.cancel()
        stopJob?.cancel()
        cleanupManagers()
    }

    private data class WorkoutTick(
        val hr: Int,
        val connected: Boolean,
        val distanceMeters: Float,
        val location: Location?
    )
}
