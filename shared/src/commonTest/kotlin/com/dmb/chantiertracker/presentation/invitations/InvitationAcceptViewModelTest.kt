package com.dmb.chantiertracker.presentation.invitations

import com.dmb.chantiertracker.domain.model.DomainException
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

    private fun pending() = FakeInvitationRepository().apply {
        preview = InvitationPreview(projectId = 7L, projectName = "Villa Vidal", status = InvitationStatus.PENDING)
    }

    private val mine = IncomingInvitation(
        id = 31L, projectId = 7L, projectName = "Villa Vidal", role = ProjectRole.SUPERVISOR,
        invitedByName = "Paul", createdAt = null, expiresAt = null,
    )

    @Test
    fun opening_the_link_shows_the_invitation_without_accepting_it() = runTest {
        val invitations = pending().apply { incoming = listOf(mine) }
        val projects = FakeProjectRepository()
        val vm = InvitationAcceptViewModel(invitations, projects)

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
    fun the_inviter_and_role_stay_unknown_when_the_invitation_is_not_in_my_list() = runTest {
        val vm = InvitationAcceptViewModel(pending(), FakeProjectRepository())

        vm.load("tok")
        advanceUntilIdle()

        val ready = assertIs<InvitationAcceptStatus.Ready>(vm.status.value)
        assertNull(ready.invitedByName)
        assertNull(ready.role)
    }

    @Test
    fun my_list_failing_still_shows_the_invitation() = runTest {
        val invitations = pending().apply { listIncomingError = DomainException.Network }
        val vm = InvitationAcceptViewModel(invitations, FakeProjectRepository())

        vm.load("tok")
        advanceUntilIdle()

        assertIs<InvitationAcceptStatus.Ready>(vm.status.value)
    }

    @Test
    fun joining_accepts_and_resolves_the_local_project_id() = runTest {
        val invitations = pending()
        val projects = FakeProjectRepository().apply { localIdByServerId = mapOf(7L to "local-1") }
        val vm = InvitationAcceptViewModel(invitations, projects)
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
        val vm = InvitationAcceptViewModel(pending(), FakeProjectRepository())
        vm.load("tok")
        advanceUntilIdle()

        vm.join()
        advanceUntilIdle()

        assertNull(assertIs<InvitationAcceptStatus.Accepted>(vm.status.value).projectLocalId)
    }

    @Test
    fun declining_refuses_by_the_link_and_never_accepts() = runTest {
        val invitations = pending()
        val vm = InvitationAcceptViewModel(invitations, FakeProjectRepository())
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
        val vm = InvitationAcceptViewModel(invitations, FakeProjectRepository())
        vm.load("tok")
        advanceUntilIdle()

        vm.decline()
        advanceUntilIdle()

        assertEquals(InvitationAcceptStatus.InvalidLink, vm.status.value)
    }

    @Test
    fun a_non_pending_invitation_is_flagged_invalid() = runTest {
        val invitations = FakeInvitationRepository().apply {
            preview = InvitationPreview(projectId = 7L, projectName = "Villa Vidal", status = InvitationStatus.ACCEPTED)
        }
        val projects = FakeProjectRepository()
        val vm = InvitationAcceptViewModel(invitations, projects)

        vm.load("tok")
        advanceUntilIdle()

        assertEquals(InvitationAcceptStatus.InvalidLink, vm.status.value)
        assertEquals(emptyList(), invitations.accepted)
        assertEquals(0, projects.refreshCount)
    }

    @Test
    fun an_unknown_token_is_flagged_invalid_not_a_generic_error() = runTest {
        val invitations = FakeInvitationRepository().apply { previewError = DomainException.NotFound }
        val vm = InvitationAcceptViewModel(invitations, FakeProjectRepository())

        vm.load("tok")
        advanceUntilIdle()

        assertEquals(InvitationAcceptStatus.InvalidLink, vm.status.value)
    }

    @Test
    fun a_network_failure_fetching_the_preview_surfaces_as_a_generic_error() = runTest {
        val invitations = FakeInvitationRepository().apply { previewError = DomainException.Network }
        val vm = InvitationAcceptViewModel(invitations, FakeProjectRepository())

        vm.load("tok")
        advanceUntilIdle()

        assertEquals(InvitationAcceptStatus.Failed(DomainException.Network), vm.status.value)
    }

    @Test
    fun a_refused_join_surfaces_the_error_without_refreshing_projects() = runTest {
        val invitations = pending().apply { acceptError = DomainException.Forbidden }
        val projects = FakeProjectRepository()
        val vm = InvitationAcceptViewModel(invitations, projects)
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
        val vm = InvitationAcceptViewModel(invitations, FakeProjectRepository())
        vm.load("tok")
        advanceUntilIdle()

        vm.join()
        advanceUntilIdle()

        assertEquals(InvitationAcceptStatus.InvalidLink, vm.status.value)
    }

    @Test
    fun retry_shows_the_invitation_again_without_accepting() = runTest {
        val invitations = FakeInvitationRepository().apply { previewError = DomainException.Network }
        val vm = InvitationAcceptViewModel(invitations, FakeProjectRepository())
        vm.load("tok")
        advanceUntilIdle()
        assertEquals(InvitationAcceptStatus.Failed(DomainException.Network), vm.status.value)

        invitations.previewError = null
        invitations.preview = InvitationPreview(projectId = 7L, projectName = "Villa Vidal", status = InvitationStatus.PENDING)
        vm.retry()
        advanceUntilIdle()

        assertIs<InvitationAcceptStatus.Ready>(vm.status.value)
        assertEquals(emptyList(), invitations.accepted)
    }
}
