package com.dmb.chantiertracker.data.remote.dto

import kotlinx.serialization.Serializable

// Every field null until a SUPER_ADMIN fills it in on the backend — never "".
@Serializable
data class EditorIdentityDto(
    val firstName: String? = null,
    val lastName: String? = null,
    val companyName: String? = null,
    val legalStatus: String? = null,
    val siret: String? = null,
    val address: String? = null,
    val contactEmail: String? = null,
    val vatNumber: String? = null,
    val hostingProviderName: String? = null,
    val hostingProviderAddress: String? = null,
)
