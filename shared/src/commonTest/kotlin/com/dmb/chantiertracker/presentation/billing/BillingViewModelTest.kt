package com.dmb.chantiertracker.presentation.billing

import com.dmb.chantiertracker.domain.model.BillingCycle
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.PlanUsage
import com.dmb.chantiertracker.support.FakeAccountRepository
import com.dmb.chantiertracker.support.FakeBillingRepository
import com.dmb.chantiertracker.support.FakeUrlOpener
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class Harness(
    val account: FakeAccountRepository = FakeAccountRepository(PlanUsage(Plan.SEMI_FLEX, projectsLimit = 3)),
    val billing: FakeBillingRepository = FakeBillingRepository(),
    val opener: FakeUrlOpener = FakeUrlOpener(),
) {
    val vm = BillingViewModel(account, billing, opener)
}

@OptIn(ExperimentalCoroutinesApi::class)
class BillingViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    @Test
    fun exposes_the_last_known_plan_usage_and_refreshes_on_init() = runTest {
        val h = Harness()
        advanceUntilIdle()

        assertEquals(Plan.SEMI_FLEX, h.vm.state.value.planUsage?.plan)
        assertEquals(1, h.account.refreshCount)
    }

    @Test
    fun checkout_hands_the_returned_url_to_the_platform_opener() = runTest {
        val h = Harness()
        advanceUntilIdle()

        h.vm.startCheckout(Plan.LIBERTE, BillingCycle.YEARLY)
        advanceUntilIdle()

        assertEquals(listOf(Plan.LIBERTE to BillingCycle.YEARLY), h.billing.checkoutCalls)
        assertEquals(listOf(h.billing.checkoutUrl), h.opener.opened)
        assertFalse(h.vm.state.value.isProcessingAction)
        assertNull(h.vm.state.value.actionError)
    }

    @Test
    fun manage_subscription_hands_the_returned_url_to_the_platform_opener() = runTest {
        val h = Harness()
        advanceUntilIdle()

        h.vm.openManageSubscription()
        advanceUntilIdle()

        assertEquals(1, h.billing.portalCalls)
        assertEquals(listOf(h.billing.portalUrl), h.opener.opened)
    }

    @Test
    fun a_second_action_while_one_is_running_is_ignored() = runTest {
        val gate = CompletableDeferred<Unit>()
        val billing = FakeBillingRepository()
        // Block the first call mid-flight by making the opener wait.
        val opener = UrlOpener { gate.await() }
        val vm = BillingViewModel(FakeAccountRepository(PlanUsage(Plan.SEMI_FLEX, projectsLimit = 3)), billing, opener)
        advanceUntilIdle()

        vm.startCheckout(Plan.LIBERTE, BillingCycle.MONTHLY)
        advanceUntilIdle()
        assertTrue(vm.state.value.isProcessingAction)

        vm.startCheckout(Plan.LIBERTE, BillingCycle.MONTHLY)
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, billing.checkoutCalls.size, "the in-flight action blocks a second one")
    }

    @Test
    fun a_domain_failure_is_surfaced_and_nothing_is_opened() = runTest {
        val billing = FakeBillingRepository().apply { checkoutError = DomainException.Network }
        val h = Harness(billing = billing)
        advanceUntilIdle()

        h.vm.startCheckout(Plan.SEMI_FLEX, BillingCycle.MONTHLY)
        advanceUntilIdle()

        assertEquals(DomainException.Network, h.vm.state.value.actionError)
        assertTrue(h.opener.opened.isEmpty())
        assertFalse(h.vm.state.value.isProcessingAction)
    }

    @Test
    fun an_opener_failure_surfaces_as_unexpected() = runTest {
        val opener = FakeUrlOpener().apply { error = IllegalStateException("no browser") }
        val h = Harness(opener = opener)
        advanceUntilIdle()

        h.vm.openManageSubscription()
        advanceUntilIdle()

        assertEquals(DomainException.Unexpected, h.vm.state.value.actionError)
    }

    @Test
    fun clear_action_error_resets_it() = runTest {
        val billing = FakeBillingRepository().apply { portalError = DomainException.Unexpected }
        val h = Harness(billing = billing)
        advanceUntilIdle()
        h.vm.openManageSubscription()
        advanceUntilIdle()
        assertEquals(DomainException.Unexpected, h.vm.state.value.actionError)

        h.vm.clearActionError()

        assertNull(h.vm.state.value.actionError)
    }
}
