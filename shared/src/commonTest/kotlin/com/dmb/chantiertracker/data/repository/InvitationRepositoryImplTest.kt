package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.domain.model.InvitationStatus
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.support.FakeInvitationDao
import com.dmb.chantiertracker.support.localInvitation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InvitationRepositoryImplTest {

    private fun repo(dao: FakeInvitationDao = FakeInvitationDao()) = InvitationRepositoryImpl(dao)

    @Test
    fun observe_invitations_maps_rows_newest_first() = runTest {
        val dao = FakeInvitationDao(
            listOf(
                localInvitation(1, projectLocalId = "p1", email = "a@x.dev", createdAt = "2026-09-01T10:00:00"),
                localInvitation(2, projectLocalId = "p1", email = "b@x.dev", createdAt = "2026-09-03T10:00:00"),
                localInvitation(3, projectLocalId = "other", email = "c@x.dev"),
            ),
        )

        val invitations = repo(dao).observeInvitations("p1").first()

        assertEquals(listOf("b@x.dev", "a@x.dev"), invitations.map { it.email }, "other projects are excluded, newest first")
    }

    @Test
    fun unknown_role_and_status_strings_map_to_unknown() = runTest {
        val dao = FakeInvitationDao(
            listOf(localInvitation(1, projectLocalId = "p1", role = "MYSTERY", status = "WEIRD")),
        )

        val invitation = repo(dao).observeInvitations("p1").first().single()

        assertEquals(ProjectRole.UNKNOWN, invitation.role)
        assertEquals(InvitationStatus.UNKNOWN, invitation.status)
    }

    @Test
    fun known_role_and_status_strings_map_through() = runTest {
        val dao = FakeInvitationDao(
            listOf(localInvitation(1, projectLocalId = "p1", role = "SUPERVISOR", status = "PENDING")),
        )

        val invitation = repo(dao).observeInvitations("p1").first().single()

        assertEquals(ProjectRole.SUPERVISOR, invitation.role)
        assertEquals(InvitationStatus.PENDING, invitation.status)
    }
}
