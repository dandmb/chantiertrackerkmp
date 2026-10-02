package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.domain.model.BillingAvailability
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.presentation.auth.welcome.WelcomeScreen
import com.dmb.chantiertracker.presentation.billing.LocalBillingAvailability
import com.dmb.chantiertracker.presentation.billing.paidPlansAreOffered
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.presentation.main.AccountMenuBody
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

// ADR-66 — everything the founders launch mode hides or rewords outside the
// billing screen itself (which has its own BillingScreenUiTest). What needs a
// real NavHost or a live lifecycle is in BillingAvailabilityWiringUiTest.
@OptIn(ExperimentalTestApi::class)
class FoundersLaunchModeUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
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

    // ---- the default answer

    @Test
    fun a_screen_composed_outside_any_provider_does_not_offer_paid_plans() = runComposeUiTest {
        var offered: Boolean? = null
        mount { offered = paidPlansAreOffered() }

        assertEquals(false, offered, "hidden by default — only a confirmed OPEN shows them")
    }

    @Test
    fun only_a_confirmed_open_status_offers_paid_plans() = runComposeUiTest {
        val answers = mutableMapOf<BillingAvailability, Boolean>()
        mount {
            BillingAvailability.entries.forEach { availability ->
                CompositionLocalProvider(LocalBillingAvailability provides availability) {
                    answers[availability] = paidPlansAreOffered()
                }
            }
        }

        assertEquals(
            mapOf(
                BillingAvailability.OPEN to true,
                BillingAvailability.CLOSED to false,
                BillingAvailability.UNKNOWN to false,
            ),
            answers,
        )
    }

    // ---- before sign-in: WelcomeScreen

    @Test
    fun the_welcome_screen_shows_the_plans_entry_when_it_is_given_one() = runComposeUiTest {
        var opened = 0
        mount { WelcomeScreen(onCreateAccount = {}, onSignIn = {}, onDiscoverPlans = { opened++ }, onOpenLegalDocument = {}) }

        onNodeWithText("Découvrir nos formules").performScrollTo().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun the_welcome_screen_removes_the_plans_entry_entirely_when_it_is_given_none() = runComposeUiTest {
        mount { WelcomeScreen(onCreateAccount = {}, onSignIn = {}, onDiscoverPlans = null, onOpenLegalDocument = {}) }

        onNodeWithText("Découvrir nos formules").assertDoesNotExist()
        onNodeWithText("formule payante", substring = true).assertDoesNotExist()
        onNodeWithText("Créer un compte").assertExists()
        onNodeWithText("Se connecter").assertExists()
    }

    // ---- wording of plan-limit messages

    private fun ComposeUiTest.planLimitMessage(availability: BillingAvailability) = mount {
        CompositionLocalProvider(LocalBillingAvailability provides availability) {
            Text(DomainException.PlanLimitReached.localizedText())
        }
    }

    @Test
    fun a_plan_limit_error_suggests_upgrading_only_while_billing_is_open() = runComposeUiTest {
        planLimitMessage(BillingAvailability.OPEN)

        onNodeWithText(
            "Vous avez atteint la limite de projets de votre formule. Passez à un palier supérieur pour en créer davantage.",
        ).assertExists()
    }

    @Test
    fun a_plan_limit_error_stays_neutral_while_billing_is_closed() = runComposeUiTest {
        planLimitMessage(BillingAvailability.CLOSED)

        onNodeWithText("Vous avez atteint la limite de projets de votre formule.").assertExists()
        onNodeWithText("palier supérieur", substring = true).assertDoesNotExist()
    }

    @Test
    fun a_plan_limit_error_stays_neutral_while_billing_status_is_unknown() = runComposeUiTest {
        planLimitMessage(BillingAvailability.UNKNOWN)

        onNodeWithText("Vous avez atteint la limite de projets de votre formule.").assertExists()
    }

    @Test
    fun a_checkout_refused_by_the_server_has_its_own_message() = runComposeUiTest {
        mount { Text(DomainException.BillingNotOpen.localizedText()) }

        onNodeWithText("Les abonnements payants ne sont pas encore ouverts.").assertExists()
    }

    // ---- founder status in the account menu

    private fun ComposeUiTest.accountMenu(plan: Plan, isFounder: Boolean) = mount {
        Surface {
            androidx.compose.foundation.layout.Column {
                AccountMenuBody(
                    userName = "Jean Marchand",
                    email = "jean@chantier.dev",
                    plan = plan,
                    onSubscription = {},
                    onLogout = {},
                    isFounder = isFounder,
                )
            }
        }
    }

    @Test
    fun the_account_menu_names_the_founder_status_next_to_the_plan() = runComposeUiTest {
        accountMenu(Plan.FREE, isFounder = true)

        onNodeWithText("Formule Gratuite · Fondateur").assertExists()
        onNodeWithText("Mon abonnement").assertExists()
    }

    @Test
    fun the_account_menu_shows_the_plan_alone_for_everyone_else() = runComposeUiTest {
        accountMenu(Plan.FREE, isFounder = false)

        onNodeWithText("Formule Gratuite").assertExists()
        onNodeWithText("Fondateur", substring = true).assertDoesNotExist()
    }
}
