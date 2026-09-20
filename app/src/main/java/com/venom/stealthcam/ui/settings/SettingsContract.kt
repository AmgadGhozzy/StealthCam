package com.venom.stealthcam.ui.settings

import com.venom.stealthcam.domain.model.AppSettings

data class SettingsState(
    val isLoading: Boolean = true,
    val settings: AppSettings = AppSettings.Default
)

sealed class SettingsIntent {
    data class UpdateSettings(val newSettings: AppSettings) : SettingsIntent()
    data object NavigateBack : SettingsIntent()
}

sealed class SettingsEffect {
    data object NavigateBack : SettingsEffect()
}
