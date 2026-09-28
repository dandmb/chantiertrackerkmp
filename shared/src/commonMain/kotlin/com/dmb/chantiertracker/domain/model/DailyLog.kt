package com.dmb.chantiertracker.domain.model

enum class EntryType { PURCHASE, WORK, UNKNOWN }

data class DailyLog(
    val localId: String,
    val stageLocalId: String,
    val date: String,
    val hasPurchase: Boolean,
    val hasWork: Boolean,
)

data class DailyEntry(
    val localId: String,
    val dailyLogLocalId: String,
    val type: EntryType,
    val summary: String?,
)

data class DailyLogDetail(
    val localId: String,
    val stageLocalId: String,
    val date: String,
    val entries: List<DailyEntry>,
)
