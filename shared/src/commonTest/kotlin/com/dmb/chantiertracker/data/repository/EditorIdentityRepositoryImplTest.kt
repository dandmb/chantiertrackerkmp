package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.EditorIdentityEntity
import com.dmb.chantiertracker.data.remote.EditorIdentityApi
import com.dmb.chantiertracker.domain.model.EditorIdentity
import com.dmb.chantiertracker.support.FakeEditorIdentityDao
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.MutableClock
import com.dmb.chantiertracker.support.RecordingMockClient
import com.dmb.chantiertracker.support.respondJson
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EditorIdentityRepositoryImplTest {

    private val allNull = """{"firstName":null,"lastName":null,"companyName":null,"legalStatus":null,"siret":null,
        |"address":null,"contactEmail":null,"vatNumber":null,"hostingProviderName":null,"hostingProviderAddress":null}""".trimMargin()

    private fun repo(dao: FakeEditorIdentityDao = FakeEditorIdentityDao(), respond: () -> String): Pair<EditorIdentityRepositoryImpl, RecordingMockClient> {
        val client = RecordingMockClient(FakeTokenStorage(null)) { respondJson(respond()) }
        return EditorIdentityRepositoryImpl(EditorIdentityApi(client.client), dao, MutableClock(5_000L)) to client
    }

    @Test
    fun nothing_is_known_before_the_first_refresh() = runTest {
        val (r, client) = repo { allNull }

        assertNull(r.observe().first())
        assertEquals(0, client.requests.size)
    }

    // Public endpoint: read before any account exists, so never with a token.
    @Test
    fun refresh_reads_the_public_endpoint_without_a_token() = runTest {
        val (r, client) = repo { allNull }

        r.refresh()

        val request = client.requests.single()
        assertEquals("/api/v1/editor-identity", request.url.encodedPath)
        assertNull(request.headers["Authorization"])
    }

    @Test
    fun an_identity_not_filled_in_yet_is_stored_as_all_null() = runTest {
        val (r, _) = repo { allNull }

        r.refresh()

        assertEquals(EditorIdentity(), r.observe().first())
    }

    @Test
    fun a_filled_identity_is_stored_field_by_field() = runTest {
        val (r, _) = repo {
            """{"firstName":"Jean","lastName":"Martin","companyName":null,"legalStatus":"Micro-entrepreneur",
                |"siret":"123 456 789 00012","address":"12 rue des Lilas, 30000 Nîmes","contactEmail":"contact@chantiertracker.com",
                |"vatNumber":null,"hostingProviderName":"Hetzner Online GmbH","hostingProviderAddress":null}""".trimMargin()
        }

        r.refresh()

        assertEquals(
            EditorIdentity(
                firstName = "Jean", lastName = "Martin", legalStatus = "Micro-entrepreneur", siret = "123 456 789 00012",
                address = "12 rue des Lilas, 30000 Nîmes", contactEmail = "contact@chantiertracker.com",
                hostingProviderName = "Hetzner Online GmbH",
            ),
            r.observe().first(),
        )
    }

    // Offline (or server down): the legal pages keep showing the last identity
    // they knew, never a regression to "[À COMPLÉTER]" once it has been filled in.
    @Test
    fun a_failed_refresh_never_throws_and_keeps_the_last_known_identity() = runTest {
        val dao = FakeEditorIdentityDao(EditorIdentityEntity(siret = "123 456 789 00012", refreshedAt = 1L))
        val offline = RecordingMockClient(FakeTokenStorage(null)) { throw kotlin.RuntimeException("no connection") }
        val r = EditorIdentityRepositoryImpl(EditorIdentityApi(offline.client), dao, MutableClock(5_000L))

        r.refresh()

        assertEquals("123 456 789 00012", r.observe().first()?.siret)
    }

    @Test
    fun a_server_error_keeps_the_last_known_identity_too() = runTest {
        val dao = FakeEditorIdentityDao(EditorIdentityEntity(address = "12 rue des Lilas", refreshedAt = 1L))
        val failing = RecordingMockClient(FakeTokenStorage(null)) {
            respondJson("""{"status":500}""", HttpStatusCode.InternalServerError)
        }
        val r = EditorIdentityRepositoryImpl(EditorIdentityApi(failing.client), dao, MutableClock(5_000L))

        r.refresh()

        assertEquals("12 rue des Lilas", r.observe().first()?.address)
    }

    @Test
    fun a_field_cleared_on_the_backend_is_cleared_locally_too() = runTest {
        val dao = FakeEditorIdentityDao(EditorIdentityEntity(siret = "123 456 789 00012", refreshedAt = 1L))
        val (r, _) = repo(dao) { allNull }

        r.refresh()

        assertNull(r.observe().first()?.siret)
    }
}
