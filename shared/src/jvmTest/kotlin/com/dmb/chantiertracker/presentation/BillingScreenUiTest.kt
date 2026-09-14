package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.domain.model.BillingCycle
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.PlanUsage
import com.dmb.chantiertracker.presentation.billing.BillingScreen
import com.dmb.chantiertracker.presentation.billing.BillingViewModel
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
    ) = PlanUsage(
        plan = plan,
        projectsLimit = projectsLimit,
        projectsUsed = projectsUsed,
        photosUsed = 40,
        photosLimit = 300,
        videosUsed = 1,
        videosLimit = 5,
        videoDurationLimitSeconds = 120,
        supervisorsUsed = 1,
        supervisorsLimit = 3,
        hasStripeCustomer = hasStripeCustomer,
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
                    Box(Modifier.size(412.dp, 892.dp)) {
                        BillingScreen(
                            viewModel = BillingViewModel(FakeAccountRepository(planUsage), billing, opener),
                        )
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
}
