package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.remote.BillingApi
import com.dmb.chantiertracker.domain.model.BillingAvailability
import com.dmb.chantiertracker.domain.model.FounderOffer
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.RecordingMockClient
import com.dmb.chantiertracker.support.respondProblem
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LaunchStatusRepositoryImplTest {

    private val fullOffer = """{"billingOpen":false,"limits":{"projects":3,"photos":150,"videos":5,"videoMaxSeconds":120,
        "supervisorsPerProject":3,"pdfExport":true,"historyDays":180,"storageGb":10},"durationMonths":null,"seats":null}"""

    private fun repositoryAnswering(founderOffer: String?, status: String? = null) = LaunchStatusRepositoryImpl(
        BillingApi(
            RecordingMockClient(FakeTokenStorage()) { request ->
                val body = if (request.url.encodedPath.endsWith("founder-offer")) founderOffer else status
                if (body == null) {
                    respondProblem(HttpStatusCode.ServiceUnavailable, "Indisponible.", code = "X")
                } else {
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                }
            }.client,
        ),
    )

    @Test
    fun it_reads_the_offer_and_ignores_the_limits_it_does_not_know() = runTest {
        val repository = repositoryAnswering(fullOffer).apply { refresh() }

        assertEquals(BillingAvailability.CLOSED, repository.billingStatus.value)
        assertEquals(
            FounderOffer(3, 150, 5, 120, 3, true, 180),
            repository.founderOffer.value,
        )
    }

    @Test
    fun duration_and_seats_are_ignored_even_when_sent() = runTest {
        val body = """{"billingOpen":false,"limits":{"projects":3},"durationMonths":6,"seats":{"total":200,"remaining":143}}"""
        val offer = repositoryAnswering(body).apply { refresh() }.founderOffer.value

        assertEquals(3, offer?.projects)
        assertEquals(FounderOffer(projects = 3), offer)
    }

    @Test
    fun an_open_switch_keeps_no_founder_figures() = runTest {
        val repository = repositoryAnswering("""{"billingOpen":true,"limits":{"projects":3}}""").apply { refresh() }

        assertEquals(BillingAvailability.OPEN, repository.billingStatus.value)
        assertNull(repository.founderOffer.value)
    }

    @Test
    fun without_the_route_the_status_still_gives_the_switch_but_no_figure() = runTest {
        val repository = repositoryAnswering(null, status = """{"billingOpen":false}""").apply { refresh() }

        assertEquals(BillingAvailability.CLOSED, repository.billingStatus.value)
        assertNull(repository.founderOffer.value)
    }

    @Test
    fun with_no_answer_at_all_it_stays_unknown() = runTest {
        val repository = repositoryAnswering(null).apply { refresh() }

        assertEquals(BillingAvailability.UNKNOWN, repository.billingStatus.value)
        assertNull(repository.founderOffer.value)
    }
}
