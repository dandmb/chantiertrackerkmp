package com.dmb.chantiertracker.presentation.projects

import com.dmb.chantiertracker.domain.model.Invitation
import com.dmb.chantiertracker.domain.model.InvitationStatus
import com.dmb.chantiertracker.domain.model.ProjectMember

/**
 * Client-side mirror of the backend `InvitationService.countCollaboratorsForProject`
 * + `PlanLimitService.assertCanInviteSupervisor` — used to block the invite
 * form *before* any network call, offline included (ADR-33). A pending
 * invitation already occupies a slot, otherwise an admin could queue several
 * invites and overshoot. The server stays the final arbiter (a `403` on send
 * is still surfaced).
 */
object SupervisorLimit {

    fun slotsUsed(ownerId: Long?, members: List<ProjectMember>, invitations: List<Invitation>): Int =
        members.count { it.userId != ownerId } +
            invitations.count { it.status == InvitationStatus.PENDING }

    /** @param maxSupervisors the owner's per-project cap; `null` = no cap, or not known yet (fail open). */
    fun isReached(maxSupervisors: Int?, ownerId: Long?, members: List<ProjectMember>, invitations: List<Invitation>): Boolean {
        val limit = maxSupervisors ?: return false
        return slotsUsed(ownerId, members, invitations) >= limit
    }
}
