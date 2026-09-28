package com.dmb.chantiertracker.presentation.billing

import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.domain.model.BillingCycle
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.repository.BillingRepository
import kotlinx.coroutines.launch

/**
 * Starts a checkout right after a successful login (ADR-50) — fire-and-forget,
 * exactly the shape of `Syncer.requestSync()`. Deliberately **not** a suspend
 * function: `LoginViewModel.submit()` calls `authRepository.login(...)`, which
 * updates `AuthState` to `Authenticated` as its very last step — `RootNavHost`
 * reacts to that immediately by tearing down the whole auth nav graph
 * (`LoginScreen`/`LoginViewModel` included). There is no "login succeeded"
 * callback to hook into on mobile the way the web intercepts its own
 * `navigate('/app')` — the checkout call must survive `LoginViewModel` being
 * cleared out from under it, so it can never run on `viewModelScope`.
 */
fun interface CheckoutLauncher {
    fun launch(plan: Plan, billingCycle: BillingCycle)
}

/**
 * Runs the checkout on [appScope] — an application-lifetime scope, never a
 * screen's (same posture as `SyncEngine`, which documents this exact
 * reasoning for `requestSync()`). A failure is swallowed deliberately,
 * mirroring the web (`LoginPage.tsx`): the user has already reached
 * `MainScreen` by the time this could fail, and retrying isn't possible from
 * here anyway — they can always retry from `BillingScreen`.
 */
class AppScopeCheckoutLauncher(
    private val billingRepository: BillingRepository,
    private val urlOpener: UrlOpener,
    private val appScope: AppCoroutineScope,
) : CheckoutLauncher {
    override fun launch(plan: Plan, billingCycle: BillingCycle) {
        appScope.launch {
            try {
                val url = billingRepository.startCheckout(plan, billingCycle)
                urlOpener.open(url)
            } catch (e: Throwable) {
                // Deliberately swallowed — see the class doc.
            }
        }
    }
}
