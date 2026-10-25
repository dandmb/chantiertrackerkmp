package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.db.LegalDocumentDao
import com.dmb.chantiertracker.data.local.db.LegalDocumentEntity
import com.dmb.chantiertracker.domain.model.LegalLanguage
import com.dmb.chantiertracker.domain.model.LegalText
import com.dmb.chantiertracker.domain.repository.LegalDocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeLegalDocumentDao(vararg initial: LegalDocumentEntity) : LegalDocumentDao {
    private val rows = MutableStateFlow(initial.associateBy { it.document to it.requestedLanguage })

    val all: Collection<LegalDocumentEntity> get() = rows.value.values

    override fun observe(document: String, requestedLanguage: String): Flow<LegalDocumentEntity?> =
        rows.map { it[document to requestedLanguage] }

    override suspend fun upsert(entity: LegalDocumentEntity) {
        rows.value = rows.value + ((entity.document to entity.requestedLanguage) to entity)
    }
}

fun legalDocumentJson(
    document: String = "terms-of-use",
    language: String = "fr",
    requestedLanguage: String = language,
    fallback: Boolean = false,
    title: String = "Conditions Générales d'Utilisation",
    content: String = "## 1. Objet\n\nLe Service est fourni par l'éditeur.",
    version: Int = 1,
    publishedOn: String = "2026-10-25",
): String {
    fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
    return """{"document":${quoted(document)},"language":${quoted(language)},"requestedLanguage":${quoted(requestedLanguage)},""" +
        """"fallback":$fallback,"title":${quoted(title)},"content":${quoted(content)},"version":$version,"publishedOn":${quoted(publishedOn)}}"""
}

class FakeLegalDocumentRepository(vararg saved: LegalText) : LegalDocumentRepository {
    private val texts = MutableStateFlow(saved.associateBy { it.document to it.requestedLanguage() })
    var serverReachable = true
    var served: (document: String, language: LegalLanguage) -> LegalText = { document, language -> legalText(document, language) }
    val refreshes = mutableListOf<Pair<String, LegalLanguage>>()

    private fun LegalText.requestedLanguage(): LegalLanguage = if (fallback) LegalLanguage.English else language

    override fun observe(document: String, language: LegalLanguage): Flow<LegalText?> = texts.map { it[document to language] }

    override suspend fun refresh(document: String, language: LegalLanguage): Boolean {
        refreshes += document to language
        if (!serverReachable) return false
        texts.value = texts.value + ((document to language) to served(document, language))
        return true
    }
}

fun legalText(
    document: String = "cookie-policy",
    language: LegalLanguage = LegalLanguage.French,
    title: String = if (language == LegalLanguage.English) "Cookie Policy" else "Politique de cookies",
    markdown: String = if (language == LegalLanguage.English) "## 1. Cookies\n\nEnglish text." else "## 1. Cookies\n\nTexte français.",
    version: Int = 1,
    publishedOn: String = "2026-10-25",
) = LegalText(document, language, fallback = false, title = title, markdown = markdown, version = version, publishedOn = publishedOn)
