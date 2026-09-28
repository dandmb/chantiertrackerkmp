package com.dmb.chantiertracker.presentation.admin

import com.dmb.chantiertracker.domain.model.AdminUser
import com.dmb.chantiertracker.domain.model.AdminUserPage
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.PlanSource
import com.dmb.chantiertracker.domain.model.User
import com.dmb.chantiertracker.support.FakeAdminRepository
import com.dmb.chantiertracker.support.FakeAuthRepository
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

    private fun user(id: Long, active: Boolean = true) = AdminUser(
        id = id, email = "user$id@chantier.dev", name = "User $id", active = active,
        globalRole = GlobalRole.USER, projectCount = 1, createdAt = "2026-09-0${id}T09:00:00",
        plan = Plan.FREE, planSource = null, planExpiresAt = null,
    )

    private fun page(index: Int, total: Int, items: List<AdminUser>) = AdminUserPage(
        items = items, page = index, totalPages = total,
        isFirst = index == 0, isLast = index == total - 1, totalElements = total * 20,
    )

    private fun authAs(id: Long) = FakeAuthRepository().apply {
        emitState(AuthState.Authenticated(User(id, "admin$id@chantier.dev", "Admin $id", true, GlobalRole.SUPER_ADMIN)))
    }

    @Test
    fun the_first_page_loads_on_load() = runTest {
        val repo = FakeAdminRepository(listOf(page(0, 1, listOf(user(1), user(2)))))

        val vm = AdminUsersViewModel(repo, authAs(99))
        vm.load()
        advanceUntilIdle()

        assertEquals(listOf(0), repo.calls)
        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(listOf(1L, 2L), state.items.map { it.id })
        assertTrue(state.isFirst && state.isLast)
        assertFalse(state.showPagination)
        assertEquals(99L, state.currentUserId)
    }

    @Test
    fun next_and_previous_move_through_pages_and_stop_at_the_ends() = runTest {
        val repo = FakeAdminRepository(listOf(page(0, 2, listOf(user(1))), page(1, 2, listOf(user(2)))))
        val vm = AdminUsersViewModel(repo, authAs(99))
        vm.load()
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
        val vm = AdminUsersViewModel(repo, authAs(99))
        vm.load()
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

        val vm = AdminUsersViewModel(repo, authAs(99))
        vm.load()
        advanceUntilIdle()

        assertEquals(DomainException.Forbidden, vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun renaming_updates_the_row_in_place_from_the_server_response() = runTest {
        val repo = FakeAdminRepository(listOf(page(0, 1, listOf(user(1)))))
        val vm = AdminUsersViewModel(repo, authAs(99))
        vm.load()
        advanceUntilIdle()

        vm.updateName(1, "Renamed")
        advanceUntilIdle()

        assertEquals(listOf(1L to "Renamed"), repo.updateNameCalls)
        assertEquals("Renamed", vm.state.value.items.single { it.id == 1L }.name)
        assertTrue(vm.state.value.processingIds.isEmpty())
    }

    @Test
    fun a_rename_failure_surfaces_as_an_action_error_and_leaves_the_row_untouched() = runTest {
        val repo = FakeAdminRepository(listOf(page(0, 1, listOf(user(1))))).apply {
            updateNameError = DomainException.Network
        }
        val vm = AdminUsersViewModel(repo, authAs(99))
        vm.load()
        advanceUntilIdle()

        vm.updateName(1, "Renamed")
        advanceUntilIdle()

        assertEquals(DomainException.Network, vm.state.value.actionError)
        assertEquals("User 1", vm.state.value.items.single { it.id == 1L }.name)
    }

    @Test
    fun resetting_a_password_surfaces_a_confirmation_message() = runTest {
        val repo = FakeAdminRepository(listOf(page(0, 1, listOf(user(1)))))
        val vm = AdminUsersViewModel(repo, authAs(99))
        vm.load()
        advanceUntilIdle()

        vm.resetPassword(1, "user1@chantier.dev")
        advanceUntilIdle()

        assertEquals(listOf(1L), repo.resetPasswordCalls)
        assertEquals(
            AdminUserActionMessage.PasswordResetSent("user1@chantier.dev"),
            vm.state.value.actionMessage,
        )
    }

    @Test
    fun resending_activation_surfaces_a_confirmation_message() = runTest {
        val repo = FakeAdminRepository(listOf(page(0, 1, listOf(user(1, active = false)))))
        val vm = AdminUsersViewModel(repo, authAs(99))
        vm.load()
        advanceUntilIdle()

        vm.resendActivation(1, "user1@chantier.dev")
        advanceUntilIdle()

        assertEquals(listOf(1L), repo.resendActivationCalls)
        assertEquals(
            AdminUserActionMessage.ActivationResent("user1@chantier.dev"),
            vm.state.value.actionMessage,
        )
    }

    @Test
    fun updating_the_plan_updates_the_row_in_place_from_the_server_response() = runTest {
        val repo = FakeAdminRepository(listOf(page(0, 1, listOf(user(1)))))
        val vm = AdminUsersViewModel(repo, authAs(99))
        vm.load()
        advanceUntilIdle()

        vm.updatePlan(1, Plan.LIBERTE, "2026-12-31T23:59:59")
        advanceUntilIdle()

        assertEquals(listOf(Triple<Long, Plan, String?>(1L, Plan.LIBERTE, "2026-12-31T23:59:59")), repo.updatePlanCalls)
        val updated = vm.state.value.items.single { it.id == 1L }
        assertEquals(Plan.LIBERTE, updated.plan)
        assertEquals(PlanSource.ADMIN_GRANTED, updated.planSource)
        assertEquals("2026-12-31T23:59:59", updated.planExpiresAt)
        assertTrue(vm.state.value.processingIds.isEmpty())
    }

    @Test
    fun a_plan_update_failure_surfaces_as_an_action_error_and_leaves_the_row_untouched() = runTest {
        val repo = FakeAdminRepository(listOf(page(0, 1, listOf(user(1))))).apply {
            updatePlanError = DomainException.Unexpected
        }
        val vm = AdminUsersViewModel(repo, authAs(99))
        vm.load()
        advanceUntilIdle()

        vm.updatePlan(1, Plan.LIBERTE, null)
        advanceUntilIdle()

        assertEquals(DomainException.Unexpected, vm.state.value.actionError)
        assertEquals(Plan.FREE, vm.state.value.items.single { it.id == 1L }.plan)
    }

    @Test
    fun deleting_a_user_removes_the_row_from_the_list() = runTest {
        val repo = FakeAdminRepository(listOf(page(0, 1, listOf(user(1), user(2)))))
        val vm = AdminUsersViewModel(repo, authAs(99))
        vm.load()
        advanceUntilIdle()

        vm.deleteUser(1)
        advanceUntilIdle()

        assertEquals(listOf(1L), repo.deleteCalls)
        assertEquals(listOf(2L), vm.state.value.items.map { it.id })
    }

    @Test
    fun deleting_the_last_row_of_a_later_page_steps_back_a_page() = runTest {
        val repo = FakeAdminRepository(listOf(page(0, 2, listOf(user(1))), page(1, 2, listOf(user(2)))))
        val vm = AdminUsersViewModel(repo, authAs(99))
        vm.load()
        advanceUntilIdle()
        vm.nextPage()
        advanceUntilIdle()
        assertEquals(1, vm.state.value.page)

        vm.deleteUser(2)
        advanceUntilIdle()

        assertEquals(listOf(2L), repo.deleteCalls)
        assertEquals(0, vm.state.value.page)
        assertEquals(listOf(1L), vm.state.value.items.map { it.id })
    }

    @Test
    fun deleting_the_current_users_own_account_is_refused_client_side() = runTest {
        val repo = FakeAdminRepository(listOf(page(0, 1, listOf(user(99)))))
        val vm = AdminUsersViewModel(repo, authAs(99))
        vm.load()
        advanceUntilIdle()

        vm.deleteUser(99)
        advanceUntilIdle()

        assertTrue(repo.deleteCalls.isEmpty(), "no server-side guard exists — the client must never even attempt this")
        assertEquals(listOf(99L), vm.state.value.items.map { it.id })
    }
}
