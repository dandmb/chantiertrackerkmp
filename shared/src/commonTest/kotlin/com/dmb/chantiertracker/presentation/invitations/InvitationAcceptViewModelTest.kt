package com.dmb.chantiertracker.presentation.invitations

import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.User
import com.dmb.chantiertracker.support.FakeAuthRepository
import com.dmb.chantiertracker.domain.model.IncomingInvitation
import com.dmb.chantiertracker.domain.model.InvitationPreview
import com.dmb.chantiertracker.domain.model.InvitationStatus
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.support.FakeInvitationRepository
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
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class InvitationAcceptViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() = resetTestMainDispatcher()

    private val links = InvitationDeepLinkDispatcher()
    private val auth = FakeAuthRepository().apply {
        emitState(AuthState.Authenticated(User(1, "u@x.dev", "U", true, GlobalRole.USER)))
    }

    private fun vm(invitations: FakeInvitationRepository, projects: FakeProjectRepository = FakeProjectRepository()) =
        InvitationAcceptViewModel(invitations, projects, links, auth)

    private fun pending(invitedByName: String? = "Paul", role: ProjectRole? = ProjectRole.SUPERVISOR) = FakeInvitationRepository().apply {
        preview = InvitationPreview(7L, "Villa Vidal", InvitationStatus.PENDING, role, invitedByName)
    }

    private fun opened(token: String = "tok") = links.apply { dispatch(token); consume() }

    @Test
    fun opening_the_link_shows_the_project_the_inviter_and_the_role_from_the_link_without_accepting() = runTest {
        val invitations = pending().apply {
            incoming = listOf(IncomingInvitation(31L, 7L, "Autre", ProjectRole.ADMIN, "Quelqu un", null, null))
        }
        val projects = FakeProjectRepository()
        val vm = vm(invitations, projects)

        vm.load("tok")
        advanceUntilIdle()

        val ready = assertIs<InvitationAcceptStatus.Ready>(vm.status.value)
        assertEquals("Villa Vidal", ready.projectName)
        assertEquals("Paul", ready.invitedByName)
        assertEquals(ProjectRole.SUPERVISOR, ready.role)
        assertEquals(emptyList(), invitations.accepted)
        assertEquals(0, projects.refreshCount)
    }

    @Test
    fun a_deleted_inviter_leaves_no_inviter() = runTest {
        val vm = vm(pending(invitedByName = null))

        vm.load("tok")
        advanceUntilIdle()

        assertNull(assertIs<InvitationAcceptStatus.Ready>(vm.status.value).invitedByName)
    }

    @Test
    fun joining_with_another_account_says_so_and_keeps_the_link_for_the_right_account() = runTest {
        val invitations = pending().apply { acceptError = DomainException.InvitationForAnotherAccount }
        opened()
        val vm = vm(invitations)
        vm.load("tok")
        advanceUntilIdle()

        vm.join()
        advanceUntilIdle()

        assertEquals(InvitationAcceptStatus.WrongAccount, vm.status.value)
        auth.emitState(AuthState.Unauthenticated)
        links.reopenHeld()
        assertEquals("tok", links.pending.value)
    }

    @Test
    fun declining_with_another_account_says_so_too() = runTest {
        val invitations = pending().apply { declineError = DomainException.InvitationForAnotherAccount }
        val vm = vm(invitations)
        vm.load("tok")
        advanceUntilIdle()

        vm.decline()
        advanceUntilIdle()

        assertEquals(InvitationAcceptStatus.WrongAccount, vm.status.value)
    }

    @Test
    fun a_settled_invitation_is_no_longer_held() = runTest {
        opened()
        val vm = vm(pending())
        vm.load("tok")
        advanceUntilIdle()

        vm.join()
        advanceUntilIdle()
        links.reopenHeld()

        assertNull(links.pending.value)
    }

    @Test
    fun leaving_the_screen_while_signed_in_lets_the_link_go() = runTest {
        opened()
        val store = androidx.lifecycle.ViewModelStore()
        val vm = vm(pending())
        store.put("invitation", vm)
        vm.load("tok")
        advanceUntilIdle()

        store.clear()
        links.reopenHeld()

        assertNull(links.pending.value)
    }

    @Test
    fun a_session_ended_on_the_screen_keeps_the_link_for_the_next_sign_in() = runTest {
        opened()
        val store = androidx.lifecycle.ViewModelStore()
        val vm = vm(pending().apply { acceptError = DomainException.InvalidCredentials })
        store.put("invitation", vm)
        vm.load("tok")
        advanceUntilIdle()

        vm.join()
        advanceUntilIdle()
        auth.emitState(AuthState.Unauthenticated)
        store.clear()
        links.reopenHeld()

        assertEquals("tok", links.pending.value)
    }

    @Test
    fun joining_accepts_and_resolves_the_local_project_id() = runTest {
        val invitations = pending()
        val projects = FakeProjectRepository().apply { localIdByServerId = mapOf(7L to "local-1") }
        val vm = vm(invitations, projects)
        vm.load("tok")
        advanceUntilIdle()

        vm.join()
        advanceUntilIdle()

        assertEquals(listOf("tok"), invitations.accepted)
        assertEquals(1, projects.refreshCount)
        val status = assertIs<InvitationAcceptStatus.Accepted>(vm.status.value)
        assertEquals("Villa Vidal", status.projectName)
        assertEquals("local-1", status.projectLocalId)
    }

    @Test
    fun joined_but_not_yet_synced_locally_resolves_to_a_null_project_id() = runTest {
        val vm = vm(pending())
        vm.load("tok")
        advanceUntilIdle()

        vm.join()
        advanceUntilIdle()

        assertNull(assertIs<InvitationAcceptStatus.Accepted>(vm.status.value).projectLocalId)
    }

    @Test
    fun declining_refuses_by_the_link_and_never_accepts() = runTest {
        val invitations = pending()
        val vm = vm(invitations)
        vm.load("tok")
        advanceUntilIdle()

        vm.decline()
        advanceUntilIdle()

        assertEquals(listOf("tok"), invitations.declinedTokens)
        assertEquals(emptyList(), invitations.accepted)
        assertEquals(InvitationAcceptStatus.Declined, vm.status.value)
    }

    @Test
    fun declining_a_link_gone_meanwhile_is_flagged_invalid() = runTest {
        val invitations = pending().apply { declineError = DomainException.NotFound }
        val vm = vm(invitations)
        vm.load("tok")
        advanceUntilIdle()

        vm.decline()
        advanceUntilIdle()

        assertEquals(InvitationAcceptStatus.InvalidLink, vm.status.value)
    }

    @Test
    fun a_non_pending_invitation_is_flagged_invalid() = runTest {
        val invitations = FakeInvitationRepository().apply {
            preview = InvitationPreview(7L, "Villa Vidal", InvitationStatus.ACCEPTED, ProjectRole.SUPERVISOR, null)
        }
        val projects = FakeProjectRepository()
        val vm = vm(invitations, projects)

        vm.load("tok")
        advanceUntilIdle()

        assertEquals(InvitationAcceptStatus.InvalidLink, vm.status.value)
        assertEquals(emptyList(), invitations.accepted)
        assertEquals(0, projects.refreshCount)
    }

    @Test
    fun an_unknown_token_is_flagged_invalid_not_a_generic_error() = runTest {
        val invitations = FakeInvitationRepository().apply { previewError = DomainException.NotFound }
        val vm = vm(invitations)

        vm.load("tok")
        advanceUntilIdle()

        assertEquals(InvitationAcceptStatus.InvalidLink, vm.status.value)
    }

    @Test
    fun a_network_failure_fetching_the_preview_surfaces_as_a_generic_error() = runTest {
        val invitations = FakeInvitationRepository().apply { previewError = DomainException.Network }
        val vm = vm(invitations)

        vm.load("tok")
        advanceUntilIdle()

        assertEquals(InvitationAcceptStatus.Failed(DomainException.Network), vm.status.value)
    }

    @Test
    fun a_refused_join_surfaces_the_error_without_refreshing_projects() = runTest {
        val invitations = pending().apply { acceptError = DomainException.Forbidden }
        val projects = FakeProjectRepository()
        val vm = vm(invitations, projects)
        vm.load("tok")
        advanceUntilIdle()

        vm.join()
        advanceUntilIdle()

        assertEquals(InvitationAcceptStatus.Failed(DomainException.Forbidden), vm.status.value)
        assertEquals(0, projects.refreshCount)
    }

    @Test
    fun a_link_refused_at_acceptance_because_its_sender_lost_their_rights_is_invalid() = runTest {
        val invitations = pending().apply { acceptError = DomainException.NotFound }
        val vm = vm(invitations)
        vm.load("tok")
        advanceUntilIdle()

        vm.join()
        advanceUntilIdle()

        assertEquals(InvitationAcceptStatus.InvalidLink, vm.status.value)
    }

    @Test
    fun retry_shows_the_invitation_again_without_accepting() = runTest {
        val invitations = FakeInvitationRepository().apply { previewError = DomainException.Network }
        val vm = vm(invitations)
        vm.load("tok")
        advanceUntilIdle()
        assertEquals(InvitationAcceptStatus.Failed(DomainException.Network), vm.status.value)

        invitations.previewError = null
        invitations.preview = InvitationPreview(7L, "Villa Vidal", InvitationStatus.PENDING, ProjectRole.SUPERVISOR, null)
        vm.retry()
        advanceUntilIdle()

        assertIs<InvitationAcceptStatus.Ready>(vm.status.value)
        assertEquals(emptyList(), invitations.accepted)
    }
}
