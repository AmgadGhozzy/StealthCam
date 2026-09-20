package com.venom.stealthcam.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.venom.stealthcam.domain.model.*
import com.venom.stealthcam.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore implementation of [SettingsRepository].
 * Maps domain models to flat DataStore preferences keys and back.
 */
@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    private object Keys {
        val VIDEO_CODEC = stringPreferencesKey("video_codec")
        val RES_WIDTH = intPreferencesKey("res_width")
        val RES_HEIGHT = intPreferencesKey("res_height")
        val FRAME_RATE = intPreferencesKey("frame_rate")
        val BITRATE_AUTO = booleanPreferencesKey("bitrate_auto")
        val BITRATE_BPS = intPreferencesKey("bitrate_bps")
        val AUDIO_ENABLED = booleanPreferencesKey("audio_enabled")
        val STORAGE_URI = stringPreferencesKey("storage_uri")
        val HIDDEN_STORAGE = booleanPreferencesKey("hidden_storage")
        val FILE_NAME_FORMAT = stringPreferencesKey("file_name_format")
        val MAX_DURATION_MS = longPreferencesKey("max_duration_ms")
        val MAX_FILE_SIZE_BYTES = longPreferencesKey("max_file_size_bytes")
        val NOTIFICATION_ENABLED = booleanPreferencesKey("notification_enabled")
        val LONG_PRESS_DURATION_MS = longPreferencesKey("long_press_duration_ms")
        val SHOW_OVERLAY = booleanPreferencesKey("show_overlay")
    }

    override fun observeSettings(): Flow<AppSettings> {
        return dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs ->
                mapPreferencesToSettings(prefs)
            }
    }

    override suspend fun getSettings(): AppSettings {
        return observeSettings().first()
    }

    override suspend fun updateSettings(settings: AppSettings) {
        dataStore.edit { prefs ->
            prefs[Keys.VIDEO_CODEC] = settings.videoCodec.name
            
            if (settings.preferredResolution != null) {
                prefs[Keys.RES_WIDTH] = settings.preferredResolution.width
                prefs[Keys.RES_HEIGHT] = settings.preferredResolution.height
            } else {
                prefs.remove(Keys.RES_WIDTH)
                prefs.remove(Keys.RES_HEIGHT)
            }
            
            if (settings.preferredFrameRate != null) {
                prefs[Keys.FRAME_RATE] = settings.preferredFrameRate
            } else {
                prefs.remove(Keys.FRAME_RATE)
            }
            
            when (val mode = settings.bitrateMode) {
                is BitrateMode.Auto -> {
                    prefs[Keys.BITRATE_AUTO] = true
                    prefs.remove(Keys.BITRATE_BPS)
                }
                is BitrateMode.Manual -> {
                    prefs[Keys.BITRATE_AUTO] = false
                    prefs[Keys.BITRATE_BPS] = mode.bps
                }
            }
            prefs[Keys.AUDIO_ENABLED] = settings.isAudioEnabled
            
            if (settings.storageConfig.folderUriString != null) {
                prefs[Keys.STORAGE_URI] = settings.storageConfig.folderUriString
            } else {
                prefs.remove(Keys.STORAGE_URI)
            }
            
            prefs[Keys.HIDDEN_STORAGE] = settings.storageConfig.isHiddenStorage
            prefs[Keys.FILE_NAME_FORMAT] = settings.storageConfig.fileNameFormat
            
            if (settings.maxDurationMs != null) {
                prefs[Keys.MAX_DURATION_MS] = settings.maxDurationMs
            } else {
                prefs.remove(Keys.MAX_DURATION_MS)
            }
            
            if (settings.maxFileSizeBytes != null) {
                prefs[Keys.MAX_FILE_SIZE_BYTES] = settings.maxFileSizeBytes
            } else {
                prefs.remove(Keys.MAX_FILE_SIZE_BYTES)
            }
            
            prefs[Keys.NOTIFICATION_ENABLED] = settings.isForegroundNotificationEnabled
            prefs[Keys.LONG_PRESS_DURATION_MS] = settings.longPressDurationMs
            prefs[Keys.SHOW_OVERLAY] = settings.showRecordingOverlay
        }
    }

    private fun mapPreferencesToSettings(prefs: Preferences): AppSettings {
        val default = AppSettings.Default
        
        val videoCodec = prefs[Keys.VIDEO_CODEC]?.let { 
            runCatching { VideoCodec.valueOf(it) }.getOrNull() 
        } ?: default.videoCodec
        
        val width = prefs[Keys.RES_WIDTH]
        val height = prefs[Keys.RES_HEIGHT]
        val resolution = if (width != null && height != null) Resolution(width, height) else default.preferredResolution
        
        val frameRate = prefs[Keys.FRAME_RATE] ?: default.preferredFrameRate
        
        val isBitrateAuto = prefs[Keys.BITRATE_AUTO] ?: true
        val bitrateMode = if (isBitrateAuto) {
            BitrateMode.Auto
        } else {
            prefs[Keys.BITRATE_BPS]?.let { BitrateMode.Manual(it) } ?: default.bitrateMode
        }
        
        val audioEnabled = prefs[Keys.AUDIO_ENABLED] ?: default.isAudioEnabled
        
        val storageConfig = StorageConfig(
            folderUriString = prefs[Keys.STORAGE_URI] ?: default.storageConfig.folderUriString,
            isHiddenStorage = prefs[Keys.HIDDEN_STORAGE] ?: default.storageConfig.isHiddenStorage,
            fileNameFormat = prefs[Keys.FILE_NAME_FORMAT] ?: default.storageConfig.fileNameFormat
        )
        
        val maxDurationMs = prefs[Keys.MAX_DURATION_MS] ?: default.maxDurationMs
        val maxFileSizeBytes = prefs[Keys.MAX_FILE_SIZE_BYTES] ?: default.maxFileSizeBytes
        val notificationEnabled = prefs[Keys.NOTIFICATION_ENABLED] ?: default.isForegroundNotificationEnabled
        val longPressDurationMs = prefs[Keys.LONG_PRESS_DURATION_MS] ?: default.longPressDurationMs
        val showOverlay = prefs[Keys.SHOW_OVERLAY] ?: default.showRecordingOverlay
        
        return AppSettings(
            videoCodec = videoCodec,
            preferredResolution = resolution,
            preferredFrameRate = frameRate,
            bitrateMode = bitrateMode,
            isAudioEnabled = audioEnabled,
            storageConfig = storageConfig,
            maxDurationMs = maxDurationMs,
            maxFileSizeBytes = maxFileSizeBytes,
            isForegroundNotificationEnabled = notificationEnabled,
            longPressDurationMs = longPressDurationMs,
            showRecordingOverlay = showOverlay
        )
    }
}
