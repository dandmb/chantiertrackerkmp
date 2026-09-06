package com.dmb.chantiertracker.presentation.projects

import com.dmb.chantiertracker.domain.model.Invitation
import com.dmb.chantiertracker.domain.model.InvitationStatus
import com.dmb.chantiertracker.domain.model.Plan
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
    fun free_plan_caps_at_one_supervisor() {
        val members = listOf(member(ProjectRole.SUPERVISOR))
        assertTrue(SupervisorLimit.isReached(Plan.FREE, members, emptyList()))
        assertFalse(SupervisorLimit.isReached(Plan.FREE, emptyList(), emptyList()))
    }

    @Test
    fun a_pending_invitation_already_fills_a_slot() {
        val pending = listOf(invitation(ProjectRole.SUPERVISOR, InvitationStatus.PENDING))
        assertTrue(SupervisorLimit.isReached(Plan.FREE, emptyList(), pending), "FREE limit of 1 hit by the pending invite alone")
    }

    @Test
    fun semi_flex_allows_three() {
        val two = List(2) { member(ProjectRole.SUPERVISOR) }
        assertFalse(SupervisorLimit.isReached(Plan.SEMI_FLEX, two, emptyList()))
        assertTrue(SupervisorLimit.isReached(Plan.SEMI_FLEX, two + member(ProjectRole.SUPERVISOR), emptyList()))
    }

    @Test
    fun liberte_and_unknown_and_missing_plan_never_block() {
        val many = List(10) { member(ProjectRole.SUPERVISOR) }
        assertFalse(SupervisorLimit.isReached(Plan.LIBERTE, many, emptyList()))
        assertFalse(SupervisorLimit.isReached(Plan.UNKNOWN, many, emptyList()))
        assertFalse(SupervisorLimit.isReached(null, many, emptyList()), "plan not yet pulled → fail open, server decides")
    }
}
