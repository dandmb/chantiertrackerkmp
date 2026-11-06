package com.dmb.chantiertracker

import com.dmb.chantiertracker.data.remote.dto.CreateInvitationRequestDto
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.presentation.auth.login.LoginViewModel
import com.dmb.chantiertracker.presentation.auth.verify.VerifyEmailViewModel
import com.dmb.chantiertracker.support.DeviceStack
import com.dmb.chantiertracker.support.DisposableAccounts
import com.dmb.chantiertracker.support.FakeBillingRepository
import com.dmb.chantiertracker.support.FakeCheckoutLauncher
import com.dmb.chantiertracker.support.retryingOnRateLimit
import com.dmb.chantiertracker.support.skipUnless
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Recette 3, point 11 : inscription réelle, app fermée sans saisir le code, app rouverte (nouvelle
 * pile, mémoire vide), connexion. ACCOUNT_NOT_VERIFIED doit ouvrir l'écran du code, qui redemande
 * un code ; le code lu dans MailDev active le compte. Point 12 joué au passage : s'inviter soi-même
 * est refusé par le serveur. Le compte se supprime lui-même en fin de test. L'inscription prend une
 * place fondateur : `-Dchantiertracker.integrationAllowFounderSeat=true` requis.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UnverifiedSignInIntegrationTest {

    private val accounts = DisposableAccounts()
    private val devices = mutableListOf<DeviceStack>()

    @BeforeTest fun setUp() { Dispatchers.setMain(Dispatchers.Default) }

    @AfterTest
    fun cleanUp() {
        devices.forEach { it.close() }
        accounts.close()
        Dispatchers.resetMain()
    }

    private fun codesFor(email: String): List<String> {
        val codeCell = Regex("""class="ct-code"[^>]*>\s*(\d{6})\s*<""")
        val messages = runCatching { Json.parseToJsonElement(URI("http://localhost:1080/email").toURL().readText()).jsonArray }.getOrNull().orEmpty()
        return messages.filter { it.jsonObject["to"]?.toString()?.contains(email) == true }
            .mapNotNull { codeCell.find(it.jsonObject["html"]?.jsonPrimitive?.contentOrNull.orEmpty())?.groupValues?.get(1) }
    }

    private suspend fun awaitNewCode(email: String, alreadySeen: Int): String {
        repeat(40) {
            val codes = codesFor(email)
            if (codes.size > alreadySeen) return codes.last()
            delay(300)
        }
        error("Aucun nouveau code pour $email dans MailDev")
    }

    @Test
    fun registered_then_app_closed_then_reopened_the_sign_in_opens_the_code_screen_and_activates_the_account(): Unit = runBlocking {
        com.dmb.chantiertracker.support.skipUnlessRealBackendScenariosAreEnabled()
        skipUnless(
            System.getProperty("chantiertracker.integrationAllowFounderSeat") == "true",
            "inscription réelle : elle prend une place fondateur (-Dchantiertracker.integrationAllowFounderSeat=true requis)",
        )
        val email = "qa-recette3-${System.currentTimeMillis()}@local.dev"
        val password = "QaRecette3Pass1234!"

        val firstLaunch = DeviceStack().also { devices += it }
        retryingOnRateLimit { firstLaunch.auth.register(email, password, "QA Recette 3") }
        val codesAfterSignUp = awaitNewCode(email, alreadySeen = 0).let { codesFor(email).size }
        firstLaunch.close()
        devices -= firstLaunch
        println("Recette 3 — inscrit $email, app fermée sans saisir le code")

        val reopened = DeviceStack().also { devices += it }
        assertFalse(reopened.auth.holdsVerificationPassword(email), "app rouverte : rien en mémoire")
        val login = LoginViewModel(reopened.auth, FakeCheckoutLauncher(), FakeBillingRepository())
        login.onEmailChange(email)
        login.onPasswordChange(password)
        login.submit()
        withTimeout(20_000) { login.state.first { it.opensVerification || it.formError != null } }
        assertTrue(login.state.value.opensVerification, "ACCOUNT_NOT_VERIFIED ouvre l'écran du code : ${login.state.value.formError}")
        assertEquals(email, reopened.auth.pendingVerificationEmail())
        println("Recette 3 — connexion refusée ACCOUNT_NOT_VERIFIED, écran du code ouvert")

        val verify = VerifyEmailViewModel(reopened.auth)
        verify.start(email, requestCode = true)
        withTimeout(20_000) { verify.state.first { it.codeResent || it.formError != null } }
        assertTrue(verify.state.value.codeResent, "nouveau code demandé : ${verify.state.value.formError}")
        assertFalse(verify.state.value.asksPassword, "mot de passe gardé en mémoire depuis la connexion")
        val code = awaitNewCode(email, alreadySeen = codesAfterSignUp)

        verify.onCodeChange(code)
        verify.submit(email)
        withTimeout(20_000) { verify.state.first { it.verified || it.formError != null } }
        assertTrue(verify.state.value.verified, "compte activé : ${verify.state.value.formError}")
        println("Recette 3 — code saisi, compte activé")

        retryingOnRateLimit { reopened.auth.login(email, password) }
        val signedIn = assertIs<AuthState.Authenticated>(reopened.authState.state.value)
        var deleted = false
        try {
            val projectLocalId = reopened.projects.createProject(CreateProjectInput("QA recette 3", null, null, "EUR", "Europe/Paris"))
            reopened.waitForTheRunningSyncPass()
            reopened.sync.syncNow()
            val serverId = reopened.db.projectDao().findByLocalId(projectLocalId)?.serverId ?: error("projet non synchronisé")
            assertFailsWith<DomainException.CannotInviteSelf> {
                com.dmb.chantiertracker.data.remote.apiCall {
                    reopened.invitationApi.create(serverId, CreateInvitationRequestDto(email.uppercase(), "SUPERVISOR"), idempotencyKey = null)
                }
            }
            println("Recette 3 — s'inviter soi-même : CANNOT_INVITE_SELF")

            reopened.accountDeletion.deleteAccount(password)
            deleted = true
            println("Recette 3 — compte supprimé par lui-même")
        } finally {
            if (!deleted) accounts.track(signedIn.user.id)
        }
    }
}
