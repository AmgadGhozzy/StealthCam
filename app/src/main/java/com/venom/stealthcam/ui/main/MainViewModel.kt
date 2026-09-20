package com.venom.stealthcam.ui.main

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venom.stealthcam.core.Constants
import com.venom.stealthcam.core.trigger.VolumeTriggerManager
import com.venom.stealthcam.data.recording.RecordingService
import com.venom.stealthcam.domain.model.CameraFacing
import com.venom.stealthcam.domain.model.RecordingState
import com.venom.stealthcam.domain.usecase.ObserveRecordingStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val observeRecordingStateUseCase: ObserveRecordingStateUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<MainEffect>()
    val effect: SharedFlow<MainEffect> = _effect.asSharedFlow()

    init {
        observeRecordingState()
        startTimerUpdates()
    }

    fun onIntent(intent: MainIntent) {
        when (intent) {
            is MainIntent.ToggleRecording -> {
                val currentState = _state.value
                if (currentState.isRecording || currentState.isPreparing) {
                    dispatchStopService()
                } else {
                    dispatchStartService(CameraFacing.REAR)
                }
            }
            is MainIntent.StartRecording -> dispatchStartService(intent.facing)
            is MainIntent.StopRecording -> dispatchStopService()
            is MainIntent.DismissError -> _state.update { it.copy(errorMessage = null) }
        }
    }

    private fun observeRecordingState() {
        viewModelScope.launch {
            observeRecordingStateUseCase().collect { recordingState ->
                when (recordingState) {
                    is RecordingState.Idle -> {
                        _state.update {
                            it.copy(
                                isRecording = false,
                                isPreparing = false,
                                recordingDurationMs = 0L,
                                activeCameraFacing = null
                            )
                        }
                    }
                    is RecordingState.Preparing -> {
                        _state.update {
                            it.copy(
                                isRecording = false,
                                isPreparing = true,
                                recordingDurationMs = 0L,
                                activeCameraFacing = null,
                                errorMessage = null
                            )
                        }
                    }
                    is RecordingState.Recording -> {
                        _state.update {
                            it.copy(
                                isRecording = true,
                                isPreparing = false,
                                activeCameraFacing = recordingState.cameraFacing,
                                recordingDurationMs = System.currentTimeMillis() - recordingState.startTimeMs,
                                errorMessage = null
                            )
                        }
                    }
                    is RecordingState.Error -> {
                        _state.update {
                            it.copy(
                                isRecording = false,
                                isPreparing = false,
                                recordingDurationMs = 0L,
                                activeCameraFacing = null,
                                errorMessage = recordingState.message
                            )
                        }
                    }
                    is RecordingState.Stopping -> {
                        _state.update {
                            it.copy(
                                isRecording = false,
                                isPreparing = false
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startTimerUpdates() {
        viewModelScope.launch {
            while (isActive) {
                val currentState = _state.value
                if (currentState.isRecording) {
                    val recordingState = observeRecordingStateUseCase().value
                    if (recordingState is RecordingState.Recording) {
                        _state.update {
                            it.copy(recordingDurationMs = System.currentTimeMillis() - recordingState.startTimeMs)
                        }
                    }
                }
                delay(100L)
            }
        }
    }

    private fun dispatchStartService(facing: CameraFacing) {
        val intent = Intent(context, RecordingService::class.java).apply {
            action = if (facing == CameraFacing.REAR) Constants.ACTION_START_REAR else Constants.ACTION_START_FRONT
        }
        ContextCompat.startForegroundService(context, intent)
    }

    private fun dispatchStopService() {
        val intent = Intent(context, RecordingService::class.java).apply {
            action = Constants.ACTION_STOP_RECORDING
        }
        context.startService(intent)
    }

    override fun onCleared() {
        super.onCleared()
    }
}
