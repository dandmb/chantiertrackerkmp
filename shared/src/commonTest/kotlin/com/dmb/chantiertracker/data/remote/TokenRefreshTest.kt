package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.RecordingMockClient
import com.dmb.chantiertracker.support.respondJson
import com.dmb.chantiertracker.support.respondProblem
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
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
}
