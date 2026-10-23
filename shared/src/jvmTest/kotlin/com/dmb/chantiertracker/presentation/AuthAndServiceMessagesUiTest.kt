package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.data.AuthStateHolder
import com.dmb.chantiertracker.data.remote.AuthApi
import com.dmb.chantiertracker.data.repository.AuthRepositoryImpl
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.presentation.auth.forgot.ForgotPasswordScreen
import com.dmb.chantiertracker.presentation.auth.forgot.ForgotPasswordViewModel
import com.dmb.chantiertracker.presentation.auth.reset.ResetPasswordScreen
import com.dmb.chantiertracker.presentation.auth.reset.ResetPasswordViewModel
import com.dmb.chantiertracker.presentation.auth.verify.VerifyEmailScreen
import com.dmb.chantiertracker.presentation.auth.verify.VerifyEmailViewModel
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.projects.history.ProjectHistoryScreen
import com.dmb.chantiertracker.presentation.projects.history.ProjectHistoryViewModel
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeHistoryRepository
import com.dmb.chantiertracker.support.FakeOnboardingStore
import com.dmb.chantiertracker.support.FakeProjectRepository
import com.dmb.chantiertracker.support.FakeSyncer
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.RecordingMockClient
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import com.dmb.chantiertracker.support.respondProblem
import com.dmb.chantiertracker.support.respondWhileRedeploying
import com.dmb.chantiertracker.support.testOwnership
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AuthAndServiceMessagesUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private val wrongCodeText = "Ce code est invalide"

    private fun authRepository(answer: io.ktor.client.engine.mock.MockRequestHandleScope.() -> io.ktor.client.request.HttpResponseData): AuthRepositoryImpl {
        val storage = FakeTokenStorage()
        val mock = RecordingMockClient(storage) { answer() }
        return AuthRepositoryImpl(AuthApi(mock.client), storage, AuthStateHolder(), FakeOnboardingStore(), FakeSyncer(), testOwnership())
    }

    private fun rateLimited() = authRepository {
        respondProblem(HttpStatusCode.TooManyRequests, "Trop de requêtes.", code = "RATE_LIMITED", extraHeaders = mapOf(HttpHeaders.RetryAfter to "42"))
    }

    private fun ComposeUiTest.show(screen: @Composable () -> Unit) = setContent {
        customAppLocale = "fr"
        AppEnvironment { AppTheme { Box(Modifier.size(412.dp, 892.dp)) { screen() } } }
    }

    private fun ComposeUiTest.awaitText(text: String) =
        waitUntil(timeoutMillis = 5_000L) { onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.assertNoText(text: String) {
        waitForIdle()
        check(onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isEmpty()) { "« $text » must not be shown" }
    }

    @Test
    fun resending_the_code_too_often_shows_the_wait_never_a_wrong_code() = runComposeUiTest {
        val viewModel = VerifyEmailViewModel(rateLimited())
        show { VerifyEmailScreen("sam@chantier.dev", onVerified = {}, onCancelVerification = {}, viewModel = viewModel) }

        runOnIdle { viewModel.resendCode("sam@chantier.dev") }

        awaitText("Réessayez dans 42 secondes")
        assertNoText(wrongCodeText)
    }

    @Test
    fun verifying_too_often_shows_the_wait_never_a_wrong_code() = runComposeUiTest {
        val viewModel = VerifyEmailViewModel(rateLimited())
        show { VerifyEmailScreen("sam@chantier.dev", onVerified = {}, onCancelVerification = {}, viewModel = viewModel) }

        runOnIdle {
            viewModel.onCodeChange("123456")
            viewModel.submit("sam@chantier.dev")
        }

        awaitText("Réessayez dans 42 secondes")
        assertNoText(wrongCodeText)
    }

    @Test
    fun asking_for_a_reset_too_often_shows_the_wait() = runComposeUiTest {
        val viewModel = ForgotPasswordViewModel(rateLimited())
        show { ForgotPasswordScreen(onCodeSent = {}, viewModel = viewModel) }

        runOnIdle {
            viewModel.onEmailChange("sam@chantier.dev")
            viewModel.submit()
        }

        awaitText("Réessayez dans 42 secondes")
    }

    @Test
    fun resetting_too_often_shows_the_wait_never_a_wrong_code() = runComposeUiTest {
        val viewModel = ResetPasswordViewModel(rateLimited())
        show { ResetPasswordScreen("sam@chantier.dev", onReset = {}, onBackToLogin = {}, viewModel = viewModel) }

        runOnIdle {
            viewModel.onCodeChange("123456")
            viewModel.onPasswordChange("NewPass1234!")
            viewModel.submit("sam@chantier.dev")
        }

        awaitText("Réessayez dans 42 secondes")
        assertNoText(wrongCodeText)
    }

    @Test
    fun a_wrong_code_says_to_ask_for_a_new_one_after_several_attempts() = runComposeUiTest {
        val viewModel = VerifyEmailViewModel(authRepository { respondProblem(HttpStatusCode.BadRequest, "Code invalide.", code = "INVALID_VERIFICATION_CODE") })
        show { VerifyEmailScreen("sam@chantier.dev", onVerified = {}, onCancelVerification = {}, viewModel = viewModel) }

        runOnIdle {
            viewModel.onCodeChange("000000")
            viewModel.submit("sam@chantier.dev")
        }

        awaitText("Après plusieurs essais, demandez un nouveau code")
    }

    @Test
    fun an_online_only_screen_during_a_redeploy_says_the_service_is_unavailable_not_that_the_network_is_down() = runComposeUiTest {
        val history = FakeHistoryRepository().apply { error = DomainException.ServiceUnavailable }
        val viewModel = ProjectHistoryViewModel(
            history,
            FakeProjectRepository(
                detail = ProjectDetail(
                    localId = "p1", name = "Villa", description = null, location = null,
                    currency = "EUR", timezone = "Europe/Paris", status = ProjectStatus.IN_PROGRESS,
                    ownerId = 1L, ownerPlan = Plan.LIBERTE,
                ),
            ),
        )
        show { ProjectHistoryScreen("p1", viewModel = viewModel) }

        awaitText("Le service est momentanément indisponible")
        assertNoText("connexion")
    }

    @Test
    fun the_answer_of_the_proxy_during_a_redeploy_reaches_the_screens_as_an_unavailable_service() = runComposeUiTest {
        val viewModel = ForgotPasswordViewModel(authRepository { respondWhileRedeploying() })
        show { ForgotPasswordScreen(onCodeSent = {}, viewModel = viewModel) }

        runOnIdle {
            viewModel.onEmailChange("sam@chantier.dev")
            viewModel.submit()
        }

        awaitText("Le service est momentanément indisponible")
    }
}
