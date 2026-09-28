package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.ProjectDao
import com.dmb.chantiertracker.data.remote.HistoryApi
import com.dmb.chantiertracker.data.remote.apiCall
import com.dmb.chantiertracker.data.remote.dto.HistoryPageDto
import com.dmb.chantiertracker.data.remote.dto.ModificationHistoryDto
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.HistoryActionType
import com.dmb.chantiertracker.domain.model.HistoryPage
import com.dmb.chantiertracker.domain.model.HistorySort
import com.dmb.chantiertracker.domain.model.ModificationHistoryItem
import com.dmb.chantiertracker.domain.repository.HistoryRepository

// Online only, no Room cache — see HistoryRepository / ADR-44. Talks to
// HistoryApi directly via apiCall, like InvitationRepositoryImpl's online-only
// paths. The project's serverId is resolved from Room; a project that has
// never synced can't have server history.
class HistoryRepositoryImpl(
    private val api: HistoryApi,
    private val projectDao: ProjectDao,
) : HistoryRepository {

    override suspend fun projectHistory(
        projectLocalId: String,
        page: Int,
        sort: HistorySort,
    ): HistoryPage {
        val serverId = projectDao.findByLocalId(projectLocalId)?.serverId ?: throw DomainException.NotFound
        return apiCall {
            api.projectHistory(
                projectId = serverId,
                page = page,
                size = PAGE_SIZE,
                sort = sort.apiSort,
                order = sort.apiOrder,
            )
        }.toHistoryPage()
    }

    companion object {
        // A dedicated full screen, not the web's size=4 sidebar — a deliberate
        // Material 3 adaptation. Well under the backend's [1, 100] bound.
        const val PAGE_SIZE = 20
    }
}

private fun HistoryPageDto.toHistoryPage() = HistoryPage(
    items = content.map(ModificationHistoryDto::toItem),
    page = number,
    totalPages = totalPages,
    isFirst = first,
    isLast = last,
    totalElements = totalElements,
)

private fun ModificationHistoryDto.toItem() = ModificationHistoryItem(
    id = id,
    modifiedAt = modifiedAt,
    actionType = when (actionType?.uppercase()) {
        "CREATION" -> HistoryActionType.CREATION
        "MODIFICATION" -> HistoryActionType.MODIFICATION
        "DELETION" -> HistoryActionType.DELETION
        else -> HistoryActionType.UNKNOWN
    },
    description = description,
    entryId = entryId,
    userId = userId,
    fieldName = fieldName,
    oldValue = oldValue,
    newValue = newValue,
)
