package com.dmb.chantiertracker.presentation.settings

import androidx.lifecycle.ViewModel
import com.dmb.chantiertracker.core.AppConfig
import com.dmb.chantiertracker.presentation.i18n.AppLanguage
import com.dmb.chantiertracker.presentation.theme.ThemeMode
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(
    appConfig: AppConfig,
    private val settings: AppSettings,
) : ViewModel() {

    val appVersion: String = appConfig.appVersion

    val language: StateFlow<AppLanguage> = settings.language
    val themeMode: StateFlow<ThemeMode> = settings.themeMode

    fun onLanguageSelected(language: AppLanguage) = settings.setLanguage(language)
    fun onThemeSelected(mode: ThemeMode) = settings.setThemeMode(mode)
}
