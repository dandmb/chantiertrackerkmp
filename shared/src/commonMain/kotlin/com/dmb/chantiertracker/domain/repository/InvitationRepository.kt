package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.Invitation
import kotlinx.coroutines.flow.Flow

interface InvitationRepository {
    fun observeInvitations(projectLocalId: String): Flow<List<Invitation>>
}
