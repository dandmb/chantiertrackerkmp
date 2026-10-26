package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.RecordingMockClient
import com.dmb.chantiertracker.support.respondProblem
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IdempotencyAnswersTest {

    private suspend fun failureFor(status: HttpStatusCode, code: String, retryAfter: String? = null): DomainException {
        val mock = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) {
            respondProblem(status, "Refus.", code = code, extraHeaders = retryAfter?.let { mapOf(HttpHeaders.RetryAfter to it) }.orEmpty())
        }
        return assertFailsWith<DomainException> { apiCall { mock.client.get("projects/9/stages") } }
    }

    @Test
    fun a_creation_still_in_progress_on_the_server_is_a_wait_of_retry_after_not_a_refusal() = runTest {
        assertEquals(DomainException.CreationInProgress(retryAfterSeconds = 10), failureFor(HttpStatusCode.Conflict, "IDEMPOTENCY_KEY_IN_PROGRESS", "10"))
    }

    @Test
    fun a_creation_in_progress_without_retry_after_still_waits() = runTest {
        assertEquals(DomainException.CreationInProgress(retryAfterSeconds = null), failureFor(HttpStatusCode.Conflict, "IDEMPOTENCY_KEY_IN_PROGRESS"))
    }

    @Test
    fun another_409_keeps_its_own_meaning() = runTest {
        val failure = failureFor(HttpStatusCode.Conflict, "DUPLICATE_ENTRY")
        assertTrue(failure !is DomainException.CreationInProgress, "$failure")
    }

    @Test
    fun a_reused_key_is_a_client_error_the_app_does_not_know_not_a_wait() = runTest {
        assertEquals(DomainException.Unexpected, failureFor(HttpStatusCode.UnprocessableEntity, "IDEMPOTENCY_KEY_REUSED"))
    }

    @Test
    fun a_replayed_creation_is_an_ordinary_success() = runTest {
        val mock = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) {
            respond(
                """{"id":7,"name":"Fondations"}""",
                HttpStatusCode.Created,
                headersOf(HttpHeaders.ContentType to listOf("application/json"), "Idempotent-Replayed" to listOf("true")),
            )
        }
        val response = apiCall { mock.client.get("projects/9/stages") }
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("""{"id":7,"name":"Fondations"}""", response.bodyAsText())
    }
}
