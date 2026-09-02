package com.dmb.chantiertracker

import com.dmb.chantiertracker.data.AuthStateHolder
import com.dmb.chantiertracker.data.local.DesktopOnboardingStore
import com.dmb.chantiertracker.data.local.DesktopTokenStorage
import com.dmb.chantiertracker.data.remote.AccountApi
import com.dmb.chantiertracker.data.remote.AuthApi
import com.dmb.chantiertracker.data.remote.ProjectApi
import com.dmb.chantiertracker.data.remote.createHttpClient
import com.dmb.chantiertracker.data.remote.httpClientEngine
import androidx.room.Room
import com.dmb.chantiertracker.data.local.db.AppDatabase
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.buildChantierDatabase
import com.dmb.chantiertracker.data.repository.AccountRepositoryImpl
import com.dmb.chantiertracker.data.repository.AuthRepositoryImpl
import com.dmb.chantiertracker.data.repository.ProjectRepositoryImpl
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.SyncEngine
import com.dmb.chantiertracker.presentation.sync.SyncStateHolder
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.UpdateProjectInput
import com.dmb.chantiertracker.support.FakeConnectivityObserver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Parcours réel du chemin Desktop (DesktopTokenStorage + engine OkHttp) contre le
 * backend local. Ignoré automatiquement si `localhost:8080` ne répond pas.
 * Compte requis : mobile-test@local.dev / ChantierTest1234! (créé pendant la vérif).
 */
class DesktopAuthIntegrationTest {

    private val dir = Files.createTempDirectory("ct-desktop-integ")
    private val storage = DesktopTokenStorage(dir)
    private val onboarding = DesktopOnboardingStore(dir.resolve("onboarding.flag"))
    private val holder = AuthStateHolder()
    private val client = createHttpClient(
        engine = httpClientEngine(),
        tokenStorage = storage,
        baseUrl = "http://localhost:8080/api/v1",
        enableLogging = false,
        onSessionExpired = { holder.update(AuthState.Unauthenticated) },
    )
    private val repo = AuthRepositoryImpl(AuthApi(client), storage, holder, onboarding)
    private val accountRepo = AccountRepositoryImpl(AccountApi(client))

    private val db: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
    private val appScope = AppCoroutineScope()
    private val connectivity = FakeConnectivityObserver(initiallyOnline = true)
    private val syncEngine = SyncEngine(db.projectDao(), ProjectApi(client), connectivity, SyncStateHolder(), appScope)
    private val projectRepo = ProjectRepositoryImpl(db.projectDao(), syncEngine, appScope)

    @AfterTest
    fun cleanUp() {
        client.close()
        db.close()
        dir.toFile().deleteRecursively()
    }

    @Test
    fun full_login_bootstrap_logout_against_local_backend() = runBlocking {
        if (System.getProperty("chantiertracker.integrationTests") != "true") {
            println("Test d'intégration désactivé (passer -Dchantiertracker.integrationTests=true).")
            return@runBlocking
        }
        if (!backendUp()) {
            println("Backend localhost:8080 indisponible — test ignoré.")
            return@runBlocking
        }

        repo.login("mobile-test@local.dev", "ChantierTest1234!")

        val authed = holder.state.value
        assertIs<AuthState.Authenticated>(authed)
        assertEquals("Mobile Test", authed.user.name)
        assertTrue(dir.resolve("auth.bin").exists(), "le token doit être persisté sur disque")
        val onDisk = Files.readAllBytes(dir.resolve("auth.bin")).decodeToString()
        assertTrue(!onDisk.contains("eyJ"), "le token ne doit pas être en clair sur disque")

        // Nouvelle session : bootstrap depuis le token stocké
        val holder2 = AuthStateHolder()
        val client2 = createHttpClient(httpClientEngine(), storage, "http://localhost:8080/api/v1", false) {
            holder2.update(AuthState.Unauthenticated)
        }
        val repo2 = AuthRepositoryImpl(AuthApi(client2), storage, holder2, onboarding)
        repo2.bootstrap()
        assertIs<AuthState.Authenticated>(holder2.state.value)
        client2.close()

        repo.logout()
        assertEquals(AuthState.Unauthenticated, holder.state.value)
        assertTrue(!dir.resolve("auth.bin").exists(), "le token doit être effacé après logout")

        // « Bon retour » ne réapparaît jamais après une 1ʳᵉ connexion réussie, même après logout.
        assertTrue(onboarding.hasCompletedFirstLogin(), "le flag première connexion doit survivre au logout")
        assertTrue(
            DesktopOnboardingStore(dir.resolve("onboarding.flag")).hasCompletedFirstLogin(),
            "le flag doit être persisté sur disque (relance de l'app)",
        )
    }

