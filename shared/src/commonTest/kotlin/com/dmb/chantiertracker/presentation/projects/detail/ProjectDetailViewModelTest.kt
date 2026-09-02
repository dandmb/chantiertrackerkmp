package com.dmb.chantiertracker.presentation.projects.detail

import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectMember
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.User
import com.dmb.chantiertracker.support.FakeAuthRepository
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectDetailViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    private fun detail(ownerId: Long) = ProjectDetail(
        id = 5,
        name = "Villa Vidal",
        description = "Grande villa",
        location = "Nîmes",
        currency = "EUR",
        timezone = "Europe/Paris",
        status = ProjectStatus.IN_PROGRESS,
        ownerId = ownerId,
    )

    private fun auth(userId: Long) = FakeAuthRepository().apply {
        emitState(AuthState.Authenticated(User(userId, "u@x.dev", "U", true, GlobalRole.USER)))
    }

    @Test
    fun loads_detail() = runTest {
        val repo = FakeProjectRepository(detail = detail(ownerId = 1))
        val vm = ProjectDetailViewModel(repo, auth(userId = 1))
        vm.load(5)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals("Villa Vidal", state.detail?.name)
        assertEquals("EUR", state.detail?.currency)
    }

    @Test
    fun owner_can_edit_without_a_members_call() = runTest {
        val repo = FakeProjectRepository(detail = detail(ownerId = 7))
        val vm = ProjectDetailViewModel(repo, auth(userId = 7))
        vm.load(5)
        advanceUntilIdle()

        assertTrue(vm.state.value.canEdit)
        assertTrue(repo.log.none { it.startsWith("getMembers") })
    }

    @Test
    fun non_owner_admin_member_can_edit() = runTest {
        val repo = FakeProjectRepository(
            detail = detail(ownerId = 7),
            members = listOf(ProjectMember(userId = 9, name = "Anna", email = "a@x.dev", role = ProjectRole.ADMIN)),
        )
        val vm = ProjectDetailViewModel(repo, auth(userId = 9))
        vm.load(5)
        advanceUntilIdle()

        assertTrue(vm.state.value.canEdit)
    }

    @Test
    fun supervisor_member_cannot_edit() = runTest {
        val repo = FakeProjectRepository(
            detail = detail(ownerId = 7),
            members = listOf(ProjectMember(userId = 9, name = "Anna", email = "a@x.dev", role = ProjectRole.SUPERVISOR)),
        )
        val vm = ProjectDetailViewModel(repo, auth(userId = 9))
        vm.load(5)
        advanceUntilIdle()

        assertFalse(vm.state.value.canEdit)
    }

    @Test
    fun not_found_surfaces_error() = runTest {
        val repo = FakeProjectRepository(error = DomainException.NotFound)
        val vm = ProjectDetailViewModel(repo, auth(userId = 1))
        vm.load(404)
        advanceUntilIdle()

        assertIs<DomainException.NotFound>(vm.state.value.error)
    }
}
