package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.BillingCycle
import com.dmb.chantiertracker.domain.model.Plan

interface BillingRepository {

    /**
     * Starts a hosted Stripe Checkout session for [plan]/[billingCycle] and
     * returns the URL to open in the system browser.
     *
     * **Online only** (ADR-49) — no Room cache, no SyncEngine, like
     * `ExportRepository`. The URL is single-use and per-account, not
     * per-project — no local/server id to resolve first, unlike
     * `ExportRepository`/`HistoryRepository`.
     *
     * Throws a `DomainException`: `Network` offline, `Unexpected` otherwise
     * (the UI never offers this for `FREE` or while a checkout is genuinely
     * unreachable — `UnsupportedPlanException` shouldn't happen in practice).
     */
    suspend fun startCheckout(plan: Plan, billingCycle: BillingCycle): String

    /**
     * Opens the Stripe billing portal for the caller's existing subscription
     * and returns the URL to open in the system browser.
     *
     * **Online only**, same posture as [startCheckout]. Throws `Unexpected`
     * if the caller has no Stripe customer at all (`NoStripeCustomerException`,
     * 400) — the UI gates the button on `PlanUsage.hasStripeCustomer` so this
     * shouldn't happen in practice either.
     */
    suspend fun openManageSubscription(): String
}
