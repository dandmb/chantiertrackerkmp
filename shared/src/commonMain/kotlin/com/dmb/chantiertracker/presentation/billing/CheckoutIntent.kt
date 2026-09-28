package com.dmb.chantiertracker.presentation.billing

import com.dmb.chantiertracker.domain.model.BillingCycle
import com.dmb.chantiertracker.domain.model.Plan

// Mirrors the web's resolveCheckoutIntent (src/billing/checkoutIntent.ts) —
// tolerant lookup by raw name, like LoginNotice.fromArg, never
// Plan.valueOf()/BillingCycle.valueOf() (would throw on a value carried
// through a route that this build doesn't recognize). Only a plan actually
// offered for purchase (PRICING_TIERS) resolves — never FREE/UNKNOWN.
fun resolveCheckoutIntent(planArg: String?, cycleArg: String?): Pair<Plan, BillingCycle>? {
    val plan = planArg
        ?.let { arg -> Plan.entries.firstOrNull { it.name == arg } }
        ?.takeIf { it in PRICING_TIERS }
        ?: return null
    val cycle = cycleArg?.let { arg -> BillingCycle.entries.firstOrNull { it.name == arg } } ?: return null
    return plan to cycle
}
