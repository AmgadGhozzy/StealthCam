package com.venom.stealthcam.domain.model

/**
 * Represents the full lifecycle state of a recording session.
 *
 * State transitions:
 *   Idle → Preparing → Recording → Stopping → Idle
 *   Any state → Error → Idle (after user acknowledgement)
 */
sealed class RecordingState {

    /** No active session. The app is idle and ready to start. */
    data object Idle : RecordingState()

    /**
     * Camera and recorder resources are being acquired.
     * A short-lived transitional state; UI should show a spinner.
     */
    data object Preparing : RecordingState()

    /**
     * Actively recording.
     *
     * @param startTimeMs  System.currentTimeMillis() when recording began.
     * @param outputFilePath  Absolute filesystem path (or SAF URI string) of the output file.
     * @param cameraFacing  Which camera lens is in use.
     */
    data class Recording(
        val startTimeMs: Long,
        val outputFilePath: String,
        val cameraFacing: CameraFacing
    ) : RecordingState()

    /**
     * Finalization in progress — MediaRecorder.stop() has been called but
     * the file has not yet been flushed and closed.
     */
    data object Stopping : RecordingState()

    /**
     * An unrecoverable error occurred. The recording has been aborted.
     *
     * @param message  A human-readable description suitable for showing in the UI.
     * @param cause    The underlying exception, if any.
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : RecordingState()
}
