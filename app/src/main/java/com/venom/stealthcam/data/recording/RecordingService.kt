package com.venom.stealthcam.data.recording

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.ServiceCompat
import com.venom.stealthcam.core.Constants
import com.venom.stealthcam.core.extension.hasAudioPermission
import com.venom.stealthcam.core.trigger.VolumeTriggerManager
import com.venom.stealthcam.domain.model.CameraFacing
import com.venom.stealthcam.domain.model.RecordingState
import com.venom.stealthcam.domain.model.TriggerAction
import com.venom.stealthcam.domain.repository.RecordingRepository
import com.venom.stealthcam.domain.repository.SettingsRepository
import com.venom.stealthcam.domain.usecase.ObserveRecordingStateUseCase
import com.venom.stealthcam.domain.usecase.StartRecordingUseCase
import com.venom.stealthcam.domain.usecase.StopRecordingUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground Service that hosts the Camera2 recording session.
 *
 * Lifecycle:
 * — Started via [startForegroundService] from the UI or [VolumeKeyAccessibilityService].
 * — Calls [startForeground] immediately in [onStartCommand] to satisfy the 5-second rule.
 * — Acquires a [PowerManager.PARTIAL_WAKE_LOCK] so the CPU stays awake with screen off.
 * — Collects [VolumeTriggerManager.events] to handle START/STOP from the volume buttons.
 * — Stops itself when [RecordingState] transitions to [RecordingState.Idle] or Error.
 *
 * Vibration feedback:
 * — 1× short pulse (80ms) when recording becomes active.
 * — 2× short pulses (40ms + 80ms gap + 40ms) when recording stops.
 *
 * Threading:
 * — [serviceScope] uses [Dispatchers.Main.immediate] for state observation.
 * — Use-case calls are dispatched internally to IO by the repository/engine.
 */
@AndroidEntryPoint
class RecordingService : Service() {

