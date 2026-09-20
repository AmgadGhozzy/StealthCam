package com.venom.stealthcam.domain.usecase

import com.venom.stealthcam.domain.repository.RecordingRepository
import javax.inject.Inject

/**
 * Stops the active recording session and ensures all resources are released.
 * Delegates entirely to [RecordingRepository.stopRecording]; having a dedicated
 * use case preserves the ability to add pre/post stop logic (e.g. thumbnail
 * generation, analytics) without touching the UI layer.
 */
class StopRecordingUseCase @Inject constructor(
    private val recordingRepository: RecordingRepository
) {
    suspend operator fun invoke(): Result<Unit> =
        recordingRepository.stopRecording()
}
