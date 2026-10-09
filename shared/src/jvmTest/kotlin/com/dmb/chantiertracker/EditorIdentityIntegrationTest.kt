package com.dmb.chantiertracker

import androidx.room.Room
import com.dmb.chantiertracker.data.local.db.AppDatabase
import com.dmb.chantiertracker.data.local.db.buildChantierDatabase
import com.dmb.chantiertracker.data.remote.EditorIdentityApi
import com.dmb.chantiertracker.data.remote.createHttpClient
import com.dmb.chantiertracker.data.remote.httpClientEngine
import com.dmb.chantiertracker.data.repository.EditorIdentityRepositoryImpl
import com.dmb.chantiertracker.domain.model.EditorIdentity
import com.dmb.chantiertracker.presentation.legal.LegalPlaceholder
import com.dmb.chantiertracker.presentation.legal.valueFor
import com.dmb.chantiertracker.support.FakeTokenStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * ADR-68 — le vrai client Ktor, le dépôt et Room contre un backend réel, sans compte.
 * Désactivé par défaut, comme [FoundersLaunchModeIntegrationTest] :
 *
 *     ./gradlew :shared:jvmTest --tests '*EditorIdentityIntegrationTest' \
 *         -Dchantiertracker.integrationTests=true \
 *         -Dchantiertracker.integrationBaseUrl=http://localhost:8080/api/v1 \
 *         -Dchantiertracker.integrationPhase=<phase>
 *
 * - `editor-empty` : `editor_identity` telle qu'au déploiement (tous les champs `NULL`).
 * - `editor-filled` : prénom `Jean`, nom `Martin`, raison sociale `NULL`, SIRET
 *   `123 456 789 00012`, nom d'hébergeur `Hetzner Online GmbH`, adresse d'hébergeur `NULL`,
 *   les autres champs `NULL` (préparé à la main, puis remis à `NULL`).
 */
class EditorIdentityIntegrationTest {

    private val enabled = System.getProperty("chantiertracker.integrationTests") == "true"
    private val phase = System.getProperty("chantiertracker.integrationPhase")
    private val baseUrl = System.getProperty("chantiertracker.integrationBaseUrl") ?: "http://localhost:8080/api/v1"

    private val client = createHttpClient(
        engine = httpClientEngine(),
        tokenStorage = FakeTokenStorage(null),
        baseUrl = baseUrl,
        enableLogging = false,
        onSessionExpired = {},
    )
    private val db: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
    private val repository = EditorIdentityRepositoryImpl(EditorIdentityApi(client), db.editorIdentityDao())

    @AfterTest
    fun cleanUp() {
        client.close()
        db.close()
    }

    private fun runPhase(name: String, block: suspend () -> Unit) = runBlocking {
        com.dmb.chantiertracker.support.skipUnless(enabled && phase == name, "phase '$name' contre le vrai backend (integrationTests=$enabled, integrationPhase=$phase)")
        println("=== phase '$name' contre $baseUrl")
        block()
    }

    @Test
    fun an_identity_not_filled_in_yet_keeps_every_waiting_text() = runPhase("editor-empty") {
        repository.refresh()
        val identity = repository.observe().first()
        println("identité lue -> $identity")

        assertEquals(EditorIdentity(), identity, "reçue et stockée, tous les champs null")
        LegalPlaceholder.entries.forEach { assertNull(identity.valueFor(it), "$it garde son [À COMPLÉTER]") }
    }

    @Test
    fun a_partly_filled_identity_fills_exactly_the_matching_placeholders() = runPhase("editor-filled") {
        repository.refresh()
        val identity = repository.observe().first()
        println("identité lue -> $identity")

        assertEquals("Jean Martin", identity.valueFor(LegalPlaceholder.EditorName), "pas de raison sociale : la personne")
        assertEquals("Jean Martin", identity.valueFor(LegalPlaceholder.PublicationDirectorName))
        assertEquals("123 456 789 00012", identity.valueFor(LegalPlaceholder.EditorSiret))
        assertEquals("Hetzner Online GmbH", identity.valueFor(LegalPlaceholder.BackendHostDetails), "le nom seul")
        assertNull(identity.valueFor(LegalPlaceholder.EditorAddress), "non renseigné : texte d'attente")
        assertNull(identity.valueFor(LegalPlaceholder.EditorVatNumber))
        assertNull(identity.valueFor(LegalPlaceholder.SupportContactEmail), "jeton sans champ backend")

        // Hors ligne ensuite : la dernière identité connue reste affichable.
        val offline = createHttpClient(httpClientEngine(), FakeTokenStorage(null), "http://localhost:9/api/v1", false) {}
        EditorIdentityRepositoryImpl(EditorIdentityApi(offline), db.editorIdentityDao()).refresh()
        offline.close()
        assertEquals("123 456 789 00012", repository.observe().first()?.siret)
    }
}
