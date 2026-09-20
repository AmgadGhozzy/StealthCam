package com.venom.stealthcam.domain.usecase

import com.venom.stealthcam.domain.model.AppSettings
import com.venom.stealthcam.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Provides a continuous stream of [AppSettings] changes.
 * Used by the Settings screen ViewModel to reactively update the UI
 * whenever a setting is persisted — even from another coroutine or process restart.
 */
class ObserveSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    operator fun invoke(): Flow<AppSettings> =
        settingsRepository.observeSettings()
}