    @Test
    fun register_then_reset_password_then_login_with_new_password() = runBlocking {
        if (System.getProperty("chantiertracker.integrationTests") != "true") {
            println("Test d'intégration désactivé (passer -Dchantiertracker.integrationTests=true).")
            return@runBlocking
        }
        if (!backendUp()) {
            println("Backend localhost:8080 indisponible — test ignoré.")
            return@runBlocking
        }

        val email = "reset-flow-${System.currentTimeMillis()}@local.dev"
        val firstPassword = "FirstPass1234!"
        val newPassword = "SecondPass5678!"

        repo.register(email, firstPassword, "Reset Flow")
        val verifyCode = latestCodeFor(email)
        repo.verifyEmail(email, verifyCode)
        repo.login(email, firstPassword)
        assertIs<AuthState.Authenticated>(holder.state.value)

        // Le reset révoque tous les tokens serveur — l'ancien couple est encore sur disque.
        repo.forgotPassword(email)
        repo.resetPassword(email, latestCodeFor(email, differentFrom = verifyCode), newPassword)

        // Le cœur du bug #1 : se reconnecter avec le NOUVEAU mot de passe doit réussir.
        repo.login(email, newPassword)
        val authed = holder.state.value
        assertIs<AuthState.Authenticated>(authed)
        assertEquals("Reset Flow", authed.user.name)
    }

    @Test
    fun logged_in_user_lists_projects_and_reads_plan() = runBlocking {
        if (System.getProperty("chantiertracker.integrationTests") != "true") {
            println("Test d'intégration désactivé (passer -Dchantiertracker.integrationTests=true).")
            return@runBlocking
        }
        if (!backendUp()) {
            println("Backend localhost:8080 indisponible — test ignoré.")
            return@runBlocking
        }

        repo.login("mobile-test@local.dev", "ChantierTest1234!")
        assertIs<AuthState.Authenticated>(holder.state.value)

        // Room est la source de vérité : on pull d'abord, puis on lit le store local.
        projectRepo.refresh()
        val projects = projectRepo.observeProjects().first()
        assertTrue(projects.all { it.name.isNotBlank() }, "chaque projet a un nom")

        // `GET /users/me/plan-usage` → Plan connu (compte de test = FREE par défaut).
        assertTrue(accountRepo.getCurrentPlan() != Plan.UNKNOWN, "le plan du compte de test doit être reconnu")
    }

    @Test
    fun register_create_project_then_open_its_detail() = runBlocking {
        if (System.getProperty("chantiertracker.integrationTests") != "true") {
            println("Test d'intégration désactivé (passer -Dchantiertracker.integrationTests=true).")
            return@runBlocking
        }
        if (!backendUp()) {
            println("Backend localhost:8080 indisponible — test ignoré.")
            return@runBlocking
        }

        // Compte neuf : le compte de test est FREE (1 projet max) et en a déjà un.
        val email = "project-flow-${System.currentTimeMillis()}@local.dev"
        repo.register(email, "ProjectPass1234!", "Project Flow")
        repo.verifyEmail(email, latestCodeFor(email))
        repo.login(email, "ProjectPass1234!")
        assertIs<AuthState.Authenticated>(holder.state.value)

        // Écriture locale immédiate…
        val localId = projectRepo.createProject(
            CreateProjectInput(
                name = "Villa d'intégration",
                description = "Créée par le test E2E",
                location = "Nîmes",
                currency = null,
                timezone = "Europe/Paris",
            ),
        )
        assertEquals("Villa d'intégration", projectRepo.observeProject(localId).first()?.name)

        // …puis synchronisation vers le serveur réel (push du CREATE + pull).
        syncEngine.syncNow()

        val detail = projectRepo.observeProject(localId).first()!!
        assertEquals("Villa d'intégration", detail.name)
        assertEquals("USD", detail.currency, "devise absente → USD par défaut côté backend")
        assertEquals("Europe/Paris", detail.timezone)
        assertTrue(db.projectDao().findByLocalId(localId)?.serverId != null, "un id serveur a été attribué au sync")

        // Le projet apparaît dans la liste locale, réconciliée avec le serveur.
        assertTrue(projectRepo.observeProjects().first().any { it.localId == localId })
    }

