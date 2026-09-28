package com.dmb.chantiertracker.presentation.reports

import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.support.FakeReportRepository
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReportEntryViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    private fun vm(repo: FakeReportRepository = FakeReportRepository()): Pair<ReportEntryViewModel, FakeReportRepository> {
        val v = ReportEntryViewModel(repo).also { it.load("entry-1") }
        return v to repo
    }

    @Test
    fun can_send_only_once_a_message_is_typed() = runTest {
        val (v, _) = vm()
        assertFalse(v.state.value.canSend)

        v.onMessageChange("   ")
        assertFalse(v.state.value.canSend, "whitespace only doesn't count")

        v.onMessageChange("Quantité de ciment incorrecte")
        assertTrue(v.state.value.canSend)
    }

    @Test
    fun submitting_sends_the_trimmed_message_and_marks_sent() = runTest {
        val (v, repo) = vm()
        v.onMessageChange("  Livraison oubliée le 3  ")
        v.submit()
        advanceUntilIdle()

        assertEquals(listOf("entry-1" to "Livraison oubliée le 3"), repo.createdReports)
        assertTrue(v.state.value.sent)
        assertFalse(v.state.value.isSubmitting)
    }

    @Test
    fun a_blank_message_never_reaches_the_repository() = runTest {
        val (v, repo) = vm()
        v.submit()
        advanceUntilIdle()

        assertTrue(repo.createdReports.isEmpty())
        assertFalse(v.state.value.sent)
    }

    @Test
    fun a_domain_failure_is_surfaced_and_not_marked_sent() = runTest {
        val repo = FakeReportRepository().apply { createError = DomainException.NotFound }
        val (v, _) = vm(repo)
        v.onMessageChange("Problème")
        v.submit()
        advanceUntilIdle()

        assertEquals(DomainException.NotFound, v.state.value.error)
        assertFalse(v.state.value.sent)
        assertFalse(v.state.value.isSubmitting)
    }

    @Test
    fun typing_again_clears_a_previous_error() = runTest {
        val repo = FakeReportRepository().apply { createError = DomainException.Network }
        val (v, _) = vm(repo)
        v.onMessageChange("x")
        v.submit()
        advanceUntilIdle()
        assertEquals(DomainException.Network, v.state.value.error)

        v.onMessageChange("x2")
        assertEquals(null, v.state.value.error)
    }
}
