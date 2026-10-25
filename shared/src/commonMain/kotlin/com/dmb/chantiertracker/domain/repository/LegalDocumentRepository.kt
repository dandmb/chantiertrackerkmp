package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.LegalLanguage
import com.dmb.chantiertracker.domain.model.LegalText
import kotlinx.coroutines.flow.Flow

interface LegalDocumentRepository {
    fun observe(document: String, language: LegalLanguage): Flow<LegalText?>

    suspend fun refresh(document: String, language: LegalLanguage): Boolean
}
