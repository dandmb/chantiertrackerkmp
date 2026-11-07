package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.BillingAvailability
import com.dmb.chantiertracker.domain.model.FounderOffer
import com.dmb.chantiertracker.presentation.auth.welcome.ProductPresentationSheet
import com.dmb.chantiertracker.presentation.billing.LocalFounderOffer
import com.dmb.chantiertracker.presentation.billing.LocalServerBillingStatus
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.navigation.RootNavHost
import com.dmb.chantiertracker.presentation.navigation.RootViewModel
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class ProductPresentationSheetUiTest {

    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @AfterTest fun tearDown() {
        customAppLocale = null
        Dispatchers.resetMain()
    }

    private val offer = FounderOffer(
        projects = 3, photos = 150, videos = 5, videoMaxSeconds = 120, supervisorsPerProject = 3, pdfExport = true,
        historyDays = 180,
    )

    private val offlineSentence =
        "L'app fonctionne avec ou sans connexion Internet : tes saisies sont gardées et envoyées dès que le réseau revient."

    private fun ComposeUiTest.mount(
        locale: String = "fr",
        status: BillingAvailability = BillingAvailability.CLOSED,
        founderOffer: FounderOffer? = offer,
        content: @Composable () -> Unit,
    ) = setContent {
        customAppLocale = locale
        AppEnvironment {
            AppTheme {
                CompositionLocalProvider(LocalServerBillingStatus provides status, LocalFounderOffer provides founderOffer) {
                    Surface { Box(Modifier.fillMaxSize()) { content() } }
                }
            }
        }
    }

    private fun ComposeUiTest.awaitText(text: String) =
        waitUntil(timeoutMillis = 5_000L) { onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun runPhoneTest(block: ComposeUiTest.() -> Unit) = runDesktopComposeUiTest(width = 412, height = 892) { block() }

    @Test
    fun the_sheet_gives_a_short_hook_the_benefits_the_founder_offer_and_the_offline_sentence() = runPhoneTest {
        mount { ProductPresentationSheet(onDismiss = {}) }
        awaitText("Ton chantier sous contrôle")

        onNodeWithText("Ton chantier sous contrôle, même à distance", ignoreCase = true).assertExists()
        onNodeWithText("Le stock se tient tout seul").assertExists()
        onNodeWithText("Offre fondateur", ignoreCase = true).assertExists()
        onNodeWithText("3 projets").assertExists()
        onNodeWithText(offlineSentence).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun the_close_button_closes_the_sheet() = runPhoneTest {
        var dismissed = 0
        mount { ProductPresentationSheet(onDismiss = { dismissed++ }) }
        awaitText("C'est parti")

        onNodeWithText("C'est parti").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000L) { dismissed == 1 }
        assertEquals(1, dismissed)
    }

    @Test
    fun an_open_switch_hides_the_founder_offer_and_keeps_the_rest() = runPhoneTest {
        mount(status = BillingAvailability.OPEN) { ProductPresentationSheet(onDismiss = {}) }
        awaitText("Ton chantier sous contrôle")

        onNodeWithText("Offre fondateur", ignoreCase = true).assertDoesNotExist()
        onNodeWithText(offlineSentence).assertExists()
    }

    @Test
    fun in_english_the_sheet_says_the_app_works_offline() = runPhoneTest {
        mount(locale = "en") { ProductPresentationSheet(onDismiss = {}) }

        awaitText("The app works with or without an internet connection: what you enter is kept and sent as soon as the network is back.")
        onNodeWithText("Let's go").assertExists()
    }

    private fun ComposeUiTest.mountRoot(onboardingSeen: Boolean): FakeAuthRepository {
        val auth = FakeAuthRepository().apply {
            this.onboardingSeen = onboardingSeen
            emitState(AuthState.Unauthenticated)
        }
        mount { RootNavHost(RootViewModel(auth, com.dmb.chantiertracker.data.session.SessionNotices())) }
        return auth
    }

    @Test
    fun right_after_the_onboarding_the_sheet_opens_over_the_welcome_screen_and_closes_onto_it() = runPhoneTest {
        mountRoot(onboardingSeen = false)
        awaitText("Passer")
        onNodeWithText("Passer").performClick()
        awaitText(offlineSentence)

        onNodeWithText("C'est parti").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000L) { onAllNodes(hasText(offlineSentence)).fetchSemanticsNodes().isEmpty() }

        onNodeWithText("Créer un compte").assertIsDisplayed()
        onNodeWithText("Se connecter").assertIsDisplayed()
    }

    @Test
    fun a_later_launch_opens_on_the_welcome_screen_and_the_presentation_stays_one_tap_away() = runPhoneTest {
        mountRoot(onboardingSeen = true)
        awaitText("Créer un compte")
        check(onAllNodes(hasText(offlineSentence)).fetchSemanticsNodes().isEmpty()) { "no sheet at a later launch" }

        onNodeWithText("Découvrir ChantierTracker").performScrollTo().performClick()
        awaitText(offlineSentence)
    }
}
