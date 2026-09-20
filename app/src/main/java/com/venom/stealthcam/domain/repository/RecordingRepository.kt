package com.venom.stealthcam.domain.repository

import com.venom.stealthcam.domain.model.RecordingConfig
import com.venom.stealthcam.domain.model.RecordingState
import kotlinx.coroutines.flow.StateFlow

/**
 * Controls the recording engine and exposes session state.
 * Implemented in the data layer by the Foreground Service bridge.
 *
 * [recordingState] is a [StateFlow] so the UI and Service can both observe it
 * efficiently without polling, and new collectors get the current state immediately.
 */
interface RecordingRepository {
    val recordingState: StateFlow<RecordingState>

    /**
     * Prepares Camera2 and MediaRecorder with the supplied [config] and starts encoding.
     * Returns [Result.failure] if the camera cannot be opened or the recorder
     * cannot be configured (e.g. unsupported codec, disk full).
     */
    suspend fun startRecording(config: RecordingConfig): Result<Unit>

    /**
     * Signals MediaRecorder to stop, flushes the output file, and releases
     * all camera resources. Safe to call from any coroutine context.
     */
    suspend fun stopRecording(): Result<Unit>
}
