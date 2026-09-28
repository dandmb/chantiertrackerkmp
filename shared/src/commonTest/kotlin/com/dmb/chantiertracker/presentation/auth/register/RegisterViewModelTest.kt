package com.dmb.chantiertracker.presentation.auth.register

import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.resources.Res
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {

    private lateinit var repo: FakeAuthRepository
    private lateinit var viewModel: RegisterViewModel

    @BeforeTest
    fun setUp() {
        installTestMainDispatcher()
        repo = FakeAuthRepository()
        viewModel = RegisterViewModel(repo)
    }

    @AfterTest
    fun tearDown() = resetTestMainDispatcher()

    @Test
    fun weak_password_blocks_submission() = runTest {
        viewModel.onNameChange("Alice")
        viewModel.onEmailChange("alice@example.com")
        viewModel.onPasswordChange("weak")
        viewModel.submit()
        advanceUntilIdle()
        assertEquals(Res.string.validation_password_too_short, viewModel.state.value.passwordError)
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun successful_registration_exposes_email_for_navigation() = runTest {
        viewModel.onNameChange("Alice")
        viewModel.onEmailChange("alice@example.com")
        viewModel.onPasswordChange("Abcdefgh1234!")
        viewModel.submit()
        advanceUntilIdle()
        assertEquals("alice@example.com", viewModel.state.value.registeredEmail)
        assertEquals(listOf("register:alice@example.com:Abcdefgh1234!:Alice"), repo.calls)
    }

    @Test
    fun email_already_used_shows_form_error() = runTest {
        repo.error = DomainException.EmailAlreadyUsed
        viewModel.onNameChange("Alice")
        viewModel.onEmailChange("alice@example.com")
        viewModel.onPasswordChange("Abcdefgh1234!")
        viewModel.submit()
        advanceUntilIdle()
        assertIs<DomainException.EmailAlreadyUsed>(viewModel.state.value.formError)
        assertNull(viewModel.state.value.registeredEmail)
    }

    @Test
    fun backend_validation_error_shows_form_error() = runTest {
        repo.error = DomainException.Validation
        viewModel.onNameChange("Alice")
        viewModel.onEmailChange("alice@example.com")
        viewModel.onPasswordChange("Abcdefgh1234!")
        viewModel.submit()
        advanceUntilIdle()
        assertIs<DomainException.Validation>(viewModel.state.value.formError)
    }
}
