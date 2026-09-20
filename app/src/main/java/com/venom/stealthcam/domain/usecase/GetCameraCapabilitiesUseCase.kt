package com.venom.stealthcam.domain.usecase

import com.venom.stealthcam.domain.model.CameraCapabilities
import com.venom.stealthcam.domain.model.CameraFacing
import com.venom.stealthcam.domain.repository.CameraRepository
import javax.inject.Inject

/**
 * Queries hardware capabilities for the specified camera.
 * Used by the Settings screen to populate resolution / fps pickers
 * and by [StartRecordingUseCase] to validate user preferences.
 */
class GetCameraCapabilitiesUseCase @Inject constructor(
    private val cameraRepository: CameraRepository
) {
    suspend operator fun invoke(facing: CameraFacing): Result<CameraCapabilities> =
        cameraRepository.getCapabilities(facing)
}
