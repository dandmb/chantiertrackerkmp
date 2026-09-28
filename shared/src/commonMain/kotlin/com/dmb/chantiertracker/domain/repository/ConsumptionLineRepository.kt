package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.ConsumptionLine
import com.dmb.chantiertracker.domain.model.CreateConsumptionLineInput
import com.dmb.chantiertracker.domain.model.UpdateConsumptionLineInput
import kotlinx.coroutines.flow.Flow

interface ConsumptionLineRepository {
    fun observeLines(entryLocalId: String): Flow<List<ConsumptionLine>>

    /** Writes the line to the local store immediately and returns its stable local id. Sync happens in the background. */
    suspend fun createLine(entryLocalId: String, input: CreateConsumptionLineInput): String

    /** Applies the edit to the local store immediately, flagged pending. Sync happens in the background. */
    suspend fun updateLine(lineLocalId: String, input: UpdateConsumptionLineInput)

    /** Marks the line for deletion locally (or drops it outright if it never reached the server). Sync happens in the background. */
    suspend fun deleteLine(lineLocalId: String)
}
