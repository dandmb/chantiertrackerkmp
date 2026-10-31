package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.RefusalReason
import com.dmb.chantiertracker.domain.model.knownRefusalCodes
import com.dmb.chantiertracker.presentation.i18n.clockWait
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.RecordingMockClient
import com.dmb.chantiertracker.support.respondProblem
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RateLimitMappingTest {

    private suspend fun failureFor(retryAfter: String?): DomainException {
        val mock = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) {
            respondProblem(
                status = HttpStatusCode.TooManyRequests,
                detail = "Trop de requêtes.",
                extraHeaders = retryAfter?.let { mapOf(HttpHeaders.RetryAfter to it) }.orEmpty(),
            )
        }
        return assertFailsWith<DomainException> { apiCall { mock.client.get("projects") } }
    }

    @Test
    fun retry_after_header_becomes_the_wait_in_seconds() = runTest {
        assertEquals(DomainException.RateLimited(retryAfterSeconds = 42), failureFor("42"))
    }

    @Test
    fun surrounding_whitespace_in_retry_after_is_tolerated() = runTest {
        assertEquals(DomainException.RateLimited(retryAfterSeconds = 7), failureFor(" 7 "))
    }

    @Test
    fun missing_retry_after_gives_rate_limited_without_wait() = runTest {
        assertEquals(DomainException.RateLimited(retryAfterSeconds = null), failureFor(null))
    }

    @Test
    fun http_date_retry_after_is_ignored_rather_than_misread() = runTest {
        assertEquals(
            DomainException.RateLimited(retryAfterSeconds = null),
            failureFor("Wed, 21 Oct 2026 07:28:00 GMT"),
        )
    }

    @Test
    fun zero_or_negative_retry_after_is_ignored() = runTest {
        assertEquals(DomainException.RateLimited(retryAfterSeconds = null), failureFor("0"))
        assertEquals(DomainException.RateLimited(retryAfterSeconds = null), failureFor("-3"))
    }

    @Test
    fun other_statuses_are_not_affected_by_a_retry_after_header() = runTest {
        val mock = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) {
            respondProblem(
                status = HttpStatusCode.NotFound,
                detail = "Introuvable.",
                extraHeaders = mapOf(HttpHeaders.RetryAfter to "30"),
                code = "RESOURCE_NOT_FOUND",
            )
        }
        assertFailsWith<DomainException.NotFound> { apiCall { mock.client.get("projects/9") } }
    }

    @Test
    fun a_busy_media_server_gives_its_wait() = runTest {
        val mock = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) {
            respondProblem(
                status = HttpStatusCode.ServiceUnavailable,
                detail = "Le serveur traite déjà plusieurs fichiers.",
                extraHeaders = mapOf(HttpHeaders.RetryAfter to "30"),
                code = "MEDIA_PROCESSING_BUSY",
            )
        }
        assertEquals(DomainException.RateLimited(30), assertFailsWith<DomainException> { apiCall { mock.client.get("entries/1/attachments") } })
    }

    @Test
    fun a_body_over_the_limit_has_its_own_message_not_a_field_error() = runTest {
        val mock = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) {
            respondProblem(HttpStatusCode.PayloadTooLarge, "Le corps de la requête est trop volumineux.", code = "REQUEST_BODY_TOO_LARGE")
        }
        assertEquals(DomainException.ContentTooLarge, assertFailsWith<DomainException> { apiCall { mock.client.get("projects") } })
    }

    @Test
    fun an_upload_over_the_limit_stays_a_file_too_large() = runTest {
        val mock = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) {
            respondProblem(HttpStatusCode.PayloadTooLarge, "Fichier trop volumineux.", code = "MAX_UPLOAD_SIZE_EXCEEDED")
        }
        assertEquals(DomainException.FileTooLarge, assertFailsWith<DomainException> { apiCall { mock.client.get("projects") } })
    }

    @Test
    fun the_owner_rule_has_its_own_message_and_a_known_sync_reason() = runTest {
        val mock = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) {
            respondProblem(HttpStatusCode.Forbidden, "Seul le propriétaire du projet peut effectuer cette action.", code = "PROJECT_OWNER_REQUIRED")
        }
        assertEquals(DomainException.OwnerRequired, assertFailsWith<DomainException> { apiCall { mock.client.get("projects/1") } })
        assertEquals(RefusalReason.INSUFFICIENT_ROLE, knownRefusalCodes["PROJECT_OWNER_REQUIRED"])
        assertEquals(RefusalReason.INVALID_VALUE, knownRefusalCodes["REQUEST_BODY_TOO_LARGE"])
    }

    @Test
    fun a_locked_address_gives_its_wait_on_login_and_on_change_password() = runTest {
        val mock = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) {
            respondProblem(HttpStatusCode.Forbidden, "Temporairement bloqué.", code = "LOCKED", extraHeaders = mapOf(HttpHeaders.RetryAfter to "900"))
        }
        assertEquals(DomainException.AccountLocked(900), assertFailsWith<DomainException> { apiCall { mock.client.get("auth/login") } })
    }

    @Test
    fun the_plan_limit_is_read_from_its_code() = runTest {
        val mock = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) {
            respondProblem(HttpStatusCode.Forbidden, "Quota plein.", code = "PLAN_LIMIT_EXCEEDED")
        }
        assertEquals(DomainException.PlanLimitReached, assertFailsWith<DomainException> { apiCall { mock.client.get("projects/1/invitations") } })
    }

    @Test
    fun an_invalid_query_string_is_logged_and_never_read_as_a_wrong_code() = runTest {
        val logged = mutableListOf<String>()
        val previous = apiLog
        apiLog = { logged += it }
        try {
            val mock = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) {
                respondProblem(HttpStatusCode.BadRequest, "Les paramètres de la requête sont invalides.", code = "INVALID_QUERY_STRING")
            }
            assertEquals(DomainException.Unexpected, assertFailsWith<DomainException> { apiCall { mock.client.get("projects") } })
        } finally {
            apiLog = previous
        }
        assertEquals(1, logged.size)
        assertTrue("INVALID_QUERY_STRING" in logged.single())
    }

    @Test
    fun a_wait_of_hours_reads_in_hours() {
        assertEquals("23 h 59", clockWait(86_378))
        assertEquals("59:59", clockWait(3_599))
    }
}
