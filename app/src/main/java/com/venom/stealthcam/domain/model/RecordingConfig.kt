package com.venom.stealthcam.domain.model

/**
 * The complete, resolved configuration for a single recording session.
 * Built by [com.venom.stealthcam.domain.usecase.StartRecordingUseCase] from
 * [AppSettings] + [CameraCapabilities] and handed directly to the recording engine.
 * All values are concrete — no nulls, no "auto" flags — by the time this object exists.
 */
data class RecordingConfig(
    /** Camera2 camera ID string. */
    val cameraId: String,

    val cameraFacing: CameraFacing,
    val resolution: Resolution,
    val frameRate: Int,
    val videoCodec: VideoCodec,

    /** Resolved bitrate in bits-per-second, whether calculated or user-supplied. */
    val videoBitrateBps: Int,

    val isAudioEnabled: Boolean,

    /**
     * Absolute file-system path where the MP4 will be written.
     * Resolved by [com.venom.stealthcam.domain.repository.StorageRepository]
     * before this config is constructed.
     */
    val outputFilePath: String,

    /** null = no time limit. */
    val maxDurationMs: Long?,

    /** null = no size limit. */
    val maxFileSizeBytes: Long?,

    /** Apply EIS if the camera supports it. */
    val enableElectronicStabilization: Boolean,

    /** Apply OIS if the lens supports it. */
    val enableOpticalStabilization: Boolean,

    /**
     * Clockwise rotation in degrees (0, 90, 180, 270) to embed as the orientation hint
     * in the MP4 container. Computed from sensorOrientation + device display rotation by
     * [com.venom.stealthcam.domain.usecase.StartRecordingUseCase].
     */
    val orientationHint: Int
)
