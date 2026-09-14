package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.remote.dto.BillingPortalResponseDto
import com.dmb.chantiertracker.data.remote.dto.CheckoutRequestDto
import com.dmb.chantiertracker.data.remote.dto.CheckoutResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class BillingApi(private val client: HttpClient) {

    // POST /billing/checkout — starts a hosted Stripe Checkout session for
    // the given plan/cycle; the caller opens checkoutUrl in the system
    // browser, never a payment form built in-app. platform="MOBILE" tells
    // the backend to hand back the chantiertracker:// success/cancel URLs
    // instead of the web frontend's (see ADR-51 point 4 walkthrough).
    suspend fun checkout(plan: String, billingCycle: String): CheckoutResponseDto =
        client.post(ApiRoutes.BILLING_CHECKOUT) {
            contentType(ContentType.Application.Json)
            setBody(CheckoutRequestDto(plan, billingCycle, platform = "MOBILE"))
        }.body()

    // GET /billing/portal — 400 (NoStripeCustomerException) if the caller has
    // never had a Stripe customer; the UI only offers this action when
    // hasStripeCustomer is true, so that should never actually happen.
    // ?platform=MOBILE — same reasoning as checkout() above; no request body
    // on a GET, so it travels as a query param instead.
    suspend fun portal(): BillingPortalResponseDto =
        client.get(ApiRoutes.BILLING_PORTAL) {
            parameter("platform", "MOBILE")
        }.body()
}
