package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.db.EditorIdentityDao
import com.dmb.chantiertracker.data.local.db.EditorIdentityEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeEditorIdentityDao(initial: EditorIdentityEntity? = null) : EditorIdentityDao {
    private val row = MutableStateFlow(initial)
    override fun observe(): Flow<EditorIdentityEntity?> = row
    override suspend fun upsert(entity: EditorIdentityEntity) { row.value = entity }
}
