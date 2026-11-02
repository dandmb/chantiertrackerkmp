package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.SupportContactEntity
import com.dmb.chantiertracker.data.remote.LegalSettingsApi
import com.dmb.chantiertracker.support.FakeSupportContactDao
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.RecordingMockClient
import com.dmb.chantiertracker.support.respondProblem
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SupportContactRepositoryImplTest {

    private val cached = SupportContactEntity(email = "avant@exemple.test", refreshedAt = 1)

    private fun repository(body: String?, dao: FakeSupportContactDao = FakeSupportContactDao()) = SupportContactRepositoryImpl(
        LegalSettingsApi(
            RecordingMockClient(FakeTokenStorage()) {
                if (body == null) {
                    respondProblem(HttpStatusCode.ServiceUnavailable, "Indisponible.", code = "X")
                } else {
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                }
            }.client,
        ),
        dao,
    )

    @Test
    fun it_keeps_the_address_of_the_admin_for_offline_reading() = runTest {
        val repository = repository("""{"supportContactEmail":" aide@exemple.test ","dataProtectionContactEmail":"dpo@exemple.test"}""")
        repository.refresh()
        assertEquals("aide@exemple.test", repository.observeEmail().first())
    }

    @Test
    fun nothing_is_known_before_the_first_answer() = runTest {
        val repository = repository(null)
        repository.refresh()
        assertNull(repository.observeEmail().first())
    }

    @Test
    fun a_failed_refresh_keeps_the_last_known_address() = runTest {
        val repository = repository(null, FakeSupportContactDao(cached))
        repository.refresh()
        assertEquals("avant@exemple.test", repository.observeEmail().first())
    }

    @Test
    fun an_address_removed_by_the_admin_goes_away() = runTest {
        for (body in listOf("""{"supportContactEmail":null}""", """{"supportContactEmail":"  "}""")) {
            val repository = repository(body, FakeSupportContactDao(cached))
            repository.refresh()
            assertNull(repository.observeEmail().first())
        }
    }
}
