package com.dmb.chantiertracker.presentation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.main.SignOutBackDialog
import com.dmb.chantiertracker.presentation.theme.AppTheme
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalTestApi::class)
class SignOutBackDialogUiTest {

    @AfterTest fun tearDown() { customAppLocale = null }

    private var signOuts = 0
    private var dismissals = 0

    private fun ComposeUiTest.show(unsent: Int, locale: String = "fr") = setContent {
        customAppLocale = locale
        AppEnvironment { AppTheme { SignOutBackDialog(unsentChanges = unsent, onSignOut = { signOuts++ }, onDismiss = { dismissals++ }) } }
    }

    private fun ComposeUiTest.buttonFill(label: String): Color {
        val pixels = onNode(hasClickAction() and hasText(label)).captureToImage().toPixelMap()
        return pixels[6, pixels.height / 2]
    }

    @Test
    fun with_everything_sent_the_dialog_only_asks_to_sign_out() = runComposeUiTest {
        show(unsent = 0)

        onNodeWithText("Te déconnecter ?").assertExists()
        check(onAllNodes(hasText("perdue", substring = true)).fetchSemanticsNodes().isEmpty())
        assertNotEquals(Color(0xFFB3361E), buttonFill("Me déconnecter"))
        onNodeWithText("Me déconnecter").performClick()
        assertEquals(1, signOuts)
    }

    @Test
    fun a_single_unsent_entry_is_announced_in_the_singular_with_only_an_acknowledgement() = runComposeUiTest {
        show(unsent = 1)

        onNodeWithText("1 saisie n'est pas encore envoyée. Reconnecte-toi au réseau pour l'envoyer avant de te déconnecter.").assertExists()
        assertNoSignOutChoice()
    }

    @Test
    fun three_unsent_entries_are_announced_in_the_plural_and_understood_only_closes_the_dialog() = runComposeUiTest {
        show(unsent = 3)

        onNodeWithText("3 saisies ne sont pas encore envoyées. Reconnecte-toi au réseau pour les envoyer avant de te déconnecter.").assertExists()
        assertNoSignOutChoice()
        assertNotEquals(Color(0xFFB3361E), buttonFill("Compris"))
        onNodeWithText("Compris").performClick()
        assertEquals(1, dismissals)
        assertEquals(0, signOuts)
    }

    @Test
    fun in_english() = runComposeUiTest {
        show(unsent = 0, locale = "en")

        onNodeWithText("Sign out?").assertExists()
        onNodeWithText("Sign me out").assertExists()
        onNodeWithText("Stay").assertExists()
    }

    @Test
    fun unsent_entries_in_english() = runComposeUiTest {
        show(unsent = 3, locale = "en")

        onNodeWithText("3 entries have not been sent yet. Reconnect to the network to send them before signing out.").assertExists()
        onNodeWithText("Got it").assertExists()
        check(onAllNodes(hasText("Sign me out")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun a_single_entry_in_english() = runComposeUiTest {
        show(unsent = 1, locale = "en")
        onNodeWithText("1 entry has not been sent yet. Reconnect to the network to send it before signing out.").assertExists()
    }

    private fun ComposeUiTest.assertNoSignOutChoice() {
        onNodeWithText("Compris").assertExists()
        for (label in listOf("Me déconnecter", "Rester")) {
            assertEquals(0, onAllNodes(hasText(label)).fetchSemanticsNodes().size, "« $label » ne doit pas s'afficher")
        }
    }
}
