package com.venom.stealthcam.domain.repository

import com.venom.stealthcam.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

/**
 * Persists and retrieves user preferences.
 * Implemented in the data layer via DataStore<Preferences>.
 */
interface SettingsRepository {
    /**
     * A cold [Flow] that emits the current [AppSettings] and re-emits
     * whenever any setting changes. Suitable for driving reactive UI.
     */
    fun observeSettings(): Flow<AppSettings>

    /**
     * One-shot read — returns the current settings synchronously within a
     * suspend context. Used by use cases that need settings at a point in time.
     */
    suspend fun getSettings(): AppSettings

    /** Persists [settings], replacing any previously stored values. */
    suspend fun updateSettings(settings: AppSettings)
}
