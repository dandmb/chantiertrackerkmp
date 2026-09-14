package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.ExportFileStore
import com.dmb.chantiertracker.data.local.db.ProjectDao
import com.dmb.chantiertracker.data.remote.ExportApi
import com.dmb.chantiertracker.data.remote.apiCall
import com.dmb.chantiertracker.data.remote.parseContentDispositionFilename
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.ExportedPdf
import com.dmb.chantiertracker.domain.repository.ExportRepository

// Online only, no Room cache — see ExportRepository / ADR-48. Talks to ExportApi
// directly via apiCall, like HistoryRepositoryImpl / ReportRepositoryImpl. The
// project's server id is resolved from Room; a project that has never synced
// can't be exported → NotFound, no network call.
class ExportRepositoryImpl(
    private val api: ExportApi,
    private val projectDao: ProjectDao,
    private val fileStore: ExportFileStore,
) : ExportRepository {

    override suspend fun exportProjectPdf(projectLocalId: String): ExportedPdf {
        val serverId = projectDao.findByLocalId(projectLocalId)?.serverId ?: throw DomainException.NotFound
        val raw = apiCall { api.exportProjectPdf(serverId) }
        val fileName = parseContentDispositionFilename(raw.contentDisposition) ?: FALLBACK_FILE_NAME
        val path = fileStore.save(raw.bytes, fileName)
        return ExportedPdf(path = path, fileName = fileName)
    }

    private companion object {
        // The backend always sends a Content-Disposition — this is a pure safety net.
        const val FALLBACK_FILE_NAME = "chantier.pdf"
    }
}
