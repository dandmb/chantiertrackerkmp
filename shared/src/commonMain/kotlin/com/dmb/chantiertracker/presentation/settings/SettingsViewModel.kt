package com.dmb.chantiertracker.presentation.settings

import androidx.lifecycle.ViewModel
import com.dmb.chantiertracker.core.AppConfig

class SettingsViewModel(
    appConfig: AppConfig,
) : ViewModel() {
    val appVersion: String = appConfig.appVersion
}
