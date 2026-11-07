package com.dmb.chantiertracker.presentation

import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.ProfileField
import com.dmb.chantiertracker.domain.model.User
import com.dmb.chantiertracker.presentation.account.ProfileViewModel
import com.dmb.chantiertracker.presentation.auth.login.LoginViewModel
import com.dmb.chantiertracker.presentation.auth.verify.VerifyEmailViewModel
import com.dmb.chantiertracker.support.FakeAuthRepository
import com.dmb.chantiertracker.support.FakeBillingRepository
import com.dmb.chantiertracker.support.FakeCheckoutLauncher
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class NewSubmissionClearsMessagesTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    private val refusal = DomainException.ServiceUnavailable

    private fun failingAuth() = FakeAuthRepository().apply { error = refusal }

    @Test
    fun login_a_new_submission_with_a_wrong_field_clears_the_previous_server_message() = runTest {
        val vm = LoginViewModel(failingAuth(), FakeCheckoutLauncher(), FakeBillingRepository())
        vm.onEmailChange("jean@chantier.dev")
        vm.onPasswordChange("Password1234!")
        vm.submit()
        advanceUntilIdle()
        assertEquals(refusal, vm.state.value.formError)

        vm.onEmailChange("")
        vm.submit()
        advanceUntilIdle()
        assertNull(vm.state.value.formError)
        assertNotNull(vm.state.value.emailError)
    }

    @Test
    fun verify_email_asking_for_a_new_code_clears_the_field_message() = runTest {
        val auth = FakeAuthRepository().apply { heldVerificationEmail = "jean@chantier.dev" }
        val vm = VerifyEmailViewModel(auth)
        vm.start("jean@chantier.dev")
        vm.onCodeChange("12")
        vm.submit("jean@chantier.dev")
        advanceUntilIdle()
        assertNotNull(vm.state.value.codeError)

        vm.resendCode("jean@chantier.dev")
        advanceUntilIdle()
        assertNull(vm.state.value.codeError)
    }

    @Test
    fun profile_a_new_submission_clears_the_field_messages_the_server_sent_before() = runTest {
        val auth = FakeAuthRepository().apply {
            emitState(AuthState.Authenticated(User(7, "jean@chantier.dev", "Jean", true, GlobalRole.USER)))
            error = DomainException.InvalidProfile(setOf(ProfileField.LANGUAGE))
        }
        val vm = ProfileViewModel(auth)
        advanceUntilIdle()
        vm.submit()
        advanceUntilIdle()
        assertNotNull(vm.state.value.languageError)

        auth.error = refusal
        vm.submit()
        advanceUntilIdle()
        assertNull(vm.state.value.languageError)
        assertEquals(refusal, vm.state.value.formError)

        vm.onNameChange("")
        vm.submit()
        advanceUntilIdle()
        assertNull(vm.state.value.formError)
        assertNotNull(vm.state.value.nameError)
    }

}
