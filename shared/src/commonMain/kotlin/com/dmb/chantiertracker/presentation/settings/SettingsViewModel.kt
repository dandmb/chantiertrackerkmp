package com.dmb.chantiertracker.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.core.AppConfig
import com.dmb.chantiertracker.domain.repository.SupportContactRepository
import com.dmb.chantiertracker.presentation.i18n.AppLanguage
import com.dmb.chantiertracker.presentation.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    appConfig: AppConfig,
    private val settings: AppSettings,
    supportContact: SupportContactRepository,
) : ViewModel() {

    val appVersion: String = appConfig.appVersion
    val appBuild: String = appConfig.appBuild

    val supportEmail: StateFlow<String?> = supportContact.observeEmail()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val language: StateFlow<AppLanguage> = settings.language
    val themeMode: StateFlow<ThemeMode> = settings.themeMode

    init {
        viewModelScope.launch { supportContact.refresh() }
    }

    fun onLanguageSelected(language: AppLanguage) = settings.setLanguage(language)
    fun onThemeSelected(mode: ThemeMode) = settings.setThemeMode(mode)
}
