package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppChromeTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    @Test
    fun the_staging_banner_is_shown_on_a_pre_production_build() = runComposeUiTest {
        setContent {
            customAppLocale = "fr"
            AppEnvironment { AppTheme { AppChrome(showStagingBanner = true) { Box(Modifier.fillMaxSize()) { Text("écran") } } } }
        }
        waitForIdle()
        onNodeWithText("PRÉPROD · DONNÉES RÉELLES").assertIsDisplayed()
        onNodeWithText("écran").assertIsDisplayed()
    }

    @Test
    fun no_banner_on_a_normal_build() = runComposeUiTest {
        setContent {
            customAppLocale = "fr"
            AppEnvironment { AppTheme { AppChrome(showStagingBanner = false) { Box(Modifier.fillMaxSize()) { Text("écran") } } } }
        }
        waitForIdle()
        onNodeWithText("PRÉPROD · DONNÉES RÉELLES").assertDoesNotExist()
        onNodeWithText("écran").assertIsDisplayed()
    }
}
