package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.reports.ReportEntryScreen
import com.dmb.chantiertracker.presentation.reports.ReportEntryViewModel
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeReportRepository
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ReportEntryScreenUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private fun ComposeUiTest.mount(repo: FakeReportRepository, onDone: () -> Unit = {}) {
        setContent {
            customAppLocale = "fr"
            AppEnvironment {
                AppTheme {
                    Box(Modifier.size(412.dp, 892.dp)) {
                        ReportEntryScreen(
                            entryLocalId = "entry-1",
                            onDone = onDone,
                            viewModel = ReportEntryViewModel(repo),
                        )
                    }
                }
            }
        }
        waitForIdle()
    }

    @Test
    fun the_send_button_is_disabled_until_a_message_is_typed() = runComposeUiTest {
        mount(FakeReportRepository())

        onNodeWithText("Message").assertExists()
        onNodeWithText("Envoyer").assertIsNotEnabled()

        onNodeWithText("Message").performTextInput("Quantité incorrecte")
        waitForIdle()
        onNodeWithText("Envoyer").assertExists()
    }

    @Test
    fun sending_shows_the_confirmation_and_records_the_report() = runComposeUiTest {
        val repo = FakeReportRepository()
        mount(repo)

        onNodeWithText("Message").performTextInput("Livraison oubliée")
        waitForIdle()
        onNodeWithText("Envoyer").performClick()
        waitForIdle()

        assertEquals(listOf("entry-1" to "Livraison oubliée"), repo.createdReports)
        onNodeWithText("Signalement envoyé. L'administrateur du projet en sera informé.").assertExists()
        onNodeWithText("Fermer").assertExists()
    }

    @Test
    fun closing_the_confirmation_calls_on_done() = runComposeUiTest {
        var done = false
        mount(FakeReportRepository(), onDone = { done = true })

        onNodeWithText("Message").performTextInput("x")
        waitForIdle()
        onNodeWithText("Envoyer").performClick()
        waitForIdle()
        onNodeWithText("Fermer").performClick()

        assertEquals(true, done)
    }

    @Test
    fun a_failure_keeps_the_form_and_shows_the_error() = runComposeUiTest {
        val repo = FakeReportRepository().apply { createError = DomainException.Network }
        mount(repo)

        onNodeWithText("Message").performTextInput("Problème")
        waitForIdle()
        onNodeWithText("Envoyer").performClick()
        waitForIdle()

        onNodeWithText("Message").assertExists()
        onNodeWithText("Signalement envoyé. L'administrateur du projet en sera informé.").assertDoesNotExist()
    }
}
