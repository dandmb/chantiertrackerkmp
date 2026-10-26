package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.data.local.db.LegalDocumentEntity
import com.dmb.chantiertracker.data.remote.LegalDocumentsApi
import com.dmb.chantiertracker.data.repository.LegalDocumentRepositoryImpl
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.presentation.auth.login.LoginScreen
import com.dmb.chantiertracker.presentation.auth.login.LoginViewModel
import com.dmb.chantiertracker.presentation.auth.register.RegisterScreen
import com.dmb.chantiertracker.presentation.auth.register.RegisterViewModel
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.legal.LegalDocument
import com.dmb.chantiertracker.presentation.legal.LegalDocumentScreen
import com.dmb.chantiertracker.presentation.legal.LegalDocumentViewModel
import com.dmb.chantiertracker.presentation.legal.LegalPageOpener
import com.dmb.chantiertracker.presentation.legal.LocalLegalPageOpener
import com.dmb.chantiertracker.presentation.navigation.RootNavHost
import com.dmb.chantiertracker.presentation.navigation.RootViewModel
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.presentation.theme.Yellow
import com.dmb.chantiertracker.support.FakeAuthRepository
import com.dmb.chantiertracker.support.FakeBillingRepository
import com.dmb.chantiertracker.support.FakeCheckoutLauncher
import com.dmb.chantiertracker.support.FakeConnectivityObserver
import com.dmb.chantiertracker.support.FakeLegalDocumentDao
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.MutableClock
import com.dmb.chantiertracker.support.RecordingMockClient
import com.dmb.chantiertracker.support.legalDocumentJson
import com.dmb.chantiertracker.support.respondJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class LegalDocumentScreenUiTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() { Dispatchers.setMain(testDispatcher) }

    @AfterTest fun tearDown() {
        customAppLocale = null
        Dispatchers.resetMain()
    }

    private val frenchTerms = """
        ## 1. Objet

        Le Service est édité par \[À COMPLÉTER : nom et prénom\].

        - Lire les [CGV](https://chantiertracker.com/cgv).
        - Écrire à [dpo@chantiertracker.com](mailto:dpo@chantiertracker.com).
    """.trimIndent()

    private val englishTerms = """
        _This English translation is provided for convenience; in case of discrepancy, the French version prevails._

        ## 1. Purpose

        The Service is published by \[TO BE COMPLETED: first and last name\].
    """.trimIndent()

    private val connectivity = FakeConnectivityObserver(initiallyOnline = true)
    private var serverReachable = true
    private val client = RecordingMockClient(FakeTokenStorage(null), dispatcher = testDispatcher) { request ->
        if (!serverReachable) throw RuntimeException("no connection")
        val path = request.url.encodedPath
        val document = path.substringAfter("legal-documents/").substringBefore('/')
        respondJson(
            if (path.endsWith("/en")) legalDocumentJson(document = document, language = "en", title = "Terms of Use", content = englishTerms)
            else legalDocumentJson(document = document, language = "fr", title = "Conditions Générales d'Utilisation", content = frenchTerms),
        )
    }
    private val dao = FakeLegalDocumentDao()
    private val webOpened = mutableListOf<String>()
    private val mailOpened = mutableListOf<String>()

    private fun viewModel() = LegalDocumentViewModel(LegalDocumentRepositoryImpl(LegalDocumentsApi(client.client), dao, MutableClock(5_000L)), connectivity)

    private fun ComposeUiTest.mount(locale: String = "fr", content: @Composable () -> Unit) {
        val uriHandler = object : UriHandler {
            override fun openUri(uri: String) { mailOpened += uri }
        }
        setContent {
            customAppLocale = locale
            AppEnvironment {
                AppTheme {
                    CompositionLocalProvider(
                        LocalLegalPageOpener provides LegalPageOpener { webOpened += it },
                        LocalUriHandler provides uriHandler,
                    ) {
                        Box(Modifier.size(412.dp, 892.dp)) { content() }
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.mountScreen(document: LegalDocument = LegalDocument.TermsOfUse, locale: String = "fr", vm: LegalDocumentViewModel = viewModel()) =
        mount(locale) { LegalDocumentScreen(document, viewModel = vm) }

    private fun ComposeUiTest.assertShows(text: String) {
        waitForIdle()
        assertTrue(onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty(), "not shown: $text")
    }

    private fun ComposeUiTest.annotatedTextContaining(text: String): AnnotatedString =
        onNodeWithText(text, substring = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.single()

    private fun ComposeUiTest.tapInside(fullText: String, linkText: String) {
        val node = onNodeWithText(fullText, substring = true)
        node.performScrollTo()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty(), "text layout not exposed")
        val shown = layouts.single().layoutInput.text.text
        val center = layouts.single().getBoundingBox(shown.indexOf(linkText) + linkText.length / 2).center
        node.performTouchInput { click(center) }
        waitForIdle()
    }

    private fun welcomeStartingRoot(): RootViewModel {
        val auth = FakeAuthRepository().apply {
            onboardingSeen = true
            emitState(AuthState.Unauthenticated)
        }
        return RootViewModel(auth, com.dmb.chantiertracker.data.session.SessionNotices())
    }

    @Test
    fun a_link_of_the_welcome_screen_opens_the_native_screen_with_the_server_title_and_date() = runComposeUiTest {
        val vm = viewModel()
        mount { RootNavHost(welcomeStartingRoot(), legalDocumentViewModel = { remember { vm } }) }
        assertShows("Créer un compte")

        onNodeWithText("CGU").performClick()
        assertShows("Mis à jour le 25-10-2026")

        onNodeWithText("Conditions Générales d'Utilisation").assertIsDisplayed()
        onNodeWithText("1. Objet").assertIsDisplayed()
        assertEquals("/api/v1/legal-documents/terms-of-use/fr", client.requests.single().url.encodedPath)
        assertEquals(emptyList(), webOpened, "no web page any more for the six links")
    }

    @Test
    fun the_six_links_of_the_welcome_screen_ask_for_their_own_document() = runComposeUiTest {
        mount { RootNavHost(welcomeStartingRoot(), legalDocumentViewModel = { remember { viewModel() } }) }
        assertShows("Créer un compte")

        listOf("Mentions légales", "CGU", "CGV", "Confidentialité", "Cookies", "Suppression du compte").forEach { link ->
            onNodeWithText(link).performClick()
            assertShows("Mis à jour le")
            onNodeWithContentDescription("Retour").performClick()
            assertShows("Créer un compte")
        }

        assertEquals(
            listOf("legal-notice", "terms-of-use", "terms-of-sale", "privacy-policy", "cookie-policy", "account-deletion").map { "/api/v1/legal-documents/$it/fr" },
            client.requests.map { it.url.encodedPath },
        )
    }

    @Test
    fun an_english_phone_reads_the_english_text_with_its_english_date_line() = runComposeUiTest {
        mountScreen(locale = "en")
        assertShows("Updated on 25-10-2026")

        onNodeWithText("This English translation is provided for convenience", substring = true).assertIsDisplayed()
        onNodeWithText("1. Purpose").assertIsDisplayed()
        assertEquals("/api/v1/legal-documents/terms-of-use/en", client.requests.single().url.encodedPath)
    }

    @Test
    fun the_french_and_english_texts_still_to_be_completed_are_highlighted() = runComposeUiTest {
        mountScreen()
        assertShows("[À COMPLÉTER : nom et prénom]")

        val french = annotatedTextContaining("[À COMPLÉTER : nom et prénom]")
        val pending = french.text.indexOf("[À COMPLÉTER")
        assertTrue(
            french.spanStyles.any { it.item.background == Yellow && it.start == pending && it.end == french.text.indexOf(']', pending) + 1 },
            "highlighted: ${french.spanStyles}",
        )
        assertTrue(annotatedTextContaining("Le Service est édité par").text.startsWith("Le Service est édité par ["), "escape removed")
    }

    @Test
    fun the_english_pending_text_is_highlighted_too() = runComposeUiTest {
        mountScreen(locale = "en")
        assertShows("[TO BE COMPLETED: first and last name]")

        val english = annotatedTextContaining("[TO BE COMPLETED")
        assertTrue(english.spanStyles.any { it.item.background == Yellow }, "highlighted: ${english.spanStyles}")
    }

    @Test
    fun a_web_link_of_the_text_opens_in_the_in_app_tab_and_a_mail_link_in_the_mail_app() = runComposeUiTest {
        mountScreen()
        assertShows("Lire les CGV.")

        tapInside("Lire les CGV.", "CGV")
        tapInside("Écrire à dpo@chantiertracker.com.", "dpo@chantiertracker.com")

        assertEquals(listOf("https://chantiertracker.com/cgv"), webOpened)
        assertEquals(listOf("mailto:dpo@chantiertracker.com"), mailOpened)
    }

    @Test
    fun offline_a_web_link_of_the_text_gives_its_address_instead_of_opening() = runComposeUiTest {
        mountScreen()
        assertShows("Lire les CGV.")
        connectivity.setOnline(false)

        tapInside("Lire les CGV.", "CGV")
        assertShows("Tu es hors ligne")

        onNodeWithText("https://chantiertracker.com/cgv").assertIsDisplayed()
        assertEquals(emptyList(), webOpened)
        onNodeWithText("OK").performClick()
        waitForIdle()
        onNodeWithText("Tu es hors ligne").assertDoesNotExist()
    }

    @Test
    fun offline_the_last_version_received_is_shown_with_a_clear_notice() = runComposeUiTest {
        connectivity.setOnline(false)
        serverReachable = false
        val saved = FakeLegalDocumentDao(
            LegalDocumentEntity(
                document = "terms-of-use", requestedLanguage = "fr", language = "fr", fallback = false,
                title = "Conditions Générales d'Utilisation", content = "## 1. Objet\n\nTexte enregistré.", version = 1,
                publishedOn = "2026-10-01", receivedAt = 1L,
            ),
        )
        mountScreen(vm = LegalDocumentViewModel(LegalDocumentRepositoryImpl(LegalDocumentsApi(client.client), saved, MutableClock(5_000L)), connectivity))
        assertShows("Texte enregistré.")

        onNodeWithText("Tu es hors ligne : voici la dernière version reçue sur cet appareil.").assertIsDisplayed()
        onNodeWithText("Mis à jour le 01-10-2026").assertIsDisplayed()
    }

    @Test
    fun offline_a_document_never_received_says_so_and_retry_shows_it_once_back_online() = runComposeUiTest {
        connectivity.setOnline(false)
        serverReachable = false
        mountScreen()
        assertShows("Document indisponible hors ligne")
        onNodeWithText("Ce document n'a encore jamais été chargé sur cet appareil. Connecte-toi à Internet, puis réessaie.").assertIsDisplayed()

        connectivity.setOnline(true)
        serverReachable = true
        onNodeWithText("Réessayer").performClick()
        assertShows("1. Objet")

        onNodeWithText("Document indisponible hors ligne").assertDoesNotExist()
    }

    @Test
    fun the_login_screen_keeps_the_six_documents_reachable() = runComposeUiTest {
        val opened = mutableListOf<LegalDocument>()
        mount {
            LoginScreen(
                onNavigateToRegister = {},
                onNavigateToForgotPassword = {},
                onOpenLegalDocument = { opened += it },
                viewModel = LoginViewModel(FakeAuthRepository(), FakeCheckoutLauncher(), FakeBillingRepository()),
            )
        }

        onNodeWithText("Mentions légales").performScrollTo().performClick()
        onNodeWithText("Suppression du compte").performScrollTo().performClick()

        assertEquals(listOf(LegalDocument.LegalNotice, LegalDocument.AccountDeletion), opened)
    }

    @Test
    fun the_register_consent_sentence_links_open_the_terms_of_use_and_the_privacy_policy() = runComposeUiTest {
        val opened = mutableListOf<LegalDocument>()
        mount {
            RegisterScreen(onRegistered = {}, onBackToLogin = {}, onOpenLegalDocument = { opened += it }, viewModel = RegisterViewModel(FakeAuthRepository()))
        }
        val sentence = "En créant un compte, tu acceptes nos Conditions générales d'utilisation et notre Politique de confidentialité."

        tapInside(sentence, "Conditions générales d'utilisation")
        tapInside(sentence, "Politique de confidentialité")

        assertEquals(listOf(LegalDocument.TermsOfUse, LegalDocument.PrivacyPolicy), opened)
    }
}
