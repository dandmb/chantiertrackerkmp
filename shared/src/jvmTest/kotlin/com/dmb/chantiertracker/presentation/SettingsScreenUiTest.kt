package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.core.AppConfig
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.settings.AppSettings
import com.dmb.chantiertracker.presentation.settings.SettingsScreen
import com.dmb.chantiertracker.presentation.settings.SettingsViewModel
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeAppPreferences
import com.dmb.chantiertracker.support.FakeBuildInfo
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SettingsScreenUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private fun vm(prefs: FakeAppPreferences): SettingsViewModel = SettingsViewModel(
        AppConfig(FakeBuildInfo(isDebug = false, appVersion = "2.1.0")),
        AppSettings(prefs, CoroutineScope(Dispatchers.Unconfined)),
    )

    private fun ComposeUiTest.mount(viewModel: SettingsViewModel) {
        setContent {
            customAppLocale = "fr"
            AppEnvironment {
                AppTheme {
                    Box(Modifier.size(412.dp, 892.dp)) { SettingsScreen(viewModel = viewModel) }
                }
            }
        }
        waitForIdle()
    }

    @Test
    fun the_three_sections_and_the_version_render() = runComposeUiTest {
        val viewModel = vm(FakeAppPreferences())
        mount(viewModel)

        onNodeWithText("Langue").assertExists()
        onNodeWithText("Thème").assertExists()
        onNodeWithText("À propos").assertExists()
        onNodeWithText("Version de l'app").assertExists()
        onNodeWithText("2.1.0").assertExists()
        // Nothing chosen yet → neither of the two "off-System" options is selected.
        onNodeWithText("English").assertIsNotSelected()
        onNodeWithText("Sombre").assertIsNotSelected()
    }

    @Test
    fun picking_a_language_persists_it_and_updates_the_selection() = runComposeUiTest {
        val prefs = FakeAppPreferences()
        val viewModel = vm(prefs)
        mount(viewModel)

        onNodeWithText("English").performClick()
        waitForIdle()

        assertEquals("English", prefs.store["app_language"])
        onNodeWithText("English").assertIsSelected()
    }

    @Test
    fun picking_a_theme_persists_it() = runComposeUiTest {
        val prefs = FakeAppPreferences()
        val viewModel = vm(prefs)
        mount(viewModel)

        onNodeWithText("Sombre").performClick()
        waitForIdle()

        assertEquals("Dark", prefs.store["app_theme_mode"])
        onNodeWithText("Sombre").assertIsSelected()
    }
}
