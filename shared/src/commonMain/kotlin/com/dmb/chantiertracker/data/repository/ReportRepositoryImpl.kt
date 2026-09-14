package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.DailyEntryDao
import com.dmb.chantiertracker.data.local.db.ProjectDao
import com.dmb.chantiertracker.data.remote.ReportApi
import com.dmb.chantiertracker.data.remote.apiCall
import com.dmb.chantiertracker.data.remote.dto.CreateReportRequestDto
import com.dmb.chantiertracker.data.remote.dto.ReportDto
import com.dmb.chantiertracker.data.remote.dto.ReportPageDto
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.Report
import com.dmb.chantiertracker.domain.model.ReportPage
import com.dmb.chantiertracker.domain.model.ReportSort
import com.dmb.chantiertracker.domain.model.ReportStatus
import com.dmb.chantiertracker.domain.repository.ReportRepository

// Online only, no Room cache — see ReportRepository / ADR-47. Talks to ReportApi
// directly via apiCall, like HistoryRepositoryImpl and InvitationRepositoryImpl's
// online-only paths. Server ids are resolved from Room; an entry or project that
// has never synced can't carry a server-side report → NotFound, no network call.
class ReportRepositoryImpl(
    private val api: ReportApi,
    private val entryDao: DailyEntryDao,
    private val projectDao: ProjectDao,
) : ReportRepository {

    override suspend fun createReport(entryLocalId: String, message: String) {
        val entryServerId = entryDao.findByLocalId(entryLocalId)?.serverId ?: throw DomainException.NotFound
        apiCall { api.create(entryServerId, CreateReportRequestDto(message = message)) }
    }

    override suspend fun projectReports(projectLocalId: String, page: Int, sort: ReportSort): ReportPage {
        val serverId = projectDao.findByLocalId(projectLocalId)?.serverId ?: throw DomainException.NotFound
        return apiCall {
            api.projectReports(serverId, page = page, size = PAGE_SIZE, sort = sort.apiSort, order = sort.apiOrder)
        }.toReportPage()
    }

    override suspend fun markProcessed(reportId: Long): Report =
        apiCall { api.process(reportId) }.toReport()

    companion object {
        // A dedicated, paginated full screen (like the history), not the web's
        // size=100 no-pagination list. Well under any server bound.
        const val PAGE_SIZE = 20
    }
}

private fun ReportPageDto.toReportPage() = ReportPage(
    items = content.map(ReportDto::toReport),
    page = number,
    totalPages = totalPages,
    isFirst = first,
    isLast = last,
    totalElements = totalElements,
)

private fun ReportDto.toReport() = Report(
    id = id,
    entryId = entryId,
    entryType = entryType.orEmpty().toEntryType(),
    entryDate = entryDate,
    authorName = authorName,
    message = message,
    createdAt = createdAt,
    status = when (status?.uppercase()) {
        "NEW" -> ReportStatus.NEW
        "PROCESSED" -> ReportStatus.PROCESSED
        else -> ReportStatus.UNKNOWN
    },
    processedAt = processedAt,
)
