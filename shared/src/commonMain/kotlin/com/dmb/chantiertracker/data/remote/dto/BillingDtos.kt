package com.dmb.chantiertracker.data.remote.dto

import kotlinx.serialization.Serializable

// Mirrors CheckoutRequest — plan/billingCycle as the raw backend enum names
// ("SEMI_FLEX"/"LIBERTE", "MONTHLY"/"YEARLY"), same convention as every other
// enum-carrying request DTO in this app.
@Serializable
data class CheckoutRequestDto(val plan: String, val billingCycle: String)

@Serializable
data class CheckoutResponseDto(val checkoutUrl: String)

@Serializable
data class BillingPortalResponseDto(val portalUrl: String)
