package com.dmb.chantiertracker.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.main.LogoutPrompt
import com.dmb.chantiertracker.presentation.main.LogoutPromptDialog
import com.dmb.chantiertracker.presentation.theme.AppTheme
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class LogoutPromptDialogUiTest {

    @AfterTest fun tearDown() { customAppLocale = null }

    private class Clicks { var retry = 0; var anyway = 0; var dismiss = 0 }

    private fun mount(prompt: LogoutPrompt, locale: String = "fr", clicks: Clicks = Clicks()) = runComposeUiTest {
        setContent {
            customAppLocale = locale
            AppEnvironment {
                AppTheme {
                    LogoutPromptDialog(prompt, onRetry = { clicks.retry++ }, onLogoutAnyway = { clicks.anyway++ }, onDismiss = { clicks.dismiss++ })
                }
            }
        }
        waitForIdle()
        block(this, clicks)
    }

    private var block: (androidx.compose.ui.test.ComposeUiTest, Clicks) -> Unit = { _, _ -> }

    @Test
    fun while_sending_the_dialog_says_so_and_offers_nothing_to_click() {
        block = { ui, _ -> ui.onNodeWithText("Envoi de vos saisies avant la déconnexion…").assertExists() }
        mount(LogoutPrompt.Sending)
    }

    @Test
    fun a_blocked_sign_out_names_the_count_and_offers_retry_or_cancel() {
        val clicks = Clicks()
        block = { ui, c ->
            ui.onNodeWithText("Des saisies ne sont pas encore envoyées").assertExists()
            ui.onNodeWithText("3 saisies ne sont pas encore envoyées. Reconnectez-vous à Internet pour les envoyer avant de vous déconnecter.").assertExists()
            ui.onNodeWithText("Se déconnecter quand même").assertDoesNotExist()
            ui.onNodeWithText("Réessayer").performClick()
            ui.onNodeWithText("Annuler").performClick()
            assertEquals(1, c.retry)
            assertEquals(1, c.dismiss)
        }
        mount(LogoutPrompt.Blocked(3), clicks = clicks)
    }

    @Test
    fun a_single_unsent_entry_is_worded_in_the_singular() {
        block = { ui, _ ->
            ui.onNodeWithText("1 saisie n'est pas encore envoyée. Reconnectez-vous à Internet pour l'envoyer avant de vous déconnecter.").assertExists()
        }
        mount(LogoutPrompt.Blocked(1))
    }

    @Test
    fun refused_entries_warn_they_will_be_erased_if_another_account_signs_in() {
        val clicks = Clicks()
        block = { ui, c ->
            ui.onNodeWithText("Saisies refusées par le serveur").assertExists()
            ui.onNodeWithText("2 saisies ont été refusées par le serveur et ne seront pas envoyées. Elles seront effacées si un autre compte se connecte sur cet appareil.").assertExists()
            ui.onNodeWithText("Se déconnecter quand même").performClick()
            assertEquals(1, c.anyway)
        }
        mount(LogoutPrompt.RefusedWritesLeft(2), clicks = clicks)
    }

    @Test
    fun the_dialog_is_translated() {
        block = { ui, _ ->
            ui.onNodeWithText("Some entries have not been sent yet").assertExists()
            ui.onNodeWithText("Retry").assertExists()
        }
        mount(LogoutPrompt.Blocked(2), locale = "en")
    }
}
