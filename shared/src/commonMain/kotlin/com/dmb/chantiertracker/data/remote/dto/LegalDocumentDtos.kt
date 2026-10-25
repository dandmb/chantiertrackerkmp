package com.dmb.chantiertracker.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class LegalDocumentDto(
    val document: String,
    val language: String,
    val requestedLanguage: String,
    val fallback: Boolean = false,
    val title: String,
    val content: String,
    val version: Int,
    val publishedOn: String,
)
