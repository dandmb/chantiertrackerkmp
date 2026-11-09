package com.dmb.chantiertracker.core

import com.dmb.chantiertracker.data.remote.apiCall
import com.dmb.chantiertracker.data.remote.dto.LoginRequestDto
import com.dmb.chantiertracker.data.remote.dto.LoginResponseDto
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.RecordingMockClient
import com.dmb.chantiertracker.support.respondJson
import com.dmb.chantiertracker.support.respondProblem
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NonFatalErrorReportingTest {

    private val reports = mutableListOf<NonFatalReport>()

    @BeforeTest fun setUp() { NonFatalErrors.sink = { reports += it } }
    @AfterTest fun tearDown() { NonFatalErrors.sink = {} }

    private suspend fun failingCall(
        screen: ErrorReportingScreen?,
        answer: suspend MockRequestHandleScope.() -> HttpResponseData,
    ) {
        val mock = RecordingMockClient(FakeTokenStorage(null)) { answer() }
        val call: suspend () -> Unit = {
            apiCall {
                mock.client.post("auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody(LoginRequestDto(EMAIL, PASSWORD))
                }.body<LoginResponseDto>()
            }
        }
        assertFailsWith<DomainException> { if (screen == null) call() else withContext(screen) { call() } }
    }

    @Test
    fun a_refused_sign_in_is_reported_with_its_type_status_code_and_screen() = runTest {
        failingCall(ErrorReportingScreen.Login) { respondProblem(HttpStatusCode.Unauthorized, DETAIL, code = "INVALID_CREDENTIALS") }

        assertEquals(listOf(NonFatalReport("login", "InvalidCredentials", 401, "INVALID_CREDENTIALS", null)), reports)
    }

    @Test
    fun a_gateway_answer_is_reported_as_the_service_being_unavailable_with_its_status() = runTest {
        failingCall(ErrorReportingScreen.Login) { respond("<html>maintenance</html>", HttpStatusCode.BadGateway, headersOf(HttpHeaders.ContentType, "text/html")) }

        assertEquals(NonFatalReport("login", "ServiceUnavailable", 502, null, null), reports.single())
    }

    @Test
    fun an_unreadable_answer_names_the_failure_class_only() = runTest {
        failingCall(ErrorReportingScreen.Login) { respondJson("""{"jeton":"$TOKEN"}""") }

        val report = reports.single()
        assertEquals("Unexpected", report.errorType)
        assertTrue(!report.cause.isNullOrBlank(), "the class of the failure helps to tell what went wrong")
    }

    @Test
    fun a_network_failure_at_sign_in_is_reported_with_its_class() = runTest {
        failingCall(ErrorReportingScreen.Login) { throw kotlinx.io.IOException("unreachable $EMAIL") }

        assertEquals(NonFatalReport("login", "Network", null, null, "IOException"), reports.single())
    }

    @Test
    fun a_sync_failure_is_reported_but_being_offline_during_a_sync_is_not() = runTest {
        failingCall(ErrorReportingScreen.Sync) { respondProblem(HttpStatusCode.InternalServerError, DETAIL, code = "INTERNAL_ERROR") }
        failingCall(ErrorReportingScreen.Sync) { throw kotlinx.io.IOException("offline") }

        assertEquals(listOf(NonFatalReport("sync", "Unexpected", 500, "INTERNAL_ERROR", null)), reports)
    }

    @Test
    fun calls_outside_sign_in_and_sync_are_never_reported() = runTest {
        failingCall(null) { respondProblem(HttpStatusCode.Unauthorized, DETAIL, code = "INVALID_CREDENTIALS") }

        assertTrue(reports.isEmpty())
    }

    @Test
    fun no_report_carries_the_address_the_password_a_token_or_what_was_typed() = runTest {
        failingCall(ErrorReportingScreen.Login) { respondProblem(HttpStatusCode.Unauthorized, DETAIL, code = "INVALID_CREDENTIALS") }
        failingCall(ErrorReportingScreen.Login) { respondJson("""{"jeton":"$TOKEN"}""") }
        failingCall(ErrorReportingScreen.Login) { throw kotlinx.io.IOException("unreachable $EMAIL $PASSWORD") }
        failingCall(ErrorReportingScreen.Sync) { respondProblem(HttpStatusCode.BadRequest, "Le résumé $SUMMARY est invalide.", code = "VALIDATION_FAILED") }

        assertEquals(4, reports.size)
        reports.forEach { report ->
            listOf(report.toString(), report.message()).forEach { text ->
                listOf(EMAIL, PASSWORD, TOKEN, SUMMARY, DETAIL, "jean").forEach { secret ->
                    assertFalse(text.contains(secret, ignoreCase = true), "\"$secret\" leaked in $text")
                }
            }
        }
    }

    private companion object {
        const val EMAIL = "jean.dupont@chantier.dev"
        const val PASSWORD = "MotDePasse-Secret-42"
        const val TOKEN = "eyJhbGciOiJIUzI1NiJ9.secret"
        const val SUMMARY = "Livraison chez Mme Martin"
        const val DETAIL = "Email ou mot de passe incorrect pour jean.dupont@chantier.dev."
    }
}
