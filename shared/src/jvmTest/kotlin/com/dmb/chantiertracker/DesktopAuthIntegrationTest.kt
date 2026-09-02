package com.dmb.chantiertracker

import com.dmb.chantiertracker.data.AuthStateHolder
import com.dmb.chantiertracker.data.local.DesktopOnboardingStore
import com.dmb.chantiertracker.data.local.DesktopTokenStorage
import com.dmb.chantiertracker.data.remote.AccountApi
import com.dmb.chantiertracker.data.remote.AuthApi
import com.dmb.chantiertracker.data.remote.ProjectApi
import com.dmb.chantiertracker.data.remote.createHttpClient
import com.dmb.chantiertracker.data.remote.httpClientEngine
import com.dmb.chantiertracker.data.repository.AccountRepositoryImpl
import com.dmb.chantiertracker.data.repository.AuthRepositoryImpl
import com.dmb.chantiertracker.data.repository.ProjectRepositoryImpl
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.domain.model.Plan
import kotlinx.coroutines.runBlocking
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
    private val projectRepo = ProjectRepositoryImpl(ProjectApi(client))
    private val accountRepo = AccountRepositoryImpl(AccountApi(client))

    @AfterTest
    fun cleanUp() {
        client.close()
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
        repo.verifyEmail(email, latestCodeFor(email))
        repo.login(email, firstPassword)
        assertIs<AuthState.Authenticated>(holder.state.value)

        // Le reset révoque tous les tokens serveur — l'ancien couple est encore sur disque.
        repo.forgotPassword(email)
        repo.resetPassword(email, latestCodeFor(email), newPassword)

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

        // Réponse réelle = Page Spring (`{content:[...], ...}`) — on ne lit que `content`.
        val projects = projectRepo.getProjects()
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

        val newId = projectRepo.createProject(
            CreateProjectInput(
                name = "Villa d'intégration",
                description = "Créée par le test E2E",
                location = "Nîmes",
                currency = null,
                timezone = "Europe/Paris",
            ),
        )

        val detail = projectRepo.getProject(newId)
        assertEquals("Villa d'intégration", detail.name)
        assertEquals("USD", detail.currency, "devise absente → USD par défaut côté backend")
        assertEquals("Europe/Paris", detail.timezone)

        // Le créateur est ADMIN sur son projet.
        val members = projectRepo.getMembers(newId)
        assertTrue(members.any { it.userId == (holder.state.value as AuthState.Authenticated).user.id })

        // Le projet apparaît maintenant dans la liste.
        assertTrue(projectRepo.getProjects().any { it.id == newId })
    }

    private fun backendUp(): Boolean = runCatching {
        val conn = URI("http://localhost:8080/actuator/health").toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 1000
        conn.readTimeout = 1000
        conn.responseCode in 200..499
    }.getOrDefault(false)

    private fun latestCodeFor(email: String): String {
        val json = URI("http://localhost:1080/email").toURL().readText()
        val entries = json.split("{\"id\":").drop(1)
        val match = entries.lastOrNull { it.contains(email) }
            ?: error("Aucun email pour $email dans MailDev")
        return Regex("\\b\\d{6}\\b").find(match)?.value
            ?: error("Aucun code à 6 chiffres pour $email")
    }
}
