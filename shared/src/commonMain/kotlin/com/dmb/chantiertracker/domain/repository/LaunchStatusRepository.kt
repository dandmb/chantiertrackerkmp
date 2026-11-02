package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.BillingAvailability
import com.dmb.chantiertracker.domain.model.FounderOffer
import kotlinx.coroutines.flow.StateFlow

// The server's billing switch as it really is, for wording only. BillingRepository answers
// "may this app sell?", which is always no on Android: nothing here can open a payment.
interface LaunchStatusRepository {
    val billingStatus: StateFlow<BillingAvailability>

    val founderOffer: StateFlow<FounderOffer?>

    suspend fun refresh()
}
