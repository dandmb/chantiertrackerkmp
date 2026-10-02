package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.BillingAvailability
import com.dmb.chantiertracker.presentation.billing.BillingAvailabilityProvider
import com.dmb.chantiertracker.presentation.billing.LocalBillingAvailability
import com.dmb.chantiertracker.presentation.billing.paidPlansAreOffered
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.navigation.RootNavHost
import com.dmb.chantiertracker.presentation.navigation.RootViewModel
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeAuthRepository
import com.dmb.chantiertracker.support.FakeBillingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

// ADR-66 — the two places where the billing status is wired for real: the
// provider App() installs, and the pre-sign-in navigation graph.
//
// Unconfined main dispatcher, unlike the other UI tests' StandardTestDispatcher:
// NavHost and the lifecycle observers check the main thread by running a block
// on Dispatchers.Main, which a never-advanced standard dispatcher would hang on.
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class BillingAvailabilityWiringUiTest {

    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @AfterTest fun tearDown() {
        customAppLocale = null
        Dispatchers.resetMain()
    }

    private fun ComposeUiTest.mount(content: @Composable () -> Unit) {
        setContent {
            customAppLocale = "fr"
            AppEnvironment {
                AppTheme {
                    Box(Modifier.size(412.dp, 892.dp)) { content() }
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.awaitText(text: String) =
        waitUntil(timeoutMillis = 5_000L) {
            onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

    private fun ComposeUiTest.awaitNoText(text: String) =
        waitUntil(timeoutMillis = 5_000L) {
            onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isEmpty()
        }

    // ---- the provider App() installs

    @Test
    fun the_app_level_provider_asks_the_server_on_start_and_publishes_the_answer() = runComposeUiTest {
        val billing = FakeBillingRepository(BillingAvailability.UNKNOWN).apply { refreshedAvailability = BillingAvailability.OPEN }
        mount {
            BillingAvailabilityProvider(billing) {
                Text(if (paidPlansAreOffered()) "offered" else "hidden")
            }
        }

        awaitText("offered")
        assertTrue(billing.refreshAvailabilityCount >= 1)
    }

    @Test
    fun the_app_level_provider_keeps_paid_plans_hidden_when_the_server_cannot_be_reached() = runComposeUiTest {
        val billing = FakeBillingRepository(BillingAvailability.UNKNOWN)
        mount {
            BillingAvailabilityProvider(billing) {
                Text(if (paidPlansAreOffered()) "offered" else "hidden")
            }
        }

        waitForIdle()
        assertTrue(billing.refreshAvailabilityCount >= 1, "it did try")
        onNodeWithText("hidden").assertExists()
    }

    // ---- before sign-in: the real navigation graph

    private fun welcomeStartingRoot(): RootViewModel {
        val auth = FakeAuthRepository().apply {
            onboardingSeen = true
            emitState(AuthState.Unauthenticated)
        }
        return RootViewModel(auth)
    }

    @Test
    fun before_sign_in_a_closed_billing_leaves_no_way_into_the_plan_selection() = runComposeUiTest {
        val root = welcomeStartingRoot()
        mount {
            CompositionLocalProvider(LocalBillingAvailability provides BillingAvailability.CLOSED) {
                RootNavHost(root)
            }
        }

        awaitText("Créer un compte")
        onNodeWithText("Découvrir nos formules").assertDoesNotExist()
    }

    @Test
    fun before_sign_in_an_unknown_billing_status_leaves_no_way_into_the_plan_selection() = runComposeUiTest {
        val root = welcomeStartingRoot()
        mount {
            CompositionLocalProvider(LocalBillingAvailability provides BillingAvailability.UNKNOWN) {
                RootNavHost(root)
            }
        }

        awaitText("Créer un compte")
        onNodeWithText("Découvrir nos formules").assertDoesNotExist()
    }

    @Test
    fun the_plan_selection_closes_itself_when_billing_closes_while_it_is_on_screen() = runComposeUiTest {
        val root = welcomeStartingRoot()
        var availability by mutableStateOf(BillingAvailability.OPEN)
        mount {
            CompositionLocalProvider(LocalBillingAvailability provides availability) {
                RootNavHost(root)
            }
        }
        awaitText("Découvrir nos formules")
        onNodeWithText("Découvrir nos formules").performScrollTo().performClick()
        awaitText("Choisir une formule")

        availability = BillingAvailability.CLOSED

        awaitNoText("Choisir une formule")
        awaitText("Créer un compte")
        onNodeWithText("Découvrir nos formules").assertDoesNotExist()
        onNodeWithText("€", substring = true).assertDoesNotExist()
    }
}
