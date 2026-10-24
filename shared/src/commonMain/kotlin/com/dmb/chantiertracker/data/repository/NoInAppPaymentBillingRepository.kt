package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.remote.BillingApi
import com.dmb.chantiertracker.domain.model.BillingAvailability
import com.dmb.chantiertracker.domain.model.BillingCycle
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.repository.BillingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

fun billingRepositoryFor(api: BillingApi, inAppPaymentsAllowed: Boolean): BillingRepository =
    if (inAppPaymentsAllowed) BillingRepositoryImpl(api) else NoInAppPaymentBillingRepository

// Google Play payments policy: where the store forbids an outside payment, paid plans read as
// closed for good, so every screen hides prices and offers, and no checkout or portal can open.
object NoInAppPaymentBillingRepository : BillingRepository {

    override val availability: StateFlow<BillingAvailability> = MutableStateFlow(BillingAvailability.CLOSED).asStateFlow()

    override suspend fun refreshAvailability() = Unit

    override suspend fun startCheckout(plan: Plan, billingCycle: BillingCycle): String = throw DomainException.BillingNotOpen

    override suspend fun openManageSubscription(): String = throw DomainException.BillingNotOpen
}
