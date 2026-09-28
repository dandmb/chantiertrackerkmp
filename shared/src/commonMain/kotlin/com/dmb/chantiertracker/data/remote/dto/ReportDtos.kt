package com.dmb.chantiertracker.data.remote.dto

import kotlinx.serialization.Serializable

// Calqué sur `ReportResponse` backend. `entryType`/`status` en `String?` +
// mapping tolérant côté repository (une valeur serveur inconnue ne fait jamais
// planter). `authorName`/`processedAt` nullables (auteur supprimé, report NEW).
@Serializable
data class ReportDto(
    val id: Long,
    val entryId: Long,
    val entryType: String? = null,
    val entryDate: String,
    val authorName: String? = null,
    val message: String,
    val createdAt: String,
    val status: String? = null,
    val processedAt: String? = null,
)

// The pagination-carrying shape of a Spring `Page` — same fields the history
// screen reads for its Previous/Next controls; `ignoreUnknownKeys` drops the
// rest (`pageable`, `sort`, `size`, `empty`…).
@Serializable
data class ReportPageDto(
    val content: List<ReportDto> = emptyList(),
    val number: Int = 0,
    val totalPages: Int = 0,
    val totalElements: Int = 0,
    val first: Boolean = true,
    val last: Boolean = true,
)

@Serializable
data class CreateReportRequestDto(val message: String)
