package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.db.SupportContactDao
import com.dmb.chantiertracker.data.local.db.SupportContactEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSupportContactDao(initial: SupportContactEntity? = null) : SupportContactDao {
    private val row = MutableStateFlow(initial)
    override fun observe(): Flow<SupportContactEntity?> = row
    override suspend fun upsert(entity: SupportContactEntity) { row.value = entity }
}
