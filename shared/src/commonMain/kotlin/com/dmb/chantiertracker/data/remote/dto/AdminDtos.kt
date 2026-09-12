package com.dmb.chantiertracker.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AdminUserResponseDto(
    val id: Long,
    val email: String,
    val name: String,
    val active: Boolean,
    val globalRole: String,
    val projectCount: Long,
    val createdAt: String,
    val plan: String,
    val planSource: String? = null,
    val planExpiresAt: String? = null,
)

// The pagination-carrying shape of a raw Spring `Page` — same idiom as
// HistoryPageDto (content + page metadata only, `ignoreUnknownKeys` drops
// `pageable`/`sort`/`numberOfElements`/`size`/`empty`).
@Serializable
data class AdminUserPageDto(
    val content: List<AdminUserResponseDto> = emptyList(),
    val number: Int = 0,
    val totalPages: Int = 0,
    val totalElements: Int = 0,
    val first: Boolean = true,
    val last: Boolean = true,
)
