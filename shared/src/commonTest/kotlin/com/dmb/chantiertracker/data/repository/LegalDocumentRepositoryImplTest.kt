package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.LegalDocumentEntity
import com.dmb.chantiertracker.data.remote.LegalDocumentsApi
import com.dmb.chantiertracker.domain.model.LegalLanguage
import com.dmb.chantiertracker.domain.model.LegalText
import com.dmb.chantiertracker.support.FakeLegalDocumentDao
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.MutableClock
import com.dmb.chantiertracker.support.RecordingMockClient
import com.dmb.chantiertracker.support.legalDocumentJson
import com.dmb.chantiertracker.support.respondJson
import com.dmb.chantiertracker.support.respondProblem
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LegalDocumentRepositoryImplTest {

    private val savedFrench = LegalDocumentEntity(
        document = "privacy-policy", requestedLanguage = "fr", language = "fr", fallback = false,
        title = "Politique de confidentialité", content = "## 1. Responsable\n\nAncien texte.", version = 1,
        publishedOn = "2026-10-01", receivedAt = 1L,
    )

    private fun repo(dao: FakeLegalDocumentDao = FakeLegalDocumentDao(), respond: (path: String) -> String): Pair<LegalDocumentRepositoryImpl, RecordingMockClient> {
        val client = RecordingMockClient(FakeTokenStorage(null)) { respondJson(respond(it.url.encodedPath)) }
        return LegalDocumentRepositoryImpl(LegalDocumentsApi(client.client), dao, MutableClock(5_000L)) to client
    }

    @Test
    fun nothing_is_known_before_the_first_refresh() = runTest {
        val (r, client) = repo { legalDocumentJson() }

        assertNull(r.observe("terms-of-use", LegalLanguage.French).first())
        assertEquals(0, client.requests.size)
    }

    @Test
    fun refresh_reads_the_public_document_of_the_asked_language_without_a_token() = runTest {
        val (r, client) = repo { legalDocumentJson(document = "cookie-policy", language = "en", title = "Cookie Policy") }

        assertTrue(r.refresh("cookie-policy", LegalLanguage.English))

        val request = client.requests.single()
        assertEquals("/api/v1/legal-documents/cookie-policy/en", request.url.encodedPath)
        assertNull(request.headers["Authorization"])
    }

    @Test
    fun the_received_text_is_kept_with_its_server_title_date_and_version() = runTest {
        val dao = FakeLegalDocumentDao()
        val (r, _) = repo(dao) {
            legalDocumentJson(document = "legal-notice", title = "Mentions légales", content = "## 1. Éditeur\n\nTexte.", version = 3, publishedOn = "2026-10-24")
        }

        r.refresh("legal-notice", LegalLanguage.French)

        assertEquals(
            LegalText(
                document = "legal-notice", language = LegalLanguage.French, fallback = false, title = "Mentions légales",
                markdown = "## 1. Éditeur\n\nTexte.", version = 3, publishedOn = "2026-10-24",
            ),
            r.observe("legal-notice", LegalLanguage.French).first(),
        )
        assertEquals(5_000L, dao.all.single().receivedAt)
    }

    @Test
    fun the_french_and_english_versions_of_a_document_are_kept_apart() = runTest {
        val (r, _) = repo { path ->
            if (path.endsWith("/en")) legalDocumentJson(language = "en", title = "Terms of Use", content = "English.")
            else legalDocumentJson(language = "fr", title = "Conditions Générales d'Utilisation", content = "Français.")
        }

        r.refresh("terms-of-use", LegalLanguage.French)
        r.refresh("terms-of-use", LegalLanguage.English)

        assertEquals("Français.", r.observe("terms-of-use", LegalLanguage.French).first()?.markdown)
        assertEquals("English.", r.observe("terms-of-use", LegalLanguage.English).first()?.markdown)
        assertEquals("Terms of Use", r.observe("terms-of-use", LegalLanguage.English).first()?.title)
    }

    @Test
    fun an_english_document_served_in_french_is_kept_under_english_and_says_so() = runTest {
        val (r, _) = repo { legalDocumentJson(language = "fr", requestedLanguage = "en", fallback = true, title = "Conditions Générales d'Utilisation") }

        r.refresh("terms-of-use", LegalLanguage.English)

        val text = r.observe("terms-of-use", LegalLanguage.English).first()
        assertEquals(LegalLanguage.French, text?.language)
        assertEquals(true, text?.fallback)
        assertNull(r.observe("terms-of-use", LegalLanguage.French).first(), "the French slot is only filled by a French request")
    }

    @Test
    fun offline_the_refresh_fails_quietly_and_the_last_text_received_stays() = runTest {
        val dao = FakeLegalDocumentDao(savedFrench)
        val offline = RecordingMockClient(FakeTokenStorage(null)) { throw RuntimeException("no connection") }
        val r = LegalDocumentRepositoryImpl(LegalDocumentsApi(offline.client), dao, MutableClock(5_000L))

        assertFalse(r.refresh("privacy-policy", LegalLanguage.French))

        assertEquals("## 1. Responsable\n\nAncien texte.", r.observe("privacy-policy", LegalLanguage.French).first()?.markdown)
    }

    @Test
    fun a_server_error_keeps_the_last_text_received_too() = runTest {
        val dao = FakeLegalDocumentDao(savedFrench)
        val failing = RecordingMockClient(FakeTokenStorage(null)) { respondProblem(HttpStatusCode.NotFound, "Document introuvable", code = "LEGAL_DOCUMENT_NOT_FOUND") }
        val r = LegalDocumentRepositoryImpl(LegalDocumentsApi(failing.client), dao, MutableClock(5_000L))

        assertFalse(r.refresh("privacy-policy", LegalLanguage.French))

        assertEquals(1, r.observe("privacy-policy", LegalLanguage.French).first()?.version)
    }

    @Test
    fun a_new_version_replaces_the_one_kept_on_the_device() = runTest {
        val dao = FakeLegalDocumentDao(savedFrench)
        val (r, _) = repo(dao) {
            legalDocumentJson(document = "privacy-policy", title = "Politique de confidentialité", content = "Nouveau texte.", version = 2, publishedOn = "2026-10-25")
        }

        r.refresh("privacy-policy", LegalLanguage.French)

        val text = r.observe("privacy-policy", LegalLanguage.French).first()
        assertEquals(listOf("Nouveau texte.", "2026-10-25"), listOf(text?.markdown, text?.publishedOn))
        assertEquals(1, dao.all.size)
    }
}
