package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.BillingAvailability
import com.dmb.chantiertracker.domain.model.BillingCycle
import com.dmb.chantiertracker.domain.model.Plan
import kotlinx.coroutines.flow.StateFlow

interface BillingRepository {

    /**
     * Whether paid tiers may be shown and bought (`GET /billing/status`).
     * **In memory only, never persisted**: starts `UNKNOWN` on every launch and
     * falls back to `UNKNOWN` as soon as a refresh fails, so a stale "open"
     * can never outlive the server switch being closed.
     */
    val availability: StateFlow<BillingAvailability>

    /** Never throws — a failure of any kind leaves [availability] at `UNKNOWN`. */
    suspend fun refreshAvailability()

    /**
     * Starts a hosted Stripe Checkout session for [plan]/[billingCycle] and
     * returns the URL to open in the system browser.
     *
     * **Online only** (ADR-49) — no Room cache, no SyncEngine, like
     * `ExportRepository`. The URL is single-use and per-account, not
     * per-project — no local/server id to resolve first, unlike
     * `ExportRepository`/`HistoryRepository`.
     *
     * Throws a `DomainException`: `Network` offline, `BillingNotOpen` if the
     * server switch was closed since the last refresh (which also flips
     * [availability] to `CLOSED`), `Unexpected` otherwise.
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
