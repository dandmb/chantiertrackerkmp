package com.dmb.chantiertracker.domain.repository

import kotlinx.coroutines.flow.Flow

interface SupportContactRepository {
    /** Last-known support address; `null` = none known, and the contact stays hidden. */
    fun observeEmail(): Flow<String?>

    /** Best-effort pull from the public endpoint. Never throws; a failure keeps the last known value. */
    suspend fun refresh()
}
