package com.dmb.chantiertracker.presentation.projects

import com.dmb.chantiertracker.domain.model.Invitation
import com.dmb.chantiertracker.domain.model.InvitationStatus
import com.dmb.chantiertracker.domain.model.ProjectMember
import com.dmb.chantiertracker.domain.model.ProjectRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupervisorLimitTest {

    private fun member(role: ProjectRole) = ProjectMember(userId = 1, name = "x", email = "x@x.dev", role = role)

    private fun invitation(role: ProjectRole, status: InvitationStatus) =
        Invitation(1, "p1", "x@x.dev", role, 1L, "2026-09-01T10:00:00", null, status)

    @Test
    fun slots_used_counts_supervisor_members_plus_pending_supervisor_invitations() {
        val used = SupervisorLimit.slotsUsed(
            members = listOf(member(ProjectRole.ADMIN), member(ProjectRole.SUPERVISOR)),
            invitations = listOf(
                invitation(ProjectRole.SUPERVISOR, InvitationStatus.PENDING),
                invitation(ProjectRole.SUPERVISOR, InvitationStatus.ACCEPTED), // already a member, not double-counted here
                invitation(ProjectRole.ADMIN, InvitationStatus.PENDING),
            ),
        )
        assertEquals(2, used, "1 supervisor member + 1 pending supervisor invitation")
    }

    @Test
    fun a_cap_of_one_is_reached_by_a_single_supervisor() {
        val members = listOf(member(ProjectRole.SUPERVISOR))
        assertTrue(SupervisorLimit.isReached(1, members, emptyList()))
        assertFalse(SupervisorLimit.isReached(1, emptyList(), emptyList()))
    }

    @Test
    fun a_pending_invitation_already_fills_a_slot() {
        val pending = listOf(invitation(ProjectRole.SUPERVISOR, InvitationStatus.PENDING))
        assertTrue(SupervisorLimit.isReached(1, emptyList(), pending), "a cap of 1 hit by the pending invite alone")
    }

    @Test
    fun a_cap_of_three_allows_a_third_supervisor_and_blocks_a_fourth() {
        val two = List(2) { member(ProjectRole.SUPERVISOR) }
        assertFalse(SupervisorLimit.isReached(3, two, emptyList()))
        assertTrue(SupervisorLimit.isReached(3, two + member(ProjectRole.SUPERVISOR), emptyList()))
    }

    @Test
    fun no_cap_or_a_cap_not_known_yet_never_blocks() {
        val many = List(10) { member(ProjectRole.SUPERVISOR) }
        assertFalse(SupervisorLimit.isReached(null, many, emptyList()), "unlimited, or not pulled yet → fail open, server decides")
    }
}
