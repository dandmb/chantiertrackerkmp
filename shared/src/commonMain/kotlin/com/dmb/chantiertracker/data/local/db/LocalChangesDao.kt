package com.dmb.chantiertracker.data.local.db

import androidx.room.Transaction

interface LocalChangesDao<E : LocallyVersioned<E>> {

    suspend fun findByLocalId(localId: String): E?

    suspend fun upsert(row: E)

    suspend fun deleteByLocalId(localId: String)

    @Transaction
    suspend fun insertNew(row: E): Boolean {
        if (findByLocalId(row.localId) != null) return false
        upsert(row)
        return true
    }

    @Transaction
    suspend fun changeLocally(localId: String, change: (E) -> E?): E? {
        val current = findByLocalId(localId) ?: return null
        val changed = change(current)
        if (changed == null) deleteByLocalId(localId) else upsert(changed.withLocalVersion(current.localVersion + 1))
        return current
    }

    @Transaction
    suspend fun writeIfUnchanged(row: E): Boolean {
        if (findByLocalId(row.localId)?.localVersion != row.localVersion) return false
        upsert(row)
        return true
    }

    @Transaction
    suspend fun deleteIfUnchanged(row: E): Boolean {
        if (findByLocalId(row.localId)?.localVersion != row.localVersion) return false
        deleteByLocalId(row.localId)
        return true
    }

    @Transaction
    suspend fun keepLocalChange(localId: String, withServerResult: (E) -> E): Boolean {
        val current = findByLocalId(localId) ?: return false
        upsert(withServerResult(current).withLocalVersion(current.localVersion))
        return true
    }
}
