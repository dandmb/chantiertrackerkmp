package com.dmb.chantiertracker.presentation.reports

import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.Report
import com.dmb.chantiertracker.domain.model.ReportPage
import com.dmb.chantiertracker.domain.model.ReportStatus
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectReportsViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    private fun report(id: Long, status: ReportStatus = ReportStatus.NEW) = Report(
        id = id, entryId = id * 10, entryType = EntryType.PURCHASE, entryDate = "2026-09-0${id}",
        authorName = "Sam", message = "Problème $id", createdAt = "2026-09-0${id}T09:00:00",
        status = status, processedAt = if (status == ReportStatus.PROCESSED) "2026-09-06T18:00:00" else null,
    )

    private fun page(index: Int, total: Int, items: List<Report>) = ReportPage(
        items = items, page = index, totalPages = total,
        isFirst = index == 0, isLast = index == total - 1, totalElements = total * 20,
    )

    @Test
    fun opening_the_screen_loads_the_first_page() = runTest {
        val repo = FakeReportRepository(listOf(page(0, 1, listOf(report(1), report(2, ReportStatus.PROCESSED)))))
        val vm = ProjectReportsViewModel(repo)

        vm.load("p1")
        advanceUntilIdle()

        assertEquals("p1" to 0, repo.listCalls.single())
        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(listOf(1L, 2L), state.items.map { it.id })
        assertTrue(state.isFirst && state.isLast)
        assertFalse(state.showPagination)
    }

    @Test
    fun next_and_previous_move_through_pages_and_stop_at_the_ends() = runTest {
        val repo = FakeReportRepository(listOf(page(0, 2, listOf(report(1))), page(1, 2, listOf(report(2)))))
        val vm = ProjectReportsViewModel(repo)
        vm.load("p1")
        advanceUntilIdle()

        vm.previousPage() // already first — no-op
        advanceUntilIdle()
        assertEquals(1, repo.listCalls.size)

        vm.nextPage()
        advanceUntilIdle()
        assertEquals(1, vm.state.value.page)
        assertTrue(vm.state.value.isLast)

        vm.nextPage() // already last — no-op
        advanceUntilIdle()
        assertEquals(listOf(0, 1), repo.listCalls.map { it.second })
    }

    @Test
    fun an_error_is_surfaced_and_retry_refetches_the_same_page() = runTest {
        val repo = FakeReportRepository(listOf(page(0, 2, listOf(report(1))), page(1, 2, listOf(report(2)))))
        val vm = ProjectReportsViewModel(repo)
        vm.load("p1")
        advanceUntilIdle()
        vm.nextPage()
        advanceUntilIdle()

        repo.listError = DomainException.Network
        vm.retry()
        advanceUntilIdle()
        assertEquals(DomainException.Network, vm.state.value.error)
        assertEquals(1, repo.listCalls.last().second, "retry re-requests the current page, not page 0")

        repo.listError = null
        vm.retry()
        advanceUntilIdle()
        assertNull(vm.state.value.error)
        assertEquals(listOf(2L), vm.state.value.items.map { it.id })
    }

    @Test
    fun a_403_is_surfaced_as_forbidden() = runTest {
        val repo = FakeReportRepository().apply { listError = DomainException.Forbidden }
        val vm = ProjectReportsViewModel(repo)

        vm.load("p1")
        advanceUntilIdle()

        assertEquals(DomainException.Forbidden, vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun marking_a_report_processed_updates_only_that_row_in_place() = runTest {
        val repo = FakeReportRepository(listOf(page(0, 1, listOf(report(1), report(2)))))
        val vm = ProjectReportsViewModel(repo)
        vm.load("p1")
        advanceUntilIdle()

        vm.markProcessed(1)
        advanceUntilIdle()

        assertEquals(listOf(1L), repo.processedIds)
        assertEquals(1, repo.listCalls.size, "the page is not re-fetched — only the row is patched")
        val items = vm.state.value.items
        assertEquals(ReportStatus.PROCESSED, items.first { it.id == 1L }.status)
        assertEquals(ReportStatus.NEW, items.first { it.id == 2L }.status)
        assertTrue(vm.state.value.processingIds.isEmpty())
    }

    @Test
    fun a_process_failure_frees_the_button_and_surfaces_the_error() = runTest {
        val repo = FakeReportRepository(listOf(page(0, 1, listOf(report(1)))))
            .apply { processError = DomainException.Network }
        val vm = ProjectReportsViewModel(repo)
        vm.load("p1")
        advanceUntilIdle()

        vm.markProcessed(1)
        advanceUntilIdle()

        assertEquals(DomainException.Network, vm.state.value.processError)
        assertTrue(vm.state.value.processingIds.isEmpty())
        assertEquals(ReportStatus.NEW, vm.state.value.items.single().status, "the row stays NEW on failure")

        vm.clearProcessError()
        assertNull(vm.state.value.processError)
    }
}
