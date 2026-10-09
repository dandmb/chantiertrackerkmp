package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.RecordingMockClient
import com.dmb.chantiertracker.support.respondProblem
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RefusalCodeMappingTest {

    private class Refusal(val exception: DomainException, val reportedCodes: List<String?>)

    private suspend fun refusal(status: HttpStatusCode, code: String?, detail: String = "Refusé."): Refusal {
        val mock = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) { respondProblem(status = status, detail = detail, code = code) }
        val reported = mutableListOf<String?>()
        val exception = assertFailsWith<DomainException> { apiCallReportingRefusalCode({ reported += it }) { mock.client.get("projects") } }
        return Refusal(exception, reported)
    }

    @Test
    fun the_server_code_of_every_refusal_is_reported_as_it_is() = runTest {
        val codes = listOf(
            HttpStatusCode.Forbidden to "PLAN_LIMIT_EXCEEDED",
            HttpStatusCode.Forbidden to "PROJECT_OR_STAGE_INACTIVE",
            HttpStatusCode.Forbidden to "ENTRY_DATE_RESTRICTED",
            HttpStatusCode.Forbidden to "PROJECT_INSUFFICIENT_ROLE",
            HttpStatusCode.Conflict to "INSUFFICIENT_STOCK",
            HttpStatusCode.Conflict to "STOCK_CONSUMED",
            HttpStatusCode.Conflict to "STOCK_RELEASE_BLOCKED",
            HttpStatusCode.Conflict to "DUPLICATE_ENTRY",
            HttpStatusCode.Conflict to "DUPLICATE_MATERIAL",
            HttpStatusCode.BadRequest to "VALIDATION_FAILED",
            HttpStatusCode.BadRequest to "INVALID_AMOUNT",
            HttpStatusCode.BadRequest to "INVALID_ATTACHMENT_TYPE",
            HttpStatusCode.BadRequest to "UNSUPPORTED_IMAGE",
            HttpStatusCode.Forbidden to "VIDEO_TOO_LONG",
            HttpStatusCode.PayloadTooLarge to "ATTACHMENT_TOO_LARGE",
            HttpStatusCode.Conflict to "A_CODE_THIS_APP_DOES_NOT_KNOW",
        )
        for ((status, code) in codes) {
            assertEquals(listOf<String?>(code), refusal(status, code).reportedCodes, "$status $code")
        }
    }

    @Test
    fun a_refusal_without_a_code_reports_none() = runTest {
        assertEquals(listOf<String?>(null), refusal(HttpStatusCode.Conflict, code = null).reportedCodes)
    }

    @Test
    fun a_file_too_large_is_a_refusal_of_its_own_with_or_without_a_body() = runTest {
        assertEquals(DomainException.FileTooLarge, refusal(HttpStatusCode.PayloadTooLarge, "ATTACHMENT_TOO_LARGE").exception)

        val bare = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) { respond("<html>413 Request Entity Too Large</html>", HttpStatusCode.PayloadTooLarge) }
        val reported = mutableListOf<String?>()
        val exception = assertFailsWith<DomainException> { apiCallReportingRefusalCode({ reported += it }) { bare.client.get("projects") } }

        assertEquals(DomainException.FileTooLarge, exception)
        assertEquals(listOf<String?>(null), reported)
    }

    @Test
    fun a_plain_api_call_maps_errors_exactly_as_before() = runTest {
        val mock = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) { respondProblem(HttpStatusCode.Conflict, "Déjà pris.", code = "INSUFFICIENT_STOCK") }

        assertEquals(DomainException.EmailAlreadyUsed, assertFailsWith<DomainException> { apiCall { mock.client.get("projects") } })
    }

    @Test
    fun a_success_reports_nothing() = runTest {
        val mock = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) { respond("ok", HttpStatusCode.OK) }
        val reported = mutableListOf<String?>()

        apiCallReportingRefusalCode({ reported += it }) { mock.client.get("projects") }

        assertEquals(emptyList(), reported)
    }
}
