package com.dmb.chantiertracker.presentation.auth.changepassword

import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.validation_password_required
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
class ChangePasswordViewModelTest {

    private lateinit var repo: FakeAuthRepository
    private lateinit var viewModel: ChangePasswordViewModel

    @BeforeTest
    fun setUp() {
        installTestMainDispatcher()
        repo = FakeAuthRepository()
        viewModel = ChangePasswordViewModel(repo)
    }

    @AfterTest
    fun tearDown() = resetTestMainDispatcher()

    @Test
    fun invalid_fields_block_submission() = runTest {
        viewModel.onCurrentPasswordChange("")
        viewModel.onNewPasswordChange("weak")
        viewModel.submit("dan@chantier.dev")
        advanceUntilIdle()

        assertEquals(Res.string.validation_password_required, viewModel.state.value.currentPasswordError)
        assertEquals(Res.string.validation_password_too_short, viewModel.state.value.newPasswordError)
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun a_successful_change_immediately_logs_in_again_with_the_new_password() = runTest {
        viewModel.onCurrentPasswordChange("TempPass1234!")
        viewModel.onNewPasswordChange("Abcdefgh1234!")
        viewModel.submit("dan@chantier.dev")
        advanceUntilIdle()

        assertEquals(
            listOf(
                "changePassword:TempPass1234!:Abcdefgh1234!",
                "login:dan@chantier.dev:Abcdefgh1234!",
            ),
            repo.calls,
        )
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun a_wrong_current_password_shows_a_form_error_and_never_attempts_the_re_login() = runTest {
        repo.error = DomainException.InvalidCurrentPassword
        viewModel.onCurrentPasswordChange("wrong")
        viewModel.onNewPasswordChange("Abcdefgh1234!")
        viewModel.submit("dan@chantier.dev")
        advanceUntilIdle()

        assertIs<DomainException.InvalidCurrentPassword>(viewModel.state.value.formError)
        assertEquals(listOf("changePassword:wrong:Abcdefgh1234!"), repo.calls)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun logout_delegates_to_the_repository() = runTest {
        viewModel.logout()
        advanceUntilIdle()

        assertEquals(listOf("logout"), repo.calls)
    }
}
