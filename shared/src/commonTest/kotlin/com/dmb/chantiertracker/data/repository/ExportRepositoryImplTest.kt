package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.data.remote.ExportApi
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.support.FakeExportFileStore
import com.dmb.chantiertracker.support.FakeProjectDao
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.RecordingMockClient
import com.dmb.chantiertracker.support.localProject
import com.dmb.chantiertracker.support.respondBytes
import com.dmb.chantiertracker.support.respondProblem
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExportRepositoryImplTest {

    private val pdfBytes = "%PDF-1.4 fake dossier de chantier".encodeToByteArray()
    private val disposition =
        "attachment; filename=\"chantier-villa-2026-09-08.pdf\"; filename*=UTF-8''chantier-villa-2026-09-08.pdf"

    private fun setup(
        projectDao: FakeProjectDao = FakeProjectDao(listOf(localProject("p1", serverId = 42))),
        fileStore: FakeExportFileStore = FakeExportFileStore(),
        respond: MockRequestHandleScope.() -> HttpResponseData = {
            respondBytes(pdfBytes, contentDisposition = disposition)
        },
    ): Triple<ExportRepositoryImpl, RecordingMockClient, FakeExportFileStore> {
        val client = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) { respond() }
        return Triple(ExportRepositoryImpl(ExportApi(client.client), projectDao, fileStore), client, fileStore)
    }

    @Test
    fun generates_the_pdf_resolving_the_project_server_id() = runTest {
        val (repo, client, fileStore) = setup()

        val exported = repo.exportProjectPdf("p1")

        assertEquals("/api/v1/projects/42/export/pdf", client.requests.single().url.encodedPath)
        assertEquals("chantier-villa-2026-09-08.pdf" to pdfBytes.size, fileStore.saved.single())
        assertEquals("chantier-villa-2026-09-08.pdf", exported.fileName)
        assertEquals("/cache/exports/chantier-villa-2026-09-08.pdf", exported.path)
    }

    @Test
    fun a_never_synced_project_fails_without_a_network_call() = runTest {
        val (repo, client, _) = setup(projectDao = FakeProjectDao(listOf(localProject("p1", serverId = null))))

        assertFailsWith<DomainException.NotFound> { repo.exportProjectPdf("p1") }
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun a_free_owner_surfaces_as_plan_limit_reached() = runTest {
        val (repo, _, fileStore) = setup(
            respond = {
                respondProblem(
                    HttpStatusCode.Forbidden,
                    "L'export PDF n'est pas inclus dans le plan Gratuit. Passez a un palier superieur pour generer le dossier de chantier.",
                )
            },
        )

        assertFailsWith<DomainException.PlanLimitReached> { repo.exportProjectPdf("p1") }
        assertTrue(fileStore.saved.isEmpty(), "nothing is written when the export is refused")
    }

    @Test
    fun a_missing_content_disposition_falls_back_to_a_default_name() = runTest {
        val (repo, _, fileStore) = setup(respond = { respondBytes(pdfBytes, contentDisposition = null) })

        val exported = repo.exportProjectPdf("p1")

        assertEquals("chantier.pdf", exported.fileName)
        assertEquals("chantier.pdf", fileStore.saved.single().first)
    }

    @Test
    fun a_generation_failure_surfaces_as_unexpected() = runTest {
        val (repo, _, _) = setup(
            respond = { respondProblem(HttpStatusCode.InternalServerError, "Erreur lors de la generation du PDF.") },
        )

        assertFailsWith<DomainException.Unexpected> { repo.exportProjectPdf("p1") }
    }
}