    @Inject lateinit var startRecordingUseCase: StartRecordingUseCase
    @Inject lateinit var stopRecordingUseCase: StopRecordingUseCase
    @Inject lateinit var observeRecordingStateUseCase: ObserveRecordingStateUseCase
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var recordingRepository: RecordingRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    /** Coroutine scope tied to the service lifetime. Cancelled in [onDestroy]. */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var wakeLock: PowerManager.WakeLock? = null
    private var timerJob: Job? = null
    private var hasStartedRecording = false

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannel()
        observeRecordingState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_START_REAR  -> beginRecording(CameraFacing.REAR)
            Constants.ACTION_START_FRONT -> beginRecording(CameraFacing.FRONT)
            Constants.ACTION_STOP_RECORDING -> endRecording()
        }
        // START_NOT_STICKY: if the process is killed, do not restart.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        timerJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    // ── Command handlers ──────────────────────────────────────────────────────

    /**
     * Acquires the wake lock, starts the foreground notification immediately,
     * then launches [StartRecordingUseCase] on the service scope.
     */
    private fun beginRecording(facing: CameraFacing) {
        // Guard: don't start a second recording if one is already running
        val currentState = recordingRepository.recordingState.value
        if (currentState is RecordingState.Recording || currentState is RecordingState.Preparing) return

        acquireWakeLock()

        // startForeground MUST be called before any long-running work.
        ServiceCompat.startForeground(
            this,
            Constants.RECORDING_NOTIFICATION_ID,
            notificationHelper.buildPreparingNotification(),
            buildForegroundServiceTypes()
        )

        serviceScope.launch {
            startRecordingUseCase(facing).onFailure {
                // Use case failed (e.g. camera permission denied, no space).
                // State is already Error via Camera2RecordingRepository; stopSelf
                // is triggered by the state observer below.
            }
        }
    }

    private fun endRecording() {
        serviceScope.launch {
            stopRecordingUseCase()
            // Service terminates itself via state observer once state reaches Idle.
        }
    }

    // ── State observation ────────────────────────────────────────────────────

    /**
     * Continuously observes [RecordingState] and:
     * — vibrates once when recording starts,
     * — vibrates twice when recording ends,
     * — starts the notification timer when recording begins,
     * — stops the service when the session ends or errors.
     */
    private fun observeRecordingState() {
        serviceScope.launch {
            observeRecordingStateUseCase().collect { state ->
                when (state) {
                    is RecordingState.Preparing,
                    is RecordingState.Recording -> {
                        hasStartedRecording = true
                        if (state is RecordingState.Recording) {
                            vibrateStart()
                            onRecordingActive(state)
                            startOverlay()
                        }
                    }
                    is RecordingState.Idle -> {
                        if (hasStartedRecording) {
                            vibrateStop()
                            stopOverlay()
                            onRecordingEnded()
                        }
                    }
                    is RecordingState.Error     -> {
                        stopOverlay()
                        onRecordingEnded()
                    }
                    else                        -> Unit
                }
            }
        }
    }

    private fun onRecordingActive(state: RecordingState.Recording) {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            val showNotification = settingsRepository.getSettings().isForegroundNotificationEnabled
            while (isActive) {
                if (showNotification) {
                    val elapsed = System.currentTimeMillis() - state.startTimeMs
                    notificationHelper.updateNotification(
                        notificationHelper.buildRecordingNotification(elapsed, state.cameraFacing)
                    )
                }
                delay(1_000)
            }
        }
    }

    private fun onRecordingEnded() {
        hasStartedRecording = false
        timerJob?.cancel()
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        serviceScope.launch {
            delay(200) // Allow vibration waveform to finish before the service context dies
            stopSelf()
        }
    }

    // ── Vibration ─────────────────────────────────────────────────────────────

    /**
     * Returns the system [Vibrator], handling both API 31+ [VibratorManager] and
     * the legacy [Vibrator] service for API < 31.
     */
    private fun getVibrator(): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(VibratorManager::class.java)
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
    }

    /**
     * Single short pulse on recording **start**.
     * 80ms is long enough to feel deliberate but short enough to not be intrusive.
     */
    private fun vibrateStart() {
        getVibrator().vibrate(
            VibrationEffect.createOneShot(80L, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }

    /**
     * Double pulse on recording **stop**: 40ms · pause 80ms · 40ms.
     * The pattern is clearly distinguishable from the single-start pulse.
     */
    private fun vibrateStop() {
        // timings: [delay, vibrate, sleep, vibrate] — first delay = 0
        val timings    = longArrayOf(0L, 40L, 80L, 40L)
        val amplitudes = intArrayOf(
            0,
            VibrationEffect.DEFAULT_AMPLITUDE,
            0,
            VibrationEffect.DEFAULT_AMPLITUDE
        )
        getVibrator().vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }

    // ── WakeLock ──────────────────────────────────────────────────────────────

    /**
     * Acquires a [PowerManager.PARTIAL_WAKE_LOCK] with a 12-hour safety timeout.
     * PARTIAL_WAKE_LOCK keeps the CPU running without keeping the screen on.
     */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "StealthCam::Recording"
        ).also {
            it.acquire(12L * 60L * 60L * 1_000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    /**
     * Starts the [RecordingOverlayService] red-dot overlay only when:
     * 1. The user has granted SYSTEM_ALERT_WINDOW permission.
     * 2. The [AppSettings.showRecordingOverlay] preference is enabled.
     */
    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        serviceScope.launch {
            val showOverlay = settingsRepository.getSettings().showRecordingOverlay
            if (showOverlay) {
                startService(Intent(this@RecordingService, RecordingOverlayService::class.java))
            }
        }
    }

    private fun stopOverlay() {
        stopService(Intent(this, RecordingOverlayService::class.java))
    }


    // ── Foreground service type ───────────────────────────────────────────────

    /**
     * Dynamically determines the foreground service type bitmask.
     * Always includes CAMERA; adds MICROPHONE only when the runtime permission is present.
     */
    private fun buildForegroundServiceTypes(): Int {
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        if (hasAudioPermission()) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        return types
    }
}

