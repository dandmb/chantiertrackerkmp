package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.domain.model.BillingAvailability
import com.dmb.chantiertracker.domain.model.BillingCycle
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.PlanUsage
import com.dmb.chantiertracker.presentation.billing.BillingScreen
import com.dmb.chantiertracker.presentation.billing.BillingViewModel
import com.dmb.chantiertracker.presentation.billing.LocalBillingAvailability
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeAccountRepository
import com.dmb.chantiertracker.support.FakeBillingRepository
import com.dmb.chantiertracker.support.FakeUrlOpener
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class BillingScreenUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private fun usage(
        plan: Plan,
        hasStripeCustomer: Boolean = false,
        projectsUsed: Int = 2,
        projectsLimit: Int? = 3,
        photosLimit: Int? = 300,
        isFounder: Boolean = false,
        historyDaysLimit: Int? = null,
        historyDaysLimitKnown: Boolean = false,
    ) = PlanUsage(
        plan = plan,
        projectsLimit = projectsLimit,
        projectsUsed = projectsUsed,
        photosUsed = 40,
        photosLimit = photosLimit,
        videosUsed = 1,
        videosLimit = 5,
        videoDurationLimitSeconds = 120,
        supervisorsUsed = 1,
        supervisorsLimit = 3,
        hasStripeCustomer = hasStripeCustomer,
        isFounder = isFounder,
        historyDaysLimit = historyDaysLimit,
        historyDaysLimitKnown = historyDaysLimitKnown,
    )

    private fun ComposeUiTest.mount(
        planUsage: PlanUsage?,
        billing: FakeBillingRepository = FakeBillingRepository(),
        opener: FakeUrlOpener = FakeUrlOpener(),
    ) {
        setContent {
            customAppLocale = "fr"
            AppEnvironment {
                AppTheme {
                    // Same wiring as App(): the screen reads what the repository knows.
                    CompositionLocalProvider(LocalBillingAvailability provides billing.availability.value) {
                        Box(Modifier.size(412.dp, 892.dp)) {
                            BillingScreen(
                                viewModel = BillingViewModel(FakeAccountRepository(planUsage), billing, opener),
                            )
                        }
                    }
                }
            }
        }
        waitForIdle()
    }

    @Test
    fun a_free_plan_shows_both_upgrade_cards_and_no_manage_button() = runComposeUiTest {
        mount(usage(Plan.FREE))

        onNodeWithText("Formule Gratuite").assertExists()
        onNodeWithText("Passer à un palier supérieur").assertExists()
        onNodeWithText("Formule Semi-Flex").assertExists()
        onNodeWithText("Formule Liberté").assertExists()
        onNodeWithText("Gérer mon abonnement").assertDoesNotExist()
    }

    @Test
    fun a_paying_customer_sees_the_manage_button_and_the_remaining_upgrade() = runComposeUiTest {
        mount(usage(Plan.SEMI_FLEX, hasStripeCustomer = true))

        onNodeWithText("Gérer mon abonnement").assertExists()
        onNodeWithText("Passer à un palier supérieur").assertExists()
        onNodeWithText("Formule Liberté").assertExists()
    }

    @Test
    fun a_gifted_top_tier_plan_shows_the_no_action_message() = runComposeUiTest {
        mount(usage(Plan.LIBERTE, hasStripeCustomer = false))

        onNodeWithText("Aucune action de facturation n'est disponible pour ce plan.").assertExists()
        onNodeWithText("Gérer mon abonnement").assertDoesNotExist()
        onNodeWithText("Passer à un palier supérieur").assertDoesNotExist()
    }

    @Test
    fun usage_rows_render_the_real_counts() = runComposeUiTest {
        mount(usage(Plan.SEMI_FLEX, projectsUsed = 2, projectsLimit = 3))

        onNodeWithText("2/3 projets utilisés").assertExists()
        onNodeWithText("40/300 photos utilisées").assertExists()
        onNodeWithText("1/5 vidéos utilisées").assertExists()
        onNodeWithText("1/3 superviseurs par projet").assertExists()
    }

    @Test
    fun clicking_subscribe_starts_checkout_and_opens_the_url() = runComposeUiTest {
        val billing = FakeBillingRepository()
        val opener = FakeUrlOpener()
        // SEMI_FLEX has a single upgrade target (LIBERTE) -> a single "S'abonner"
        // button, unambiguous to click; FREE would render two.
        mount(usage(Plan.SEMI_FLEX), billing = billing, opener = opener)

        onNodeWithText("S'abonner").performScrollTo().performClick()
        waitForIdle()

        assertEquals(listOf(Plan.LIBERTE to BillingCycle.MONTHLY), billing.checkoutCalls)
        assertEquals(listOf(billing.checkoutUrl), opener.opened)
    }

    @Test
    fun clicking_manage_subscription_opens_the_portal_url() = runComposeUiTest {
        val billing = FakeBillingRepository()
        val opener = FakeUrlOpener()
        mount(usage(Plan.SEMI_FLEX, hasStripeCustomer = true), billing = billing, opener = opener)

        onNodeWithText("Gérer mon abonnement").performScrollTo().performClick()
        waitForIdle()

        assertEquals(1, billing.portalCalls)
        assertEquals(listOf(billing.portalUrl), opener.opened)
    }

    @Test
    fun a_checkout_failure_shows_an_error_banner() = runComposeUiTest {
        val billing = FakeBillingRepository().apply { checkoutError = DomainException.Network }
        mount(usage(Plan.SEMI_FLEX), billing = billing)

        onNodeWithText("S'abonner").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000L) {
            onAllNodes(hasText("Connexion au serveur impossible", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ---- ADR-66 — billing closed / unknown: the screen stays, every price goes

    private fun ComposeUiTest.assertNothingToBuy() {
        onNodeWithText("Passer à un palier supérieur").assertDoesNotExist()
        onNodeWithText("S'abonner").assertDoesNotExist()
        onNodeWithText("Formule Semi-Flex").assertDoesNotExist()
        onNodeWithText("Formule Liberté").assertDoesNotExist()
        onNodeWithText("€", substring = true).assertDoesNotExist()
        onNodeWithText("Passez à", substring = true).assertDoesNotExist()
    }

    @Test
    fun while_billing_is_closed_a_free_plan_keeps_its_plan_and_usage_but_sees_nothing_to_buy() = runComposeUiTest {
        mount(usage(Plan.FREE, projectsUsed = 1, projectsLimit = 1), billing = FakeBillingRepository(BillingAvailability.CLOSED))

        onNodeWithText("Formule Gratuite").assertExists()
        onNodeWithText("1/1 projets utilisés").assertExists()
        onNodeWithText("Historique limité aux 30 derniers jours.").assertExists()
        assertNothingToBuy()
    }

    @Test
    fun an_unknown_billing_status_hides_the_paid_tiers_exactly_like_closed() = runComposeUiTest {
        mount(usage(Plan.FREE), billing = FakeBillingRepository(BillingAvailability.UNKNOWN))

        onNodeWithText("Formule Gratuite").assertExists()
        assertNothingToBuy()
    }

    @Test
    fun an_existing_customer_still_reaches_the_stripe_portal_while_billing_is_closed() = runComposeUiTest {
        val billing = FakeBillingRepository(BillingAvailability.CLOSED)
        val opener = FakeUrlOpener()
        mount(usage(Plan.SEMI_FLEX, hasStripeCustomer = true), billing = billing, opener = opener)

        onNodeWithText("Passer à un palier supérieur").assertDoesNotExist()
        onNodeWithText("S'abonner").assertDoesNotExist()
        onNodeWithText("Gérer mon abonnement").performScrollTo().performClick()
        waitForIdle()

        assertEquals(1, billing.portalCalls)
        assertEquals(listOf(billing.portalUrl), opener.opened)
    }

    @Test
    fun with_billing_open_the_history_notice_suggests_the_upgrade() = runComposeUiTest {
        mount(usage(Plan.FREE))

        onNodeWithText("Historique limité aux 30 derniers jours. Passez à Semi-flex ou Liberté pour un historique plus long.")
            .assertExists()
    }

    // ---- ADR-66 — founder status

    @Test
    fun a_founder_on_the_free_plan_sees_the_badge_the_explanation_and_the_combined_limits() = runComposeUiTest {
        mount(
            usage(
                Plan.FREE, projectsUsed = 2, projectsLimit = 3, photosLimit = 150, isFounder = true,
                historyDaysLimit = 180, historyDaysLimitKnown = true,
            ),
            billing = FakeBillingRepository(BillingAvailability.CLOSED),
        )

        onNodeWithText("Formule Gratuite").assertExists()
        onNodeWithText("Fondateur").assertExists()
        onNodeWithText("Vous faites partie des premiers inscrits.", substring = true).assertExists()
        // The photo quota is the one exception to "Semi-Flex benefits" (150, not 300):
        // the sentence must say so, like the web's, never promise the full Semi-Flex tier.
        onNodeWithText("avec moitié moins de photos", substring = true).assertExists()
        onNodeWithText("2/3 projets utilisés").assertExists()
        onNodeWithText("40/150 photos utilisées").assertExists()
        onNodeWithText("1/5 vidéos utilisées").assertExists()
        onNodeWithText("1/3 superviseurs par projet").assertExists()
        onNodeWithText("Historique limité aux 6 derniers mois.").assertExists()
        onNodeWithText("30 derniers jours", substring = true).assertDoesNotExist()
        assertNothingToBuy()
    }

    @Test
    fun a_founder_on_the_free_plan_reads_six_months_of_history_with_billing_open_too() = runComposeUiTest {
        mount(usage(Plan.FREE, isFounder = true, historyDaysLimit = 180, historyDaysLimitKnown = true))

        onNodeWithText("Historique limité aux 6 derniers mois. Passez à Liberté pour un historique illimité.").assertExists()
        onNodeWithText("30 derniers jours", substring = true).assertDoesNotExist()
    }

    @Test
    fun an_unlimited_history_sent_by_the_server_shows_no_notice() = runComposeUiTest {
        mount(usage(Plan.FREE, historyDaysLimit = null, historyDaysLimitKnown = true))

        onNodeWithText("Historique limité", substring = true).assertDoesNotExist()
    }

    @Test
    fun an_account_that_is_not_a_founder_sees_neither_badge_nor_explanation() = runComposeUiTest {
        mount(usage(Plan.FREE))

        onNodeWithText("Fondateur").assertDoesNotExist()
        onNodeWithText("premiers inscrits", substring = true).assertDoesNotExist()
    }
}
