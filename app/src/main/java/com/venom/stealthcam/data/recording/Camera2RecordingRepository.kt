package com.venom.stealthcam.data.recording

import com.venom.stealthcam.core.di.ApplicationScope
import com.venom.stealthcam.domain.model.RecordingConfig
import com.venom.stealthcam.domain.model.RecordingState
import com.venom.stealthcam.domain.repository.RecordingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements [RecordingRepository] by delegating Camera2 + MediaRecorder work to
 * [RecordingEngine] and managing [RecordingState] transitions.
 *
 * Responsibilities:
 * — Own and expose the canonical [StateFlow<RecordingState>].
 * — Translate engine exceptions into [RecordingState.Error].
 * — Handle asynchronous errors (camera disconnect, hardware failure) via callbacks
 *   injected into [RecordingEngine.start].
 * — Use [appScope] to launch coroutines triggered by async events (e.g. auto-stop
 *   on max duration reached) — these cannot be tied to a specific caller coroutine.
 */
@Singleton
class Camera2RecordingRepository @Inject constructor(
    private val engine: RecordingEngine,
    @param:ApplicationScope private val appScope: CoroutineScope
) : RecordingRepository {

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    override val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    /**
     * Validates current state, transitions to [RecordingState.Preparing], delegates to
     * [RecordingEngine], and on success emits [RecordingState.Recording].
     * On any failure, emits [RecordingState.Error] and releases engine resources.
     */
    override suspend fun startRecording(config: RecordingConfig): Result<Unit> = runCatching {
        check(_recordingState.value is RecordingState.Idle) {
            "Cannot start recording: already in state ${_recordingState.value}"
        }
        _recordingState.value = RecordingState.Preparing

        engine.start(
            config = config,
            onAsyncError = { throwable ->
                // Called from the camera handler thread — StateFlow.value is thread-safe
                _recordingState.value = RecordingState.Error(
                    message = throwable.message ?: "Camera hardware error",
                    cause = throwable
                )
                appScope.launch { engine.stop() }
            },
            onMaxLimitReached = {
                // Max duration or file size reached — auto-stop
                appScope.launch { stopRecording() }
            }
        )

        _recordingState.value = RecordingState.Recording(
            startTimeMs    = System.currentTimeMillis(),
            outputFilePath = config.outputFilePath,
            cameraFacing   = config.cameraFacing
        )
    }.onFailure { throwable ->
        _recordingState.value = RecordingState.Error(
            message = throwable.message ?: "Failed to start recording",
            cause   = throwable
        )
        appScope.launch { engine.stop() }
    }

    /**
     * Signals [RecordingEngine] to stop encoding and release all resources.
     * Transitions state to [RecordingState.Stopping] → [RecordingState.Idle].
     * Idempotent: calling this when already [RecordingState.Idle] is a no-op.
     */
    override suspend fun stopRecording(): Result<Unit> = runCatching {
        if (_recordingState.value is RecordingState.Idle ||
            _recordingState.value is RecordingState.Stopping
        ) return@runCatching

        _recordingState.value = RecordingState.Stopping
        engine.stop()
        _recordingState.value = RecordingState.Idle
    }.onFailure { throwable ->
        _recordingState.value = RecordingState.Error(
            message = throwable.message ?: "Failed to stop recording",
            cause   = throwable
        )
    }
}
