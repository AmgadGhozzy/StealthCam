package com.venom.stealthcam.domain.usecase

import com.venom.stealthcam.domain.model.RecordingState
import com.venom.stealthcam.domain.repository.RecordingRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Exposes the live [RecordingState] stream to consumers.
 * Returns [StateFlow] rather than [Flow] so:
 * — the current state is always accessible synchronously via [StateFlow.value].
 * — the Foreground Service and the UI share the same hot stream without duplication.
 */
class ObserveRecordingStateUseCase @Inject constructor(
    private val recordingRepository: RecordingRepository
) {
    operator fun invoke(): StateFlow<RecordingState> =
        recordingRepository.recordingState
}
