package com.dmb.chantiertracker.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LegalDocumentDao {

    @Query("SELECT * FROM legal_documents WHERE document = :document AND requestedLanguage = :requestedLanguage")
    fun observe(document: String, requestedLanguage: String): Flow<LegalDocumentEntity?>

    @Upsert
    suspend fun upsert(entity: LegalDocumentEntity)
}
