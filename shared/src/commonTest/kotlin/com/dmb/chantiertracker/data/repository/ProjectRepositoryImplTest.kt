package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.data.remote.ProjectApi
import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.RecordingMockClient
import com.dmb.chantiertracker.support.respondJson
import com.dmb.chantiertracker.support.respondProblem
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val PAGE_JSON = """
{
  "content": [
    {"id": 1, "name": "Villa Vidal", "description": null, "location": "Nîmes", "status": "IN_PROGRESS", "currency": "EUR"},
    {"id": 2, "name": "Hangar Est", "location": null, "status": "SUSPENDED"},
    {"id": 3, "name": "Toiture Marchand", "status": "COMPLETED"},
    {"id": 4, "name": "Futur statut", "status": "ARCHIVED"}
  ],
  "totalElements": 4, "totalPages": 1, "number": 0, "size": 20, "first": true, "last": true, "empty": false
}
"""

class ProjectRepositoryImplTest {

    private fun repo(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): ProjectRepositoryImpl {
        val client = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r")), handler = handler)
        return ProjectRepositoryImpl(ProjectApi(client.client))
    }

    @Test
    fun list_parses_content_and_maps_status() = runTest {
        val repo = repo {
            assertEquals("/api/v1/projects", it.url.encodedPath)
            respondJson(PAGE_JSON)
        }

        val projects = repo.getProjects()

        assertEquals(4, projects.size)
        assertEquals("Villa Vidal", projects[0].name)
        assertEquals("Nîmes", projects[0].location)
        assertEquals(ProjectStatus.IN_PROGRESS, projects[0].status)
        assertEquals(ProjectStatus.SUSPENDED, projects[1].status)
        assertEquals(ProjectStatus.COMPLETED, projects[2].status)
        assertEquals(ProjectStatus.UNKNOWN, projects[3].status, "unknown backend status must not crash parsing")
    }

    @Test
    fun empty_page_yields_empty_list() = runTest {
        val repo = repo { respondJson("""{"content":[],"empty":true}""") }
        assertTrue(repo.getProjects().isEmpty())
    }

    @Test
    fun server_error_maps_to_domain_exception() = runTest {
        val repo = repo { respondError(HttpStatusCode.InternalServerError) }
        assertFailsWith<DomainException> { repo.getProjects() }
    }

    @Test
    fun get_parses_detail() = runTest {
        val repo = repo {
            assertEquals("/api/v1/projects/7", it.url.encodedPath)
            respondJson(
                """{"id":7,"name":"Villa Vidal","description":"Grande villa","location":"Nîmes",
                   "currency":"EUR","timezone":"Europe/Paris","ownerId":3,"ownerPlan":"FREE",
                   "status":"IN_PROGRESS","createdAt":"2026-01-01T10:00:00"}""",
            )
        }
        val detail = repo.getProject(7)
        assertEquals("Villa Vidal", detail.name)
        assertEquals("EUR", detail.currency)
        assertEquals("Europe/Paris", detail.timezone)
        assertEquals(3L, detail.ownerId)
        assertEquals(ProjectStatus.IN_PROGRESS, detail.status)
    }

    @Test
    fun get_missing_project_maps_to_not_found() = runTest {
        val repo = repo { respondProblem(HttpStatusCode.NotFound, "Projet introuvable.") }
        assertFailsWith<DomainException.NotFound> { repo.getProject(999) }
    }

    @Test
    fun members_parses_roles() = runTest {
        val repo = repo {
            assertEquals("/api/v1/projects/7/members", it.url.encodedPath)
            respondJson(
                """{"content":[
                   {"id":1,"userId":3,"email":"a@b.dev","name":"Alice","role":"ADMIN","joinedAt":"2026-01-01T10:00:00"},
                   {"id":2,"userId":4,"email":"c@d.dev","name":"Carl","role":"SUPERVISOR","joinedAt":"2026-01-02T10:00:00"}
                ]}""",
            )
        }
        val members = repo.getMembers(7)
        assertEquals(ProjectRole.ADMIN, members[0].role)
        assertEquals(ProjectRole.SUPERVISOR, members[1].role)
        assertEquals(4L, members[1].userId)
    }

    @Test
    fun create_posts_body_and_returns_new_id() = runTest {
        val repo = repo {
            assertEquals(HttpMethod.Post, it.method)
            assertEquals("/api/v1/projects", it.url.encodedPath)
            val body = it.body.toByteArray().decodeToString()
            assertTrue("\"name\":\"Villa\"" in body)
            assertTrue("\"timezone\":\"Europe/Paris\"" in body)
            respondJson(
                """{"id":58,"name":"Villa","description":null,"location":null,
                   "currency":"USD","timezone":"Europe/Paris","ownerId":3,"status":"IN_PROGRESS",
                   "createdAt":"2026-01-01T10:00:00"}""",
                HttpStatusCode.Created,
            )
        }
        val id = repo.createProject(
            CreateProjectInput("Villa", description = null, location = null, currency = null, timezone = "Europe/Paris"),
        )
        assertEquals(58L, id)
    }

    @Test
    fun create_plan_limit_maps_to_plan_limit_reached() = runTest {
        val repo = repo {
            respondProblem(HttpStatusCode.Forbidden, "Vous avez atteint la limite de projets de votre plan.")
        }
        assertFailsWith<DomainException.PlanLimitReached> {
            repo.createProject(CreateProjectInput("X", null, null, null, "Europe/Paris"))
        }
    }

    @Test
    fun create_validation_error_maps_to_validation() = runTest {
        val repo = repo {
            respondProblem(HttpStatusCode.BadRequest, "Données invalides.", mapOf("timezone" to "Fuseau invalide."))
        }
        assertFailsWith<DomainException.Validation> {
            repo.createProject(CreateProjectInput("X", null, null, null, "Nowhere/Nope"))
        }
    }
}
