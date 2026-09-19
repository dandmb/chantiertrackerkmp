package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.presentation.auth.login.LoginScreen
import com.dmb.chantiertracker.presentation.auth.login.LoginViewModel
import com.dmb.chantiertracker.presentation.auth.register.RegisterScreen
import com.dmb.chantiertracker.presentation.auth.register.RegisterViewModel
import com.dmb.chantiertracker.presentation.auth.welcome.WelcomeScreen
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.legal.LegalDocument
import com.dmb.chantiertracker.presentation.legal.StandaloneLegalDocumentScreen
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeAuthRepository
import com.dmb.chantiertracker.support.FakeCheckoutLauncher
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LegalScreensUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private fun ComposeUiTest.mount(locale: String = "fr", screen: @androidx.compose.runtime.Composable () -> Unit) {
        setContent {
            customAppLocale = locale
            AppEnvironment {
                AppTheme {
                    Box(Modifier.size(412.dp, 892.dp)) { screen() }
                }
            }
        }
        waitForIdle()
    }

    @Test
    fun a_document_shows_its_title_bar_the_update_date_and_its_numbered_sections() = runComposeUiTest {
        mount { StandaloneLegalDocumentScreen(LegalDocument.TermsOfUse, onBack = {}) }

        onNodeWithText("Conditions générales d'utilisation").assertIsDisplayed()
        onNodeWithText("Dernière mise à jour : [À COMPLÉTER : date]").assertIsDisplayed()
        onNodeWithText("1. Objet").assertIsDisplayed()
        onNodeWithText("10. Droit applicable").assertExists()
    }

    @Test
    fun a_list_is_rendered_as_bullets_with_the_exact_web_wording() = runComposeUiTest {
        mount { StandaloneLegalDocumentScreen(LegalDocument.TermsOfUse, onBack = {}) }

        onNodeWithText("ne pas contourner les mesures de sécurité du Service ;").assertExists()
        onAllNodes(hasText("•")).assertCountEquals(4)
    }

    @Test
    fun unknown_editor_information_is_shown_as_the_same_a_completer_markers_as_the_web() = runComposeUiTest {
        mount { StandaloneLegalDocumentScreen(LegalDocument.LegalNotice, onBack = {}) }

        onNodeWithText("Numéro SIRET : [À COMPLÉTER : numéro SIRET].").assertExists()
        onNodeWithText("Statut juridique : [À COMPLÉTER : micro-entrepreneur / société].").assertExists()
    }

    @Test
    fun no_document_ever_shows_a_raw_placeholder_token() = runComposeUiTest {
        val current = androidx.compose.runtime.mutableStateOf(LegalDocument.LegalNotice)
        mount { StandaloneLegalDocumentScreen(current.value, onBack = {}) }

        LegalDocument.entries.forEach { document ->
            current.value = document
            waitForIdle()
            onAllNodes(hasText("{", substring = true)).assertCountEquals(0)
        }
    }

    @Test
    fun the_back_arrow_leaves_the_document() = runComposeUiTest {
        var backs = 0
        mount { StandaloneLegalDocumentScreen(LegalDocument.CookiePolicy, onBack = { backs++ }) }

        onNodeWithContentDescription("Retour").performClick()

        assertEquals(1, backs)
    }

    @Test
    fun an_english_user_reads_the_english_legal_text_with_english_placeholders() = runComposeUiTest {
        mount(locale = "en") { StandaloneLegalDocumentScreen(LegalDocument.PrivacyPolicy, onBack = {}) }

        onNodeWithText("Privacy policy").assertIsDisplayed()
        onNodeWithText("Last updated: [TO BE COMPLETED: date]").assertIsDisplayed()
        onNodeWithText("1. Data controller").assertExists()
        onNodeWithText("1. Responsable du traitement").assertDoesNotExist()
        onAllNodes(hasText("À COMPLÉTER", substring = true)).assertCountEquals(0)
    }

    @Test
    fun every_document_states_in_both_languages_that_only_the_french_version_is_binding() = runComposeUiTest {
        val current = androidx.compose.runtime.mutableStateOf(LegalDocument.LegalNotice)
        mount(locale = "en") { StandaloneLegalDocumentScreen(current.value, onBack = {}) }

        LegalDocument.entries.forEach { document ->
            current.value = document
            waitForIdle()
            onNodeWithText("only the French version is legally binding", substring = true).assertIsDisplayed()
        }
    }

    @Test
    fun the_french_reader_sees_the_precedence_reminder_too() = runComposeUiTest {
        mount { StandaloneLegalDocumentScreen(LegalDocument.TermsOfSale, onBack = {}) }

        onNodeWithText("seule la version française fait foi juridiquement", substring = true).assertIsDisplayed()
    }

    @Test
    fun the_welcome_screen_offers_the_five_documents_before_any_account_exists() = runComposeUiTest {
        val opened = mutableListOf<LegalDocument>()
        mount {
            WelcomeScreen(onCreateAccount = {}, onSignIn = {}, onDiscoverPlans = {}, onOpenLegalDocument = { opened += it })
        }

        listOf("Mentions légales", "CGU", "CGV", "Confidentialité", "Cookies").forEach { onNodeWithText(it).assertExists() }
        onNodeWithText("CGV").performClick()
        onNodeWithText("Confidentialité").performClick()

        assertEquals(listOf(LegalDocument.TermsOfSale, LegalDocument.PrivacyPolicy), opened)
    }

    @Test
    fun the_login_screen_keeps_the_documents_reachable_for_returning_users_who_never_see_the_welcome_again() = runComposeUiTest {
        val opened = mutableListOf<LegalDocument>()
        mount {
            LoginScreen(
                onNavigateToRegister = {},
                onNavigateToForgotPassword = {},
                onOpenLegalDocument = { opened += it },
                viewModel = LoginViewModel(FakeAuthRepository(), FakeCheckoutLauncher()),
            )
        }

        onNodeWithText("Mentions légales").performScrollTo().performClick()
        onNodeWithText("Cookies").performScrollTo().performClick()

        assertEquals(listOf(LegalDocument.LegalNotice, LegalDocument.CookiePolicy), opened)
    }

    @Test
    fun the_register_screen_states_the_consent_sentence_with_two_working_links() = runComposeUiTest {
        val opened = mutableListOf<LegalDocument>()
        mount {
            RegisterScreen(
                onRegistered = {},
                onBackToLogin = {},
                onOpenLegalDocument = { opened += it },
                viewModel = RegisterViewModel(FakeAuthRepository()),
            )
        }
        val sentence = "En créant un compte, vous acceptez nos Conditions générales d'utilisation et notre Politique de confidentialité."

        onNodeWithText(sentence).assertExists()
        tapInside(sentence, "Conditions générales d'utilisation")
        tapInside(sentence, "Politique de confidentialité")

        assertEquals(listOf(LegalDocument.TermsOfUse, LegalDocument.PrivacyPolicy), opened)
    }

    @Test
    fun the_register_consent_sentence_is_translated() = runComposeUiTest {
        mount(locale = "en") {
            RegisterScreen(
                onRegistered = {},
                onBackToLogin = {},
                onOpenLegalDocument = {},
                viewModel = RegisterViewModel(FakeAuthRepository()),
            )
        }

        onNodeWithText("By creating an account, you agree to our Terms of use and our Privacy policy.").assertExists()
    }

    private fun ComposeUiTest.tapInside(fullText: String, linkText: String) {
        val node = onNodeWithText(fullText)
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty(), "text layout not exposed")
        val center = layouts.single().getBoundingBox(fullText.indexOf(linkText) + linkText.length / 2).center
        node.performTouchInput { click(center) }
        waitForIdle()
    }
}
