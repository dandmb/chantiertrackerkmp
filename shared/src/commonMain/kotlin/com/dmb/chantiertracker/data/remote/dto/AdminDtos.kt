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

@Serializable
data class CreateAdminUserRequestDto(
    val email: String,
    val name: String,
    val password: String,
    // GlobalRole.name raw ("USER"/"SUPER_ADMIN") — matches the backend Java
    // enum's constant names exactly, verified against GlobalRole.java.
    val globalRole: String,
)

@Serializable
data class UpdateAdminUserRequestDto(val name: String)

@Serializable
data class UpdateUserPlanRequestDto(
    val plan: String,
    // yyyy-MM-ddTHH:mm:ss, end-of-day — null = indefinite grant. Ignored
    // server-side when plan is FREE (verified in UserService.grantPlanByAdmin).
    val expiresAt: String? = null,
)

@Serializable
data class TimeSeriesPointDto(val bucket: String, val count: Long)

@Serializable
data class AdminStatsResponseDto(
    val totalUsers: Long,
    val totalProjects: Long,
    val registrations: List<TimeSeriesPointDto> = emptyList(),
    val projectsCreated: List<TimeSeriesPointDto> = emptyList(),
)
