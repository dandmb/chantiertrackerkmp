package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.Report
import com.dmb.chantiertracker.domain.model.ReportPage
import com.dmb.chantiertracker.domain.model.ReportSort

interface ReportRepository {

    /**
     * Flag a problem on an existing daily entry. **Any project member** may do
     * this — there is **no date restriction** (unlike a purchase/consumption
     * line): only an inactive project/stage blocks it server-side (`403`).
     * **Online only** (ADR-47), like `HistoryRepository` and
     * `InvitationRepository`'s online paths — no Room cache, no SyncEngine.
     * Throws a `DomainException` on failure (`NotFound` if the entry has never
     * synced — resolved from Room, no network call; `Network` offline;
     * `Forbidden` on an inactive project/stage).
     */
    suspend fun createReport(entryLocalId: String, message: String)

    /**
     * One page of a project's reports, ordered by [sort] (the enum is the only
     * place the server `sort`/`order` pair is chosen). **ADMIN only** server-side
     * (`403` otherwise). Online only, no Room cache (ADR-47). `NotFound` without
     * a network call if the project has never synced.
     */
    suspend fun projectReports(projectLocalId: String, page: Int, sort: ReportSort): ReportPage

    /**
     * Mark a report processed — **ADMIN only**, **irreversible** (the backend
     * has no PROCESSED → NEW transition). Online only. Returns the updated
     * report so the caller can refresh the row in place.
     */
    suspend fun markProcessed(reportId: Long): Report
}
