package com.venom.stealthcam.domain.repository

import com.venom.stealthcam.domain.model.CameraCapabilities
import com.venom.stealthcam.domain.model.CameraFacing

/**
 * Abstracts Camera2 hardware queries.
 * Implemented in the data layer by Camera2CameraRepository.
 * All methods are suspend functions — Camera2 characteristic reads are fast but
 * must not block the main thread.
 */
interface CameraRepository {
    /**
     * Returns the capabilities of the camera matching [facing].
     * Fails with [IllegalStateException] if no camera with that facing exists.
     */
    suspend fun getCapabilities(facing: CameraFacing): Result<CameraCapabilities>

    /**
     * Returns all available camera IDs on the device.
     * Useful for multi-camera enumeration in future phases.
     */
    suspend fun getAllCameraIds(): Result<List<String>>
}
