package com.dmb.chantiertracker.presentation.settings

import com.dmb.chantiertracker.data.local.AppPreferences
import com.dmb.chantiertracker.presentation.i18n.AppLanguage
import com.dmb.chantiertracker.presentation.i18n.applyLanguage
import com.dmb.chantiertracker.presentation.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// App-wide user preferences (language, theme), session-scoped singleton like
// ProjectSortHolder / SyncStateHolder but **persisted**. Values are read
// synchronously at construction; the persisted language is applied to the
// compose-resources hook by [applyPersistedLanguage], called once from
// initKoin before the first composition (no visible switch) — never a side
// effect of construction, so tests and previews stay in control of the locale.
class AppSettings(
    private val preferences: AppPreferences,
    private val scope: CoroutineScope,
) {
    private val _language = MutableStateFlow(read(KEY_LANGUAGE) { AppLanguage.valueOf(it) } ?: AppLanguage.System)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _themeMode = MutableStateFlow(read(KEY_THEME) { ThemeMode.valueOf(it) } ?: ThemeMode.System)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun applyPersistedLanguage() {
        applyLanguage(_language.value)
    }

    fun setLanguage(language: AppLanguage) {
        if (language == _language.value) return
        _language.value = language
        applyLanguage(language)
        persist(KEY_LANGUAGE, language.name)
    }

    fun setThemeMode(mode: ThemeMode) {
        if (mode == _themeMode.value) return
        _themeMode.value = mode
        persist(KEY_THEME, mode.name)
    }

    private inline fun <T> read(key: String, parse: (String) -> T): T? =
        preferences.read(key)?.let { runCatching { parse(it) }.getOrNull() }

    private fun persist(key: String, value: String) {
        scope.launch { preferences.write(key, value) }
    }

    private companion object {
        const val KEY_LANGUAGE = "app_language"
        const val KEY_THEME = "app_theme_mode"
    }
}
