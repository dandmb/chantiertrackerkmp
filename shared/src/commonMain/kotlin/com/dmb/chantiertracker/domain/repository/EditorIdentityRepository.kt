package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.EditorIdentity
import kotlinx.coroutines.flow.Flow

interface EditorIdentityRepository {
    /** Last-known identity from the local store (`null` until first fetched). */
    fun observe(): Flow<EditorIdentity?>

    /** Best-effort pull from the public endpoint into the local store. Never throws. */
    suspend fun refresh()
}
