package com.dmb.chantiertracker.presentation

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.main.ExitAppDialog
import com.dmb.chantiertracker.presentation.theme.AppTheme
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ExitAppDialogUiTest {

    @AfterTest fun tearDown() { customAppLocale = null }

    private var exits = 0
    private var dismissals = 0

    private fun ComposeUiTest.show(unsent: Int, locale: String = "fr") = setContent {
        customAppLocale = locale
        AppEnvironment { AppTheme { ExitAppDialog(unsentChanges = unsent, onExit = { exits++ }, onDismiss = { dismissals++ }) } }
    }

    @Test
    fun with_everything_sent_the_dialog_only_asks_to_quit() = runComposeUiTest {
        show(unsent = 0)

        onNodeWithText("Quitter ChantierTracker ?").assertExists()
        check(onAllNodes(hasText("prochain lancement", substring = true)).fetchSemanticsNodes().isEmpty())
        onNodeWithText("Quitter").performClick()
        assertEquals(1, exits)
    }

    @Test
    fun changes_not_sent_yet_are_announced_with_their_number() = runComposeUiTest {
        show(unsent = 3)

        onNodeWithText("3 modifications seront envoyées au prochain lancement avec du réseau.").assertExists()
        onNodeWithText("Rester").performClick()
        assertEquals(1, dismissals)
        assertEquals(0, exits)
    }

    @Test
    fun a_single_change_reads_in_the_singular() = runComposeUiTest {
        show(unsent = 1)
        onNodeWithText("1 modification sera envoyée au prochain lancement avec du réseau.").assertExists()
    }

    @Test
    fun in_english() = runComposeUiTest {
        show(unsent = 3, locale = "en")

        onNodeWithText("Quit ChantierTracker?").assertExists()
        onNodeWithText("3 changes will be sent the next time you open the app with a network connection.").assertExists()
        onNodeWithText("Quit").assertExists()
        onNodeWithText("Stay").assertExists()
    }
}
