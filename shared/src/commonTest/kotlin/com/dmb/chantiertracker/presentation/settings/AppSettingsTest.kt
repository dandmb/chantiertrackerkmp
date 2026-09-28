package com.dmb.chantiertracker.presentation.settings

import com.dmb.chantiertracker.presentation.i18n.AppLanguage
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.theme.ThemeMode
import com.dmb.chantiertracker.support.FakeAppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @AfterTest fun tearDown() { customAppLocale = null }

    @Test
    fun defaults_to_system_when_nothing_is_stored() {
        val settings = AppSettings(FakeAppPreferences(), scope)

        assertEquals(AppLanguage.System, settings.language.value)
        assertEquals(ThemeMode.System, settings.themeMode.value)
    }

    @Test
    fun restores_the_persisted_choice_on_construction() {
        val prefs = FakeAppPreferences(mapOf("app_language" to "French", "app_theme_mode" to "Dark"))

        val settings = AppSettings(prefs, scope)

        assertEquals(AppLanguage.French, settings.language.value)
        assertEquals(ThemeMode.Dark, settings.themeMode.value)
        assertEquals(null, customAppLocale, "construction alone must not touch the global locale hook")

        settings.applyPersistedLanguage()
        assertEquals("fr", customAppLocale, "applyPersistedLanguage (called once from initKoin) applies it")
    }

    @Test
    fun a_corrupt_stored_value_falls_back_to_system() {
        val prefs = FakeAppPreferences(mapOf("app_language" to "Klingon", "app_theme_mode" to "Sepia"))

        val settings = AppSettings(prefs, scope)

        assertEquals(AppLanguage.System, settings.language.value)
        assertEquals(ThemeMode.System, settings.themeMode.value)
    }

    @Test
    fun setting_a_language_updates_the_flow_the_global_hook_and_persists() {
        val prefs = FakeAppPreferences()
        val settings = AppSettings(prefs, scope)

        settings.setLanguage(AppLanguage.English)

        assertEquals(AppLanguage.English, settings.language.value)
        assertEquals("en", customAppLocale)
        assertEquals("English", prefs.store["app_language"])
    }

    @Test
    fun setting_the_theme_updates_the_flow_and_persists_without_touching_the_locale() {
        val prefs = FakeAppPreferences()
        val settings = AppSettings(prefs, scope)
        customAppLocale = null

        settings.setThemeMode(ThemeMode.Light)

        assertEquals(ThemeMode.Light, settings.themeMode.value)
        assertEquals("Light", prefs.store["app_theme_mode"])
        assertEquals(null, customAppLocale, "theme changes never touch the language hook")
    }

    @Test
    fun selecting_system_language_clears_the_forced_locale() {
        val settings = AppSettings(FakeAppPreferences(mapOf("app_language" to "French")), scope)
        settings.applyPersistedLanguage()
        assertEquals("fr", customAppLocale)

        settings.setLanguage(AppLanguage.System)

        assertEquals(null, customAppLocale)
    }
}
