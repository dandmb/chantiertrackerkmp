package com.dmb.chantiertracker.presentation.auth.forgot

import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.validation_email_invalid
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ForgotPasswordViewModelTest {

    private lateinit var repo: FakeAuthRepository
    private lateinit var viewModel: ForgotPasswordViewModel

    @BeforeTest
    fun setUp() {
        installTestMainDispatcher()
        repo = FakeAuthRepository()
        viewModel = ForgotPasswordViewModel(repo)
    }

    @AfterTest
    fun tearDown() = resetTestMainDispatcher()

    @Test
    fun invalid_email_blocks_submission() = runTest {
        viewModel.onEmailChange("bad")
        viewModel.submit()
        advanceUntilIdle()
        assertEquals(Res.string.validation_email_invalid, viewModel.state.value.emailError)
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun valid_email_triggers_request_and_exposes_email() = runTest {
        viewModel.onEmailChange("alice@example.com")
        viewModel.submit()
        advanceUntilIdle()
        assertEquals("alice@example.com", viewModel.state.value.submittedEmail)
        assertEquals(listOf("forgotPassword:alice@example.com"), repo.calls)
    }
}
