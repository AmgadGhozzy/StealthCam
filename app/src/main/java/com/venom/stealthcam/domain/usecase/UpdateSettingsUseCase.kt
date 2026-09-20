package com.venom.stealthcam.domain.usecase

import com.venom.stealthcam.domain.model.AppSettings
import com.venom.stealthcam.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * Use case to update application settings.
 */
class UpdateSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(settings: AppSettings) {
        settingsRepository.updateSettings(settings)
    }
}
