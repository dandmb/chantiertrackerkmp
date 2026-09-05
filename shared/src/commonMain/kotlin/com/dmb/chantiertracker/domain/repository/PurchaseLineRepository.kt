package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.CreatePurchaseLineInput
import com.dmb.chantiertracker.domain.model.PurchaseLine
import com.dmb.chantiertracker.domain.model.UpdatePurchaseLineInput
import kotlinx.coroutines.flow.Flow

interface PurchaseLineRepository {
    fun observeLines(entryLocalId: String): Flow<List<PurchaseLine>>

    /** Writes the line to the local store immediately and returns its stable local id. Sync happens in the background. */
    suspend fun createLine(entryLocalId: String, input: CreatePurchaseLineInput): String

    /** Applies the edit to the local store immediately, flagged pending. Sync happens in the background. */
    suspend fun updateLine(lineLocalId: String, input: UpdatePurchaseLineInput)

    /** Marks the line for deletion locally (or drops it outright if it never reached the server). Sync happens in the background. */
    suspend fun deleteLine(lineLocalId: String)
}
