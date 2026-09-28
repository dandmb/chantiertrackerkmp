package com.dmb.chantiertracker.presentation.projects

import com.dmb.chantiertracker.domain.model.Invitation
import com.dmb.chantiertracker.domain.model.InvitationStatus
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.ProjectMember
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.domain.model.maxSupervisorsPerProject

/**
 * Client-side mirror of the backend `InvitationService.countSupervisorsForProject`
 * + `PlanLimitService.assertCanInviteSupervisor` — used to block the invite
 * form *before* any network call, offline included (ADR-33). A pending
 * invitation already occupies a slot, otherwise an admin could queue several
 * invites and overshoot. The server stays the final arbiter (a `403` on send
 * is still surfaced).
 */
object SupervisorLimit {

    fun slotsUsed(members: List<ProjectMember>, invitations: List<Invitation>): Int =
        members.count { it.role == ProjectRole.SUPERVISOR } +
            invitations.count { it.role == ProjectRole.SUPERVISOR && it.status == InvitationStatus.PENDING }

    fun isReached(ownerPlan: Plan?, members: List<ProjectMember>, invitations: List<Invitation>): Boolean {
        val limit = ownerPlan?.maxSupervisorsPerProject() ?: return false
        return slotsUsed(members, invitations) >= limit
    }
}
