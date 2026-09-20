package com.venom.stealthcam.domain.model

/**
 * Persisted storage preferences.
 * [folderUriString] is the string form of a Storage Access Framework tree URI.
 * A null value means the user has not yet chosen a folder; the app will fall back
 * to the external files directory for that session and prompt again.
 */
data class StorageConfig(
    val folderUriString: String?,
    val isHiddenStorage: Boolean,
    val fileNameFormat: String
) {
    companion object {
        const val DEFAULT_FILE_NAME_FORMAT = "yyyyMMdd_HHmmss"

        val Default = StorageConfig(
            folderUriString  = null,
            isHiddenStorage  = true,
            fileNameFormat   = DEFAULT_FILE_NAME_FORMAT
        )
    }
}
