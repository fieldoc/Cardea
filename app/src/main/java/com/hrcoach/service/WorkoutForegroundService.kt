package com.hrcoach.service

import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.data.firebase.CloudBackupManager
import com.hrcoach.data.firebase.FirebasePartnerRepository
import com.hrcoach.data.repository.BootcampRepository
import com.hrcoach.domain.achievement.StreakCalculator
import com.hrcoach.domain.bootcamp.BootcampSessionCompleter
import com.hrcoach.data.repository.AdaptiveProfileRepository
import com.hrcoach.data.repository.AudioSettingsRepository
import com.hrcoach.data.repository.WorkoutMetricsRepository
import com.hrcoach.data.repository.UserProfileRepository
import com.hrcoach.data.repository.WorkoutRepository
import com.hrcoach.data.repository.AutoPauseSettingsRepository
import com.hrcoach.domain.engine.AdaptivePaceController
import com.hrcoach.domain.engine.AutoPauseDetector
import com.hrcoach.domain.engine.AutoPauseEvent
import com.hrcoach.domain.engine.EnvironmentFlagDetector
import com.hrcoach.domain.engine.FitnessLoadCalculator
import com.hrcoach.domain.engine.FitnessSignalEvaluator
import com.hrcoach.domain.engine.TuningDirection
import com.hrcoach.domain.engine.HrArtifactDetector
import com.hrcoach.domain.engine.HrCalibrator
import com.hrcoach.domain.engine.MetricsCalculator
import com.hrcoach.domain.engine.ZoneEngine
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.domain.model.ZoneStatus
import com.hrcoach.domain.simulation.HrDataSource
import com.hrcoach.domain.simulation.LocationDataSource
import com.hrcoach.domain.simulation.RealClock
import com.hrcoach.domain.simulation.WorkoutClock
import com.hrcoach.service.audio.CoachingAudioManager
import com.hrcoach.service.simulation.RealDataSourceFactory
import com.hrcoach.service.simulation.SimulatedDataSourceFactory
import com.hrcoach.service.simulation.SimulatedHrSource
import com.hrcoach.service.simulation.SimulatedLocationSource
import com.hrcoach.service.simulation.SimulationClock
import com.hrcoach.service.simulation.SimulationController
import com.hrcoach.service.workout.AlertPolicy
import com.hrcoach.service.workout.CoachingEventRouter
import com.hrcoach.service.workout.TrackPointRecorder
import com.hrcoach.service.workout.WorkoutNotificationHelper
import com.hrcoach.util.JsonCodec
import com.hrcoach.util.PermissionGate
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
        const val ACTION_TOGGLE_AUTO_PAUSE = "com.hrcoach.ACTION_TOGGLE_AUTO_PAUSE"
        const val EXTRA_CONFIG_JSON = "config_json"
        const val EXTRA_DEVICE_ADDRESS = "device_address"

        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "workout_channel"
        private const val TRACK_POINT_INTERVAL_MS = 5_000L
        private const val AUTO_PAUSE_GRACE_MS = 15_000L
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
    lateinit var autoPauseSettingsRepository: AutoPauseSettingsRepository

    @Inject
    lateinit var userProfileRepository: UserProfileRepository

    @Inject
    lateinit var partnerRepository: FirebasePartnerRepository

    @Inject
    lateinit var cloudBackupManager: CloudBackupManager

    @Inject
    lateinit var bootcampRepository: BootcampRepository

    @Inject
    lateinit var bootcampSessionCompleter: BootcampSessionCompleter

    @Inject
    lateinit var bleCoordinator: BleConnectionCoordinator

    @Inject
    lateinit var realDataSourceFactory: RealDataSourceFactory

    private val gson = JsonCodec.gson
    private lateinit var notificationHelper: WorkoutNotificationHelper

    private var locationSource: LocationDataSource? = null
    private var hrSource: HrDataSource? = null
    private var clock: WorkoutClock = RealClock()
    private var coachingAudioManager: CoachingAudioManager? = null
    private var zoneEngine: ZoneEngine? = null
    private var adaptiveController: AdaptivePaceController? = null

    private val alertPolicy = AlertPolicy()
    private val coachingEventRouter = CoachingEventRouter()
    private val trackPointRecorder = TrackPointRecorder(TRACK_POINT_INTERVAL_MS)

    private var observationJob: Job? = null
    private var simTickJob: Job? = null
    private var stopJob: Job? = null
    private var startupJob: Job? = null

    private var autoPauseDetector: AutoPauseDetector? = null
    private var sessionAutoPauseEnabled: Boolean = true
    private var autoPauseStartMs: Long = 0L
    private var totalAutoPausedMs: Long = 0L
    private var autoPauseGraceUntilMs: Long = 0L

    private var sessionDistanceUnit = com.hrcoach.domain.model.DistanceUnit.KM
    private var workoutId: Long = 0L
    private var workoutStartMs: Long = 0L
    private var totalPausedMs: Long = 0L
    private var pauseStartMs: Long = 0L
    private var isStopping: Boolean = false
    private var latestTick: WorkoutTick? = null

    private var hrSampleSum: Long = 0L
    private var hrSampleCount: Int = 0
    private var lastNotificationText: String = ""

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
                if (!SimulationController.isActive && !PermissionGate.hasAllRuntimePermissions(this)) {
                    handleStartFailure("Missing required permissions.")
                    return START_NOT_STICKY
                }
                val deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                runCatching {
                    startWorkout(parsedConfig, deviceAddress)
                }.onFailure {
                    handleStartFailure("Unable to start workout.", it)
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

            ACTION_TOGGLE_AUTO_PAUSE -> {
                sessionAutoPauseEnabled = !sessionAutoPauseEnabled
                if (!sessionAutoPauseEnabled) {
                    // If currently auto-paused when toggled off, resume everything cleanly
                    if (WorkoutState.snapshot.value.isAutoPaused) {
                        totalAutoPausedMs += clock.now() - autoPauseStartMs
                        autoPauseStartMs = 0L
                        locationSource?.setMoving(true)
                    }
                    autoPauseDetector?.reset()
                    WorkoutState.update { it.copy(isAutoPaused = false, autoPauseEnabled = false) }
                } else {
                    WorkoutState.update { it.copy(autoPauseEnabled = true) }
                }
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun startWorkout(workoutConfig: WorkoutConfig, deviceAddress: String?) {
        isStopping = false
        workoutId = 0L
        latestTick = null
        totalPausedMs = 0L
        pauseStartMs = 0L
        autoPauseDetector = AutoPauseDetector()
        sessionAutoPauseEnabled = autoPauseSettingsRepository.isAutoPauseEnabled()
        autoPauseStartMs = 0L
        totalAutoPausedMs = 0L
        autoPauseGraceUntilMs = 0L
        workoutStartMs = 0L
        alertPolicy.reset()
        coachingEventRouter.reset()
        trackPointRecorder.reset()
        hrSampleBuffer.clear()
        hrSessionSamples.clear()
        cadenceLockSuspected = false
        lastNotificationText = ""

        notificationHelper.startForeground(this, "Starting workout...")

        // Choose data source factory based on simulation mode — single atomic snapshot
        val simState = SimulationController.state.value
        val factory = if (simState.isActive && simState.scenario != null) {
            val simClock = SimulationClock(MutableStateFlow(simState.speedMultiplier))
            SimulationController.attachClock(simClock)
            SimulatedDataSourceFactory(simState.scenario, simClock)
        } else {
            realDataSourceFactory
        }

        clock = factory.getClock()
        hrSource = factory.createHrSource()
        locationSource = factory.createLocationSource()
        coachingAudioManager = CoachingAudioManager(this, audioSettingsRepository.getAudioSettings())
        zoneEngine = ZoneEngine(workoutConfig)
        adaptiveController = AdaptivePaceController(
            config = workoutConfig,
            initialProfile = adaptiveProfileRepository.getProfile()
        )

        startupJob?.cancel()
        startupJob = lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                // Start BLE and GPS early so they warm up during countdown
                locationSource?.start()

                if (!SimulationController.isActive) {
                    val alreadyConnected = bleCoordinator.isConnected.value
                    if (!alreadyConnected) {
                        val connected = deviceAddress?.let { address ->
                            bleCoordinator.connectToAddress(address)
                        } ?: false
                        if (!connected) {
                            bleCoordinator.startScan()
                        }
                    }
                }

                // Play 3-2-1-GO countdown (suspends ~4 seconds)
                coachingAudioManager?.playStartSequence(workoutConfig)

                // NOW start the workout clock — after countdown completes
                workoutId = repository.createWorkout(
                    WorkoutEntity(
                        startTime = clock.now(),
                        mode = workoutConfig.mode.name,
                        targetConfig = gson.toJson(workoutConfig),
                        isSimulated = SimulationController.isActive
                    )
                )
                workoutStartMs = clock.now()
                sessionDistanceUnit = com.hrcoach.domain.model.DistanceUnit.fromString(userProfileRepository.getDistanceUnit())
                coachingAudioManager?.distanceUnit = sessionDistanceUnit
                coachingEventRouter.reset(workoutStartMs)  // stamp the start time for IN_ZONE_CONFIRM baseline

                // Suppress auto-pause for 15 seconds so the runner can pocket
                // their phone and start moving without seeing "Auto-Paused"
                autoPauseGraceUntilMs = clock.now() + AUTO_PAUSE_GRACE_MS

                WorkoutState.update { current ->
                    WorkoutSnapshot(
                        isRunning = true,
                        isPaused = false,
                        targetHr = workoutConfig.targetHrAtDistance(0f) ?: 0,
                        guidanceText = if (SimulationController.isActive) "SIM STARTING" else "GET HR SIGNAL",
                        autoPauseEnabled = sessionAutoPauseEnabled,
                        pendingBootcampSessionId = current.pendingBootcampSessionId,
                    )
                }
                observeWorkoutTicks(workoutConfig)
            }.onFailure {
                handleStartFailure("Workout start failed. Check permissions and try again.", it)
            }
        }
    }

    private fun observeWorkoutTicks(workoutConfig: WorkoutConfig) {
        val hr = hrSource ?: return
        val loc = locationSource ?: return

        // Drive simulated sources if in sim mode
        if (hr is SimulatedHrSource && loc is SimulatedLocationSource) {
            val scenarioDurationSec: Float = SimulationController.state.value.scenario
                ?.durationSeconds?.toFloat() ?: Float.MAX_VALUE
            simTickJob?.cancel()
            simTickJob = lifecycleScope.launch(Dispatchers.IO) {
                while (true) {
                    val snap = WorkoutState.snapshot.value
                    if (!snap.isPaused && !snap.isAutoPaused) {
                        val elapsedSec = (clock.now() - workoutStartMs) / 1000f
                        hr.updateForTime(elapsedSec)
                        loc.updateForTime(elapsedSec)
                        if (elapsedSec >= scenarioDurationSec) {
                            stopWorkout()
                            break
                        }
                    }
                    delay(100) // 100ms real time between ticks
                }
            }
        }

        observationJob?.cancel()
        observationJob = lifecycleScope.launch(Dispatchers.IO) {
            combine(
                hr.heartRate,
                hr.isConnected,
                loc.distanceMeters,
                loc.currentLocation,
                loc.currentSpeed
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                WorkoutTick(
                    hr = values[0] as Int,
                    connected = values[1] as Boolean,
                    distanceMeters = values[2] as Float,
                    location = values[3] as Location?,
                    speed = values[4] as Float?
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
        val nowMs = clock.now()

        // Auto-pause detection: run before elapsed-time math so state is fresh this tick
        // Skip during grace period after start so runner can pocket phone without "Auto-Paused"
        var isAutoPaused = WorkoutState.snapshot.value.isAutoPaused  // read BEFORE potential update
        if (sessionAutoPauseEnabled && nowMs >= autoPauseGraceUntilMs) {
            when (autoPauseDetector?.update(tick.speed, nowMs)) {
                AutoPauseEvent.PAUSED -> {
                    isAutoPaused = true  // use local var immediately — no StateFlow lag
                    autoPauseStartMs = nowMs
                    locationSource?.setMoving(false)
                    WorkoutState.update { it.copy(isAutoPaused = true) }
                    coachingAudioManager?.playPauseFeedback(paused = true)
                }
                AutoPauseEvent.RESUMED -> {
                    isAutoPaused = false  // use local var immediately
                    totalAutoPausedMs += nowMs - autoPauseStartMs
                    autoPauseStartMs = 0L
                    locationSource?.setMoving(true)
                    WorkoutState.update { it.copy(isAutoPaused = false) }
                    coachingAudioManager?.playPauseFeedback(paused = false)
                }
                else -> Unit
            }
        }
        val currentAutoPauseMs = if (isAutoPaused && autoPauseStartMs > 0L) nowMs - autoPauseStartMs else 0L
        val elapsedSeconds = if (workoutStartMs > 0L) {
            ((nowMs - workoutStartMs - totalPausedMs - totalAutoPausedMs - currentAutoPauseMs).coerceAtLeast(0L)) / 1000L
        } else {
            0L
        }
        val target = when {
            workoutConfig.isTimeBased() -> workoutConfig.targetHrAtElapsedSeconds(elapsedSeconds)
            workoutConfig.hasMixedSegments() -> workoutConfig.targetHrForMixed(elapsedSeconds, tick.distanceMeters)
            else -> workoutConfig.targetHrAtDistance(tick.distanceMeters)
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

        val guidance = when {
            isAutoPaused -> "STOPPED \u2022 ALERTS PAUSED"
            adaptiveResult?.guidance != null -> adaptiveResult.guidance
            // Strides guidance after 20 minutes
            workoutConfig.guidanceTag == "strides" && elapsedSeconds >= 1200 && zoneStatus == ZoneStatus.IN_ZONE ->
                "Time for strides! 4\u20136 \u00d7 20s fast & smooth, jog easy 60\u201390s between"
            // Zone 2 conversational pace nudge
            zoneStatus == ZoneStatus.IN_ZONE && (workoutConfig.presetId == "zone2_base" || workoutConfig.presetId == "zone2_with_strides") ->
                "Easy pace builds your aerobic engine. Hold a conversation."
            else -> when (zoneStatus) {
                ZoneStatus.ABOVE_ZONE -> "SLOW DOWN NOW"
                ZoneStatus.BELOW_ZONE -> "SPEED UP NOW"
                ZoneStatus.IN_ZONE -> "HOLD THIS PACE"
                ZoneStatus.NO_DATA -> "GET HR SIGNAL"
            }
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
                projectionReady = adaptiveResult?.hasProjectionConfidence ?: false,
                isFreeRun = workoutConfig.mode == WorkoutMode.FREE_RUN,
                avgHr = sessionAvgHr,
                elapsedSeconds = elapsedSeconds,
            )
        }

        if (!isAutoPaused) {
            coachingEventRouter.route(
                workoutConfig = workoutConfig,
                connected = tick.connected,
                distanceMeters = tick.distanceMeters,
                elapsedSeconds = elapsedSeconds,
                zoneStatus = zoneStatus,
                adaptiveResult = adaptiveResult,
                guidance = guidance,
                nowMs = nowMs,
                distanceUnit = sessionDistanceUnit,
                emitEvent = { event, eventGuidance ->
                    val pace = adaptiveResult?.currentPaceMinPerKm
                    coachingAudioManager?.fireEvent(event, eventGuidance, paceMinPerKm = pace)
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
                    val pace = adaptiveResult?.currentPaceMinPerKm
                    coachingAudioManager?.fireEvent(event, eventGuidance, paceMinPerKm = pace)
                }
            )
        }

        if (workoutId > 0L) {
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
        }

        val notificationText = when {
            !tick.connected -> "HR monitor disconnected"
            tick.hr <= 0 -> "Connected. Waiting for heart rate..."
            target != null && target > 0 -> "$guidance - HR ${tick.hr} / $target"
            else -> "HR ${tick.hr} bpm"
        }
        if (notificationText != lastNotificationText) {
            lastNotificationText = notificationText
            notificationHelper.update(notificationText)
        }
    }

    private fun pauseWorkout() {
        var didPause = false
        WorkoutState.update { current ->
            if (!current.isRunning || current.isPaused) current else {
                didPause = true
                current.copy(isPaused = true, guidanceText = "Workout paused")
            }
        }
        if (didPause) {
            pauseStartMs = clock.now()
            coachingAudioManager?.playPauseFeedback(paused = true)
        }
        notificationHelper.update("Workout paused")
    }

    private fun resumeWorkout() {
        val nowMs = clock.now()
        var didResume = false
        WorkoutState.update { current ->
            if (!current.isRunning || !current.isPaused) current else {
                didResume = true
                current.copy(isPaused = false)
            }
        }
        if (didResume && pauseStartMs > 0L) {
            totalPausedMs += nowMs - pauseStartMs
            pauseStartMs = 0L
            coachingAudioManager?.playPauseFeedback(paused = false)
        }
        notificationHelper.update("Workout resumed")
    }

    private fun stopWorkout() {
        if (isStopping) return
        isStopping = true
        val finalHrSampleSum = hrSampleSum
        val finalHrSampleCount = hrSampleCount
        hrSampleSum = 0L
        hrSampleCount = 0

        stopJob?.cancel()
        stopJob = lifecycleScope.launch(Dispatchers.IO) {
            startupJob?.join()
            startupJob = null

            simTickJob?.cancel()
            simTickJob?.join()
            simTickJob = null
            observationJob?.cancel()
            observationJob?.join()
            observationJob = null

            val finalTick = latestTick
            if (finalTick != null) {
                trackPointRecorder.saveIfNeeded(
                    workoutId = workoutId,
                    timestampMs = clock.now(),
                    latitude = finalTick.location?.latitude,
                    longitude = finalTick.location?.longitude,
                    heartRate = finalTick.hr,
                    distanceMeters = finalTick.distanceMeters,
                    force = true,
                    save = repository::addTrackPoint
                )
            }

            locationSource?.stop()
            if (!SimulationController.isActive) {
                bleCoordinator.disconnect()
            }

            if (workoutId > 0L) {
                val now = clock.now()

                // Essential: save workout end state — must succeed even if metrics crash
                runCatching {
                    val currentWorkout = repository.getWorkoutById(workoutId)
                    if (currentWorkout != null) {
                        repository.updateWorkout(
                            currentWorkout.copy(
                                endTime = now,
                                totalDistanceMeters = WorkoutState.snapshot.value.distanceMeters
                            )
                        )
                    }
                }.onFailure { e ->
                    Log.e("WorkoutService", "Failed to save essential workout data", e)
                }

                // Discard runs that are too short to be meaningful (< 200m AND < 1 min)
                val finalDistance = WorkoutState.snapshot.value.distanceMeters
                val durationMs = now - workoutStartMs
                if (finalDistance < 200f && durationMs < 60_000L) {
                    Log.i("WorkoutService", "Discarding short run: ${finalDistance}m, ${durationMs}ms")
                    runCatching { repository.deleteWorkout(workoutId) }
                    WorkoutState.setPendingBootcampSessionId(null)
                    cleanupManagers()
                    WorkoutState.reset()
                    if (SimulationController.isActive) SimulationController.deactivate()
                    notificationHelper.stop()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    // Keep isStopping=true — see comment at end of stopWorkout()
                    return@launch
                }

                // Best-effort: metrics derivation, calibration, fitness signals
                runCatching {
                    val session = adaptiveController?.finishSession(workoutId = workoutId, endedAtMs = now)

                    // --- Calibration pass ---
                    // session.updatedProfile preserves all fields from the workout's initialProfile
                    // (hrMax, ctl, atl, hrRest, etc.) — the blocks below patch in fresh values
                    // computed from this session's data.
                    var currentProfile = session?.updatedProfile ?: adaptiveProfileRepository.getProfile()

                    // hrMax: only update if cadence lock was NOT suspected
                    val ageBasedFallback = userProfileRepository.getAge()?.let { 220 - it } ?: 180
                    val previousHrMax = currentProfile.hrMax ?: ageBasedFallback
                    val newHrMax = HrCalibrator.detectNewHrMax(
                        currentHrMax = previousHrMax,
                        recentSamples = hrSessionSamples.toList(),
                        cadenceLockSuspected = cadenceLockSuspected
                    )
                    if (newHrMax != null) {
                        currentProfile = currentProfile.copy(
                            hrMax = newHrMax,
                            hrMaxIsCalibrated = true,
                            hrMaxCalibratedAtMs = now
                        )
                        if (!SimulationController.isActive) {
                            userProfileRepository.setMaxHr(newHrMax)
                        }
                        // Surface the change to the post-run summary screen
                        WorkoutState.update { it.copy(hrMaxUpdatedDelta = Pair(previousHrMax, newHrMax)) }
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

                    // Profile is saved once below after CTL/ATL and tuning direction are computed.
                    // --------------------------------

                    val canonicalMetrics = MetricsCalculator.deriveFullMetrics(
                        workoutId = workoutId,
                        recordedAtMs = now,
                        trackPoints = trackPoints,
                        targetHr = currentProfile.hrMax?.toFloat()
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

                    // --- TRIMP score ---
                    val durationMin = (now - workoutStartMs) / 60_000f
                    val sessionAvgHr = reliableMetrics?.avgHr
                        ?: if (finalHrSampleCount > 0) (finalHrSampleSum.toFloat() / finalHrSampleCount) else null
                    val hrMaxEst = currentProfile.hrMax?.toFloat() ?: ageBasedFallback.toFloat()
                    val trimpScore = reliableMetrics?.trimpScore
                        ?: if (sessionAvgHr != null && durationMin > 0f) {
                            val intensity = sessionAvgHr / hrMaxEst
                            durationMin * sessionAvgHr * intensity * intensity
                        } else null

                    // --- Environment flag — compares session pace to recent baseline at similar HR ---
                    val recentMetricsForEnv = workoutMetricsRepository.getRecentMetrics(42)
                    val baselinePace: Float? = if (sessionAvgHr != null) {
                        recentMetricsForEnv
                            .filter { m ->
                                m.workoutId != workoutId &&
                                m.avgHr != null &&
                                kotlin.math.abs(m.avgHr!! - sessionAvgHr) < 10f &&
                                m.avgPaceMinPerKm != null
                            }
                            .mapNotNull { it.avgPaceMinPerKm }
                            .sorted()
                            .takeIf { it.isNotEmpty() }
                            ?.let { paces -> paces[paces.size / 2] }
                    } else null

                    val environmentAffected = EnvironmentFlagDetector.isEnvironmentAffected(
                        aerobicDecoupling = reliableMetrics?.aerobicDecoupling,
                        sessionAvgGapPace = reliableMetrics?.avgPaceMinPerKm,
                        baselineGapPaceAtEquivalentHr = baselinePace
                    )

                    // Save complete metrics
                    val completeMetrics = reliableMetrics?.copy(
                        trimpScore = trimpScore,
                        environmentAffected = environmentAffected
                    )
                    completeMetrics?.let { workoutMetricsRepository.saveWorkoutMetrics(it) }

                    // --- Update CTL / ATL ---
                    if (trimpScore != null) {
                        val prevWorkout = repository.getAllWorkoutsOnce()
                            .sortedByDescending { it.startTime }
                            .firstOrNull { it.id != workoutId }
                        val daysSinceLast = if (prevWorkout != null) {
                            ((now - prevWorkout.startTime) / 86_400_000L).toInt().coerceAtLeast(1)
                        } else 1
                        val loadResult = FitnessLoadCalculator.updateLoads(
                            currentCtl = currentProfile.ctl,
                            currentAtl = currentProfile.atl,
                            trimpScore = trimpScore,
                            daysSinceLast = daysSinceLast
                        )
                        currentProfile = currentProfile.copy(ctl = loadResult.ctl, atl = loadResult.atl)
                    }

                    // --- Fitness signal evaluation → store tuning direction for next Bootcamp session ---
                    val updatedRecentMetrics = workoutMetricsRepository.getRecentMetrics(42)
                    val fitnessEval = FitnessSignalEvaluator.evaluate(currentProfile, updatedRecentMetrics)
                    currentProfile = currentProfile.copy(lastTuningDirection = fitnessEval.tuningDirection)
                    if (!SimulationController.isActive) {
                        adaptiveProfileRepository.saveProfile(currentProfile)
                    }
                }.onFailure { e ->
                    Log.e("WorkoutService", "Metrics derivation failed for workout $workoutId", e)
                }

                // Sync activity to Firebase for accountability partners (real runs only)
                if (!SimulationController.isActive) {
                    try {
                        val weeklyCount = repository.getWorkoutsCompletedThisWeek()
                        val durationMin = ((now - workoutStartMs) / 60_000L).toInt()
                        val phase = WorkoutState.snapshot.value.pendingBootcampSessionId?.let {
                            "Bootcamp"
                        } ?: "Free run"
                        val streak = try {
                            val enrollment = bootcampRepository.getActiveEnrollmentOnce()
                            if (enrollment != null) {
                                val sessions = bootcampRepository.getSessionsForEnrollmentOnce(enrollment.id)
                                StreakCalculator.computeSessionStreak(sessions, enrollment.startDate)
                            } else 0
                        } catch (_: Exception) { 0 }
                        partnerRepository.syncWorkoutActivity(
                            currentStreak = streak,
                            weeklyRunCount = weeklyCount,
                            lastRunDurationMin = durationMin,
                            lastRunPhase = phase,
                        )
                    } catch (e: Exception) {
                        Log.w("WorkoutService", "Failed to sync partner activity", e)
                    }
                }

                // Cloud backup (real runs only)
                if (!SimulationController.isActive) {
                    runCatching {
                        val savedWorkout = repository.getWorkoutById(workoutId)
                        if (savedWorkout != null) {
                            val points = repository.getTrackPoints(workoutId)
                            val metricsEntity = workoutMetricsRepository.getMetricsEntity(workoutId)
                            cloudBackupManager.syncWorkout(savedWorkout, points, metricsEntity)
                            cloudBackupManager.syncAdaptiveProfile()
                        }
                    }.onFailure { Log.w("WorkoutService", "Cloud backup failed", it) }
                }

                // Sim runs: complete bootcamp session (if any) before deleting the workout row.
                // The session will reference a deleted workoutId — acceptable for sim testing.
                if (SimulationController.isActive && workoutId > 0L) {
                    val pendingId = WorkoutState.snapshot.value.pendingBootcampSessionId
                    if (pendingId != null) {
                        runCatching {
                            val simProfile = adaptiveProfileRepository.getProfile()
                            bootcampSessionCompleter.complete(
                                workoutId = workoutId,
                                pendingSessionId = pendingId,
                                tuningDirection = simProfile.lastTuningDirection ?: TuningDirection.HOLD
                            )
                        }.onFailure { e ->
                            Log.w("WorkoutService", "Sim bootcamp session completion failed", e)
                        }
                    }
                    runCatching { repository.deleteWorkout(workoutId) }
                        .onFailure { e ->
                            Log.w("WorkoutService", "Sim workout auto-delete failed (isSimulated flag prevents history pollution)", e)
                        }
                }

                // Sim runs: no post-run navigation — workout was deleted
                if (!SimulationController.isActive) {
                    WorkoutState.update { it.copy(completedWorkoutId = workoutId) }
                }
            }

            cleanupManagers()
            WorkoutState.reset()
            if (SimulationController.isActive) {
                SimulationController.deactivate()
            }
            notificationHelper.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            // Do NOT reset isStopping here. The service is about to be destroyed,
            // and resetting this flag lets onDestroy() re-enter the orphan-save block
            // which reads stale WorkoutState (distanceMeters=0 after reset) and
            // deletes the successfully saved workout.
        }
    }

    private fun handleStartFailure(message: String, cause: Throwable? = null) {
        Log.e("WorkoutService", message, cause)
        // Delete orphaned workout record if one was created before the failure
        if (workoutId > 0L) {
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching { repository.deleteWorkout(workoutId) }
            }
            workoutId = 0L
        }
        runCatching { notificationHelper.update(message) }
            .onFailure { Log.w("WorkoutService", "Failed to update notification", it) }
        cleanupManagers()
        WorkoutState.reset()
        WorkoutState.setPendingBootcampSessionId(null)
        WorkoutState.clearCompletedWorkoutId()
        notificationHelper.stop()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            .onFailure { Log.w("WorkoutService", "Failed to stop foreground", it) }
        stopSelf()
    }

    private fun cleanupManagers() {
        locationSource?.stop()
        locationSource = null
        hrSource = null
        clock = RealClock()
        coachingAudioManager?.destroy()
        coachingAudioManager = null
        zoneEngine = null
        adaptiveController = null
        autoPauseDetector?.reset()
        autoPauseDetector = null
        autoPauseStartMs = 0L
        totalAutoPausedMs = 0L
        alertPolicy.reset()
        coachingEventRouter.reset()
        trackPointRecorder.reset()
    }

    override fun onDestroy() {
        super.onDestroy()
        // If a workout was active but stopWorkout never ran, persist what we can
        if (workoutId > 0L && !isStopping) {
            Log.w("WorkoutService", "onDestroy called without stopWorkout — saving partial data")
            runCatching {
                // onDestroy runs on main thread; Room forbids main-thread DB access.
                // runBlocking is acceptable here as a last-resort save with a timeout to prevent ANR.
                kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    kotlinx.coroutines.withTimeoutOrNull(3000L) {
                        val finalDist = WorkoutState.snapshot.value.distanceMeters
                        val durationMs = clock.now() - workoutStartMs
                        // Must match stopWorkout(): only discard when BOTH short distance AND short time
                        if (finalDist < 200f && durationMs < 60_000L) {
                            repository.deleteWorkout(workoutId)
                        } else {
                            val workout = repository.getWorkoutById(workoutId)
                            if (workout != null && workout.endTime == 0L) {
                                repository.updateWorkout(workout.copy(
                                    endTime = clock.now(),
                                    totalDistanceMeters = finalDist
                                ))
                            }
                        }
                    }
                }
            }.onFailure { Log.e("WorkoutService", "Failed to save partial workout in onDestroy", it) }
        }
        startupJob?.cancel()
        simTickJob?.cancel()
        observationJob?.cancel()
        stopJob?.cancel()
        cleanupManagers()
        WorkoutState.reset()
    }

    private data class WorkoutTick(
        val hr: Int,
        val connected: Boolean,
        val distanceMeters: Float,
        val location: Location?,
        val speed: Float?
    )
}
