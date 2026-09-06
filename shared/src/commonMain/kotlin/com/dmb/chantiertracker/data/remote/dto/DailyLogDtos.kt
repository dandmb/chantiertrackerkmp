package com.dmb.chantiertracker.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class DailyLogSummaryDto(
    val id: Long,
    val stageId: Long,
    val date: String,
    val hasPurchase: Boolean = false,
    val hasWork: Boolean = false,
)

@Serializable
data class DailyLogDetailDto(
    val id: Long,
    val stageId: Long,
    val date: String,
    val entries: List<DailyEntryDto> = emptyList(),
)

// The backend has no standalone entry endpoint — an entry (and its daily log)
// is created by POSTing to .../logs/{date}/purchases | works. The response
// carries `dailyLogId`, which is how the client learns the parent log's
// server id (a daily_logs row has no pendingOp of its own — see ADR-27).
@Serializable
data class DailyEntryDto(
    val id: Long,
    val dailyLogId: Long,
    val type: String,
    val summary: String? = null,
    val createdById: Long? = null,
    val createdAt: String? = null,
    val modifiedById: Long? = null,
    val modifiedAt: String? = null,
)

@Serializable
data class EntryRequestDto(
    val summary: String? = null,
)
