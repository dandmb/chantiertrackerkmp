package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.domain.model.DomainException
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
            )
        }
        assertFailsWith<DomainException.NotFound> { apiCall { mock.client.get("projects/9") } }
    }
}
