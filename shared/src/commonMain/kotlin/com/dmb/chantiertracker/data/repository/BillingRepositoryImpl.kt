package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.remote.BillingApi
import com.dmb.chantiertracker.data.remote.apiCall
import com.dmb.chantiertracker.domain.model.BillingAvailability
import com.dmb.chantiertracker.domain.model.BillingCycle
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.repository.BillingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// Online only, no Room cache — see BillingRepository / ADR-49. Talks to
// BillingApi directly via apiCall, like ExportRepositoryImpl; unlike it,
// there's no project-scoped server id to resolve — billing is per account.
class BillingRepositoryImpl(private val api: BillingApi) : BillingRepository {

    private val _availability = MutableStateFlow(BillingAvailability.UNKNOWN)
    override val availability = _availability.asStateFlow()

    override suspend fun refreshAvailability() {
        _availability.value = try {
            if (apiCall { api.status() }.billingOpen) BillingAvailability.OPEN else BillingAvailability.CLOSED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            BillingAvailability.UNKNOWN
        }
    }

    override suspend fun startCheckout(plan: Plan, billingCycle: BillingCycle): String =
        try {
            billingCall { api.checkout(plan.name, billingCycle.name).checkoutUrl }
        } catch (e: DomainException.BillingNotOpen) {
            _availability.value = BillingAvailability.CLOSED
            throw e
        }

    override suspend fun openManageSubscription(): String =
        billingCall { api.portal().portalUrl }

    // apiCall's generic 400-without-field-errors mapping (DomainException.InvalidCode)
    // reads as "this code is invalid or expired" — meaningless for a billing
    // failure (UnsupportedPlanException/NoStripeCustomerException). Neither
    // should fire in practice (the UI gates both actions), but if one does,
    // Unexpected is the honest generic message instead of a nonsensical one.
    private suspend fun billingCall(block: suspend () -> String): String =
        try {
            apiCall(block)
        } catch (e: CancellationException) {
            throw e
        } catch (e: DomainException.InvalidCode) {
            throw DomainException.Unexpected
        }
}
