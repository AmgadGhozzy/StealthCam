package com.venom.stealthcam.domain.repository

import com.venom.stealthcam.domain.model.StorageConfig

/**
 * Resolves SAF URIs to writable file paths and manages the hidden storage folder.
 * Implemented in Phase 6 by the Storage layer.
 */
interface StorageRepository {
    /**
     * Ensures the `.StealthCam/` directory and its `.nomedia` file exist
     * inside the folder chosen by the user. Creates them if absent.
     * Returns [Result.failure] if the folder URI is not writable.
     */
    suspend fun ensureStealthFolder(config: StorageConfig): Result<Unit>

    /**
     * Resolves the storage config into a writable absolute file path for a new recording.
     * The [fileName] should not include the extension.
     * Returns the full path including `.mp4` extension.
     */
    suspend fun resolveOutputFilePath(config: StorageConfig, fileName: String): Result<String>
}
