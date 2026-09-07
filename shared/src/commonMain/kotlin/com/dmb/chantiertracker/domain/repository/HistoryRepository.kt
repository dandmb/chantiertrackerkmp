package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.HistoryPage
import com.dmb.chantiertracker.domain.model.HistorySort

interface HistoryRepository {

    /**
     * One page of a project's modification history. **ADMIN only** server-side
     * (`403` for any other role) and **online only** — no Room cache, no
     * SyncEngine (ADR-44): the retention window is computed server-side against
     * `now()`, so a stale local copy would show a misleading "limited to the
     * last 30 days" notice; and it is a deliberate, occasional consultation
     * that never drives a local write. Throws a `DomainException` on failure
     * (`Network` offline, `Forbidden` if not an admin, `NotFound` if the
     * project has never synced).
     */
    suspend fun projectHistory(projectLocalId: String, page: Int, sort: HistorySort): HistoryPage
}
