package com.dmb.chantiertracker.presentation.admin

import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.validation_password_too_short
import com.dmb.chantiertracker.support.FakeAdminRepository
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
class AdminCreateUserViewModelTest {

    private lateinit var repo: FakeAdminRepository
    private lateinit var viewModel: AdminCreateUserViewModel

    @BeforeTest
    fun setUp() {
        installTestMainDispatcher()
        repo = FakeAdminRepository()
        viewModel = AdminCreateUserViewModel(repo)
    }

    @AfterTest
    fun tearDown() = resetTestMainDispatcher()

    @Test
    fun defaults_to_the_user_role() {
        assertEquals(GlobalRole.USER, viewModel.state.value.globalRole)
    }

    @Test
    fun weak_password_blocks_submission() = runTest {
        viewModel.onNameChange("Dan")
        viewModel.onEmailChange("dan@chantier.dev")
        viewModel.onPasswordChange("weak")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(Res.string.validation_password_too_short, viewModel.state.value.passwordError)
        assertTrue(repo.createCalls.isEmpty())
    }

    @Test
    fun successful_creation_sends_the_chosen_role() = runTest {
        viewModel.onNameChange("Dan")
        viewModel.onEmailChange("dan@chantier.dev")
        viewModel.onPasswordChange("Abcdefgh1234!")
        viewModel.onRoleChange(GlobalRole.SUPER_ADMIN)
        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.created)
        val call = repo.createCalls.single()
        assertEquals("dan@chantier.dev", call.email)
        assertEquals("Dan", call.name)
        assertEquals(GlobalRole.SUPER_ADMIN, call.globalRole)
    }

    @Test
    fun duplicate_email_shows_form_error_and_does_not_navigate() = runTest {
        repo.createError = DomainException.EmailAlreadyUsed
        viewModel.onNameChange("Dan")
        viewModel.onEmailChange("dan@chantier.dev")
        viewModel.onPasswordChange("Abcdefgh1234!")
        viewModel.submit()
        advanceUntilIdle()

        assertIs<DomainException.EmailAlreadyUsed>(viewModel.state.value.formError)
        assertFalse(viewModel.state.value.created)
    }
}
