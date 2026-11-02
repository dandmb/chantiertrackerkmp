package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.RecordingMockClient
import com.dmb.chantiertracker.support.respondJson
import com.dmb.chantiertracker.support.respondProblem
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val USER_JSON =
    """{"id":1,"email":"a@b.dev","name":"A","active":true,"globalRole":"USER"}"""

class TokenRefreshTest {

    @Test
    fun expired_access_token_is_refreshed_then_request_retried() = runTest {
        val storage = FakeTokenStorage(AuthTokens("stale-access", "good-refresh"))
        var meCalls = 0
        val mock = RecordingMockClient(storage) { request ->
            when (request.url.encodedPath) {
                "/api/v1/users/me" -> {
                    meCalls++
                    if (request.headers[HttpHeaders.Authorization] == "Bearer stale-access") {
                        respondProblem(HttpStatusCode.Unauthorized, "Token expiré.")
                    } else {
                        respondJson(USER_JSON)
                    }
                }
                "/api/v1/auth/refresh-token" -> {
                    val body = request.body.toByteArray().decodeToString()
                    assertTrue("good-refresh" in body)
                    respondJson("""{"accessToken":"new-access","refreshToken":"new-refresh"}""")
                }
                else -> error("unexpected ${request.url}")
            }
        }

        val response = mock.client.get("users/me")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(AuthTokens("new-access", "new-refresh"), storage.tokens)
        assertTrue(meCalls >= 2)
    }

    @Test
    fun failed_refresh_clears_tokens_and_signals_session_expired() = runTest {
        val storage = FakeTokenStorage(AuthTokens("stale", "dead-refresh"))
        var expiredSignals = 0
        val mock = RecordingMockClient(storage, onSessionExpired = { expiredSignals++ }) { request ->
            when (request.url.encodedPath) {
                "/api/v1/users/me" -> respondProblem(HttpStatusCode.Unauthorized, "Token expiré.")
                "/api/v1/auth/refresh-token" -> respondProblem(HttpStatusCode.Unauthorized, "Refresh token invalide ou expiré.")
                else -> error("unexpected ${request.url}")
            }
        }

        assertFailsWith<ClientRequestException> { mock.client.get("users/me") }

        assertEquals(null, storage.tokens)
        assertTrue(storage.clearCount >= 1)
        assertTrue(expiredSignals >= 1)
    }

    @Test
    fun a_rate_limited_refresh_keeps_the_session() = runTest {
        val storage = FakeTokenStorage(AuthTokens("stale", "good-refresh"))
        var expiredSignals = 0
        val mock = RecordingMockClient(storage, onSessionExpired = { expiredSignals++ }) { request ->
            when (request.url.encodedPath) {
                "/api/v1/users/me" -> respondProblem(HttpStatusCode.Unauthorized, "Token expiré.")
                "/api/v1/auth/refresh-token" -> respondProblem(
                    HttpStatusCode.TooManyRequests, "Trop de requêtes.", extraHeaders = mapOf(HttpHeaders.RetryAfter to "12"), code = "RATE_LIMITED",
                )
                else -> error("unexpected ${request.url}")
            }
        }

        val failure = assertFailsWith<DomainException> { apiCall { mock.client.get("users/me") } }

        assertEquals(DomainException.RateLimited(12), failure)
        assertEquals(AuthTokens("stale", "good-refresh"), storage.tokens)
        assertEquals(0, expiredSignals)
    }

    @Test
    fun requests_expiring_together_exchange_the_refresh_token_once() = runTest {
        val storage = FakeTokenStorage(AuthTokens("stale", "once"))
        val bothRefused = CompletableDeferred<Unit>()
        var refused = 0
        var refreshes = 0
        val mock = RecordingMockClient(storage) { request ->
            when (request.url.encodedPath) {
                "/api/v1/users/me" -> if (request.headers[HttpHeaders.Authorization] == "Bearer stale") {
                    if (++refused == 2) bothRefused.complete(Unit)
                    bothRefused.await()
                    respondProblem(HttpStatusCode.Unauthorized, "Token expiré.")
                } else {
                    respondJson(USER_JSON)
                }
                "/api/v1/auth/refresh-token" -> {
                    refreshes++
                    if (refreshes > 1) {
                        respondProblem(HttpStatusCode.Unauthorized, "Refresh token invalide ou expiré.")
                    } else {
                        respondJson("""{"accessToken":"new-access","refreshToken":"new-refresh"}""")
                    }
                }
                else -> error("unexpected ${request.url}")
            }
        }

        val responses = listOf(async { mock.client.get("users/me") }, async { mock.client.get("users/me") }).awaitAll()

        assertEquals(listOf(HttpStatusCode.OK, HttpStatusCode.OK), responses.map { it.status })
        assertEquals(1, refreshes, "a second exchange would read as a replay and revoke the session")
        assertEquals(AuthTokens("new-access", "new-refresh"), storage.tokens)
    }

    @Test
    fun a_refusal_landing_after_the_refresh_does_not_exchange_the_token_again() = runTest {
        val storage = FakeTokenStorage(AuthTokens("stale", "once"))
        val refreshed = CompletableDeferred<Unit>()
        val secondSent = CompletableDeferred<Unit>()
        var refused = 0
        var refreshes = 0
        val mock = RecordingMockClient(storage) { request ->
            when (request.url.encodedPath) {
                "/api/v1/users/me" -> if (request.headers[HttpHeaders.Authorization] == "Bearer stale") {
                    if (++refused == 1) {
                        secondSent.await()
                    } else {
                        secondSent.complete(Unit)
                        refreshed.await()
                        yield()
                    }
                    respondProblem(HttpStatusCode.Unauthorized, "Token expiré.")
                } else {
                    respondJson(USER_JSON)
                }
                "/api/v1/auth/refresh-token" -> {
                    refreshes++
                    refreshed.complete(Unit)
                    if (refreshes > 1) {
                        respondProblem(HttpStatusCode.Unauthorized, "Refresh token invalide ou expiré.")
                    } else {
                        respondJson("""{"accessToken":"new-access","refreshToken":"new-refresh"}""")
                    }
                }
                else -> error("unexpected ${request.url}")
            }
        }

        val responses = listOf(async { mock.client.get("users/me") }, async { mock.client.get("users/me") }).awaitAll()

        assertEquals(listOf(HttpStatusCode.OK, HttpStatusCode.OK), responses.map { it.status })
        assertEquals(1, refreshes)
    }
}
