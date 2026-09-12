package com.dmb.chantiertracker.presentation.admin

import com.dmb.chantiertracker.domain.model.AdminUser
import com.dmb.chantiertracker.domain.model.AdminUserPage
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.support.FakeAdminRepository
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
class AdminUsersViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    private fun user(id: Long) = AdminUser(
        id = id, email = "user$id@chantier.dev", name = "User $id", active = true,
        globalRole = GlobalRole.USER, projectCount = 1, createdAt = "2026-09-0${id}T09:00:00",
        plan = Plan.FREE, planSource = null, planExpiresAt = null,
    )

    private fun page(index: Int, total: Int, items: List<AdminUser>) = AdminUserPage(
        items = items, page = index, totalPages = total,
        isFirst = index == 0, isLast = index == total - 1, totalElements = total * 20,
    )

    @Test
    fun the_first_page_loads_on_construction() = runTest {
        val repo = FakeAdminRepository(listOf(page(0, 1, listOf(user(1), user(2)))))

        val vm = AdminUsersViewModel(repo)
        advanceUntilIdle()

        assertEquals(listOf(0), repo.calls)
        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(listOf(1L, 2L), state.items.map { it.id })
        assertTrue(state.isFirst && state.isLast)
        assertFalse(state.showPagination)
    }

    @Test
    fun next_and_previous_move_through_pages_and_stop_at_the_ends() = runTest {
        val repo = FakeAdminRepository(listOf(page(0, 2, listOf(user(1))), page(1, 2, listOf(user(2)))))
        val vm = AdminUsersViewModel(repo)
        advanceUntilIdle()

        vm.previousPage() // already first — no-op
        advanceUntilIdle()
        assertEquals(1, repo.calls.size)

        vm.nextPage()
        advanceUntilIdle()
        assertEquals(1, vm.state.value.page)
        assertTrue(vm.state.value.isLast)

        vm.nextPage() // already last — no-op
        advanceUntilIdle()
        assertEquals(2, repo.calls.size)
        assertEquals(listOf(0, 1), repo.calls)
    }

    @Test
    fun an_error_is_surfaced_and_retry_refetches_the_same_page() = runTest {
        val repo = FakeAdminRepository(listOf(page(0, 2, listOf(user(1))), page(1, 2, listOf(user(2)))))
        val vm = AdminUsersViewModel(repo)
        advanceUntilIdle()
        vm.nextPage()
        advanceUntilIdle()

        repo.error = DomainException.Network
        vm.retry()
        advanceUntilIdle()
        assertEquals(DomainException.Network, vm.state.value.error)
        assertEquals(1, repo.calls.last(), "retry re-requests the current page, not page 0")

        repo.error = null
        vm.retry()
        advanceUntilIdle()
        assertNull(vm.state.value.error)
        assertEquals(listOf(2L), vm.state.value.items.map { it.id })
    }

    @Test
    fun a_403_is_surfaced_as_forbidden() = runTest {
        val repo = FakeAdminRepository().apply { error = DomainException.Forbidden }

        val vm = AdminUsersViewModel(repo)
        advanceUntilIdle()

        assertEquals(DomainException.Forbidden, vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }
}
