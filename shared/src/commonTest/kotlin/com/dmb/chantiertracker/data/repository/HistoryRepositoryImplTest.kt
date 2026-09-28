package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.data.remote.HistoryApi
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.HistoryActionType
import com.dmb.chantiertracker.domain.model.HistorySort
import com.dmb.chantiertracker.support.FakeProjectDao
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.RecordingMockClient
import com.dmb.chantiertracker.support.localProject
import com.dmb.chantiertracker.support.respondJson
import com.dmb.chantiertracker.support.respondProblem
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HistoryRepositoryImplTest {

    private val pageJson = """
        {
          "content": [
            {"id": 5, "entryId": null, "userId": 1, "modifiedAt": "2026-09-05T14:32:11.123",
             "actionType": "MODIFICATION", "fieldName": "estimatedBudget", "oldValue": "500000", "newValue": "600000",
             "description": "Dan a modifie le budget previsionnel de l'etape Gros oeuvre : 500 000 -> 600 000 FCFA"},
            {"id": 4, "userId": 1, "modifiedAt": "2026-09-04T09:00:00",
             "actionType": "CREATION", "description": "Dan a cree le projet Villa Vidal"}
          ],
          "number": 0, "totalPages": 3, "totalElements": 45, "first": true, "last": false, "size": 20
        }
    """.trimIndent()

    private fun setup(
        projectDao: FakeProjectDao = FakeProjectDao(listOf(localProject("p1", serverId = 42))),
        respond: MockRequestHandleScope.() -> HttpResponseData = { respondJson(pageJson) },
    ): Pair<HistoryRepositoryImpl, RecordingMockClient> {
        val client = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) { respond() }
        return HistoryRepositoryImpl(HistoryApi(client.client), projectDao) to client
    }

    @Test
    fun fetches_the_first_page_with_the_default_sort_and_maps_it() = runTest {
        val (repo, client) = setup()

        val page = repo.projectHistory("p1", page = 0, sort = HistorySort.NEWEST_FIRST)

        val request = client.requests.single()
        assertEquals("/api/v1/projects/42/history", request.url.encodedPath)
        assertEquals("0", request.url.parameters["page"])
        assertEquals("20", request.url.parameters["size"])
        assertEquals("date", request.url.parameters["sort"])
        assertEquals("desc", request.url.parameters["order"])

        assertEquals(0, page.page)
        assertEquals(3, page.totalPages)
        assertEquals(45, page.totalElements)
        assertTrue(page.isFirst)
        assertTrue(!page.isLast)
        assertEquals(2, page.items.size)
        assertEquals(HistoryActionType.MODIFICATION, page.items[0].actionType)
        assertEquals("estimatedBudget", page.items[0].fieldName)
        assertTrue(page.items[0].description!!.contains("budget"))
        assertEquals(HistoryActionType.CREATION, page.items[1].actionType)
    }

    @Test
    fun the_by_action_sort_maps_to_action_ascending() = runTest {
        val (repo, client) = setup()

        repo.projectHistory("p1", page = 0, sort = HistorySort.BY_ACTION)

        assertEquals("action", client.requests.single().url.parameters["sort"])
        assertEquals("asc", client.requests.single().url.parameters["order"])
    }

    @Test
    fun the_oldest_first_sort_maps_to_date_ascending() = runTest {
        val (repo, client) = setup()

        repo.projectHistory("p1", page = 0, sort = HistorySort.OLDEST_FIRST)

        assertEquals("date", client.requests.single().url.parameters["sort"])
        assertEquals("asc", client.requests.single().url.parameters["order"])
    }

    @Test
    fun requests_the_page_number_it_is_given() = runTest {
        val (repo, client) = setup()

        repo.projectHistory("p1", page = 2, sort = HistorySort.NEWEST_FIRST)

        assertEquals("2", client.requests.single().url.parameters["page"])
    }

    @Test
    fun a_never_synced_project_fails_without_a_network_call() = runTest {
        val (repo, client) = setup(projectDao = FakeProjectDao(listOf(localProject("p1", serverId = null))))

        assertFailsWith<DomainException.NotFound> {
            repo.projectHistory("p1", page = 0, sort = HistorySort.NEWEST_FIRST)
        }
        assertTrue(client.requests.isEmpty(), "no request is made when the project has never synced")
    }

    @Test
    fun a_403_from_a_non_admin_surfaces_as_forbidden() = runTest {
        val (repo, _) = setup(
            respond = { respondProblem(HttpStatusCode.Forbidden, "Action reservee a un administrateur.") },
        )

        assertFailsWith<DomainException.Forbidden> {
            repo.projectHistory("p1", page = 0, sort = HistorySort.NEWEST_FIRST)
        }
    }

    @Test
    fun a_null_description_and_an_unknown_action_are_tolerated() = runTest {
        val json = """
            {"content": [
                {"id": 9, "userId": 2, "modifiedAt": "2026-09-01T08:00:00", "actionType": "SOMETHING_NEW"}
            ], "number": 0, "totalPages": 1, "totalElements": 1, "first": true, "last": true}
        """.trimIndent()
        val (repo, _) = setup(respond = { respondJson(json) })

        val item = repo.projectHistory("p1", page = 0, sort = HistorySort.NEWEST_FIRST).items.single()

        assertEquals(null, item.description)
        assertEquals(HistoryActionType.UNKNOWN, item.actionType)
        assertEquals(2L, item.userId)
    }
}