    @Test
    fun create_pull_members_edit_offline_then_delete() = runBlocking {
        if (System.getProperty("chantiertracker.integrationTests") != "true") {
            println("Test d'intégration désactivé (passer -Dchantiertracker.integrationTests=true).")
            return@runBlocking
        }
        if (!backendUp()) {
            println("Backend localhost:8080 indisponible — test ignoré.")
            return@runBlocking
        }

        val email = "project-md-${System.currentTimeMillis()}@local.dev"
        repo.register(email, "ProjectPass1234!", "Project MD")
        repo.verifyEmail(email, latestCodeFor(email))
        repo.login(email, "ProjectPass1234!")

        val localId = projectRepo.createProject(
            CreateProjectInput("Chantier E2E", null, "Nîmes", null, "Europe/Paris"),
        )
        syncEngine.syncNow()
        val serverId = db.projectDao().findByLocalId(localId)?.serverId
        assertTrue(serverId != null, "un id serveur a été attribué au sync")

        // Pull ciblé du projet + de ses membres (nouveau en Phase D) : le créateur est ADMIN.
        projectRepo.refreshProject(localId)
        val members = projectRepo.observeMembers(localId).first()
        assertTrue(members.any { it.email == email && it.role == ProjectRole.ADMIN }, "le créateur est membre ADMIN")

        // Hors ligne : l'édition reste locale, en attente — pas de push concurrent.
        connectivity.setOnline(false)
        projectRepo.updateProject(
            localId,
            UpdateProjectInput("Chantier E2E renommé", "Édité par le test", "Nîmes", "EUR", "Europe/Paris", ProjectStatus.SUSPENDED),
        )
        assertEquals(PendingOp.UPDATE, db.projectDao().findByLocalId(localId)!!.pendingOp)
        assertEquals("SUSPENDED", db.projectDao().findByLocalId(localId)!!.status)

        // Retour en ligne : l'édition est poussée (le backend sérialise updatedAt en fuseau
        // serveur ≠ UTC — le fix ADR-21 ne s'appuie plus sur l'horloge appareil).
        connectivity.setOnline(true)
        syncEngine.syncNow()
        assertEquals("Chantier E2E renommé", ProjectApi(client).get(serverId!!).name, "l'édition offline a bien atteint le serveur")
        assertEquals(PendingOp.NONE, db.projectDao().findByLocalId(localId)!!.pendingOp)

        // Suppression : push DELETE inconditionnel → disparue des deux côtés.
        projectRepo.deleteProject(localId)
        syncEngine.syncNow()
        assertEquals(null, db.projectDao().findByLocalId(localId))
        assertTrue(projectRepo.observeProjects().first().none { it.localId == localId })
        val stillOnServer = runCatching { ProjectApi(client).get(serverId!!) }.isSuccess
        assertTrue(!stillOnServer, "le projet a bien été supprimé côté serveur")
    }

    private fun backendUp(): Boolean = runCatching {
        val conn = URI("http://localhost:8080/actuator/health").toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 1000
        conn.readTimeout = 1000
        conn.responseCode in 200..499
    }.getOrDefault(false)

    private fun latestCodeFor(email: String, differentFrom: String? = null): String {
        val sixDigits = Regex("""\b\d{6}\b""")
        repeat(20) {
            val messages = runCatching {
                Json.parseToJsonElement(URI("http://localhost:1080/email").toURL().readText()).jsonArray
            }.getOrNull() ?: emptyList()
            val message = messages.lastOrNull { el ->
                val o = el.jsonObject
                listOf("to", "envelope", "headers").any { key -> o[key]?.toString()?.contains(email) == true }
            }
            val body = message?.jsonObject?.let { o ->
                (o["text"] ?: o["html"])?.jsonPrimitive?.contentOrNull
            }
            val code = body?.let { sixDigits.find(it)?.value }
            if (code != null && code != differentFrom) return code
            Thread.sleep(300)
        }
        error("Aucun code à 6 chiffres pour $email dans MailDev")
    }
}
