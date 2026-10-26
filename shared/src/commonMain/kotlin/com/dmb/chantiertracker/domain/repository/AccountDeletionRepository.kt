package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.AccountDeletionPreview

interface AccountDeletionRepository {
    /** What the deletion would remove, and what blocks it. Online only. */
    suspend fun preview(): AccountDeletionPreview

    /** Deletes the account, then everything of it on this device, and signs out. */
    suspend fun deleteAccount(password: String)
}
