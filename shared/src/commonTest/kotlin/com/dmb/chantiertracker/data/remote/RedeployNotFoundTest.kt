package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.RecordingMockClient
import com.dmb.chantiertracker.support.RedeployAnswer
import com.dmb.chantiertracker.support.respondJson
import com.dmb.chantiertracker.support.respondProblem
import com.dmb.chantiertracker.support.respondWhileRedeploying
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private const val USER_JSON =
    """{"id":1,"email":"a@b.dev","name":"A","active":true,"globalRole":"USER"}"""

class RedeployNotFoundTest {

    private suspend fun failureFor(answer: RedeployAnswer): DomainException {
        val mock = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) { respondWhileRedeploying(answer) }
        return assertFailsWith<DomainException> { apiCall { mock.client.get("projects/9") } }
    }

    @Test
    fun a_404_from_the_proxy_in_plain_text_is_an_unreachable_server_not_a_missing_row() = runTest {
        assertEquals(DomainException.ServiceUnavailable, failureFor(RedeployAnswer.PROXY_TEXT))
    }

    @Test
    fun a_404_without_any_body_is_an_unreachable_server() = runTest {
        assertEquals(DomainException.ServiceUnavailable, failureFor(RedeployAnswer.EMPTY))
    }

    @Test
    fun a_404_in_plain_json_without_a_code_is_an_unreachable_server() = runTest {
        assertEquals(DomainException.ServiceUnavailable, failureFor(RedeployAnswer.JSON_WITHOUT_CODE))
    }

    @Test
    fun a_404_in_the_problem_format_but_without_a_code_is_an_unreachable_server() = runTest {
        assertEquals(DomainException.ServiceUnavailable, failureFor(RedeployAnswer.PROBLEM_WITHOUT_CODE))
    }

    @Test
    fun a_404_carrying_a_code_but_not_in_the_problem_format_is_an_unreachable_server() = runTest {
        val mock = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) {
            respondJson("""{"status":404,"detail":"Projet introuvable.","code":"PROJECT_ACCESS_DENIED"}""", HttpStatusCode.NotFound)
        }
        assertFailsWith<DomainException.ServiceUnavailable> { apiCall { mock.client.get("projects/9") } }
    }

    @Test
    fun a_404_in_the_backend_problem_format_with_its_code_stays_a_missing_row() = runTest {
        val mock = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) {
            respondProblem(HttpStatusCode.NotFound, "Projet introuvable.", code = "PROJECT_ACCESS_DENIED")
        }
        assertFailsWith<DomainException.NotFound> { apiCall { mock.client.get("projects/9") } }
    }

    @Test
    fun a_session_renewal_answered_by_the_proxy_keeps_the_session_and_fails_like_a_cut() = runTest {
        val storage = FakeTokenStorage(AuthTokens("stale", "good-refresh"))
        var expiredSignals = 0
        val mock = RecordingMockClient(storage, onSessionExpired = { expiredSignals++ }) { request ->
            when (request.url.encodedPath) {
                "/api/v1/users/me" -> respondProblem(HttpStatusCode.Unauthorized, "Token expiré.", code = "UNAUTHORIZED")
                "/api/v1/auth/refresh-token" -> respondWhileRedeploying()
                else -> error("unexpected ${request.url}")
            }
        }

        assertFailsWith<DomainException.ServiceUnavailable> { apiCall { mock.client.get("users/me") } }

        assertEquals(AuthTokens("stale", "good-refresh"), storage.tokens, "the session must survive a redeploy")
        assertEquals(0, storage.clearCount)
        assertEquals(0, expiredSignals, "no sign-out while the API is being redeployed")
    }

    @Test
    fun a_session_renewal_answered_by_the_proxy_is_retried_on_the_next_call() = runTest {
        val storage = FakeTokenStorage(AuthTokens("stale", "good-refresh"))
        var redeploying = true
        val mock = RecordingMockClient(storage) { request ->
            when (request.url.encodedPath) {
                "/api/v1/users/me" ->
                    if (request.headers["Authorization"] == "Bearer fresh") respondJson(USER_JSON)
                    else respondProblem(HttpStatusCode.Unauthorized, "Token expiré.", code = "UNAUTHORIZED")
                "/api/v1/auth/refresh-token" ->
                    if (redeploying) respondWhileRedeploying()
                    else respondJson("""{"accessToken":"fresh","refreshToken":"fresh-refresh"}""")
                else -> error("unexpected ${request.url}")
            }
        }

        assertFailsWith<DomainException.ServiceUnavailable> { apiCall { mock.client.get("users/me") } }
        redeploying = false
        apiCall { mock.client.get("users/me") }

        assertEquals(AuthTokens("fresh", "fresh-refresh"), storage.tokens)
    }

    @Test
    fun a_session_renewal_answered_by_a_server_error_keeps_the_session_and_is_retried_later() = runTest {
        listOf(HttpStatusCode.InternalServerError, HttpStatusCode.BadGateway, HttpStatusCode.ServiceUnavailable, HttpStatusCode.GatewayTimeout).forEach { serverError ->
            val storage = FakeTokenStorage(AuthTokens("stale", "good-refresh"))
            var expiredSignals = 0
            var down = true
            val mock = RecordingMockClient(storage, onSessionExpired = { expiredSignals++ }) { request ->
                when (request.url.encodedPath) {
                    "/api/v1/users/me" ->
                        if (request.headers["Authorization"] == "Bearer fresh") respondJson(USER_JSON)
                        else respondProblem(HttpStatusCode.Unauthorized, "Token expiré.", code = "UNAUTHORIZED")
                    "/api/v1/auth/refresh-token" ->
                        if (down) respondProblem(serverError, "Erreur.", code = "INTERNAL_ERROR")
                        else respondJson("""{"accessToken":"fresh","refreshToken":"fresh-refresh"}""")
                    else -> error("unexpected ${request.url}")
                }
            }

            assertFailsWith<DomainException.ServiceUnavailable>("$serverError") { apiCall { mock.client.get("users/me") } }
            assertEquals(AuthTokens("stale", "good-refresh"), storage.tokens, "$serverError: the session is kept")
            assertEquals(0, expiredSignals, "$serverError: no sign-out")

            down = false
            apiCall { mock.client.get("users/me") }
            assertEquals(AuthTokens("fresh", "fresh-refresh"), storage.tokens, "$serverError: renewed once the server is back")
        }
    }

    @Test
    fun a_session_renewal_refused_by_the_backend_still_signs_out() = runTest {
        val storage = FakeTokenStorage(AuthTokens("stale", "dead-refresh"))
        var expiredSignals = 0
        val mock = RecordingMockClient(storage, onSessionExpired = { expiredSignals++ }) { request ->
            when (request.url.encodedPath) {
                "/api/v1/users/me" -> respondProblem(HttpStatusCode.Unauthorized, "Token expiré.", code = "UNAUTHORIZED")
                "/api/v1/auth/refresh-token" -> respondProblem(HttpStatusCode.Unauthorized, "Refresh token invalide ou expiré.", code = "INVALID_REFRESH_TOKEN")
                else -> error("unexpected ${request.url}")
            }
        }

        assertFailsWith<DomainException> { apiCall { mock.client.get("users/me") } }

        assertEquals(null, storage.tokens)
        assertEquals(1, expiredSignals)
    }

    @Test
    fun a_real_404_on_the_session_renewal_still_signs_out() = runTest {
        val storage = FakeTokenStorage(AuthTokens("stale", "unknown-refresh"))
        var expiredSignals = 0
        val mock = RecordingMockClient(storage, onSessionExpired = { expiredSignals++ }) { request ->
            when (request.url.encodedPath) {
                "/api/v1/users/me" -> respondProblem(HttpStatusCode.Unauthorized, "Token expiré.", code = "UNAUTHORIZED")
                "/api/v1/auth/refresh-token" -> respondProblem(HttpStatusCode.NotFound, "Utilisateur introuvable.", code = "USER_NOT_FOUND")
                else -> error("unexpected ${request.url}")
            }
        }

        assertFailsWith<DomainException> { apiCall { mock.client.get("users/me") } }

        assertEquals(null, storage.tokens)
        assertEquals(1, expiredSignals)
    }
}
