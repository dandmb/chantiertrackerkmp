package com.dmb.chantiertracker.data.sync

import com.dmb.chantiertracker.data.local.db.InvitationEntity
import com.dmb.chantiertracker.data.remote.dto.InvitationDto

fun InvitationDto.toEntity(projectLocalId: String) = InvitationEntity(
    id = id,
    projectLocalId = projectLocalId,
    email = email,
    role = role,
    invitedById = invitedById,
    createdAt = createdAt,
    expiresAt = expiresAt,
    status = status,
)
