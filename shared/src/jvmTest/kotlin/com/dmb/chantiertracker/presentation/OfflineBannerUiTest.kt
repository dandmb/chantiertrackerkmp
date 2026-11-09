package com.dmb.chantiertracker.presentation

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.sync.OfflineBannerState
import com.dmb.chantiertracker.presentation.sync.TopBarWithOfflineBanner
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class OfflineBannerUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.mount(state: OfflineBannerState, dark: Boolean = false) = setContent {
        customAppLocale = "fr"
        AppEnvironment { AppTheme(darkTheme = dark) { TopBarWithOfflineBanner(state) { Text("Barre du haut") } } }
    }

    @Test
    fun online_the_top_bar_stands_alone() = runComposeUiTest {
        mount(OfflineBannerState(offline = false, pending = 4))
        waitForIdle()

        onNodeWithText("Barre du haut").assertIsDisplayed()
        assertEquals(0, onAllNodes(hasText("Hors ligne", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun offline_without_waiting_writes_says_they_will_leave_with_the_network() = runComposeUiTest {
        mount(OfflineBannerState(offline = true, pending = 0))
        waitForIdle()

        onNodeWithText("Barre du haut").assertIsDisplayed()
        onNodeWithText("Hors ligne").assertIsDisplayed()
        onNodeWithText("Tes saisies restent sur le téléphone et partiront au retour du réseau.").assertIsDisplayed()
    }

    @Test
    fun offline_counts_the_waiting_writes_with_the_right_plural() = runComposeUiTest {
        var pending by androidx.compose.runtime.mutableStateOf(1)
        setContent {
            customAppLocale = "fr"
            AppEnvironment { AppTheme(darkTheme = true) { TopBarWithOfflineBanner(OfflineBannerState(offline = true, pending = pending)) { Text("Barre du haut") } } }
        }
        waitForIdle()
        onNodeWithText("1 saisie en attente d'envoi. Elle partira au retour du réseau.").assertIsDisplayed()

        pending = 3
        waitForIdle()
        onNodeWithText("3 saisies en attente d'envoi. Elles partiront au retour du réseau.").assertIsDisplayed()
    }
}
