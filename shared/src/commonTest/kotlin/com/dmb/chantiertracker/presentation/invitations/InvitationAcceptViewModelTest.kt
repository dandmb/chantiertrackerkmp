package com.dmb.chantiertracker.presentation.invitations

import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.InvitationPreview
import com.dmb.chantiertracker.domain.model.InvitationStatus
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

    @Test
    fun accepts_a_pending_invitation_and_resolves_the_local_project_id() = runTest {
        val invitations = FakeInvitationRepository().apply {
            preview = InvitationPreview(projectId = 7L, projectName = "Villa Vidal", status = InvitationStatus.PENDING)
        }
        val projects = FakeProjectRepository().apply { localIdByServerId = mapOf(7L to "local-1") }
        val vm = InvitationAcceptViewModel(invitations, projects)

        vm.load("tok")
        advanceUntilIdle()

        assertEquals(listOf("tok"), invitations.accepted)
        assertEquals(1, projects.refreshCount, "the project list is re-pulled so the newly joined project can be resolved locally")
        val status = assertIs<InvitationAcceptStatus.Accepted>(vm.status.value)
        assertEquals("Villa Vidal", status.projectName)
        assertEquals("local-1", status.projectLocalId)
    }

    @Test
    fun accepted_but_not_yet_synced_locally_resolves_to_a_null_project_id() = runTest {
        val invitations = FakeInvitationRepository().apply {
            preview = InvitationPreview(projectId = 7L, projectName = "Villa Vidal", status = InvitationStatus.PENDING)
        }
        val projects = FakeProjectRepository() // localIdByServerId stays empty
        val vm = InvitationAcceptViewModel(invitations, projects)

        vm.load("tok")
        advanceUntilIdle()

        val status = assertIs<InvitationAcceptStatus.Accepted>(vm.status.value)
        assertNull(status.projectLocalId)
    }

    @Test
    fun a_non_pending_invitation_is_flagged_invalid_without_calling_accept() = runTest {
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
    fun a_failed_accept_call_surfaces_as_an_error_without_refreshing_projects() = runTest {
        val invitations = FakeInvitationRepository().apply {
            preview = InvitationPreview(projectId = 7L, projectName = "Villa Vidal", status = InvitationStatus.PENDING)
            acceptError = DomainException.Forbidden
        }
        val projects = FakeProjectRepository()
        val vm = InvitationAcceptViewModel(invitations, projects)

        vm.load("tok")
        advanceUntilIdle()

        assertEquals(InvitationAcceptStatus.Failed(DomainException.Forbidden), vm.status.value)
        assertEquals(0, projects.refreshCount)
    }

    @Test
    fun retry_reruns_the_whole_flow() = runTest {
        val invitations = FakeInvitationRepository().apply { previewError = DomainException.Network }
        val projects = FakeProjectRepository()
        val vm = InvitationAcceptViewModel(invitations, projects)
        vm.load("tok")
        advanceUntilIdle()
        assertEquals(InvitationAcceptStatus.Failed(DomainException.Network), vm.status.value)

        invitations.previewError = null
        invitations.preview = InvitationPreview(projectId = 7L, projectName = "Villa Vidal", status = InvitationStatus.PENDING)
        vm.retry()
        advanceUntilIdle()

        assertIs<InvitationAcceptStatus.Accepted>(vm.status.value)
    }
}
