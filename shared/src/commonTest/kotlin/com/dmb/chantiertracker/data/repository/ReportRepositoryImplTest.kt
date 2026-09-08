package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.data.remote.ReportApi
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.ReportSort
import com.dmb.chantiertracker.domain.model.ReportStatus
import com.dmb.chantiertracker.support.FakeDailyEntryDao
import com.dmb.chantiertracker.support.FakeProjectDao
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.RecordingMockClient
import com.dmb.chantiertracker.support.localDailyEntry
import com.dmb.chantiertracker.support.localProject
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

class ReportRepositoryImplTest {

    private val pageJson = """
        {
          "content": [
            {"id": 8, "entryId": 55, "entryType": "PURCHASE", "entryDate": "2026-09-03",
             "authorName": "Sam Superviseur", "message": "Quantite de ciment suspecte",
             "createdAt": "2026-09-05T14:32:11", "status": "NEW", "processedAt": null},
            {"id": 6, "entryId": 40, "entryType": "WORK", "entryDate": "2026-09-01",
             "authorName": "Sam Superviseur", "message": "Oubli sur cette entree",
             "createdAt": "2026-09-02T09:00:00", "status": "PROCESSED", "processedAt": "2026-09-02T18:00:00"}
          ],
          "number": 0, "totalPages": 2, "totalElements": 25, "first": true, "last": false, "size": 20
        }
    """.trimIndent()

