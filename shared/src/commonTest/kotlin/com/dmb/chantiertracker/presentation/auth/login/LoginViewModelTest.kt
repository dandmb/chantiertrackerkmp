package com.dmb.chantiertracker.presentation.auth.login

import com.dmb.chantiertracker.domain.model.BillingAvailability
import com.dmb.chantiertracker.domain.model.BillingCycle
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.validation_email_invalid
import com.dmb.chantiertracker.resources.validation_password_required
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private lateinit var repo: FakeAuthRepository
    private lateinit var checkoutLauncher: FakeCheckoutLauncher
    private lateinit var billing: FakeBillingRepository
    private lateinit var viewModel: LoginViewModel

    @BeforeTest
    fun setUp() {
        installTestMainDispatcher()
        repo = FakeAuthRepository()
        checkoutLauncher = FakeCheckoutLauncher()
        billing = FakeBillingRepository(BillingAvailability.OPEN)
        viewModel = LoginViewModel(repo, checkoutLauncher, billing)
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

    @Test
    fun successful_login_with_checkout_intent_launches_checkout() = runTest {
        viewModel.setCheckoutIntent("SEMI_FLEX", "MONTHLY")
        viewModel.onEmailChange("user@example.com")
        viewModel.onPasswordChange("secret")
        viewModel.submit()
        advanceUntilIdle()
        assertEquals(listOf(Plan.SEMI_FLEX to BillingCycle.MONTHLY), checkoutLauncher.calls)
    }

    @Test
    fun successful_login_without_checkout_intent_never_launches_checkout() = runTest {
        viewModel.onEmailChange("user@example.com")
        viewModel.onPasswordChange("secret")
        viewModel.submit()
        advanceUntilIdle()
        assertTrue(checkoutLauncher.calls.isEmpty())
    }

    @Test
    fun unresolvable_checkout_intent_is_ignored_on_successful_login() = runTest {
        viewModel.setCheckoutIntent("NOT_A_PLAN", "MONTHLY")
        viewModel.onEmailChange("user@example.com")
        viewModel.onPasswordChange("secret")
        viewModel.submit()
        advanceUntilIdle()
        assertTrue(checkoutLauncher.calls.isEmpty())
    }

    @Test
    fun failed_login_never_launches_checkout_even_with_intent() = runTest {
        repo.error = DomainException.InvalidCredentials
        viewModel.setCheckoutIntent("SEMI_FLEX", "MONTHLY")
        viewModel.onEmailChange("user@example.com")
        viewModel.onPasswordChange("secret")
        viewModel.submit()
        advanceUntilIdle()
        assertTrue(checkoutLauncher.calls.isEmpty())
    }

    private fun loginWithIntent(availability: BillingAvailability): LoginViewModel {
        billing = FakeBillingRepository(availability)
        return LoginViewModel(repo, checkoutLauncher, billing).apply {
            setCheckoutIntent("SEMI_FLEX", "MONTHLY")
            onEmailChange("user@example.com")
            onPasswordChange("secret")
        }
    }

    @Test
    fun a_checkout_intent_is_dropped_when_billing_is_closed() = runTest {
        val vm = loginWithIntent(BillingAvailability.CLOSED)
        advanceUntilIdle()
        assertNull(vm.state.value.checkoutPlan, "no 'finish your subscription' banner")

        vm.submit()
        advanceUntilIdle()

        assertEquals(listOf("login:user@example.com:secret"), repo.calls, "the login itself still goes through")
        assertTrue(checkoutLauncher.calls.isEmpty())
    }

    @Test
    fun a_checkout_intent_is_dropped_when_billing_status_is_unknown() = runTest {
        val vm = loginWithIntent(BillingAvailability.UNKNOWN)
        advanceUntilIdle()
        assertNull(vm.state.value.checkoutPlan)

        vm.submit()
        advanceUntilIdle()

        assertTrue(checkoutLauncher.calls.isEmpty())
    }

    @Test
    fun setting_a_checkout_intent_asks_the_server_again_and_honors_it_once_confirmed_open() = runTest {
        billing = FakeBillingRepository(BillingAvailability.UNKNOWN).apply { refreshedAvailability = BillingAvailability.OPEN }
        val vm = LoginViewModel(repo, checkoutLauncher, billing)

        vm.setCheckoutIntent("LIBERTE", "YEARLY")
        advanceUntilIdle()

        assertEquals(1, billing.refreshAvailabilityCount)
        assertEquals(Plan.LIBERTE, vm.state.value.checkoutPlan)
        assertEquals(BillingCycle.YEARLY, vm.state.value.checkoutCycle)
    }

    @Test
    fun no_checkout_intent_means_no_extra_status_request() = runTest {
        viewModel.setCheckoutIntent(null, null)
        advanceUntilIdle()
        assertEquals(0, billing.refreshAvailabilityCount)
    }

    @Test
    fun an_intent_already_shown_is_withdrawn_when_billing_closes_before_login() = runTest {
        val vm = loginWithIntent(BillingAvailability.OPEN)
        advanceUntilIdle()
        assertEquals(Plan.SEMI_FLEX, vm.state.value.checkoutPlan)

        billing.availabilityFlow.value = BillingAvailability.CLOSED
        advanceUntilIdle()
        assertNull(vm.state.value.checkoutPlan)

        vm.submit()
        advanceUntilIdle()
        assertTrue(checkoutLauncher.calls.isEmpty())
    }
}
