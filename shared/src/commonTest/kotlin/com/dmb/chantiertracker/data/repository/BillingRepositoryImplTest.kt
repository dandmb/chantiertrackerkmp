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
import com.dmb.chantiertracker.support.respondProblem
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

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

    // ---- ADR-66 — billing availability (GET /billing/status), in memory only

    @Test
    fun availability_is_unknown_until_the_server_has_answered() = runTest {
        val (repo, client) = setup { respondJson("""{"billingOpen":true}""") }

        assertEquals(BillingAvailability.UNKNOWN, repo.availability.value)
        assertEquals(0, client.requests.size, "nothing is fetched behind the caller's back")
    }

    @Test
    fun an_open_status_is_reported_as_open() = runTest {
        val (repo, client) = setup { respondJson("""{"billingOpen":true}""") }

        repo.refreshAvailability()

        assertEquals(BillingAvailability.OPEN, repo.availability.value)
        assertEquals("/api/v1/billing/status", client.requests.single().url.encodedPath)
    }

    @Test
    fun a_closed_status_is_reported_as_closed() = runTest {
        val (repo, _) = setup { respondJson("""{"billingOpen":false}""") }

        repo.refreshAvailability()

        assertEquals(BillingAvailability.CLOSED, repo.availability.value)
    }

    // The endpoint is public: WelcomeScreen asks before any account exists.
    @Test
    fun the_status_is_fetched_without_a_token_when_nobody_is_signed_in() = runTest {
        val client = RecordingMockClient(FakeTokenStorage(null)) { respondJson("""{"billingOpen":true}""") }
        val repo = BillingRepositoryImpl(BillingApi(client.client))

        repo.refreshAvailability()

        assertEquals(BillingAvailability.OPEN, repo.availability.value)
        assertNull(client.requests.single().headers["Authorization"])
    }

    @Test
    fun a_network_failure_falls_back_to_unknown_even_after_a_confirmed_open() = runTest {
        var online = true
        val client = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) {
            if (online) respondJson("""{"billingOpen":true}""") else throw kotlin.RuntimeException("no connection")
        }
        val repo = BillingRepositoryImpl(BillingApi(client.client))
        repo.refreshAvailability()
        assertEquals(BillingAvailability.OPEN, repo.availability.value)

        online = false
        repo.refreshAvailability() // must not throw

        assertEquals(BillingAvailability.UNKNOWN, repo.availability.value, "a stale 'open' never outlives a failed check")
    }

    @Test
    fun a_server_error_falls_back_to_unknown() = runTest {
        val (repo, _) = setup { respondJson("""{"status":500}""", HttpStatusCode.InternalServerError) }

        repo.refreshAvailability()

        assertEquals(BillingAvailability.UNKNOWN, repo.availability.value)
    }

    @Test
    fun a_body_without_the_expected_field_falls_back_to_unknown() = runTest {
        val (repo, _) = setup { respondJson("""{"somethingElse":true}""") }

        repo.refreshAvailability()

        assertEquals(BillingAvailability.UNKNOWN, repo.availability.value)
    }

    @Test
    fun a_checkout_refused_because_billing_closed_surfaces_as_such_and_flips_availability_to_closed() = runTest {
        val client = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) { request ->
            if (request.url.encodedPath.endsWith("/billing/status")) {
                respondJson("""{"billingOpen":true}""")
            } else {
                respondProblem(
                    HttpStatusCode.Forbidden,
                    "Les abonnements payants ne sont pas encore ouverts. Revenez bientôt !",
                    code = "BILLING_NOT_OPEN",
                )
            }
        }
        val repo = BillingRepositoryImpl(BillingApi(client.client))
        repo.refreshAvailability()
        assertEquals(BillingAvailability.OPEN, repo.availability.value)

        assertFailsWith<DomainException.BillingNotOpen> { repo.startCheckout(Plan.SEMI_FLEX, BillingCycle.MONTHLY) }

        assertEquals(BillingAvailability.CLOSED, repo.availability.value)
    }

    @Test
    fun any_other_checkout_failure_leaves_availability_untouched() = runTest {
        val client = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) { request ->
            if (request.url.encodedPath.endsWith("/billing/status")) {
                respondJson("""{"billingOpen":true}""")
            } else {
                respondJson("""{"status":500}""", HttpStatusCode.InternalServerError)
            }
        }
        val repo = BillingRepositoryImpl(BillingApi(client.client))
        repo.refreshAvailability()

        assertFailsWith<DomainException.Unexpected> { repo.startCheckout(Plan.SEMI_FLEX, BillingCycle.MONTHLY) }

        assertEquals(BillingAvailability.OPEN, repo.availability.value)
    }

    // The portal is never gated by the backend switch: an existing customer
    // keeps managing their subscription whatever the availability says.
    @Test
    fun the_portal_stays_reachable_while_billing_is_closed() = runTest {
        val client = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) { request ->
            if (request.url.encodedPath.endsWith("/billing/status")) {
                respondJson("""{"billingOpen":false}""")
            } else {
                respondJson("""{"portalUrl":"https://billing.stripe.com/portal-abc"}""")
            }
        }
        val repo = BillingRepositoryImpl(BillingApi(client.client))
        repo.refreshAvailability()

        assertEquals("https://billing.stripe.com/portal-abc", repo.openManageSubscription())
    }

    // The refusal is recognised by the backend's machine-readable code alone —
    // the French sentence can be reworded or translated without breaking this.
    @Test
    fun closed_billing_is_recognised_by_its_code_whatever_the_message_says() = runTest {
        val (repo, _) = setup {
            respondProblem(HttpStatusCode.Forbidden, "Paid subscriptions will open soon.", code = "BILLING_NOT_OPEN")
        }

        assertFailsWith<DomainException.BillingNotOpen> { repo.startCheckout(Plan.SEMI_FLEX, BillingCycle.MONTHLY) }
        assertEquals(BillingAvailability.CLOSED, repo.availability.value)
    }

    @Test
    fun the_message_alone_without_the_code_is_no_longer_read_as_closed_billing() = runTest {
        val (repo, _) = setup {
            respondProblem(HttpStatusCode.Forbidden, "Les abonnements payants ne sont pas encore ouverts. Revenez bientôt !")
        }

        assertFailsWith<DomainException.Forbidden> { repo.startCheckout(Plan.SEMI_FLEX, BillingCycle.MONTHLY) }
        assertEquals(BillingAvailability.UNKNOWN, repo.availability.value)
    }

    @Test
    fun another_403_code_keeps_its_own_meaning() = runTest {
        val (repo, _) = setup {
            respondProblem(HttpStatusCode.Forbidden, "Accès refusé.", code = "ACCESS_DENIED")
        }

        assertFailsWith<DomainException.Forbidden> { repo.startCheckout(Plan.SEMI_FLEX, BillingCycle.MONTHLY) }
    }

    @Test
    fun a_plan_limit_refusal_is_not_mistaken_for_closed_billing() = runTest {
        val (repo, _) = setup {
            respondProblem(
                HttpStatusCode.Forbidden,
                "Vous avez atteint la limite de projets actifs de votre plan.",
                code = "PLAN_LIMIT_EXCEEDED",
            )
        }

        assertFailsWith<DomainException.PlanLimitReached> { repo.startCheckout(Plan.SEMI_FLEX, BillingCycle.MONTHLY) }
        assertEquals(BillingAvailability.UNKNOWN, repo.availability.value)
    }
}
