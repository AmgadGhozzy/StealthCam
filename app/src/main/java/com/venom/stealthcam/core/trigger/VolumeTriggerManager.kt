package com.venom.stealthcam.core.trigger

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.venom.stealthcam.core.Constants
import com.venom.stealthcam.data.recording.RecordingService
import com.venom.stealthcam.domain.model.CameraFacing
import com.venom.stealthcam.domain.model.TriggerAction
import com.venom.stealthcam.domain.model.TriggerEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A thin, stateless event bus that bridges [VolumeKeyAccessibilityService] (hardware input)
 * with [RecordingService] (recording control).
 *
 * Architecture:
 *   VolumeKeyAccessibilityService → emit() here
 *   VolumeTriggerManager          → starts RecordingService via Intent
 *
 * The separation keeps the AccessibilityService ignorant of recording internals, and
 * ensures the service is actually started even if it was completely dead.
 */
@Singleton
class VolumeTriggerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Called by [VolumeKeyAccessibilityService] when a qualifying long-press is detected.
     * Dispatches an Intent to [RecordingService] to start or stop recording.
     */
    fun emit(event: TriggerEvent) {
        val intent = Intent(context, RecordingService::class.java)
        when (event.action) {
            TriggerAction.START -> {
                intent.action = if (event.camera == CameraFacing.FRONT) {
                    Constants.ACTION_START_FRONT
                } else {
                    Constants.ACTION_START_REAR
                }
                ContextCompat.startForegroundService(context, intent)
            }
            TriggerAction.STOP -> {
                intent.action = Constants.ACTION_STOP_RECORDING
                context.startService(intent)
            }
        }
    }
}
