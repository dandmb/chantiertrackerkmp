package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.LegalDocumentDao
import com.dmb.chantiertracker.data.local.db.LegalDocumentEntity
import com.dmb.chantiertracker.data.remote.LegalDocumentsApi
import com.dmb.chantiertracker.data.remote.apiCall
import com.dmb.chantiertracker.data.sync.Clock
import com.dmb.chantiertracker.data.sync.SystemClock
import com.dmb.chantiertracker.domain.model.LegalLanguage
import com.dmb.chantiertracker.domain.model.LegalText
import com.dmb.chantiertracker.domain.repository.LegalDocumentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Read-only and public, so no SyncEngine: same offline-first shape as EditorIdentityRepositoryImpl
// (the screen reads Room, a failed refresh keeps the last text received).
class LegalDocumentRepositoryImpl(
    private val api: LegalDocumentsApi,
    private val dao: LegalDocumentDao,
    private val clock: Clock = SystemClock,
) : LegalDocumentRepository {

    override fun observe(document: String, language: LegalLanguage): Flow<LegalText?> =
        dao.observe(document, language.code).map { it?.toLegalText() }

    override suspend fun refresh(document: String, language: LegalLanguage): Boolean {
        val dto = try {
            apiCall { api.get(document, language.code) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return false
        }
        dao.upsert(
            LegalDocumentEntity(
                document = document,
                requestedLanguage = language.code,
                language = dto.language,
                fallback = dto.fallback,
                title = dto.title,
                content = dto.content,
                version = dto.version,
                publishedOn = dto.publishedOn,
                receivedAt = clock.nowEpochMillis(),
            ),
        )
        return true
    }
}

internal fun LegalDocumentEntity.toLegalText() = LegalText(
    document = document,
    language = LegalLanguage.fromCode(language),
    fallback = fallback,
    title = title,
    markdown = content,
    version = version,
    publishedOn = publishedOn,
)
