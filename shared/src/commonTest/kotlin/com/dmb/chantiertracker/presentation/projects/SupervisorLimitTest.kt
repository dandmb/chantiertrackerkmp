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
    fun slots_used_counts_every_member_but_the_owner_plus_every_pending_invitation() {
        val used = SupervisorLimit.slotsUsed(
            ownerId = 99L,
            members = listOf(member(ProjectRole.ADMIN), member(ProjectRole.SUPERVISOR)),
            invitations = listOf(
                invitation(ProjectRole.SUPERVISOR, InvitationStatus.PENDING),
                invitation(ProjectRole.SUPERVISOR, InvitationStatus.ACCEPTED), // already a member, not double-counted here
                invitation(ProjectRole.ADMIN, InvitationStatus.PENDING),
            ),
        )
        assertEquals(4, used, "2 members + 2 pending invitations, whatever their role")
    }

    @Test
    fun a_cap_of_one_is_reached_by_a_single_supervisor() {
        val members = listOf(member(ProjectRole.SUPERVISOR))
        assertTrue(SupervisorLimit.isReached(1, 99L, members, emptyList()))
        assertFalse(SupervisorLimit.isReached(1, 99L, emptyList(), emptyList()))
    }

    @Test
    fun a_pending_invitation_already_fills_a_slot() {
        val pending = listOf(invitation(ProjectRole.SUPERVISOR, InvitationStatus.PENDING))
        assertTrue(SupervisorLimit.isReached(1, 99L, emptyList(), pending), "a cap of 1 hit by the pending invite alone")
    }

    @Test
    fun a_cap_of_three_allows_a_third_supervisor_and_blocks_a_fourth() {
        val two = List(2) { member(ProjectRole.SUPERVISOR) }
        assertFalse(SupervisorLimit.isReached(3, 99L, two, emptyList()))
        assertTrue(SupervisorLimit.isReached(3, 99L, two + member(ProjectRole.SUPERVISOR), emptyList()))
    }

    @Test
    fun no_cap_or_a_cap_not_known_yet_never_blocks() {
        val many = List(10) { member(ProjectRole.SUPERVISOR) }
        assertFalse(SupervisorLimit.isReached(null, 99L, many, emptyList()), "unlimited, or not pulled yet → fail open, server decides")
    }

    @Test
    fun every_collaborator_counts_whatever_the_role_the_owner_apart() {
        val owner = ProjectMember(userId = 99, name = "o", email = "o@x.dev", role = ProjectRole.ADMIN)
        val admin = ProjectMember(userId = 2, name = "a", email = "a@x.dev", role = ProjectRole.ADMIN)

        assertEquals(1, SupervisorLimit.slotsUsed(99L, listOf(owner, admin), emptyList()))
        assertEquals(2, SupervisorLimit.slotsUsed(99L, listOf(owner, admin), listOf(invitation(ProjectRole.ADMIN, InvitationStatus.PENDING))))
    }
}
