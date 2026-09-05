package com.dmb.chantiertracker.presentation.projects

import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.IncomingInvitation
import com.dmb.chantiertracker.domain.model.Project
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.domain.model.ProjectSort
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.support.FakeInvitationRepository
import com.dmb.chantiertracker.support.FakeProjectRepository
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlinx.coroutines.CompletableDeferred
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
class ProjectsViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    private val older = Project("p1", "Ancien", null, "Nîmes", ProjectStatus.IN_PROGRESS, createdAt = "2026-01-01T09:00:00")
    private val newer = Project("p2", "Récent", null, null, ProjectStatus.SUSPENDED, createdAt = "2026-06-15T12:00:00")

    private fun vm(
        repo: FakeProjectRepository,
        holder: ProjectSortHolder = ProjectSortHolder(),
        invitations: FakeInvitationRepository = FakeInvitationRepository(),
    ) = ProjectsViewModel(repo, invitations, holder)

    private fun incoming(token: String, project: String = "Villa", inviter: String? = "Jean") =
        IncomingInvitation(token, 1L, project, ProjectRole.SUPERVISOR, inviter, "2026-09-01T10:00:00", null)

    @Test
    fun list_comes_from_the_local_store_newest_first_by_default() = runTest {
        val repo = FakeProjectRepository(projects = listOf(older, newer))
        val vm = vm(repo)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(listOf(newer, older), state.projects)
        assertEquals(ProjectSort.NEWEST_FIRST, vm.sort.value)
    }

    @Test
    fun changing_sort_reorders_the_same_local_data() = runTest {
        val holder = ProjectSortHolder()
        val repo = FakeProjectRepository(projects = listOf(older, newer))
        val vm = vm(repo, holder)
        advanceUntilIdle()
        assertEquals(listOf(newer, older), vm.state.value.projects)

        holder.set(ProjectSort.OLDEST_FIRST)
        advanceUntilIdle()

        assertEquals(listOf(older, newer), vm.state.value.projects)
        assertEquals(0, repo.refreshCount, "changer le tri ne relance aucune sync")
    }

    @Test
    fun a_new_project_appearing_in_the_store_shows_up_without_a_reload() = runTest {
        val repo = FakeProjectRepository(projects = listOf(older))
        val vm = vm(repo)
        advanceUntilIdle()
        assertEquals(listOf(older), vm.state.value.projects)

        repo.projectsFlow.value = listOf(older, newer)
        advanceUntilIdle()

        assertEquals(listOf(newer, older), vm.state.value.projects)
    }

    @Test
    fun empty_store_flags_the_empty_state() = runTest {
        val vm = vm(FakeProjectRepository(projects = emptyList()))
        advanceUntilIdle()

        assertTrue(vm.state.value.isEmpty)
    }

    @Test
    fun on_enter_triggers_a_background_refresh() = runTest {
        val repo = FakeProjectRepository(projects = listOf(older))
        val vm = vm(repo)
        advanceUntilIdle()

        vm.onEnter()
        advanceUntilIdle()

        assertEquals(1, repo.refreshCount)
    }

    @Test
    fun pull_to_refresh_triggers_a_sync_and_shows_the_indicator_until_it_settles() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeProjectRepository(projects = listOf(older, newer))
        repo.onRefresh = { gate.await() }
        val vm = vm(repo)
        advanceUntilIdle()
        assertFalse(vm.state.value.isRefreshing)

        vm.refresh()
        advanceUntilIdle()

        assertTrue(vm.state.value.isRefreshing, "l'indicateur reste pendant la synchro")
        assertEquals(1, repo.refreshCount, "le geste délègue à repo.refresh()")
        assertEquals(listOf(newer, older), vm.state.value.projects, "la liste locale reste affichée pendant l'opération")

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(vm.state.value.isRefreshing, "l'indicateur disparaît une fois la synchro terminée")
    }

    @Test
    fun incoming_invitations_appear_after_login_then_disappear_once_accepted() = runTest {
        // Scenario: existing account, normal sign-in — no tokenised link. The
        // banner must still surface the invitation, and accepting must clear it.
        val repo = FakeProjectRepository(projects = listOf(older))
        val invitations = FakeInvitationRepository().apply { incoming = listOf(incoming("tok-1", project = "Villa Vidal", inviter = "Jean")) }
        val vm = vm(repo, invitations = invitations)
        advanceUntilIdle()

        // First screen entry after login (ProjectsScreen fires onEnter on ON_RESUME).
        vm.onEnter()
        advanceUntilIdle()
        assertEquals(listOf("Villa Vidal"), vm.state.value.incomingInvitations.map { it.projectName })

        vm.acceptInvitation("tok-1")
        advanceUntilIdle()

        assertEquals(listOf("tok-1"), invitations.accepted, "POST /invitations/{token}/accept")
        assertTrue(vm.state.value.incomingInvitations.isEmpty(), "the banner is gone")
        assertTrue(vm.state.value.acceptingTokens.isEmpty())
        assertEquals(2, repo.refreshCount, "onEnter + the project-list re-pull after accepting")
    }

    @Test
    fun several_pending_invitations_are_all_shown() = runTest {
        val invitations = FakeInvitationRepository().apply {
            incoming = listOf(incoming("a", "Villa"), incoming("b", "Chalet"))
        }
        val vm = vm(FakeProjectRepository(projects = listOf(older)), invitations = invitations)
        advanceUntilIdle()
        vm.onEnter()
        advanceUntilIdle()

        assertEquals(listOf("Villa", "Chalet"), vm.state.value.incomingInvitations.map { it.projectName })
    }

    @Test
    fun a_failed_accept_surfaces_the_error_and_keeps_the_invitation() = runTest {
        val invitations = FakeInvitationRepository().apply {
            incoming = listOf(incoming("stale"))
            acceptError = DomainException.NotFound
        }
        val vm = vm(FakeProjectRepository(projects = listOf(older)), invitations = invitations)
        advanceUntilIdle()
        vm.onEnter()
        advanceUntilIdle()

        vm.acceptInvitation("stale")
        advanceUntilIdle()

        assertEquals(DomainException.NotFound, vm.state.value.invitationError)
        assertEquals(listOf("stale"), vm.state.value.incomingInvitations.map { it.token }, "still shown so the user isn't left confused")
        assertTrue(vm.state.value.acceptingTokens.isEmpty())
    }

    @Test
    fun a_failing_invitations_fetch_is_swallowed_and_does_not_break_the_list() = runTest {
        val invitations = FakeInvitationRepository().apply { listIncomingError = DomainException.Network }
        val vm = vm(FakeProjectRepository(projects = listOf(older, newer)), invitations = invitations)
        advanceUntilIdle()
        vm.onEnter()
        advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        assertEquals(listOf(newer, older), vm.state.value.projects)
        assertTrue(vm.state.value.incomingInvitations.isEmpty())
    }

    @Test
    fun pull_to_refresh_clears_the_indicator_when_the_sync_is_a_no_op() = runTest {
        // Hors ligne, syncNow() renvoie Skipped presque instantanément : l'indicateur doit se fermer.
        val repo = FakeProjectRepository(projects = listOf(older))
        val vm = vm(repo)
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        assertFalse(vm.state.value.isRefreshing)
        assertEquals(listOf(older), vm.state.value.projects)
        assertEquals(1, repo.refreshCount)
    }
}
