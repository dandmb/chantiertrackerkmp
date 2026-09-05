package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.DailyLog
import com.dmb.chantiertracker.domain.model.DailyLogDetail
import kotlinx.coroutines.flow.Flow

interface DailyLogRepository {
    fun observeLogs(stageLocalId: String): Flow<List<DailyLog>>
    fun observeLog(logLocalId: String): Flow<DailyLogDetail?>

    /**
     * Writes the day (creating it locally if it doesn't exist yet) and its
     * PURCHASE entry immediately, and returns the day's stable local id.
     * Idempotent: if a PURCHASE entry already exists that day, no new entry
     * is created and the existing day's id is returned. Sync happens in the
     * background.
     */
    suspend fun createPurchaseEntry(stageLocalId: String, date: String): String

    /** Same as [createPurchaseEntry], for the WORK entry. */
    suspend fun createWorkEntry(stageLocalId: String, date: String): String

    /** Applies the summary edit to the local store immediately, flagged pending. Sync happens in the background. */
    suspend fun updateEntry(entryLocalId: String, summary: String)

    /** Best-effort pull of a stage's days from the server into the local store. Never throws. */
    suspend fun refreshLogs(stageLocalId: String)

    /** Best-effort pull of one day and its entries from the server into the local store. Never throws. */
    suspend fun refreshLog(logLocalId: String)
}
