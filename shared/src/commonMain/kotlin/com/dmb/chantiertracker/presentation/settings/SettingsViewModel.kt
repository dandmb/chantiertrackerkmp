package com.dmb.chantiertracker.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.core.AppConfig
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.presentation.billing.UrlOpener
import com.dmb.chantiertracker.presentation.i18n.AppLanguage
import com.dmb.chantiertracker.presentation.theme.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    appConfig: AppConfig,
    private val settings: AppSettings,
    private val urlOpener: UrlOpener,
) : ViewModel() {

    val appVersion: String = appConfig.appVersion

    val language: StateFlow<AppLanguage> = settings.language
    val themeMode: StateFlow<ThemeMode> = settings.themeMode

    private val _linkError = MutableStateFlow<DomainException?>(null)
    val linkError: StateFlow<DomainException?> = _linkError.asStateFlow()

    fun onLanguageSelected(language: AppLanguage) = settings.setLanguage(language)
    fun onThemeSelected(mode: ThemeMode) = settings.setThemeMode(mode)

    fun onLegalPageClick(page: LegalPage) {
        _linkError.value = null
        viewModelScope.launch {
            try {
                urlOpener.open(page.url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _linkError.value = DomainException.Unexpected
            }
        }
    }
}
