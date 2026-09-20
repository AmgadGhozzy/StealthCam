package com.venom.stealthcam.core.trigger

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.venom.stealthcam.domain.model.CameraFacing
import com.venom.stealthcam.domain.model.RecordingState
import com.venom.stealthcam.domain.model.TriggerAction
import com.venom.stealthcam.domain.model.TriggerEvent
import com.venom.stealthcam.domain.repository.RecordingRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Globally intercepts volume button long-presses using a hybrid approach.
 *
 * 1. THE MAGIC KEY: A dummy `MediaSession` in `STATE_PLAYING`.
 *    Without this, Android OEM ROMs completely ignore volume keys when the screen is off.
 *    With this active, the OS thinks music is playing and allows volume keys to wake the audio system.
 *
 * 2. CHANNEL 1: AccessibilityService (Screen ON)
 *    When the screen is on, we intercept `KeyEvent` normally, start a timer, and fire.
 *
 * 3. CHANNEL 2: ContentObserver + Volume Centering (Screen OFF)
 *    When the screen is off, we let the system adjust the volume (because of the dummy MediaSession).
 *    We observe these actual volume changes. To solve the "Volume Max/Min limit" problem
 *    (where pressing the button at max volume doesn't change it), we "center" the volume
 *    (move it away from 0 or Max) when the screen turns off. Every time a key is pressed,
 *    we reset it to the center. This guarantees the volume ALWAYS changes, and we ALWAYS
 *    detect the hold pattern.
 */
@AndroidEntryPoint
class VolumeKeyAccessibilityService : AccessibilityService() {

    @Inject lateinit var triggerManager: VolumeTriggerManager
    @Inject lateinit var recordingRepository: RecordingRepository

    private val mainHandler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaSession: MediaSession? = null
    private val thresholdMs: Long = 500L

    // ── Channel 1: Accessibility (Screen ON) ──────────────────────────────────
    private val heldKeys = mutableSetOf<Int>()
    private val pendingTriggers = mutableMapOf<Int, Runnable>()

    // ── Channel 2: ContentObserver (Screen OFF) ───────────────────────────────
    private var isScreenOff = false
    private var baseMusicVolume = -1

    private var coFirstChangeTime = 0L
    private var coChangeCount = 0
    private var coDirection = 0
    private var coTriggered = false
    private var coResetRunnable: Runnable? = null

    // ── Debounce ──────────────────────────────────────────────────────────────
    private var lastTriggerTime = 0L
    private val triggerCooldownMs = 2000L

    // ══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ══════════════════════════════════════════════════════════════════════════

    private val volumeObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            if (isScreenOff) handleScreenOffVolumeChange()
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOff = true
                    centerVolumeForScreenOff()
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOff = false
                    resetCoState()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        // 1. Setup the dummy MediaSession (The Magic Key)
        mediaSession = MediaSession(this, "StealthCamVolumeSession").apply {
            setPlaybackState(
                PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                    .build()
            )
            setPlaybackToLocal(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            isActive = true
        }

        // 2. Register Observers & Receivers
        contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI, true, volumeObserver
        )
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)

        val pm = getSystemService(PowerManager::class.java)
        if (pm != null && !pm.isInteractive) {
            isScreenOff = true
            centerVolumeForScreenOff()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        cancelAllPending()
        heldKeys.clear()
        resetCoState()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cancelAllPending()
        heldKeys.clear()
        releaseWakeLock()
        resetCoState()

        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null

        runCatching { contentResolver.unregisterContentObserver(volumeObserver) }
        runCatching { unregisterReceiver(screenReceiver) }
        return super.onUnbind(intent)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Channel 1: Accessibility (Screen ON)
    // ══════════════════════════════════════════════════════════════════════════

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (isScreenOff) return false // Let system change volume so Observer catches it

        val keyCode = event.keyCode
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> handleDown(keyCode)
            KeyEvent.ACTION_UP   -> handleUp(keyCode)
            else                 -> false
        }
    }

    private fun handleDown(keyCode: Int): Boolean {
        if (keyCode in heldKeys) return true
        heldKeys.add(keyCode)
        acquireWakeLock()

        val runnable = Runnable { fireTrigger(keyCode) }
        pendingTriggers[keyCode] = runnable
        mainHandler.postDelayed(runnable, thresholdMs)
        return true
    }

    private fun handleUp(keyCode: Int): Boolean {
        heldKeys.remove(keyCode)
        if (cancelPendingFor(keyCode)) {
            // Short press: adjust volume normally
            val am = getSystemService(AudioManager::class.java)
            val dir = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
        }
        return true
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Channel 2: ContentObserver (Screen OFF)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Ensures the volume is neither 0 nor Max when the screen turns off.
     * This guarantees that any volume key press will successfully change the volume,
     * triggering our ContentObserver.
     */
    private fun centerVolumeForScreenOff() {
        val am = getSystemService(AudioManager::class.java) ?: return
        var current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        if (current == 0) {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, 1, 0)
            current = 1
        } else if (current == max) {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, max - 1, 0)
            current = max - 1
        }
        baseMusicVolume = current
    }

    private fun handleScreenOffVolumeChange() {
        val am = getSystemService(AudioManager::class.java) ?: return
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)

        if (current == baseMusicVolume) return // Ignore our own resets

        val direction = if (current > baseMusicVolume) 1 else -1

        acquireWakeLock()
        val now = SystemClock.elapsedRealtime()

        if (direction != coDirection || now - coFirstChangeTime > 1500) {
            // New hold sequence
            coFirstChangeTime = now
            coChangeCount = 1
            coDirection = direction
            coTriggered = false

            coResetRunnable?.let { mainHandler.removeCallbacks(it) }
            coResetRunnable = Runnable { resetCoState() }
            mainHandler.postDelayed(coResetRunnable!!, thresholdMs + 300L)

        } else {
            // Continuation of hold sequence (auto-repeat)
            coChangeCount++
            val elapsed = now - coFirstChangeTime

            if (!coTriggered && coChangeCount >= 2 && elapsed >= thresholdMs) {
                coTriggered = true
                coResetRunnable?.let { mainHandler.removeCallbacks(it) }

                val keyCode = if (direction > 0) KeyEvent.KEYCODE_VOLUME_UP else KeyEvent.KEYCODE_VOLUME_DOWN
                fireTrigger(keyCode)

                coResetRunnable = Runnable { resetCoState() }
                mainHandler.postDelayed(coResetRunnable!!, 1500L)
            }
        }

        // IMMEDIATELY reset the volume back to the base level so the next auto-repeat
        // event from the system will successfully change the volume again.
        am.setStreamVolume(AudioManager.STREAM_MUSIC, baseMusicVolume, 0)
    }

    private fun resetCoState() {
        coResetRunnable?.let { mainHandler.removeCallbacks(it) }
        coResetRunnable = null
        coFirstChangeTime = 0L
        coChangeCount = 0
        coDirection = 0
        coTriggered = false
        if (isScreenOff) centerVolumeForScreenOff()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Shared
    // ══════════════════════════════════════════════════════════════════════════

    private fun fireTrigger(keyCode: Int) {
        pendingTriggers.remove(keyCode)

        val now = SystemClock.elapsedRealtime()
        if (now - lastTriggerTime < triggerCooldownMs) return
        lastTriggerTime = now

        val isRecording = recordingRepository.recordingState.value.let { state ->
            state is RecordingState.Recording || state is RecordingState.Preparing
        }

        val event = if (isRecording) {
            TriggerEvent(action = TriggerAction.STOP)
        } else {
            val camera = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) CameraFacing.FRONT else CameraFacing.REAR
            TriggerEvent(action = TriggerAction.START, camera = camera)
        }

        triggerManager.emit(event)
    }

    private fun cancelPendingFor(keyCode: Int): Boolean {
        val runnable = pendingTriggers.remove(keyCode) ?: return false
        mainHandler.removeCallbacks(runnable)
        return true
    }

    private fun cancelAllPending() {
        pendingTriggers.values.forEach { mainHandler.removeCallbacks(it) }
        pendingTriggers.clear()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StealthCam:WakeLock")
                .also { it.setReferenceCounted(false) }
        }
        wakeLock?.acquire(4_000L)
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }
}
