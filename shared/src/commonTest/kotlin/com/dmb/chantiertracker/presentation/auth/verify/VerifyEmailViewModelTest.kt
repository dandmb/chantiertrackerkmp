package com.dmb.chantiertracker.presentation.auth.verify

import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.validation_code_format
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class VerifyEmailViewModelTest {

    private lateinit var repo: FakeAuthRepository
    private lateinit var viewModel: VerifyEmailViewModel

    @BeforeTest
    fun setUp() {
        installTestMainDispatcher()
        repo = FakeAuthRepository()
        viewModel = VerifyEmailViewModel(repo)
    }

    @AfterTest
    fun tearDown() = resetTestMainDispatcher()

    @Test
    fun short_code_blocks_submission() = runTest {
        viewModel.onCodeChange("123")
        viewModel.submit("alice@example.com")
        advanceUntilIdle()
        assertEquals(Res.string.validation_code_format, viewModel.state.value.codeError)
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun valid_code_verifies_and_flags_success() = runTest {
        viewModel.onCodeChange("123456")
        viewModel.submit("alice@example.com")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.verified)
        assertEquals(listOf("verifyEmail:alice@example.com:123456"), repo.calls)
    }

    @Test
    fun invalid_code_shows_error_and_no_success() = runTest {
        repo.error = DomainException.InvalidCode
        viewModel.onCodeChange("000000")
        viewModel.submit("alice@example.com")
        advanceUntilIdle()
        assertIs<DomainException.InvalidCode>(viewModel.state.value.formError)
        assertFalse(viewModel.state.value.verified)
    }

    @Test
    fun resend_reports_confirmation() = runTest {
        viewModel.resendCode("alice@example.com")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.codeResent)
    }

    @Test
    fun right_after_registering_the_password_is_not_asked_again() = runTest {
        repo.heldVerificationEmail = "alice@example.com"
        viewModel.start("alice@example.com")
        viewModel.onCodeChange("123456")
        viewModel.submit("alice@example.com")
        advanceUntilIdle()

        assertFalse(viewModel.state.value.asksPassword)
        assertEquals(listOf("verifyEmail:alice@example.com:123456"), repo.calls)
    }

    @Test
    fun after_an_unverified_login_a_new_code_is_requested_once_and_the_password_is_not_asked() = runTest {
        repo.heldVerificationEmail = "alice@example.com"

        viewModel.start("alice@example.com", requestCode = true)
        advanceUntilIdle()
        viewModel.start("alice@example.com", requestCode = true)
        advanceUntilIdle()

        assertEquals(listOf("resendCode:alice@example.com"), repo.calls)
        assertTrue(viewModel.state.value.codeResent)
        assertFalse(viewModel.state.value.asksPassword)

        viewModel.onCodeChange("123456")
        viewModel.submit("alice@example.com")
        advanceUntilIdle()

        assertEquals("verifyEmail:alice@example.com:123456", repo.calls.last())
        assertTrue(viewModel.state.value.verified)
    }

    @Test
    fun a_refused_automatic_code_request_shows_its_wait() = runTest {
        repo.heldVerificationEmail = "alice@example.com"
        repo.error = DomainException.RateLimited(90)

        viewModel.start("alice@example.com", requestCode = true)
        advanceUntilIdle()

        assertEquals(DomainException.RateLimited(90), viewModel.state.value.formError)
    }

    @Test
    fun after_a_restart_the_password_is_asked_again() = runTest {
        viewModel.start("alice@example.com")
        viewModel.onCodeChange("123456")
        viewModel.submit("alice@example.com")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.asksPassword)
        assertEquals(Res.string.validation_password_required, viewModel.state.value.passwordError)
        assertTrue(repo.calls.isEmpty())

        viewModel.onPasswordChange("Password1234!")
        viewModel.submit("alice@example.com")
        advanceUntilIdle()

        assertEquals(listOf("verifyEmail:alice@example.com:123456:Password1234!"), repo.calls)
    }
}
