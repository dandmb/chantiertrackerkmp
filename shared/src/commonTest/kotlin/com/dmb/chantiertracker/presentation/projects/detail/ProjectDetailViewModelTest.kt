package com.dmb.chantiertracker.presentation.projects.detail

import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectMember
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.User
import com.dmb.chantiertracker.support.FakeAuthRepository
import com.dmb.chantiertracker.support.FakeProjectRepository
import com.dmb.chantiertracker.support.FakeStageRepository
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
class ProjectDetailViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    private fun detail(ownerId: Long?) = ProjectDetail(
        localId = "p5",
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
    fun observes_detail_from_the_local_store() = runTest {
        val repo = FakeProjectRepository(detail = detail(ownerId = 1))
        val vm = ProjectDetailViewModel(repo, FakeStageRepository(), auth(userId = 1))
        vm.load("p5")
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals("Villa Vidal", state.detail?.name)
        assertEquals("EUR", state.detail?.currency)
        assertEquals(1, repo.refreshProjectCount, "opening the screen kicks a background pull of this project + members")
        assertEquals(listOf("refreshProject:p5"), repo.log)
    }

    @Test
    fun retry_pulls_this_project_again() = runTest {
        val repo = FakeProjectRepository(detail = null)
        val vm = ProjectDetailViewModel(repo, FakeStageRepository(), auth(userId = 1))
        vm.load("p5")
        advanceUntilIdle()

        vm.retry()
        advanceUntilIdle()

        assertEquals(2, repo.refreshProjectCount)
    }

    @Test
    fun owner_can_edit_without_consulting_members() = runTest {
        val repo = FakeProjectRepository(detail = detail(ownerId = 7))
        val vm = ProjectDetailViewModel(repo, FakeStageRepository(), auth(userId = 7))
        vm.load("p5")
        advanceUntilIdle()

        assertTrue(vm.state.value.canEdit)
    }

    @Test
    fun non_owner_admin_member_can_edit() = runTest {
        val repo = FakeProjectRepository(
            detail = detail(ownerId = 7),
            members = listOf(ProjectMember(userId = 9, name = "Anna", email = "a@x.dev", role = ProjectRole.ADMIN)),
        )
        val vm = ProjectDetailViewModel(repo, FakeStageRepository(), auth(userId = 9))
        vm.load("p5")
        advanceUntilIdle()

        assertTrue(vm.state.value.canEdit)
    }

    @Test
    fun supervisor_member_cannot_edit() = runTest {
        val repo = FakeProjectRepository(
            detail = detail(ownerId = 7),
            members = listOf(ProjectMember(userId = 9, name = "Anna", email = "a@x.dev", role = ProjectRole.SUPERVISOR)),
        )
        val vm = ProjectDetailViewModel(repo, FakeStageRepository(), auth(userId = 9))
        vm.load("p5")
        advanceUntilIdle()

        assertFalse(vm.state.value.canEdit)
    }

    @Test
    fun a_missing_project_is_flagged_once_loading_settles() = runTest {
        val repo = FakeProjectRepository(detail = null)
        val vm = ProjectDetailViewModel(repo, FakeStageRepository(), auth(userId = 1))
        vm.load("gone")
        advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        assertTrue(vm.state.value.isMissing)
    }

    @Test
    fun the_project_reappearing_in_the_store_clears_the_missing_state() = runTest {
        val repo = FakeProjectRepository(detail = null)
        val vm = ProjectDetailViewModel(repo, FakeStageRepository(), auth(userId = 1))
        vm.load("p5")
        advanceUntilIdle()
        assertTrue(vm.state.value.isMissing)

        repo.detailFlow.value = detail(ownerId = 1)
        advanceUntilIdle()

        assertFalse(vm.state.value.isMissing)
        assertEquals("Villa Vidal", vm.state.value.detail?.name)
    }
}
