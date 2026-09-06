package com.dmb.chantiertracker.presentation.projects.invite

import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.Invitation
import com.dmb.chantiertracker.domain.model.InvitationStatus
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectMember
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.domain.model.ProjectStatus
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InviteMemberViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    private fun detail(ownerPlan: Plan?) = ProjectDetail(
        localId = "p1",
        name = "Villa",
        description = null,
        location = null,
        currency = "EUR",
        timezone = "Europe/Paris",
        status = ProjectStatus.IN_PROGRESS,
        ownerId = 1L,
        ownerPlan = ownerPlan,
    )

    private fun supervisor(id: Long) = ProjectMember(id, "S$id", "s$id@x.dev", ProjectRole.SUPERVISOR)

    private fun vm(
        projects: FakeProjectRepository,
        invitations: FakeInvitationRepository = FakeInvitationRepository(),
    ) = InviteMemberViewModel(projects, invitations)

    @Test
    fun opening_the_screen_pulls_the_project_for_a_fresh_owner_plan() = runTest {
        val projects = FakeProjectRepository(detail = detail(Plan.FREE))
        vm(projects).load("p1")
        advanceUntilIdle()

        assertEquals(1, projects.refreshProjectCount)
    }

    @Test
    fun a_blank_or_malformed_email_blocks_submit_without_a_network_call() = runTest {
        val projects = FakeProjectRepository(detail = detail(Plan.SEMI_FLEX))
        val invitations = FakeInvitationRepository()
        val model = vm(projects, invitations)
        model.load("p1")
        advanceUntilIdle()

        model.submit()
        advanceUntilIdle()
        assertTrue(model.state.value.emailError != null)

        model.onEmailChange("not-an-email")
        model.submit()
        advanceUntilIdle()
        assertTrue(model.state.value.emailError != null)
        assertTrue(invitations.invited.isEmpty())
    }

    @Test
    fun a_valid_email_sends_the_invitation_and_flags_done() = runTest {
        val projects = FakeProjectRepository(detail = detail(Plan.SEMI_FLEX))
        val invitations = FakeInvitationRepository()
        val model = vm(projects, invitations)
        model.load("p1")
        advanceUntilIdle()

        model.onEmailChange("  sam@x.dev  ")
        model.submit()
        advanceUntilIdle()

        assertEquals(listOf("p1" to "sam@x.dev"), invitations.invited, "email is trimmed")
        assertTrue(model.state.value.invited)
    }

    @Test
    fun the_supervisor_limit_blocks_submit_before_any_write() = runTest {
        // FREE = 1 supervisor/project; one is already a member → limit reached.
        val projects = FakeProjectRepository(detail = detail(Plan.FREE), members = listOf(supervisor(9)))
        val invitations = FakeInvitationRepository()
        val model = vm(projects, invitations)
        model.load("p1")
        advanceUntilIdle()

        assertTrue(model.state.value.atSupervisorLimit)

        model.onEmailChange("sam@x.dev")
        model.submit()
        advanceUntilIdle()

        assertTrue(invitations.invited.isEmpty(), "at the limit → never calls the repository")
        assertFalse(model.state.value.invited)
    }

    @Test
    fun a_pending_supervisor_invitation_counts_toward_the_limit() = runTest {
        val pending = Invitation(1, "p1", "lea@x.dev", ProjectRole.SUPERVISOR, 1L, "2026-09-01T10:00:00", null, InvitationStatus.PENDING)
        val projects = FakeProjectRepository(detail = detail(Plan.FREE))
        val model = vm(projects, FakeInvitationRepository(listOf(pending)))
        model.load("p1")
        advanceUntilIdle()

        assertTrue(model.state.value.atSupervisorLimit)
    }

    @Test
    fun a_server_refusal_is_surfaced() = runTest {
        val projects = FakeProjectRepository(detail = detail(Plan.LIBERTE))
        val invitations = FakeInvitationRepository().apply { inviteError = DomainException.PlanLimitReached }
        val model = vm(projects, invitations)
        model.load("p1")
        advanceUntilIdle()

        model.onEmailChange("sam@x.dev")
        model.submit()
        advanceUntilIdle()

        assertEquals(DomainException.PlanLimitReached, model.state.value.formError)
        assertFalse(model.state.value.invited)
    }

    @Test
    fun an_unknown_owner_plan_lets_the_server_decide() = runTest {
        val projects = FakeProjectRepository(detail = detail(ownerPlan = null), members = List(5) { supervisor(it.toLong()) })
        val model = vm(projects)
        model.load("p1")
        advanceUntilIdle()

        assertFalse(model.state.value.atSupervisorLimit, "plan not yet known → not blocked client-side")
    }
}
