package com.venom.stealthcam.domain.model

/**
 * All user-configurable preferences, persisted via DataStore.
 *
 * [preferredResolution] and [preferredFrameRate] being null means "automatic" —
 * the app selects the maximum value supported by the selected camera.
 */
data class AppSettings(
    val videoCodec: VideoCodec,
    val preferredResolution: Resolution?,
    val preferredFrameRate: Int?,
    val bitrateMode: BitrateMode,
    val isAudioEnabled: Boolean,
    val storageConfig: StorageConfig,
    val maxDurationMs: Long?,
    val maxFileSizeBytes: Long?,
    val isForegroundNotificationEnabled: Boolean,

    /**
     * How long (ms) the user must hold a volume button before it is recognised as a
     * long-press trigger. Supported values: 300, 500, 750, 1000.
     * Stored in DataStore and forwarded to [VolumeKeyAccessibilityService].
     */
    val longPressDurationMs: Long,

    /**
     * When true, a small red dot is shown in the corner of the screen while
     * a recording is active. Can be toggled off for complete stealth operation.
     */
    val showRecordingOverlay: Boolean
) {
    companion object {
        /** Sensible defaults used on first launch. */
        val Default = AppSettings(
            videoCodec                      = VideoCodec.H265,
            preferredResolution             = null,        // auto → max
            preferredFrameRate              = null,        // auto → max
            bitrateMode                     = BitrateMode.Auto,
            isAudioEnabled                  = true,
            storageConfig                   = StorageConfig.Default,
            maxDurationMs                   = null,        // unlimited
            maxFileSizeBytes                = null,        // unlimited
            isForegroundNotificationEnabled = true,
            longPressDurationMs             = 400L,
            showRecordingOverlay            = true
        )
    }
}
