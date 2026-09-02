package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.data.remote.ProjectApi
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.RecordingMockClient
import com.dmb.chantiertracker.support.respondJson
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
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
}
