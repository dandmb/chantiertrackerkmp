package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.data.remote.AdminApi
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.Granularity
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.PlanSource
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
import kotlin.test.assertTrue

class AdminRepositoryImplTest {

    private val pageJson = """
        {
          "content": [
            {"id": 5, "email": "jean@chantier.dev", "name": "Jean Marchand", "active": true,
             "globalRole": "USER", "projectCount": 2, "createdAt": "2026-09-05T14:32:11.123",
             "plan": "SEMI_FLEX", "planSource": "STRIPE", "planExpiresAt": null},
            {"id": 6, "email": "admin@chantier.dev", "name": "Dan", "active": true,
             "globalRole": "SUPER_ADMIN", "projectCount": 0, "createdAt": "2026-09-01T09:00:00",
             "plan": "FREE", "planSource": null, "planExpiresAt": null}
          ],
          "number": 0, "totalPages": 3, "totalElements": 45, "first": true, "last": false, "size": 20
        }
    """.trimIndent()

    private fun setup(
        respond: MockRequestHandleScope.() -> HttpResponseData = { respondJson(pageJson) },
    ): Pair<AdminRepositoryImpl, RecordingMockClient> {
        val client = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) { respond() }
        return AdminRepositoryImpl(AdminApi(client.client)) to client
    }

    @Test
    fun fetches_the_first_page_and_maps_it() = runTest {
        val (repo, client) = setup()

        val page = repo.listUsers(page = 0)

        val request = client.requests.single()
        assertEquals("/api/v1/admin/users", request.url.encodedPath)
        assertEquals("0", request.url.parameters["page"])
        assertEquals("20", request.url.parameters["size"])
        assertEquals("createdAt,desc", request.url.parameters["sort"])

        assertEquals(0, page.page)
        assertEquals(3, page.totalPages)
        assertEquals(45, page.totalElements)
        assertTrue(page.isFirst)
        assertTrue(!page.isLast)
        assertEquals(2, page.items.size)

        val paid = page.items[0]
        assertEquals("jean@chantier.dev", paid.email)
        assertEquals(GlobalRole.USER, paid.globalRole)
        assertEquals(2L, paid.projectCount)
        assertEquals(Plan.SEMI_FLEX, paid.plan)
        assertEquals(PlanSource.STRIPE, paid.planSource)
        assertNull(paid.planExpiresAt)

        val admin = page.items[1]
        assertEquals(GlobalRole.SUPER_ADMIN, admin.globalRole)
        assertEquals(Plan.FREE, admin.plan)
        assertNull(admin.planSource)
    }

    @Test
    fun requests_the_page_number_it_is_given() = runTest {
        val (repo, client) = setup()

        repo.listUsers(page = 2)

        assertEquals("2", client.requests.single().url.parameters["page"])
    }

    @Test
    fun an_unrecognized_plan_source_is_tolerated_as_null_rather_than_thrown() = runTest {
        val json = """
            {"content": [
                {"id": 7, "email": "x@y.com", "name": "X", "active": true, "globalRole": "USER",
                 "projectCount": 0, "createdAt": "2026-09-01T08:00:00", "plan": "FREE",
                 "planSource": "SOMETHING_NEW", "planExpiresAt": null}
            ], "number": 0, "totalPages": 1, "totalElements": 1, "first": true, "last": true}
        """.trimIndent()
        val (repo, _) = setup(respond = { respondJson(json) })

        val user = repo.listUsers(page = 0).items.single()

        assertNull(user.planSource)
    }

    @Test
    fun a_403_from_a_non_super_admin_surfaces_as_forbidden() = runTest {
        val (repo, _) = setup(
            respond = { respondProblem(HttpStatusCode.Forbidden, "Action reservee a un super-administrateur.") },
        )

        assertFailsWith<DomainException.Forbidden> { repo.listUsers(page = 0) }
    }

    @Test
    fun creates_a_user_and_maps_the_response() = runTest {
        val json = """
            {"id": 42, "email": "new@chantier.dev", "name": "New User", "active": true,
             "globalRole": "USER", "projectCount": 0, "createdAt": "2026-09-12T00:00:00",
             "plan": "FREE", "planSource": null, "planExpiresAt": null}
        """.trimIndent()
        val (repo, client) = setup(respond = { respondJson(json, HttpStatusCode.Created) })

        val user = repo.createUser("new@chantier.dev", "New User", "Str0ng!Pass", GlobalRole.USER)

        val request = client.requests.single()
        assertEquals("/api/v1/admin/users", request.url.encodedPath)
        assertEquals(
            """{"email":"new@chantier.dev","name":"New User","password":"Str0ng!Pass","globalRole":"USER"}""",
            request.body.toByteArray().decodeToString(),
        )
        assertEquals(42L, user.id)
        assertEquals("new@chantier.dev", user.email)
    }

    @Test
    fun a_duplicate_email_on_create_surfaces_as_email_already_used() = runTest {
        val (repo, _) = setup(
            respond = { respondProblem(HttpStatusCode.Conflict, "Cet email est deja utilise.") },
        )

        assertFailsWith<DomainException.EmailAlreadyUsed> {
            repo.createUser("dup@chantier.dev", "Dup", "Str0ng!Pass", GlobalRole.USER)
        }
    }

    @Test
    fun updates_the_name_and_maps_the_response() = runTest {
        val json = """
            {"id": 5, "email": "jean@chantier.dev", "name": "Jean Renamed", "active": true,
             "globalRole": "USER", "projectCount": 2, "createdAt": "2026-09-05T14:32:11",
             "plan": "SEMI_FLEX", "planSource": "STRIPE", "planExpiresAt": null}
        """.trimIndent()
        val (repo, client) = setup(respond = { respondJson(json) })

        val user = repo.updateUserName(5, "Jean Renamed")

        val request = client.requests.single()
        assertEquals("/api/v1/admin/users/5", request.url.encodedPath)
        assertEquals("Jean Renamed", user.name)
    }

    @Test
    fun deletes_a_user() = runTest {
        val (repo, client) = setup(respond = { respondJson("", HttpStatusCode.NoContent) })

        repo.deleteUser(5)

        val request = client.requests.single()
        assertEquals("/api/v1/admin/users/5", request.url.encodedPath)
    }

    @Test
    fun resets_a_password() = runTest {
        val (repo, client) = setup(respond = { respondJson("", HttpStatusCode.NoContent) })

        repo.resetPassword(5)

        assertEquals("/api/v1/admin/users/5/reset-password", client.requests.single().url.encodedPath)
    }

    @Test
    fun resends_activation() = runTest {
        val (repo, client) = setup(respond = { respondJson("", HttpStatusCode.NoContent) })

        repo.resendActivation(5)

        assertEquals("/api/v1/admin/users/5/resend-activation", client.requests.single().url.encodedPath)
    }

    @Test
    fun resend_activation_409_is_remapped_to_unexpected_not_email_already_used() = runTest {
        // AccountAlreadyActiveException is also a 409 — apiCall's generic
        // mapping would otherwise call this "email already used", which is
        // nonsensical (the UI already hides the button once active; this is
        // only reachable via a race).
        val (repo, _) = setup(
            respond = { respondProblem(HttpStatusCode.Conflict, "Ce compte est deja active.") },
        )

        assertFailsWith<DomainException.Unexpected> { repo.resendActivation(5) }
    }

    @Test
    fun updates_the_plan_with_an_expiration_and_maps_the_response() = runTest {
        val json = """
            {"id": 5, "email": "jean@chantier.dev", "name": "Jean Marchand", "active": true,
             "globalRole": "USER", "projectCount": 2, "createdAt": "2026-09-05T14:32:11",
             "plan": "LIBERTE", "planSource": "ADMIN_GRANTED", "planExpiresAt": "2026-12-31T23:59:59"}
        """.trimIndent()
        val (repo, client) = setup(respond = { respondJson(json) })

        val user = repo.updateUserPlan(5, Plan.LIBERTE, "2026-12-31T23:59:59")

        val request = client.requests.single()
        assertEquals("/api/v1/admin/users/5/plan", request.url.encodedPath)
        assertEquals(
            """{"plan":"LIBERTE","expiresAt":"2026-12-31T23:59:59"}""",
            request.body.toByteArray().decodeToString(),
        )
        assertEquals(Plan.LIBERTE, user.plan)
        assertEquals(PlanSource.ADMIN_GRANTED, user.planSource)
        assertEquals("2026-12-31T23:59:59", user.planExpiresAt)
    }

    @Test
    fun updates_the_plan_to_free_without_an_expiration() = runTest {
        val json = """
            {"id": 5, "email": "jean@chantier.dev", "name": "Jean Marchand", "active": true,
             "globalRole": "USER", "projectCount": 2, "createdAt": "2026-09-05T14:32:11",
             "plan": "FREE", "planSource": null, "planExpiresAt": null}
        """.trimIndent()
        val (repo, client) = setup(respond = { respondJson(json) })

        repo.updateUserPlan(5, Plan.FREE, null)

        // explicitNulls = false (AppJson) — a null expiresAt is omitted
        // entirely, not encoded as "expiresAt":null.
        assertEquals(
            """{"plan":"FREE"}""",
            client.requests.single().body.toByteArray().decodeToString(),
        )
    }

    @Test
    fun a_live_stripe_subscription_409_is_remapped_to_unexpected_not_email_already_used() = runTest {
        // StripeSubscriptionActiveException is also a 409 — the UI already
        // disables this form proactively once planSource == STRIPE is known
        // (see AssignPlanDialog), so this is only reachable via a race.
        val (repo, _) = setup(
            respond = {
                respondProblem(HttpStatusCode.Conflict, "Cet utilisateur a un abonnement Stripe actif (sub_123).")
            },
        )

        assertFailsWith<DomainException.Unexpected> { repo.updateUserPlan(5, Plan.LIBERTE, null) }
    }

    @Test
    fun fetches_stats_with_all_filters_and_maps_the_response() = runTest {
        val json = """
            {"totalUsers": 128, "totalProjects": 47,
             "registrations": [{"bucket": "2026-08-01", "count": 20}, {"bucket": "2026-09-01", "count": 8}],
             "projectsCreated": [{"bucket": "2026-09-01", "count": 3}]}
        """.trimIndent()
        val (repo, client) = setup(respond = { respondJson(json) })

        val stats = repo.getStats(Granularity.MONTH, "2026-08-01", "2026-09-13")

        val request = client.requests.single()
        assertEquals("/api/v1/admin/stats", request.url.encodedPath)
        assertEquals("MONTH", request.url.parameters["granularity"])
        assertEquals("2026-08-01", request.url.parameters["from"])
        assertEquals("2026-09-13", request.url.parameters["to"])
        assertEquals(128L, stats.totalUsers)
        assertEquals(47L, stats.totalProjects)
        assertEquals(2, stats.registrations.size)
        assertEquals("2026-09-01", stats.registrations[1].bucket)
        assertEquals(8L, stats.registrations[1].count)
        assertEquals(1, stats.projectsCreated.size)
    }

    @Test
    fun blank_date_bounds_are_omitted_from_the_request() = runTest {
        val json = """{"totalUsers": 0, "totalProjects": 0, "registrations": [], "projectsCreated": []}"""
        val (repo, client) = setup(respond = { respondJson(json) })

        repo.getStats(Granularity.YEAR, null, null)

        val request = client.requests.single()
        assertEquals("YEAR", request.url.parameters["granularity"])
        assertNull(request.url.parameters["from"])
        assertNull(request.url.parameters["to"])
    }

    @Test
    fun an_invalid_date_range_400_is_remapped_to_validation_not_invalid_code() = runTest {
        // InvalidStatsDateRangeException is a 400 with no field-level errors
        // (a business rule, not @Valid) — apiCall's generic mapping would
        // otherwise call this "invalid or expired code", nonsensical here.
        // The UI already validates the same two rules before submitting
        // (validateStatsDateRange), so this is only reachable via a race.
        val (repo, _) = setup(
            respond = { respondProblem(HttpStatusCode.BadRequest, "La date de fin ne peut pas etre posterieure a aujourd'hui.") },
        )

        assertFailsWith<DomainException.Validation> { repo.getStats(Granularity.MONTH, null, "2099-01-01") }
    }
}
