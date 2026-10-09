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
            ui.onNodeWithText("Pas encore envoyé : 3 saisies. Reconnectez-vous à Internet pour l'envoyer avant de vous déconnecter.").assertExists()
            ui.onNodeWithText("Se déconnecter quand même").assertDoesNotExist()
            ui.onNodeWithText("Réessayer").performClick()
            ui.onNodeWithText("Annuler").performClick()
            assertEquals(1, c.retry)
            assertEquals(1, c.dismiss)
        }
        mount(LogoutPrompt.Blocked(com.dmb.chantiertracker.domain.model.UnsentWrites(entries = 3)), clicks = clicks)
    }

    @Test
    fun a_single_unsent_entry_is_worded_in_the_singular() {
        block = { ui, _ ->
            ui.onNodeWithText("Pas encore envoyé : 1 saisie. Reconnectez-vous à Internet pour l'envoyer avant de vous déconnecter.").assertExists()
        }
        mount(LogoutPrompt.Blocked(com.dmb.chantiertracker.domain.model.UnsentWrites(entries = 1)))
    }

    @Test
    fun refused_entries_warn_they_will_be_erased_if_another_account_signs_in() {
        val clicks = Clicks()
        block = { ui, c ->
            ui.onNodeWithText("Saisies refusées par le serveur").assertExists()
            ui.onNodeWithText("Refusé par le serveur, ou en attente d'un élément refusé : 2 saisies. Rien de cela ne sera envoyé, et tout sera effacé si un autre compte se connecte sur cet appareil.").assertExists()
            ui.onNodeWithText("Se déconnecter quand même").performClick()
            assertEquals(1, c.anyway)
        }
        mount(LogoutPrompt.RefusedWritesLeft(com.dmb.chantiertracker.domain.model.UnsentWrites(entries = 2)), clicks = clicks)
    }

    @Test
    fun the_dialog_is_translated() {
        block = { ui, _ ->
            ui.onNodeWithText("Some entries have not been sent yet").assertExists()
            ui.onNodeWithText("Retry").assertExists()
        }
        mount(LogoutPrompt.Blocked(com.dmb.chantiertracker.domain.model.UnsentWrites(entries = 2)), locale = "en")
    }

    @Test
    fun what_is_unsent_is_listed_by_nature_in_french_and_in_english() {
        val unsent = com.dmb.chantiertracker.domain.model.UnsentWrites(projects = 1, entries = 1, lines = 2, attachments = 1)
        block = { ui, _ ->
            ui.onNodeWithText("Pas encore envoyé : 1 projet, 1 saisie, 2 lignes et 1 justificatif. Reconnectez-vous à Internet pour l'envoyer avant de vous déconnecter.").assertExists()
        }
        mount(LogoutPrompt.Blocked(unsent))
        block = { ui, _ ->
            ui.onNodeWithText("Refused by the server, or waiting on a refused item: 1 project, 1 entry, 2 lines and 1 attachment. None of this will be sent, and all of it will be erased if another account signs in on this device.").assertExists()
        }
        mount(LogoutPrompt.RefusedWritesLeft(unsent), locale = "en")
    }
}
