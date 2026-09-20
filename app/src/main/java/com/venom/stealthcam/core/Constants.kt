package com.venom.stealthcam.core

/**
 * Application-wide constants. All values are compile-time literals so they can
 * be used in annotations (e.g. notification channel IDs) without reflection.
 */
object Constants {

    // ── Storage ──────────────────────────────────────────────────────────────

    /** Hidden folder created inside the user-selected SAF directory. */
    const val STEALTH_FOLDER_NAME = ".StealthCam"

    /** Prevents media scanners from indexing our recordings. */
    const val NOMEDIA_FILE_NAME = ".nomedia"

    /** Extension for all output files. */
    const val VIDEO_EXTENSION = ".mp4"

    // ── Foreground Service / Notifications ───────────────────────────────────

    const val NOTIFICATION_CHANNEL_ID   = "stealthcam_recording_channel"
    const val NOTIFICATION_CHANNEL_NAME = "Recording"
    const val RECORDING_NOTIFICATION_ID = 1001

    /** Intent action used by the volume button receiver to start recording with the rear camera. */
    const val ACTION_START_REAR        = "com.venom.stealthcam.ACTION_START_REAR"

    /** Intent action used by the volume button receiver to start recording with the front camera. */
    const val ACTION_START_FRONT       = "com.venom.stealthcam.ACTION_START_FRONT"

    /** Intent action to stop recording from the notification button or volume trigger. */
    const val ACTION_STOP_RECORDING    = "com.venom.stealthcam.ACTION_STOP_RECORDING"

    // ── Input / Triggers ─────────────────────────────────────────────────────

    /**
     * Default long-press threshold. Stored in [AppSettings] so the user can customise it.
     * Supported values: 300, 400, 500, 750, 1000 ms.
     */
    const val LONG_PRESS_DURATION_MS = 400L

    // ── Bitrate bounds (bits-per-second) ─────────────────────────────────────

    const val MIN_BITRATE_BPS     =    500_000   //  500 kbps
    const val MAX_BITRATE_BPS     = 100_000_000  //  100 Mbps
    const val DEFAULT_BITRATE_BPS =   8_000_000  //    8 Mbps

    // ── DataStore ────────────────────────────────────────────────────────────

    const val SETTINGS_DATASTORE_NAME = "stealthcam_settings"

    // ── Camera2 ──────────────────────────────────────────────────────────────

    /** Maximum time to wait for a camera device to open, in milliseconds. */
    const val CAMERA_OPEN_TIMEOUT_MS = 5_000L

    /** Number of frames to buffer in the ImageReader surface during preview. */
    const val PREVIEW_BUFFER_COUNT = 2
}
