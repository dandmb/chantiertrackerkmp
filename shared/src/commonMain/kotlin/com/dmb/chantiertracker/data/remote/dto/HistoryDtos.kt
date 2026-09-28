package com.dmb.chantiertracker.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ModificationHistoryDto(
    val id: Long,
    val entryId: Long? = null,
    val userId: Long? = null,
    val modifiedAt: String,
    val actionType: String? = null,
    val fieldName: String? = null,
    val oldValue: String? = null,
    val newValue: String? = null,
    val description: String? = null,
)

// The pagination-carrying shape of a Spring `Page` — unlike the shared
// `PageDto` (content only), the history screen needs the page metadata for its
// Previous/Next controls. `ignoreUnknownKeys` drops the rest (`pageable`,
// `sort`, `numberOfElements`, `size`, `empty`).
@Serializable
data class HistoryPageDto(
    val content: List<ModificationHistoryDto> = emptyList(),
    val number: Int = 0,
    val totalPages: Int = 0,
    val totalElements: Int = 0,
    val first: Boolean = true,
    val last: Boolean = true,
)
