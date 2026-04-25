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
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.hrcoach.service.workout.notification.NotifContentFormatter
import com.hrcoach.service.workout.notification.NotifPayload
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
        const val ACTION_RELOAD_AUDIO_SETTINGS = "com.hrcoach.ACTION_RELOAD_AUDIO_SETTINGS"
        const val ACTION_FINISH_BOOTCAMP_EARLY = "com.hrcoach.ACTION_FINISH_BOOTCAMP_EARLY"
        const val EXTRA_CONFIG_JSON = "config_json"
        const val EXTRA_DEVICE_ADDRESS = "device_address"

        private const val NOTIFICATION_ID = 1
        // v2: upgraded from IMPORTANCE_LOW → IMPORTANCE_DEFAULT so the notification renders
        // as a full-width MediaStyle card (Spotify-style) instead of a compact pill. A new
        // channel ID is required because Android preserves the user's channel importance
        // setting — changing the code on the old channel ID has no effect on existing installs.
        private const val CHANNEL_ID = "workout_media_v2"
        private const val TRACK_POINT_INTERVAL_MS = 5_000L
        private const val AUTO_PAUSE_GRACE_MS = 20_000L
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
    private lateinit var mediaSession: MediaSessionCompat
    private var activeWorkoutConfig: WorkoutConfig? = null
    private var workoutTotalSeconds: Long = 0L

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
    // Auto-pause guard: the runner must have actually moved at least once before auto-pause
    // can fire. Prevents a pause tone while the runner's still tying their shoes or pocketing
    // their phone — "paused from movement" only makes sense if you've moved.
    private var hasMovedSinceStart: Boolean = false
    // Suppress the TTS "Run autopaused" announcement on the first auto-pause of a session —
    // tone still plays (safety-critical), but a first-run surprise of TTS is avoided. Subsequent
    // pauses during the same run still announce.
    private var autoPauseCountThisSession: Int = 0

    private var sessionDistanceUnit = com.hrcoach.domain.model.DistanceUnit.KM
    private var workoutId: Long = 0L
    private var workoutStartMs: Long = 0L
    private var totalPausedMs: Long = 0L
    private var pauseStartMs: Long = 0L
    private var isStopping: Boolean = false
    private var latestTick: WorkoutTick? = null

    private var hrSampleSum: Long = 0L
    private var hrSampleCount: Int = 0

    @Volatile
    private var sessionReleased = false

    private val hrSampleBuffer = ArrayDeque<Int>()      // rolling 120-sample window for artifact detection
    private val hrSessionSamples = mutableListOf<Int>() // full session for hrMax detection
    private var cadenceLockSuspected: Boolean = false
    private var hrSamplesSinceLastArtifactCheck: Int = 0 // counts samples to fire check every 10

    override fun onCreate() {
        super.onCreate()
        notificationHelper = WorkoutNotificationHelper(this, CHANNEL_ID, NOTIFICATION_ID)
        mediaSession = MediaSessionCompat(this, "CardeaWorkout").apply {
            isActive = true
        }
        notificationHelper.attachMediaSession(mediaSession.sessionToken)
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

            ACTION_RELOAD_AUDIO_SETTINGS -> {
                val audioSettings = audioSettingsRepository.getAudioSettings()
                coachingAudioManager?.applySettings(audioSettings)
                // CoachingEventRouter needs to see cadence changes mid-workout too. Other audio
                // fields (voiceVerbosity, enableKmSplits, etc.) flow through CoachingAudioManager;
                // cadence is the one setting the router owns directly.
                coachingEventRouter.confirmCadence = audioSettings.inZoneConfirmCadence
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

            ACTION_FINISH_BOOTCAMP_EARLY -> {
                // Graceful bootcamp-session finish from the active-run settings menu.
                // Mirrors the WFS sim-path completion flow: read lastTuningDirection from the
                // saved AdaptiveProfile, call the completer, then funnel through stopWorkout()
                // for the authoritative teardown (notification stop gate, dual-pause cleanup,
                // metrics save, stopForeground, stopSelf). If there is no pending bootcamp
                // session, we still stop the workout — the action is a "finish" first.
                lifecycleScope.launch(Dispatchers.IO) {
                    val pendingId = WorkoutState.snapshot.value.pendingBootcampSessionId
                    if (pendingId != null && workoutId > 0L) {
                        runCatching {
                            val profile = adaptiveProfileRepository.getProfile()
                            bootcampSessionCompleter.complete(
                                workoutId = workoutId,
                                pendingSessionId = pendingId,
                                tuningDirection = profile.lastTuningDirection ?: TuningDirection.HOLD
                            )
                        }.onFailure { e ->
                            Log.w("WorkoutService", "Bootcamp early-finish completion failed", e)
                        }
                    }
                    // skipShortRunDiscard=true is critical here: we just credited the
                    // bootcamp session with completedWorkoutId=this workoutId. If the
                    // user ended within 60s/200m, the normal discard branch would delete
                    // the workout row and FK CASCADE SET NULL would orphan the session.
                    // Pass true unconditionally on this action — even if no pendingId
                    // (defensive), the UX is that the user explicitly asked to finish.
                    stopWorkout(skipShortRunDiscard = true)
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
        hasMovedSinceStart = false
        autoPauseCountThisSession = 0
        workoutStartMs = 0L
        alertPolicy.reset()
        coachingEventRouter.reset()
        trackPointRecorder.reset()
        hrSampleBuffer.clear()
        hrSessionSamples.clear()
        hrSampleSum = 0L
        hrSampleCount = 0
        hrSamplesSinceLastArtifactCheck = 0
        cadenceLockSuspected = false

        activeWorkoutConfig = workoutConfig
        workoutTotalSeconds = computeTotalSeconds(workoutConfig)
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

                // Navigate to workout screen immediately so user sees the countdown overlay.
                // Seeding countdownSecondsRemaining = 3 here (rather than waiting for
                // StartupSequencer) covers the TTS-briefing phase too — during that window
                // the overlay holds on "3" while the voice briefing plays, and the workout
                // timer in the ViewModel stays at 0. Without this, the VM's local clock
                // would tick up during TTS + countdown, then visibly rewind to 0 when the
                // service's authoritative clock took over after the countdown.
                WorkoutState.update { current ->
                    WorkoutSnapshot(
                        isRunning = true,
                        isPaused = false,
                        countdownSecondsRemaining = 3,
                        pendingBootcampSessionId = current.pendingBootcampSessionId,
                    )
                }

                // Open the TTS debug log for this run BEFORE the briefing/countdown so the start
                // sequence itself is captured. Keeps the last 2 runs on disk in
                // filesDir/tts_debug/run_*.log. Diagnostic-only — does not touch saved workouts.
                coachingAudioManager?.startDebugLog(
                    isSimulation = SimulationController.isActive,
                    workoutMode = workoutConfig.mode.name
                )

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
                // Seed router's cadence from current AudioSettings at session start; subsequent
                // changes flow through ACTION_RELOAD_AUDIO_SETTINGS.
                coachingEventRouter.confirmCadence = audioSettingsRepository.getAudioSettings().inZoneConfirmCadence

                // Suppress auto-pause for 15 seconds so the runner can pocket
                // their phone and start moving without seeing "Auto-Paused"
                autoPauseGraceUntilMs = clock.now() + AUTO_PAUSE_GRACE_MS

                val hrAlreadyConnected = SimulationController.isActive || bleCoordinator.isConnected.value
                WorkoutState.update { current ->
                    current.copy(
                        targetHr = workoutConfig.targetHrAtDistance(0f) ?: 0,
                        guidanceText = when {
                            SimulationController.isActive -> "SIM STARTING"
                            hrAlreadyConnected -> "Get set"
                            else -> "Searching for HR signal"
                        },
                        autoPauseEnabled = sessionAutoPauseEnabled,
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

        // Auto-pause detection: run before elapsed-time math so state is fresh this tick.
        // Gated by THREE conditions:
        //   1. `sessionAutoPauseEnabled` — user preference
        //   2. `nowMs >= autoPauseGraceUntilMs` — wall-time grace (20s after start)
        //   3. `hasMovedSinceStart` — only pause-from-movement after actual movement. Prevents
        //      a pause tone while runner is still tying shoes or pocketing phone — "paused from
        //      movement" is only meaningful after you've moved.
        // ~0.5 m/s ≈ 1.8 km/h covers walking; below that is noise/GPS jitter.
        if (!hasMovedSinceStart && (tick.speed ?: 0f) > 0.5f) hasMovedSinceStart = true
        var isAutoPaused = WorkoutState.snapshot.value.isAutoPaused  // read BEFORE potential update
        if (sessionAutoPauseEnabled && nowMs >= autoPauseGraceUntilMs && hasMovedSinceStart) {
            when (autoPauseDetector?.update(tick.speed, nowMs)) {
                AutoPauseEvent.PAUSED -> {
                    isAutoPaused = true  // use local var immediately — no StateFlow lag
                    autoPauseStartMs = nowMs
                    autoPauseCountThisSession++
                    locationSource?.setMoving(false)
                    WorkoutState.update { it.copy(isAutoPaused = true) }
                    coachingAudioManager?.playPauseFeedback(paused = true)
                    // Skip TTS on first auto-pause of session — tone + banner is enough signal
                    // for a first-time surprise. Subsequent pauses announce normally.
                    if (autoPauseCountThisSession > 1) {
                        coachingAudioManager?.speakAnnouncement("Run autopaused")
                    }
                }
                AutoPauseEvent.RESUMED -> {
                    isAutoPaused = false  // use local var immediately
                    // Guard: autoPauseStartMs may have been zeroed by pauseWorkout() if manual
                    // pause overlapped auto-pause — only accumulate if we still own the timer.
                    if (autoPauseStartMs > 0L) {
                        totalAutoPausedMs += nowMs - autoPauseStartMs
                    }
                    autoPauseStartMs = 0L
                    locationSource?.setMoving(true)
                    WorkoutState.update { it.copy(isAutoPaused = false) }
                    coachingAudioManager?.playPauseFeedback(paused = false)
                    coachingAudioManager?.speakAnnouncement("Run resumed")
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
            var pausedSnapshot: WorkoutSnapshot? = null
            WorkoutState.update { current ->
                current.copy(
                    currentHr = if (tick.connected) tick.hr else 0,
                    targetHr = target ?: 0,
                    hrConnected = tick.connected,
                    guidanceText = "Workout paused",
                    projectionReady = false,
                    predictedHr = 0,
                    avgHr = current.avgHr
                ).also { pausedSnapshot = it }
            }
            // Build rich payload so the lockscreen shows "· Paused" title, dimmed badge,
            // Resume action button, and the MediaSession reports STATE_PAUSED.
            // Use the captured snapshot directly to avoid the same-tick StateFlow propagation race.
            val pausedPayload = NotifContentFormatter.format(
                snapshot = pausedSnapshot!!,
                config = workoutConfig,
                totalSeconds = workoutTotalSeconds,
            )
            notificationHelper.update(pausedPayload)
            updateMediaSessionState(pausedPayload)
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
            hrSamplesSinceLastArtifactCheck++
            // Check for cadence lock every 10 new samples once buffer has 30+.
            // Using an explicit counter rather than size % 10 because size stays permanently
            // at 120 (the cap) once full, making size % 10 == 0 fire on every single tick.
            if (hrSampleBuffer.size >= 30 && hrSamplesSinceLastArtifactCheck >= 10) {
                hrSamplesSinceLastArtifactCheck = 0
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

        // Which preset branch produced the guidance? We use this below to decide whether the
        // string is safe to speak. Preset overrides are long, static motivational quips meant
        // for on-screen display only — speaking them every zone re-entry (RETURN_TO_ZONE) or
        // every projected-drift (PREDICTIVE_WARNING) reads as nagging. See router docstring.
        val isPresetQuip =
            (workoutConfig.guidanceTag == "strides" && elapsedSeconds >= 1200 && zoneStatus == ZoneStatus.IN_ZONE) ||
            (zoneStatus == ZoneStatus.IN_ZONE && (workoutConfig.presetId == "zone2_base" || workoutConfig.presetId == "zone2_with_strides"))

        val guidance = when {
            isAutoPaused -> "STOPPED \u2022 ALERTS PAUSED"
            // Preset-specific overrides take priority over adaptive guidance
            workoutConfig.guidanceTag == "strides" && elapsedSeconds >= 1200 && zoneStatus == ZoneStatus.IN_ZONE ->
                "Time for strides! 4\u20136 \u00d7 20s fast & smooth, jog easy 60\u201390s between"
            zoneStatus == ZoneStatus.IN_ZONE && (workoutConfig.presetId == "zone2_base" || workoutConfig.presetId == "zone2_with_strides") ->
                "Easy pace builds your aerobic engine. Hold a conversation."
            adaptiveResult != null -> adaptiveResult.guidance
            else -> when (zoneStatus) {
                ZoneStatus.ABOVE_ZONE -> "SLOW DOWN NOW"
                ZoneStatus.BELOW_ZONE -> "SPEED UP NOW"
                ZoneStatus.IN_ZONE -> "HOLD THIS PACE"
                ZoneStatus.NO_DATA -> "GET HR SIGNAL"
            }
        }

        // Voice-layer guidance: null for preset quips and pause text; otherwise the same
        // contextual/adaptive string used on screen. Null makes VoicePlayer fall back to its
        // fixed per-event phrasing ("Back in zone", "Watch your pace"). AlertPolicy only fires
        // SPEED_UP/SLOW_DOWN when out-of-zone, so it never sees the zone2 IN_ZONE preset quip
        // — we still feed it the display guidance so short ALL_CAPS fallbacks like "SPEED UP
        // NOW" (used when adaptive is absent) continue to reach VoicePlayer as today.
        val voiceGuidance: String? = if (isPresetQuip || isAutoPaused) null else guidance

        var nextSnapshot: WorkoutSnapshot? = null
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
            ).also { nextSnapshot = it }
        }

        if (!isAutoPaused) {
            // Publish current HR slope to the audio manager so the PREDICTIVE_WARNING
            // fallback can pick direction-aware phrasing ("ease off" vs "pick it up")
            // when adaptive guidance text is absent. Must precede any fireEvent call
            // that could emit PREDICTIVE_WARNING below.
            coachingAudioManager?.setHrSlope(adaptiveResult?.hrSlopeBpmPerMin ?: 0f)
            val warmupGraceSec = workoutConfig.effectiveWarmupGraceSec()
            coachingEventRouter.route(
                workoutConfig = workoutConfig,
                connected = tick.connected,
                distanceMeters = tick.distanceMeters,
                elapsedSeconds = elapsedSeconds,
                zoneStatus = zoneStatus,
                adaptiveResult = adaptiveResult,
                guidance = voiceGuidance,
                nowMs = nowMs,
                distanceUnit = sessionDistanceUnit,
                warmupGraceSec = warmupGraceSec,
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
                    // Reset predictive cooldown so the two alert systems don't fire back-to-back.
                    coachingEventRouter.resetPredictiveWarningTimer()
                    val pace = adaptiveResult?.currentPaceMinPerKm
                    // Thread current+target HR so VoicePlayer can append "N under/over" on FULL
                    // verbosity. `target` is the single-BPM target computed upstream; `tick.hr`
                    // is the same raw HR that drove this zoneStatus — keeping the numbers
                    // consistent with what triggered the alert.
                    coachingAudioManager?.fireEvent(
                        event,
                        eventGuidance,
                        paceMinPerKm = pace,
                        currentHr = if (tick.connected && tick.hr > 0) tick.hr else null,
                        targetHr = target,
                    )
                    coachingEventRouter.noteExternalAlert(nowMs)
                },
                // Threaded through so AlertPolicy can suppress SLOW_DOWN/SPEED_UP when HR is
                // already self-correcting (|slope| ≥ 1.5 bpm/min) or when the runner is walking
                // (pace > 10 min/km sustained 30s). Both gates default off when unset, so they
                // can be disabled by passing default values instead of the live values here.
                hrSlopeBpmPerMin = adaptiveResult?.hrSlopeBpmPerMin ?: 0f,
                currentPaceMinPerKm = adaptiveResult?.currentPaceMinPerKm,
                elapsedSeconds = elapsedSeconds,
                warmupGraceSec = warmupGraceSec
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

        // Use the atomically-captured snapshot instead of re-reading the StateFlow to avoid
        // the same-tick race where MutableStateFlow.update may not have propagated yet.
        val payload = NotifContentFormatter.format(
            snapshot = nextSnapshot!!,
            config = workoutConfig,
            totalSeconds = workoutTotalSeconds,
        )
        notificationHelper.update(payload)
        updateMediaSessionState(payload)
    }

    private fun pauseWorkout() {
        // Capture the timestamp before emitting state so processTick() can never observe
        // isPaused=true while pauseStartMs is still 0.
        val nowMs = clock.now()
        var didPause = false
        var pausedSnapshot: WorkoutSnapshot? = null
        WorkoutState.update { current ->
            if (!current.isRunning || current.isPaused) current else {
                didPause = true
                current.copy(isPaused = true, guidanceText = "Workout paused")
                    .also { pausedSnapshot = it }
            }
        }
        if (didPause) {
            pauseStartMs = nowMs
            // If auto-pause is also active, latch its accumulated time now so the overlapping
            // period isn't double-subtracted from elapsedSeconds when auto-pause resolves.
            if (WorkoutState.snapshot.value.isAutoPaused && autoPauseStartMs > 0L) {
                totalAutoPausedMs += nowMs - autoPauseStartMs
                autoPauseStartMs = 0L
            }
            coachingAudioManager?.playPauseFeedback(paused = true)
            val config = activeWorkoutConfig
            if (config != null) {
                val payload = NotifContentFormatter.format(
                    snapshot = pausedSnapshot!!,
                    config = config,
                    totalSeconds = workoutTotalSeconds,
                )
                notificationHelper.update(payload)
                updateMediaSessionState(payload)
            } else {
                notificationHelper.update("Workout paused")
            }
        }
    }

    private fun resumeWorkout() {
        val nowMs = clock.now()
        var didResume = false
        var resumedSnapshot: WorkoutSnapshot? = null
        WorkoutState.update { current ->
            if (!current.isRunning || !current.isPaused) current else {
                didResume = true
                current.copy(isPaused = false)
                    .also { resumedSnapshot = it }
            }
        }
        if (didResume && pauseStartMs > 0L) {
            totalPausedMs += nowMs - pauseStartMs
            pauseStartMs = 0L
            // If auto-pause is still active, restart its timer from now so the period
            // already counted by pauseWorkout() is not re-counted when auto-pause resolves.
            if (WorkoutState.snapshot.value.isAutoPaused) {
                autoPauseStartMs = nowMs
            }
            coachingAudioManager?.playPauseFeedback(paused = false)
            val config = activeWorkoutConfig
            if (config != null) {
                val payload = NotifContentFormatter.format(
                    snapshot = resumedSnapshot!!,
                    config = config,
                    totalSeconds = workoutTotalSeconds,
                )
                notificationHelper.update(payload)
                updateMediaSessionState(payload)
            } else {
                notificationHelper.update("Workout resumed")
            }
        }
    }

    /**
     * [skipShortRunDiscard] — when true, disables the short-run discard branch that
     * normally deletes workouts under 200m AND under 1 min. Set by the
     * ACTION_FINISH_BOOTCAMP_EARLY handler so that ending a bootcamp session within
     * the first minute preserves BOTH the workout row AND the bootcamp session credit.
     * Without this, the FK `bootcamp_sessions.completedWorkoutId -> workouts.id`
     * (ON DELETE SET NULL, see AppDatabase) would null out after the workout row is
     * deleted, leaving a "completed" session that points to nothing.
     */
    private fun stopWorkout(skipShortRunDiscard: Boolean = false) {
        if (isStopping) return
        isStopping = true
        val finalHrSampleSum = hrSampleSum
        val finalHrSampleCount = hrSampleCount
        hrSampleSum = 0L
        hrSampleCount = 0

        // Audio bookend: symmetrical with playStartSequence. Must fire BEFORE teardown
        // because cleanupManagers() destroys the CoachingAudioManager. speakAnnouncement
        // hands text to the TTS engine which outlives our scope — final utterance will
        // complete even after the service transitions.
        runCatching {
            val now = clock.now()
            val currentPauseMs = if (pauseStartMs > 0L) now - pauseStartMs else 0L
            val currentAutoPauseMs = if (autoPauseStartMs > 0L) now - autoPauseStartMs else 0L
            val activeSec = (now - workoutStartMs - totalPausedMs - totalAutoPausedMs
                - currentPauseMs - currentAutoPauseMs).coerceAtLeast(0L) / 1000L
            val distanceMeters = WorkoutState.snapshot.value.distanceMeters
            val avgHr = if (finalHrSampleCount > 0) {
                (finalHrSampleSum.toFloat() / finalHrSampleCount).toInt()
            } else null
            // Skip bookend for runs that will be discarded (< 200m AND < 1 min — same
            // threshold used below). A phantom "Workout complete" on a 10-second tap
            // would be confusing. When skipShortRunDiscard is set (end-early from
            // bootcamp), the run is kept regardless, so play the bookend.
            val willBeDiscarded = !skipShortRunDiscard && distanceMeters < 200f && activeSec < 60L
            if (!willBeDiscarded) {
                coachingAudioManager?.playEndSequence(
                    distanceMeters = distanceMeters,
                    activeDurationSec = activeSec,
                    avgHr = avgHr
                )
            }
        }.onFailure { e ->
            Log.w("WorkoutService", "End-of-workout bookend failed (non-fatal)", e)
        }

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
            // workoutId may be 0 if handleStartFailure already deleted the row (e.g. startup
            // failed after the DB row was created); skip the save in that case.
            if (workoutId > 0L && finalTick != null) {
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
                        val currentPauseMs = if (pauseStartMs > 0L) now - pauseStartMs else 0L
                        val currentAutoPauseMs = if (autoPauseStartMs > 0L) now - autoPauseStartMs else 0L
                        val activeSec = (now - workoutStartMs - totalPausedMs - totalAutoPausedMs - currentPauseMs - currentAutoPauseMs)
                            .coerceAtLeast(0L) / 1000L
                        repository.updateWorkout(
                            currentWorkout.copy(
                                endTime = now,
                                totalDistanceMeters = WorkoutState.snapshot.value.distanceMeters,
                                activeDurationSeconds = activeSec
                            )
                        )
                    }
                }.onFailure { e ->
                    Log.e("WorkoutService", "Failed to save essential workout data", e)
                }

                // Discard runs that are too short to be meaningful (< 200m AND < 1 min).
                // Bypassed when skipShortRunDiscard is set — the end-early bootcamp path
                // credits the session before calling stopWorkout(), so deleting the
                // workout row here would null out bootcamp_sessions.completedWorkoutId
                // via ON DELETE SET NULL and leave a completed session pointing nowhere.
                val finalDistance = WorkoutState.snapshot.value.distanceMeters
                val durationMs = now - workoutStartMs
                if (!skipShortRunDiscard && finalDistance < 200f && durationMs < 60_000L) {
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
                    // Formula is owned by MetricsCalculator.trimpFrom(); this is the authoritative
                    // invocation because only WFS has access to active-run-time duration (pauses
                    // and auto-pauses subtracted). MetricsCalculator.deriveFromPaceSamples()
                    // cannot compute TRIMP itself because its pace-sample inputs don't carry
                    // pause-boundary information.
                    val durationMin = ((now - workoutStartMs - totalPausedMs - totalAutoPausedMs)
                        .coerceAtLeast(0L)) / 60_000f
                    val sessionAvgHr = reliableMetrics?.avgHr
                        ?: if (finalHrSampleCount > 0) (finalHrSampleSum.toFloat() / finalHrSampleCount) else null
                    val hrMaxEst = currentProfile.hrMax?.toFloat() ?: ageBasedFallback.toFloat()
                    val trimpScore = MetricsCalculator.trimpFrom(
                        durationMin = durationMin,
                        avgHr = sessionAvgHr,
                        hrMax = hrMaxEst
                    )

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
                    // Drain cue counts (CoachingEvent.name -> Int) and serialize to JSON for the
                    // post-run "Sounds heard today" recap. Null when no cues fired (e.g. OFF verbosity
                    // or a very short run). Must be drained AFTER all fireEvent calls for the session
                    // are complete; at stopWorkout this is guaranteed because WFS destroys the manager
                    // just after.
                    val cueCountsMap = coachingAudioManager?.consumeCueCounts().orEmpty()
                    val cueCountsJson = if (cueCountsMap.isEmpty()) null
                        else com.google.gson.Gson().toJson(cueCountsMap.mapKeys { it.key.name })

                    completeMetrics?.copy(cueCountsJson = cueCountsJson)
                        ?.let { workoutMetricsRepository.saveWorkoutMetrics(it) }

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
                        // Clear immediately after completion so reset() doesn't preserve the ID
                        // and cause the bootcamp screen to re-trigger completion on next load.
                        WorkoutState.setPendingBootcampSessionId(null)
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
        // Close the TTS debug log before destroying the audio manager. Idempotent — safe across
        // all cleanup paths (normal stop, handleStartFailure, onDestroy).
        coachingAudioManager?.endDebugLog("cleanup")
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
        // Cancel coroutines FIRST so processTick/updateMediaSessionState cannot fire
        // against a released MediaSession and throw IllegalStateException.
        startupJob?.cancel()
        simTickJob?.cancel()
        observationJob?.cancel()
        stopJob?.cancel()
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
        cleanupManagers()
        WorkoutState.reset()
        // Release MediaSession last — after all coroutines are cancelled and cleanup is done —
        // so updateMediaSessionState() cannot be called post-release.
        sessionReleased = true
        if (::mediaSession.isInitialized) {
            mediaSession.isActive = false
            mediaSession.release()
        }
    }

    private fun computeTotalSeconds(config: WorkoutConfig): Long {
        // 1. Explicit planned duration
        config.plannedDurationMinutes?.let { return it.toLong() * 60L }
        // 2. Sum of time-based segment durations
        val segSum = config.segments.sumOf { (it.durationSeconds ?: 0).toLong() }
        if (segSum > 0L) return segSum
        // 3. Unknown — treated as free run / indeterminate progress
        return 0L
    }

    private fun updateMediaSessionState(payload: NotifPayload) {
        if (sessionReleased || !::mediaSession.isInitialized) return
        val state = PlaybackStateCompat.Builder()
            .setState(
                if (payload.isPaused) PlaybackStateCompat.STATE_PAUSED
                else PlaybackStateCompat.STATE_PLAYING,
                payload.elapsedSeconds * 1000L,
                if (payload.isPaused) 0f else 1f,
            )
            .setActions(
                PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY,
            )
            .build()
        mediaSession.setPlaybackState(state)

        // 320px artwork for the MediaSession lockscreen player — matches
        // MediaMetadataCompat's internal size threshold so no rescaling occurs.
        val lockscreenArt = notificationHelper.lockscreenArtFor(payload)
        val metadata = MediaMetadataCompat.Builder()
            .putString(
                MediaMetadataCompat.METADATA_KEY_TITLE,
                payload.titleText,
            )
            .putString(
                MediaMetadataCompat.METADATA_KEY_ARTIST,
                payload.subtitleText,
            )
            .putLong(
                MediaMetadataCompat.METADATA_KEY_DURATION,
                if (payload.isIndeterminate) 0L else payload.totalSeconds * 1000L,
            )
            .putBitmap(
                MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
                lockscreenArt,
            )
            .build()
        mediaSession.setMetadata(metadata)
    }

    private data class WorkoutTick(
        val hr: Int,
        val connected: Boolean,
        val distanceMeters: Float,
        val location: Location?,
        val speed: Float?
    )
}
