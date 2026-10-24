package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.data.remote.BillingApi
import com.dmb.chantiertracker.domain.model.BillingAvailability
import com.dmb.chantiertracker.domain.model.BillingCycle
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.RecordingMockClient
import com.dmb.chantiertracker.support.respondJson
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NoInAppPaymentBillingRepositoryTest {

    private val serverSaysOpen = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) {
        respondJson("""{"billingOpen":true,"checkoutUrl":"https://checkout.stripe.com/s","portalUrl":"https://billing.stripe.com/p"}""")
    }

    @Test
    fun without_in_app_payments_paid_plans_stay_closed_even_when_the_server_opens_them() = runTest {
        val repo = billingRepositoryFor(BillingApi(serverSaysOpen.client), inAppPaymentsAllowed = false)

        repo.refreshAvailability()

        assertEquals(BillingAvailability.CLOSED, repo.availability.value)
        assertEquals(emptyList(), serverSaysOpen.requests.map { it.url.encodedPath }, "nothing asked to the server")
    }

    @Test
    fun without_in_app_payments_no_checkout_and_no_portal_can_be_opened() = runTest {
        val repo = billingRepositoryFor(BillingApi(serverSaysOpen.client), inAppPaymentsAllowed = false)

        assertFailsWith<DomainException.BillingNotOpen> { repo.startCheckout(Plan.SEMI_FLEX, BillingCycle.MONTHLY) }
        assertFailsWith<DomainException.BillingNotOpen> { repo.openManageSubscription() }
        assertEquals(emptyList(), serverSaysOpen.requests.map { it.url.encodedPath }, "no payment request leaves")
    }

    @Test
    fun with_in_app_payments_the_server_decides() = runTest {
        val repo = billingRepositoryFor(BillingApi(serverSaysOpen.client), inAppPaymentsAllowed = true)

        repo.refreshAvailability()

        assertEquals(BillingAvailability.OPEN, repo.availability.value)
    }
}
