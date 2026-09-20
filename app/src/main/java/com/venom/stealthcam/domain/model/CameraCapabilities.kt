package com.venom.stealthcam.domain.model

/**
 * Describes what a specific physical camera can actually do on the current device.
 * Queried once at startup and cached; refreshed when the selected camera changes.
 *
 * The data layer (Camera2CameraRepository) populates this by interrogating
 * CameraCharacteristics and MediaCodecList — the domain layer knows nothing
 * about those Android APIs.
 */
data class CameraCapabilities(
    /** Camera2 camera ID string (e.g. "0", "1"). */
    val cameraId: String,

    /** Physical lens orientation. */
    val facing: CameraFacing,

    /** All resolutions the camera sensor supports for video capture, sorted descending. */
    val availableResolutions: List<Resolution>,

    /** All integer frame rates supported at the [maxResolution], e.g. [30, 60]. */
    val availableFrameRates: List<Int>,

    /** Codecs confirmed encodable on this device via MediaCodecList. */
    val supportedCodecs: List<VideoCodec>,

    /**
     * True when CONTROL_VIDEO_STABILIZATION_MODE_ON is available.
     * This is digital/EIS stabilization in Camera2 terminology.
     */
    val supportsElectronicStabilization: Boolean,

    /**
     * True when LENS_OPTICAL_STABILIZATION_MODE_ON is available.
     * Hardware OIS — usually only on the main rear camera.
     */
    val supportsOpticalStabilization: Boolean,

    /**
     * The clockwise angle (0, 90, 180, 270) through which the output image needs
     * to be rotated to be upright on the device screen in its natural orientation.
     * Directly from [android.hardware.camera2.CameraCharacteristics.SENSOR_ORIENTATION].
     */
    val sensorOrientation: Int
) {
    /** The highest-resolution option; null only if the capabilities list is empty. */
    val maxResolution: Resolution?
        get() = availableResolutions.maxOrNull()

    /** Highest supported frame rate at max resolution. */
    val maxFrameRate: Int
        get() = availableFrameRates.maxOrNull() ?: 30

    /**
     * The best codec to use without user override.
     * Prefers H.265 for superior compression; falls back to H.264.
     */
    val recommendedCodec: VideoCodec
        get() = if (VideoCodec.H265 in supportedCodecs) VideoCodec.H265 else VideoCodec.H264
}
