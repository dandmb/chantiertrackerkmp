package com.dmb.chantiertracker.presentation.auth.verify

import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.validation_code_format
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
}
