package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.data.remote.AdminApi
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.PlanSource
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.RecordingMockClient
import com.dmb.chantiertracker.support.respondJson
import com.dmb.chantiertracker.support.respondProblem
import io.ktor.client.engine.mock.MockRequestHandleScope
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
}