    private fun setup(
        entryDao: FakeDailyEntryDao = FakeDailyEntryDao(listOf(localDailyEntry("e1", serverId = 77))),
        projectDao: FakeProjectDao = FakeProjectDao(listOf(localProject("p1", serverId = 42))),
        respond: MockRequestHandleScope.() -> HttpResponseData = { respondJson(pageJson) },
    ): Pair<ReportRepositoryImpl, RecordingMockClient> {
        val client = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) { respond() }
        return ReportRepositoryImpl(ReportApi(client.client), entryDao, projectDao) to client
    }

    @Test
    fun creates_a_report_resolving_the_entry_server_id() = runTest {
        val (repo, client) = setup(
            respond = {
                respondJson(
                    """{"id":1,"entryId":77,"entryType":"PURCHASE","entryDate":"2026-09-03",
                       "message":"Quantite suspecte","createdAt":"2026-09-07T10:00:00","status":"NEW"}""",
                    HttpStatusCode.Created,
                )
            },
        )

        repo.createReport("e1", "Quantite suspecte")

        val request = client.requests.single()
        assertEquals("/api/v1/entries/77/reports", request.url.encodedPath)
        assertEquals("POST", request.method.value)
        assertTrue("Quantite suspecte" in request.body.toByteArray().decodeToString())
    }

    @Test
    fun a_never_synced_entry_fails_without_a_network_call() = runTest {
        val (repo, client) = setup(entryDao = FakeDailyEntryDao(listOf(localDailyEntry("e1", serverId = null))))

        assertFailsWith<DomainException.NotFound> { repo.createReport("e1", "peu importe") }
        assertTrue(client.requests.isEmpty(), "no request is made when the entry has never synced")
    }

    @Test
    fun an_inactive_project_or_stage_surfaces_as_forbidden() = runTest {
        val (repo, _) = setup(
            respond = { respondProblem(HttpStatusCode.Forbidden, "Le projet ou l'etape n'est plus actif.") },
        )

        assertFailsWith<DomainException.Forbidden> { repo.createReport("e1", "trop tard") }
    }

    @Test
    fun lists_a_page_of_project_reports_and_maps_it() = runTest {
        val (repo, client) = setup()

        val page = repo.projectReports("p1", page = 0, sort = ReportSort.NEWEST_FIRST)

        val request = client.requests.single()
        assertEquals("/api/v1/projects/42/reports", request.url.encodedPath)
        assertEquals("0", request.url.parameters["page"])
        assertEquals("20", request.url.parameters["size"])
        assertEquals("date", request.url.parameters["sort"])
        assertEquals("desc", request.url.parameters["order"])

        assertEquals(0, page.page)
        assertEquals(2, page.totalPages)
        assertEquals(25, page.totalElements)
        assertTrue(page.isFirst)
        assertTrue(!page.isLast)
        assertEquals(2, page.items.size)
        val first = page.items[0]
        assertEquals(8L, first.id)
        assertEquals(55L, first.entryId)
        assertEquals(EntryType.PURCHASE, first.entryType)
        assertEquals("2026-09-03", first.entryDate)
        assertEquals("Sam Superviseur", first.authorName)
        assertEquals(ReportStatus.NEW, first.status)
        assertNull(first.processedAt)
        assertEquals(EntryType.WORK, page.items[1].entryType)
        assertEquals(ReportStatus.PROCESSED, page.items[1].status)
        assertEquals("2026-09-02T18:00:00", page.items[1].processedAt)
    }

    @Test
    fun requests_the_page_number_it_is_given() = runTest {
        val (repo, client) = setup()

        repo.projectReports("p1", page = 3, sort = ReportSort.NEWEST_FIRST)

        assertEquals("3", client.requests.single().url.parameters["page"])
    }

    @Test
    fun each_sort_maps_to_the_right_server_sort_and_order() = runTest {
        val (repo1, c1) = setup()
        repo1.projectReports("p1", page = 0, sort = ReportSort.OLDEST_FIRST)
        assertEquals("date", c1.requests.single().url.parameters["sort"])
        assertEquals("asc", c1.requests.single().url.parameters["order"])

        val (repo2, c2) = setup()
        repo2.projectReports("p1", page = 0, sort = ReportSort.UNPROCESSED_FIRST)
        assertEquals("status", c2.requests.single().url.parameters["sort"])
        assertEquals("asc", c2.requests.single().url.parameters["order"])
    }

    @Test
    fun a_never_synced_project_fails_without_a_network_call() = runTest {
        val (repo, client) = setup(projectDao = FakeProjectDao(listOf(localProject("p1", serverId = null))))

        assertFailsWith<DomainException.NotFound> { repo.projectReports("p1", page = 0, sort = ReportSort.NEWEST_FIRST) }
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun a_403_from_a_non_admin_listing_surfaces_as_forbidden() = runTest {
        val (repo, _) = setup(
            respond = { respondProblem(HttpStatusCode.Forbidden, "Action reservee a un administrateur.") },
        )

        assertFailsWith<DomainException.Forbidden> { repo.projectReports("p1", page = 0, sort = ReportSort.NEWEST_FIRST) }
    }

    @Test
    fun marks_a_report_processed_and_returns_the_updated_row() = runTest {
        val (repo, client) = setup(
            respond = {
                respondJson(
                    """{"id":8,"entryId":55,"entryType":"PURCHASE","entryDate":"2026-09-03",
                       "authorName":"Sam Superviseur","message":"Quantite suspecte",
                       "createdAt":"2026-09-05T14:32:11","status":"PROCESSED","processedAt":"2026-09-07T11:00:00"}""",
                )
            },
        )

        val updated = repo.markProcessed(8)

        val request = client.requests.single()
        assertEquals("/api/v1/reports/8/process", request.url.encodedPath)
        assertEquals("PATCH", request.method.value)
        assertEquals(ReportStatus.PROCESSED, updated.status)
        assertEquals("2026-09-07T11:00:00", updated.processedAt)
    }

    @Test
    fun a_null_author_and_an_unknown_status_are_tolerated() = runTest {
        val json = """
            {"content": [
                {"id": 9, "entryId": 1, "entryDate": "2026-08-30", "message": "x",
                 "createdAt": "2026-08-31T08:00:00", "status": "WEIRD"}
            ], "number": 0, "totalPages": 1, "totalElements": 1, "first": true, "last": true}
        """.trimIndent()
        val (repo, _) = setup(respond = { respondJson(json) })

        val item = repo.projectReports("p1", page = 0, sort = ReportSort.NEWEST_FIRST).items.single()

        assertNull(item.authorName)
        assertEquals(ReportStatus.UNKNOWN, item.status)
        assertEquals(EntryType.UNKNOWN, item.entryType)
    }
}
