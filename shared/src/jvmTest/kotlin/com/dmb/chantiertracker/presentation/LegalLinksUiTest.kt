package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.presentation.auth.login.LoginScreen
import com.dmb.chantiertracker.presentation.auth.login.LoginViewModel
import com.dmb.chantiertracker.presentation.auth.register.RegisterScreen
import com.dmb.chantiertracker.presentation.auth.register.RegisterViewModel
import com.dmb.chantiertracker.presentation.auth.welcome.WelcomeScreen
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.legal.LegalDocument
import com.dmb.chantiertracker.presentation.legal.LegalLinksViewModel
import com.dmb.chantiertracker.presentation.legal.LegalPageOpener
import com.dmb.chantiertracker.presentation.legal.LocalLegalPageOpener
import com.dmb.chantiertracker.presentation.legal.rememberOpenLegalDocument
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeAuthRepository
import com.dmb.chantiertracker.support.FakeBillingRepository
import com.dmb.chantiertracker.support.FakeCheckoutLauncher
import com.dmb.chantiertracker.support.FakeConnectivityObserver
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LegalLinksUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private val opened = mutableListOf<String>()

    private fun ComposeUiTest.mount(
        online: Boolean = true,
        locale: String = "fr",
        opener: LegalPageOpener = LegalPageOpener { opened += it },
        screen: @Composable (open: (LegalDocument) -> Unit) -> Unit,
    ) {
        val viewModel = LegalLinksViewModel(FakeConnectivityObserver(initiallyOnline = online))
        setContent {
            customAppLocale = locale
            AppEnvironment {
                AppTheme {
                    CompositionLocalProvider(LocalLegalPageOpener provides opener) {
                        Box(Modifier.size(412.dp, 892.dp)) { screen(rememberOpenLegalDocument(viewModel)) }
                    }
                }
            }
        }
        waitForIdle()
    }

    @Composable
    private fun Welcome(open: (LegalDocument) -> Unit) =
        WelcomeScreen(onCreateAccount = {}, onSignIn = {}, onDiscoverPlans = {}, onOpenLegalDocument = open)

    @Test
    fun the_welcome_screen_offers_the_six_pages_of_the_site_before_any_account_exists() = runComposeUiTest {
        mount { Welcome(it) }

        listOf("Mentions légales", "CGU", "CGV", "Confidentialité", "Cookies", "Suppression du compte")
            .forEach { onNodeWithText(it).assertExists() }
    }

    @Test
    fun online_a_link_opens_its_page_of_the_site_and_no_message_is_shown() = runComposeUiTest {
        mount { Welcome(it) }

        onNodeWithText("CGU").performClick()
        onNodeWithText("Suppression du compte").performClick()
        waitForIdle()

        assertEquals(listOf("https://chantiertracker.com/cgu", "https://chantiertracker.com/suppression-compte"), opened)
        onNodeWithText("Tu es hors ligne").assertDoesNotExist()
    }

    @Test
    fun offline_a_clear_message_gives_the_address_of_the_page_instead_of_opening_it() = runComposeUiTest {
        mount(online = false) { Welcome(it) }

        onNodeWithText("Confidentialité").performClick()
        waitForIdle()

        assertEquals(emptyList(), opened)
        onNodeWithText("Tu es hors ligne").assertIsDisplayed()
        onNodeWithText("Politique de confidentialité", substring = true).assertIsDisplayed()
        onNodeWithText("https://chantiertracker.com/confidentialite").assertIsDisplayed()
        onNodeWithText("OK").performClick()
        waitForIdle()
        onNodeWithText("Tu es hors ligne").assertDoesNotExist()
    }

    @Test
    fun an_english_reader_gets_the_offline_message_in_english() = runComposeUiTest {
        mount(online = false, locale = "en") { Welcome(it) }

        onNodeWithText("Account deletion").performClick()
        waitForIdle()

        onNodeWithText("You are offline").assertIsDisplayed()
        onNodeWithText("https://chantiertracker.com/suppression-compte").assertIsDisplayed()
    }

    @Test
    fun a_page_that_no_browser_can_show_gives_its_address() = runComposeUiTest {
        mount(opener = LegalPageOpener { error("no browser") }) { Welcome(it) }

        onNodeWithText("Cookies").performClick()
        waitForIdle()

        onNodeWithText("Impossible d'ouvrir la page").assertIsDisplayed()
        onNodeWithText("https://chantiertracker.com/cookies").assertIsDisplayed()
    }

    @Test
    fun the_login_screen_keeps_the_pages_reachable_for_returning_users() = runComposeUiTest {
        mount { open ->
            LoginScreen(
                onNavigateToRegister = {},
                onNavigateToForgotPassword = {},
                onOpenLegalDocument = open,
                viewModel = LoginViewModel(FakeAuthRepository(), FakeCheckoutLauncher(), FakeBillingRepository()),
            )
        }

        onNodeWithText("Mentions légales").performScrollTo().performClick()
        onNodeWithText("Suppression du compte").performScrollTo().performClick()
        waitForIdle()

        assertEquals(listOf("https://chantiertracker.com/mentions-legales", "https://chantiertracker.com/suppression-compte"), opened)
    }

    @Test
    fun the_register_consent_sentence_links_open_the_terms_of_use_and_the_privacy_policy() = runComposeUiTest {
        mount { open ->
            RegisterScreen(onRegistered = {}, onBackToLogin = {}, onOpenLegalDocument = open, viewModel = RegisterViewModel(FakeAuthRepository()))
        }
        val sentence = "En créant un compte, tu acceptes nos Conditions générales d'utilisation et notre Politique de confidentialité."

        tapInside(sentence, "Conditions générales d'utilisation")
        tapInside(sentence, "Politique de confidentialité")
        waitForIdle()

        assertEquals(listOf("https://chantiertracker.com/cgu", "https://chantiertracker.com/confidentialite"), opened)
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
