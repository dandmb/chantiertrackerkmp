package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.data.remote.BillingApi
import com.dmb.chantiertracker.domain.model.BillingCycle
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.RecordingMockClient
import com.dmb.chantiertracker.support.respondJson
import com.dmb.chantiertracker.support.respondProblem
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BillingRepositoryImplTest {

    private fun setup(
        respond: MockRequestHandleScope.() -> HttpResponseData,
    ): Pair<BillingRepositoryImpl, RecordingMockClient> {
        val client = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) { respond() }
        return BillingRepositoryImpl(BillingApi(client.client)) to client
    }

    @Test
    fun starts_a_checkout_session_and_returns_its_url() = runTest {
        val (repo, client) = setup { respondJson("""{"checkoutUrl":"https://checkout.stripe.com/session-abc"}""") }

        val url = repo.startCheckout(Plan.SEMI_FLEX, BillingCycle.MONTHLY)

        assertEquals("https://checkout.stripe.com/session-abc", url)
        val request = client.requests.single()
        assertEquals("/api/v1/billing/checkout", request.url.encodedPath)
        assertEquals(
            """{"plan":"SEMI_FLEX","billingCycle":"MONTHLY","platform":"MOBILE"}""",
            request.body.toByteArray().decodeToString(),
        )
    }

    // ADR-51 point 4 — the backend picks chantiertracker:// success/cancel
    // URLs over the web frontend's based on this field alone; never a URL the
    // client dictates itself (see the backend walkthrough for the security
    // reasoning).
    @Test
    fun opens_the_billing_portal_and_returns_its_url() = runTest {
        val (repo, client) = setup { respondJson("""{"portalUrl":"https://billing.stripe.com/portal-abc"}""") }

        val url = repo.openManageSubscription()

        assertEquals("https://billing.stripe.com/portal-abc", url)
        val request = client.requests.single()
        assertEquals("/api/v1/billing/portal", request.url.encodedPath)
        assertEquals("MOBILE", request.url.parameters["platform"])
    }

    @Test
    fun a_checkout_failure_without_field_errors_surfaces_as_unexpected_not_invalid_code() = runTest {
        val (repo, _) = setup {
            respondProblem(HttpStatusCode.BadRequest, "Le plan gratuit ne peut pas etre achete.")
        }

        assertFailsWith<DomainException.Unexpected> { repo.startCheckout(Plan.SEMI_FLEX, BillingCycle.MONTHLY) }
    }

    @Test
    fun a_portal_failure_without_a_stripe_customer_surfaces_as_unexpected() = runTest {
        val (repo, _) = setup {
            respondProblem(HttpStatusCode.BadRequest, "Aucun client Stripe pour cet utilisateur.")
        }

        assertFailsWith<DomainException.Unexpected> { repo.openManageSubscription() }
    }

    @Test
    fun offline_surfaces_as_network() = runTest {
        val client = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) { throw kotlin.RuntimeException("no connection") }
        val repo = BillingRepositoryImpl(BillingApi(client.client))

        assertFailsWith<DomainException.Network> { repo.openManageSubscription() }
    }
}
