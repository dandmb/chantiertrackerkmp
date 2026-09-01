package com.dmb.chantiertracker.presentation.auth.login

import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.validation_email_invalid
import com.dmb.chantiertracker.resources.validation_password_required
import com.dmb.chantiertracker.support.FakeAuthRepository
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private lateinit var repo: FakeAuthRepository
    private lateinit var viewModel: LoginViewModel

    @BeforeTest
    fun setUp() {
        installTestMainDispatcher()
        repo = FakeAuthRepository()
        viewModel = LoginViewModel(repo)
    }

    @AfterTest
    fun tearDown() = resetTestMainDispatcher()

    @Test
    fun invalid_inputs_block_submission() = runTest {
        viewModel.onEmailChange("bad")
        viewModel.onPasswordChange("")
        viewModel.submit()
        advanceUntilIdle()
        assertEquals(Res.string.validation_email_invalid, viewModel.state.value.emailError)
        assertEquals(Res.string.validation_password_required, viewModel.state.value.passwordError)
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun valid_submission_calls_repository() = runTest {
        viewModel.onEmailChange("user@example.com")
        viewModel.onPasswordChange("secret")
        viewModel.submit()
        advanceUntilIdle()
        assertEquals(listOf("login:user@example.com:secret"), repo.calls)
        assertEquals(null, viewModel.state.value.formError)
    }

    @Test
    fun invalid_credentials_surface_message() = runTest {
        repo.error = DomainException.InvalidCredentials
        viewModel.onEmailChange("user@example.com")
        viewModel.onPasswordChange("secret")
        viewModel.submit()
        advanceUntilIdle()
        assertIs<DomainException.InvalidCredentials>(viewModel.state.value.formError)
        assertEquals(false, viewModel.state.value.isSubmitting)
    }

    @Test
    fun unverified_account_offers_resend() = runTest {
        repo.error = DomainException.AccountNotVerified
        viewModel.onEmailChange("user@example.com")
        viewModel.onPasswordChange("secret")
        viewModel.submit()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.needsVerification)
    }
}
