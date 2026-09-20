package com.venom.stealthcam.data.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.venom.stealthcam.R
import com.venom.stealthcam.core.Constants
import com.venom.stealthcam.domain.model.CameraFacing
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates and updates the foreground recording notification.
 *
 * Responsibilities:
 * — Register the notification channel exactly once (idempotent).
 * — Produce an initial "Preparing…" notification shown before the first frame.
 * — Produce a live recording notification with elapsed time and a Stop action.
 *
 * [NotificationCompat] is used (not [Notification.Builder] directly) so the
 * builder API is consistent across the range of API levels we target.
 */
@Singleton
class NotificationHelper @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val notificationManager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    // ── Channel ───────────────────────────────────────────────────────────────

    /**
     * Creates the notification channel. Safe to call multiple times —
     * [NotificationManager.createNotificationChannel] is idempotent.
     */
    fun createChannel() {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            Constants.NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW       // IMPORTANCE_LOW = silent, no heads-up
        ).apply {
            description = "Shows active recording status"
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    /**
     * Shown immediately in [RecordingService.onStartCommand] before the camera is open.
     * The system requires [startForeground] to be called within 5 seconds of
     * [startForegroundService]; this notification satisfies that requirement.
     */
    fun buildPreparingNotification(): Notification =
        baseBuilder()
            .setContentTitle("StealthCam")
            .setContentText("Preparing camera…")
            .build()

    /**
     * Shown while recording is active. Updated every second with [elapsedMs].
     *
     * @param elapsedMs  Milliseconds since [RecordingState.Recording.startTimeMs].
     * @param facing     Which camera is recording — shown in the title.
     */
    fun buildRecordingNotification(elapsedMs: Long, facing: CameraFacing): Notification {
        val stopPendingIntent = PendingIntent.getService(
            context,
            0,
            Intent(context, RecordingService::class.java).apply {
                action = Constants.ACTION_STOP_RECORDING
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return baseBuilder()
            .setContentTitle("Recording — ${facing.toDisplayName()}")
            .setContentText(formatElapsed(elapsedMs))
            .setWhen(System.currentTimeMillis() - elapsedMs)
            .setUsesChronometer(true)          // live timer in status-bar
            .addAction(
                R.drawable.ic_notification_camera,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    /** Posts an updated notification to an existing channel without needing to restart the service. */
    fun updateNotification(notification: Notification) {
        notificationManager.notify(Constants.RECORDING_NOTIFICATION_ID, notification)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun baseBuilder(): NotificationCompat.Builder =
        NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_camera)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET) // hidden on lock screen

    /**
     * Formats elapsed milliseconds as HH:MM:SS.
     * Used in the notification content text in addition to the chronometer
     * so the text is readable even on API levels that don't show chronometers.
     */
    private fun formatElapsed(ms: Long): String {
        val totalSeconds = ms / 1000L
        val hours   = totalSeconds / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    private fun CameraFacing.toDisplayName(): String = when (this) {
        CameraFacing.REAR  -> "Rear Camera"
        CameraFacing.FRONT -> "Front Camera"
    }
}
