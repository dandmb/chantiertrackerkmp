package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.remote.dto.LoginRequestDto
import com.dmb.chantiertracker.data.remote.dto.LoginResponseDto
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.RecordingMockClient
import com.dmb.chantiertracker.support.respondJson
import com.dmb.chantiertracker.support.respondProblem
import io.ktor.client.call.body
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LoginFailureMappingTest {

    private suspend fun loginFailure(answer: suspend io.ktor.client.engine.mock.MockRequestHandleScope.() -> io.ktor.client.request.HttpResponseData): DomainException {
        val mock = RecordingMockClient(FakeTokenStorage(null)) { answer() }
        return assertFailsWith<DomainException> {
            apiCall {
                mock.client.post("auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody(LoginRequestDto("jean@chantier.dev", "x"))
                }.body<LoginResponseDto>()
            }
        }
    }

    @Test
    fun invalid_credentials_locked_and_unverified_keep_their_own_message() = runTest {
        assertEquals(DomainException.InvalidCredentials, loginFailure { respondProblem(HttpStatusCode.Unauthorized, "Email ou mot de passe incorrect.", code = "INVALID_CREDENTIALS") })
        assertEquals(DomainException.AccountLocked(retryAfterSeconds = 1800), loginFailure { respondProblem(HttpStatusCode.Forbidden, "Compte verrouillé.", code = "LOCKED", extraHeaders = mapOf(HttpHeaders.RetryAfter to "1800")) })
        assertEquals(DomainException.AccountNotVerified, loginFailure { respondProblem(HttpStatusCode.Forbidden, "Compte non vérifié.", code = "ACCOUNT_NOT_VERIFIED") })
        assertEquals(DomainException.RateLimited(retryAfterSeconds = 60), loginFailure { respondProblem(HttpStatusCode.TooManyRequests, "Trop de requêtes.", code = "RATE_LIMITED", extraHeaders = mapOf(HttpHeaders.RetryAfter to "60")) })
    }

    @Test
    fun a_gateway_or_unavailable_answer_reads_as_the_service_being_unavailable_never_as_a_server_error() = runTest {
        listOf(HttpStatusCode.ServiceUnavailable, HttpStatusCode.BadGateway, HttpStatusCode.GatewayTimeout).forEach { status ->
            assertEquals(DomainException.ServiceUnavailable, loginFailure { respond("<html>maintenance</html>", status, headersOf(HttpHeaders.ContentType, "text/html")) }, "$status")
            assertEquals(DomainException.ServiceUnavailable, loginFailure { respondProblem(status, "Indisponible.") }, "$status problem")
        }
    }

    @Test
    fun an_answer_the_app_cannot_read_is_not_reported_as_a_connection_failure() = runTest {
        assertEquals(DomainException.Unexpected, loginFailure { respondJson("""{"jeton":"inattendu"}""") })
    }

    @Test
    fun a_network_failure_still_reads_as_a_connection_problem() = runTest {
        assertEquals(DomainException.Network, loginFailure { throw kotlinx.io.IOException("unreachable") })
    }
}
