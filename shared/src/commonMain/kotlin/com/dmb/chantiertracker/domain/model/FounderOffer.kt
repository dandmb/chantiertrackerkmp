package com.dmb.chantiertracker.domain.model

data class FounderOffer(
    val projects: Int? = null,
    val photos: Int? = null,
    val videos: Int? = null,
    val videoMaxSeconds: Int? = null,
    val supervisorsPerProject: Int? = null,
    val pdfExport: Boolean = false,
    val historyDays: Int? = null,
)
