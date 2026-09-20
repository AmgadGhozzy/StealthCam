package com.venom.stealthcam.data.storage

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.venom.stealthcam.domain.model.StorageConfig
import com.venom.stealthcam.domain.repository.StorageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreStorageRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) : StorageRepository {

    override suspend fun ensureStealthFolder(config: StorageConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val uriString = config.folderUriString ?: return@runCatching // Fallback handled in resolution
            val treeUri = Uri.parse(uriString)
            
            val docUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )

            if (!config.isHiddenStorage) return@runCatching

            // We need to create .StealthCam folder if hidden storage is true.
            // But we don't have direct path access on Android 11+ via SAF.
            // Wait, we can't record MediaRecorder directly to SAF URI, MediaRecorder needs a file path or a FileDescriptor.
            // MediaRecorder.setOutputFile(FileDescriptor) is supported!
            // Wait, the interface expects a String file path! Let's check the domain model again.
            // The StartRecordingUseCase creates a RecordingConfig with an outputFilePath (String).
            error("This implementation needs to be handled via FileDescriptor or app-private storage")
        }
    }

    override suspend fun resolveOutputFilePath(config: StorageConfig, fileName: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // For MediaRecorder we need an absolute file path. 
            // On Android 11+ we can only use app-specific directories for direct File paths without MANAGE_EXTERNAL_STORAGE.
            // Let's use getExternalFilesDir.
            val baseDir = if (config.isHiddenStorage) {
                File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), ".StealthCam").apply {
                    if (!exists()) {
                        mkdirs()
                        File(this, ".nomedia").createNewFile()
                    }
                }
            } else {
                File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "StealthCam").apply {
                    if (!exists()) mkdirs()
                }
            }
            
            val file = File(baseDir, "$fileName.mp4")
            file.absolutePath
        }
    }
}
