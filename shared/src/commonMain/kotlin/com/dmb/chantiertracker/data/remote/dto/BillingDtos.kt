package com.dmb.chantiertracker.data.remote.dto

import kotlinx.serialization.Serializable

// Mirrors CheckoutRequest — plan/billingCycle as the raw backend enum names
// ("SEMI_FLEX"/"LIBERTE", "MONTHLY"/"YEARLY"), same convention as every other
// enum-carrying request DTO in this app. platform is always "MOBILE" from
// this client's own point of view (never a caller-supplied value) — it only
// selects which of two backend-owned redirect URL sets Stripe gets, see
// BillingApi.
@Serializable
data class CheckoutRequestDto(val plan: String, val billingCycle: String, val platform: String = "MOBILE")

@Serializable
data class CheckoutResponseDto(val checkoutUrl: String)

@Serializable
data class BillingPortalResponseDto(val portalUrl: String)
