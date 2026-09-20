package com.venom.stealthcam.ui.main

import com.venom.stealthcam.domain.model.CameraFacing

data class MainState(
    val isRecording: Boolean = false,
    val isPreparing: Boolean = false,
    val recordingDurationMs: Long = 0L,
    val activeCameraFacing: CameraFacing? = null,
    val errorMessage: String? = null
)

sealed class MainIntent {
    data object ToggleRecording : MainIntent()
    data class StartRecording(val facing: CameraFacing) : MainIntent()
    data object StopRecording : MainIntent()
    data object DismissError : MainIntent()
}

sealed class MainEffect {
    data class ShowToast(val message: String) : MainEffect()
    data object NavigateToSettings : MainEffect()
}
