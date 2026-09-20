package com.venom.stealthcam.data.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.media.MediaCodecList
import android.media.MediaRecorder
import com.venom.stealthcam.domain.model.CameraCapabilities
import com.venom.stealthcam.domain.model.CameraFacing
import com.venom.stealthcam.domain.model.Resolution
import com.venom.stealthcam.domain.model.VideoCodec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converts Android's [CameraCharacteristics] into the domain [CameraCapabilities] model.
 *
 * All queries are performed synchronously and are fast (no I/O, no Binder calls beyond
 * what [android.hardware.camera2.CameraManager.getCameraCharacteristics] already did).
 * This class is deliberately stateless and injectable as a [Singleton].
 */
@Singleton
class CameraCapabilityQuery @Inject constructor() {

    /**
     * Builds a [CameraCapabilities] object for a camera that has already been identified.
     *
     * @param cameraId        The Camera2 camera ID string (e.g. "0", "1").
     * @param facing          The domain-facing enum resolved by the caller.
     * @param characteristics Raw [CameraCharacteristics] from [android.hardware.camera2.CameraManager].
     */
    fun buildCapabilities(
        cameraId: String,
        facing: CameraFacing,
        characteristics: CameraCharacteristics
    ): CameraCapabilities {

        // ── Resolutions ──────────────────────────────────────────────────────
        // We query sizes for MediaRecorder specifically — these are the sizes the
        // camera HAL guarantees it can feed to a MediaRecorder surface.
        val configMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        val rawSizes = configMap?.getOutputSizes(MediaRecorder::class.java)?.map { size -> 
            Resolution(size.width, size.height) 
        } ?: emptyList()

        // Hardware video encoders often crash (error -22) if fed massive 4:3 photo resolutions (e.g. 4032x3024).
        // We filter for video resolutions that are approximately 16:9 (aspect ratio ~1.77), capped at 4K.
        // Some devices pad 1080p to 1088p (multiples of 16), so we use a 0.05 tolerance.
        val standard16by9 = rawSizes.filter { 
            val ratio = it.width.toFloat() / it.height.toFloat()
            kotlin.math.abs(ratio - (16f / 9f)) < 0.05f && it.width <= 3840
        }
        
        val resolutions: List<Resolution> = if (standard16by9.isNotEmpty()) {
            standard16by9.sortedDescending()
        } else {
            // Fallback for weird cameras: any size <= 1080p
            rawSizes.filter { it.width <= 1920 && it.height <= 1080 }.sortedDescending()
        }.ifEmpty { 
            rawSizes.sortedDescending() 
        }

        // ── Frame Rates ───────────────────────────────────────────────────────
        // AE target FPS ranges describe [min, max] pairs. For recording we care
        // about the upper bound — the peak FPS achievable at that AE configuration.
        val fpsRanges = characteristics.get(
            CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
        ) ?: emptyArray()

        val availableFrameRates: List<Int> = fpsRanges
            .map { range -> range.upper }
            .distinct()
            .sorted()
            .ifEmpty { listOf(30) }  // 30 fps fallback for cameras that don't report ranges

        // ── Electronic Image Stabilisation (EIS) ─────────────────────────────
        // Camera2 names this "video stabilization" mode. ON = EIS active.
        val videoStabModes: IntArray = characteristics.get(
            CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES
        ) ?: intArrayOf()
        val supportsEIS = CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON in videoStabModes

        // ── Optical Image Stabilisation (OIS) ────────────────────────────────
        val oisModes: IntArray = characteristics.get(
            CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION
        ) ?: intArrayOf()
        val supportsOIS = CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON in oisModes

        // ── Codec Support ────────────────────────────────────────────────────
        // We verify each codec against MediaCodecList rather than assuming H.264
        // is always available (it always is in practice, but this is correct code).
        val supportedCodecs: List<VideoCodec> = VideoCodec.entries.filter { codec ->
            isEncoderAvailable(codec.mimeType)
        }

        // ── Sensor Orientation ────────────────────────────────────────────────
        // The angle (0/90/180/270°) to rotate sensor output to match the device's
        // natural (portrait) orientation. Front cameras are typically 270°, rear 90°.
        val sensorOrientation: Int = characteristics.get(
            CameraCharacteristics.SENSOR_ORIENTATION
        ) ?: 90 // 90° is the overwhelming default on Android phones

        return CameraCapabilities(
            cameraId                        = cameraId,
            facing                          = facing,
            availableResolutions            = resolutions,
            availableFrameRates             = availableFrameRates,
            supportedCodecs                 = supportedCodecs,
            supportsElectronicStabilization = supportsEIS,
            supportsOpticalStabilization    = supportsOIS,
            sensorOrientation               = sensorOrientation
        )
    }

    /**
     * Returns true if the device has a hardware or software encoder for [mimeType].
     * Uses [MediaCodecList.REGULAR_CODECS] which includes both OMX and codec2 codecs.
     */
    private fun isEncoderAvailable(mimeType: String): Boolean {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        return list.codecInfos.any { info ->
            info.isEncoder && mimeType in info.supportedTypes
        }
    }

    // ── IntArray extension ────────────────────────────────────────────────────

    private operator fun IntArray.contains(value: Int): Boolean {
        for (element in this) if (element == value) return true
        return false
    }
}
