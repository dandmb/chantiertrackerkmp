package com.dmb.chantiertracker.presentation.auth.reset

import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.validation_code_format
import com.dmb.chantiertracker.resources.validation_password_too_short
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
class ResetPasswordViewModelTest {

    private lateinit var repo: FakeAuthRepository
    private lateinit var viewModel: ResetPasswordViewModel

    @BeforeTest
    fun setUp() {
        installTestMainDispatcher()
        repo = FakeAuthRepository()
        viewModel = ResetPasswordViewModel(repo)
    }

    @AfterTest
    fun tearDown() = resetTestMainDispatcher()

    @Test
    fun invalid_fields_block_submission() = runTest {
        viewModel.onCodeChange("12")
        viewModel.onPasswordChange("weak")
        viewModel.submit("alice@example.com")
        advanceUntilIdle()
        assertEquals(Res.string.validation_code_format, viewModel.state.value.codeError)
        assertEquals(Res.string.validation_password_too_short, viewModel.state.value.passwordError)
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun valid_reset_flags_success() = runTest {
        viewModel.onCodeChange("123456")
        viewModel.onPasswordChange("Abcdefgh1234!")
        viewModel.submit("alice@example.com")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.reset)
        assertEquals(listOf("resetPassword:alice@example.com:123456:Abcdefgh1234!"), repo.calls)
    }

    @Test
    fun expired_code_shows_error() = runTest {
        repo.error = DomainException.InvalidCode
        viewModel.onCodeChange("123456")
        viewModel.onPasswordChange("Abcdefgh1234!")
        viewModel.submit("alice@example.com")
        advanceUntilIdle()
        assertIs<DomainException.InvalidCode>(viewModel.state.value.formError)
        assertFalse(viewModel.state.value.reset)
    }
}
