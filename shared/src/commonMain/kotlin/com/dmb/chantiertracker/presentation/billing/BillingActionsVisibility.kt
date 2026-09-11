package com.dmb.chantiertracker.presentation.billing

import com.dmb.chantiertracker.domain.model.Plan

// Ported from the web's resolveBillingActionsVisibility (src/billing/) —
// "Manage my subscription" opens the Stripe billing portal, which only
// works for a user with a real Stripe customer (hasStripeCustomer); showing
// it otherwise (e.g. a plan granted manually by a super-admin with no
// Stripe history) fails every time with a generic backend error. When
// neither the portal nor an upgrade is available, showNoActionMessage
// covers the resulting empty section so it doesn't look like something's
// missing.
data class BillingActionsVisibility(
    val showManageButton: Boolean,
    val showNoActionMessage: Boolean,
)

fun resolveBillingActionsVisibility(
    plan: Plan,
    hasStripeCustomer: Boolean,
    hasUpgradeAvailable: Boolean,
): BillingActionsVisibility {
    val showManageButton = plan != Plan.FREE && hasStripeCustomer
    val showNoActionMessage = plan != Plan.FREE && !hasStripeCustomer && !hasUpgradeAvailable
    return BillingActionsVisibility(showManageButton, showNoActionMessage)
}

// Which paid tiers are still worth offering as an upgrade from [plan] — mirrors
// the web's UPGRADE_CONFIGS. Fails closed on UNKNOWN (plan not yet resolved,
// or a future backend value this app doesn't recognize): never offer to buy
// a tier when the starting point isn't actually known.
fun upgradeTargets(plan: Plan): List<Plan> = when (plan) {
    Plan.FREE -> listOf(Plan.SEMI_FLEX, Plan.LIBERTE)
    Plan.SEMI_FLEX -> listOf(Plan.LIBERTE)
    Plan.LIBERTE, Plan.UNKNOWN -> emptyList()
}
