package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.ExportedPdf

interface ExportRepository {

    /**
     * Generates the project's PDF "dossier de chantier" server-side and writes
     * it to a throwaway cache file.
     *
     * **Online only** (ADR-48) — no Room cache, no SyncEngine, like
     * `HistoryRepository` / `ReportRepository`. The document must reflect the
     * project's **current** state (financial summary, stock…), so a cached copy
     * would be misleading; and the client never originates export data.
     *
     * Throws a `DomainException`:
     * - `NotFound` if the project has never synced (resolved from Room, **no
     *   network call**)
     * - `PlanLimitReached` if the project **owner's** plan is `FREE` (403
     *   server-side — the caller's own plan is irrelevant)
     * - `Network` offline, `Unexpected` on a generation failure (500)
     */
    suspend fun exportProjectPdf(projectLocalId: String): ExportedPdf
}
