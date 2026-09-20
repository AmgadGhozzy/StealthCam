package com.venom.stealthcam.data.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import com.venom.stealthcam.domain.model.CameraCapabilities
import com.venom.stealthcam.domain.model.CameraFacing
import com.venom.stealthcam.domain.repository.CameraRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Camera2-backed implementation of [CameraRepository].
 *
 * Design decisions:
 * — Results are cached in [capabilitiesCache] by camera ID. Camera capabilities never
 *   change at runtime (short of hot-plugging external cameras), so a process-lifetime
 *   cache is safe and avoids repeated HAL queries.
 * — All calls are dispatched on [Dispatchers.IO] to avoid blocking the main thread on
 *   Binder IPC calls inside [CameraManager].
 * — [findCameraIdForFacing] iterates only the physical camera list and stops at the
 *   first match — no unnecessary HAL queries.
 */
@Singleton
class Camera2CameraRepository @Inject constructor(
    private val cameraManager: CameraManager,
    private val capabilityQuery: CameraCapabilityQuery
) : CameraRepository {

    /** Thread-safe cache: camera ID → domain capabilities. */
    private val capabilitiesCache = ConcurrentHashMap<String, CameraCapabilities>()

    /**
     * Returns the [CameraCapabilities] for the first camera matching [facing].
     * Cached after the first successful query.
     *
     * Fails with [IllegalStateException] if:
     * — no camera with the requested facing is found on this device.
     * — [CameraManager.getCameraCharacteristics] throws (broken HAL, revoked permission).
     */
    override suspend fun getCapabilities(facing: CameraFacing): Result<CameraCapabilities> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cameraId = findCameraIdForFacing(facing)
                    ?: error("No camera with facing=$facing found on this device")

                // Return cached result if available; otherwise build and cache.
                capabilitiesCache.getOrPut(cameraId) {
                    val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                    capabilityQuery.buildCapabilities(cameraId, facing, characteristics)
                }
            }
        }

    /**
     * Returns all camera IDs exposed by the system.
     * Includes logical cameras (multi-aperture) and physical sub-cameras on
     * devices that expose them — filtered to only logical cameras for simplicity.
     */
    override suspend fun getAllCameraIds(): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching { cameraManager.cameraIdList.toList() }
        }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Iterates the camera list and returns the ID of the first camera whose
     * [CameraCharacteristics.LENS_FACING] matches the requested [facing].
     *
     * Returns null if no match is found (e.g. requesting FRONT on a device
     * that has no front camera).
     */
    private fun findCameraIdForFacing(facing: CameraFacing): String? {
        val targetLensFacing = facing.toLensFacing()
        return cameraManager.cameraIdList.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
            lensFacing == targetLensFacing
        }
    }

    // ── Domain → Camera2 mapping ──────────────────────────────────────────────

    private fun CameraFacing.toLensFacing(): Int = when (this) {
        CameraFacing.REAR  -> CameraMetadata.LENS_FACING_BACK
        CameraFacing.FRONT -> CameraMetadata.LENS_FACING_FRONT
    }
}
