package com.venom.stealthcam.domain.usecase

import android.content.Context
import android.view.Surface
import android.view.WindowManager
import com.venom.stealthcam.domain.model.BitrateMode
import com.venom.stealthcam.domain.model.CameraCapabilities
import com.venom.stealthcam.domain.model.CameraFacing
import com.venom.stealthcam.domain.model.RecordingConfig
import com.venom.stealthcam.domain.model.VideoCodec
import com.venom.stealthcam.domain.repository.CameraRepository
import com.venom.stealthcam.domain.repository.RecordingRepository
import com.venom.stealthcam.domain.repository.SettingsRepository
import com.venom.stealthcam.domain.repository.StorageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Orchestrates everything needed to start a recording session:
 *  1. Reads current [AppSettings].
 *  2. Queries [CameraCapabilities] for the requested [facing].
 *  3. Resolves auto-settings (codec, resolution, fps, bitrate) against capabilities.
 *  4. Asks [StorageRepository] to prepare the output folder and produce a file path.
 *  5. Builds a concrete [RecordingConfig] and hands it to [RecordingRepository].
 *
 * Every step returns [Result] so failures are explicit and propagated cleanly.
 */
class StartRecordingUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cameraRepository: CameraRepository,
    private val recordingRepository: RecordingRepository,
    private val settingsRepository: SettingsRepository,
    private val storageRepository: StorageRepository
) {
    suspend operator fun invoke(facing: CameraFacing): Result<Unit> = runCatching {
        val settings     = settingsRepository.getSettings()
        val capabilities = cameraRepository.getCapabilities(facing).getOrThrow()

        // Resolution — prefer user choice when the camera supports it, else max available.
        val resolution = settings.preferredResolution
            ?.takeIf { preferred -> preferred in capabilities.availableResolutions }
            ?: capabilities.maxResolution
            ?: error("No supported resolutions for camera facing=$facing")

        // Frame rate — prefer user choice when valid, else max available.
        val frameRate = settings.preferredFrameRate
            ?.takeIf { fps -> fps in capabilities.availableFrameRates }
            ?: capabilities.maxFrameRate

        // Codec — prefer user choice when the device encodes it, else best available.
        val codec = settings.videoCodec
            .takeIf { it in capabilities.supportedCodecs }
            ?: capabilities.recommendedCodec

        // Bitrate — resolve Auto to a calculated value.
        val bitrateBps = when (val mode = settings.bitrateMode) {
            is BitrateMode.Auto   -> calculateAutoBitrate(resolution.width, resolution.height, frameRate, codec)
            is BitrateMode.Manual -> mode.bps
        }

        // Build a timestamp file name according to the user's format.
        val timestamp = SimpleDateFormat(
            settings.storageConfig.fileNameFormat,
            Locale.getDefault()
        ).format(Date())

        // Prefix with '.' to hide in file managers when hidden storage is enabled.
        val fileName = if (settings.storageConfig.isHiddenStorage) ".$timestamp" else timestamp

        // Ensure the .StealthCam folder exists and get the absolute output path.
        storageRepository.ensureStealthFolder(settings.storageConfig).getOrThrow()
        val outputPath = storageRepository
            .resolveOutputFilePath(settings.storageConfig, fileName)
            .getOrThrow()

        val config = RecordingConfig(
            cameraId                      = capabilities.cameraId,
            cameraFacing                  = facing,
            resolution                    = resolution,
            frameRate                     = frameRate,
            videoCodec                    = codec,
            videoBitrateBps               = bitrateBps,
            isAudioEnabled                = settings.isAudioEnabled,
            outputFilePath                = outputPath,
            maxDurationMs                 = settings.maxDurationMs,
            maxFileSizeBytes              = settings.maxFileSizeBytes,
            enableElectronicStabilization = capabilities.supportsElectronicStabilization,
            enableOpticalStabilization    = capabilities.supportsOpticalStabilization,
            orientationHint               = computeOrientationHint(capabilities)
        )

        recordingRepository.startRecording(config).getOrThrow()
    }

    /**
     * Estimates a good bitrate using the "bits-per-pixel × fps" heuristic.
     * H.265 achieves equivalent quality at ~60 % of H.264 bitrate.
     *
     * Reference: ~0.07–0.10 bpp is considered broadcast quality for H.264.
     * We use 0.08 bpp as a balanced default for mobile.
     */
    private fun calculateAutoBitrate(width: Int, height: Int, fps: Int, codec: VideoCodec): Int {
        val bpp            = 0.08
        val h264BitrateBps = (width * height * fps * bpp).toInt()
        return when (codec) {
            VideoCodec.H264 -> h264BitrateBps
            VideoCodec.H265 -> (h264BitrateBps * 0.6).toInt()
        }
    }

    /**
     * Computes the MP4 orientation hint (0/90/180/270°) by combining:
     *  - [CameraCapabilities.sensorOrientation]: the angle the sensor is physically mounted at.
     *  - Device display rotation: how many degrees the display is rotated from its natural origin.
     *
     * Formula for rear cameras  : (sensorOrientation - deviceRotationDeg + 360) % 360
     * Formula for front cameras : (sensorOrientation + deviceRotationDeg) % 360
     *
     * The front-camera formula accounts for the mirroring that front sensors apply —
     * the rotation works in the opposite direction relative to the world.
     *
     * Falls back to [CameraCapabilities.sensorOrientation] alone when [WindowManager] is
     * unavailable (e.g. running in a test context).
     */
    private fun computeOrientationHint(capabilities: CameraCapabilities): Int {
        val windowManager = context.getSystemService(WindowManager::class.java)
        val displayRotation = windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0

        // Convert Surface.ROTATION_* constant to degrees
        val deviceRotationDeg = when (displayRotation) {
            Surface.ROTATION_0   -> 0
            Surface.ROTATION_90  -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else                 -> 0
        }

        val sensor = capabilities.sensorOrientation
        return if (capabilities.facing == CameraFacing.FRONT) {
            (sensor + deviceRotationDeg) % 360
        } else {
            (sensor - deviceRotationDeg + 360) % 360
        }
    }
}
