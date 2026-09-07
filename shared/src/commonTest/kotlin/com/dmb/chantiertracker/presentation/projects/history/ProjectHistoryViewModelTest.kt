package com.dmb.chantiertracker.presentation.projects.history

import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.HistoryActionType
import com.dmb.chantiertracker.domain.model.HistoryPage
import com.dmb.chantiertracker.domain.model.HistorySort
import com.dmb.chantiertracker.domain.model.ModificationHistoryItem
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.support.FakeHistoryRepository
import com.dmb.chantiertracker.support.FakeProjectRepository
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
class ProjectHistoryViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    private fun item(id: Long, description: String? = "Dan a créé le projet") = ModificationHistoryItem(
        id = id, modifiedAt = "2026-09-0${id}T09:00:00", actionType = HistoryActionType.CREATION,
        description = description, entryId = null, userId = 1L, fieldName = null, oldValue = null, newValue = null,
    )

    private fun page(index: Int, total: Int, items: List<ModificationHistoryItem>) = HistoryPage(
        items = items, page = index, totalPages = total,
        isFirst = index == 0, isLast = index == total - 1, totalElements = total * 20,
    )

    private fun projectRepo(ownerPlan: Plan? = null) = FakeProjectRepository(
        detail = ProjectDetail(
            localId = "p1", name = "Villa", description = null, location = null,
            currency = "EUR", timezone = "Europe/Paris", status = ProjectStatus.IN_PROGRESS,
            ownerId = 1L, ownerPlan = ownerPlan,
        ),
    )

    @Test
    fun opening_the_screen_loads_the_first_page_newest_first() = runTest {
        val history = FakeHistoryRepository(listOf(page(0, 1, listOf(item(1), item(2)))))
        val vm = ProjectHistoryViewModel(history, projectRepo())

        vm.load("p1")
        advanceUntilIdle()

        assertEquals(Triple("p1", 0, HistorySort.NEWEST_FIRST), history.calls.single())
        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(listOf(1L, 2L), state.items.map { it.id })
        assertEquals(0, state.page)
        assertTrue(state.isFirst && state.isLast)
        assertFalse(state.showPagination)
    }

    @Test
    fun it_surfaces_the_owner_plan_for_the_retention_notice() = runTest {
        val history = FakeHistoryRepository(listOf(page(0, 1, listOf(item(1)))))
        val vm = ProjectHistoryViewModel(history, projectRepo(ownerPlan = Plan.FREE))

        vm.load("p1")
        advanceUntilIdle()

        assertEquals(Plan.FREE, vm.state.value.ownerPlan)
    }

    @Test
    fun changing_the_sort_refetches_from_page_zero() = runTest {
        val history = FakeHistoryRepository(listOf(page(0, 3, listOf(item(1))), page(1, 3, listOf(item(2))), page(2, 3, listOf(item(3)))))
        val vm = ProjectHistoryViewModel(history, projectRepo())
        vm.load("p1")
        advanceUntilIdle()
        vm.nextPage()
        advanceUntilIdle()
        assertEquals(1, vm.state.value.page)

        vm.setSort(HistorySort.BY_ACTION)
        advanceUntilIdle()

        assertEquals(Triple("p1", 0, HistorySort.BY_ACTION), history.calls.last())
        assertEquals(0, vm.state.value.page)
        assertEquals(HistorySort.BY_ACTION, vm.state.value.sort)
    }

    @Test
    fun next_and_previous_move_through_pages_and_stop_at_the_ends() = runTest {
        val history = FakeHistoryRepository(listOf(page(0, 2, listOf(item(1))), page(1, 2, listOf(item(2)))))
        val vm = ProjectHistoryViewModel(history, projectRepo())
        vm.load("p1")
        advanceUntilIdle()

        vm.previousPage() // already first — no-op
        advanceUntilIdle()
        assertEquals(1, history.calls.size)

        vm.nextPage()
        advanceUntilIdle()
        assertEquals(1, vm.state.value.page)
        assertTrue(vm.state.value.isLast)

        vm.nextPage() // already last — no-op
        advanceUntilIdle()
        assertEquals(2, history.calls.size)
        assertEquals(listOf(0, 1), history.calls.map { it.second })
    }

    @Test
    fun an_error_is_surfaced_and_retry_refetches_the_same_page() = runTest {
        val history = FakeHistoryRepository(listOf(page(0, 2, listOf(item(1))), page(1, 2, listOf(item(2)))))
        val vm = ProjectHistoryViewModel(history, projectRepo())
        vm.load("p1")
        advanceUntilIdle()
        vm.nextPage()
        advanceUntilIdle()

        history.error = DomainException.Network
        vm.retry()
        advanceUntilIdle()
        assertEquals(DomainException.Network, vm.state.value.error)
        assertEquals(1, history.calls.last().second, "retry re-requests the current page, not page 0")

        history.error = null
        vm.retry()
        advanceUntilIdle()
        assertNull(vm.state.value.error)
        assertEquals(listOf(2L), vm.state.value.items.map { it.id })
    }

    @Test
    fun a_403_is_surfaced_as_forbidden() = runTest {
        val history = FakeHistoryRepository().apply { error = DomainException.Forbidden }
        val vm = ProjectHistoryViewModel(history, projectRepo())

        vm.load("p1")
        advanceUntilIdle()

        assertEquals(DomainException.Forbidden, vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }
}
