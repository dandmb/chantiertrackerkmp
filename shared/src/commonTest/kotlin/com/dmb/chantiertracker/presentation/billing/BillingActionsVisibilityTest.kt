package com.dmb.chantiertracker.presentation.billing

import com.dmb.chantiertracker.domain.model.Plan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BillingActionsVisibilityTest {

    @Test
    fun a_free_plan_never_shows_the_manage_button_or_the_no_action_message() {
        val visibility = resolveBillingActionsVisibility(Plan.FREE, hasStripeCustomer = true, hasUpgradeAvailable = true)
        assertEquals(BillingActionsVisibility(showManageButton = false, showNoActionMessage = false), visibility)
    }

    @Test
    fun a_paid_plan_with_a_stripe_customer_shows_the_manage_button() {
        val visibility = resolveBillingActionsVisibility(Plan.SEMI_FLEX, hasStripeCustomer = true, hasUpgradeAvailable = false)
        assertEquals(BillingActionsVisibility(showManageButton = true, showNoActionMessage = false), visibility)
    }

    @Test
    fun a_gifted_top_tier_plan_shows_the_no_action_message() {
        // No Stripe customer at all (never checked out) and already at LIBERTE, so
        // no upgrade to offer either — the ADMIN_GRANTED case from the backend.
        val visibility = resolveBillingActionsVisibility(Plan.LIBERTE, hasStripeCustomer = false, hasUpgradeAvailable = false)
        assertEquals(BillingActionsVisibility(showManageButton = false, showNoActionMessage = true), visibility)
    }

    @Test
    fun a_paid_plan_without_a_stripe_customer_but_with_an_upgrade_available_shows_neither() {
        val visibility = resolveBillingActionsVisibility(Plan.SEMI_FLEX, hasStripeCustomer = false, hasUpgradeAvailable = true)
        assertEquals(BillingActionsVisibility(showManageButton = false, showNoActionMessage = false), visibility)
    }

    @Test
    fun upgrade_targets_offer_both_paid_tiers_from_free() {
        assertEquals(listOf(Plan.SEMI_FLEX, Plan.LIBERTE), upgradeTargets(Plan.FREE))
    }

    @Test
    fun upgrade_targets_offer_only_liberte_from_semi_flex() {
        assertEquals(listOf(Plan.LIBERTE), upgradeTargets(Plan.SEMI_FLEX))
    }

    @Test
    fun upgrade_targets_are_empty_from_liberte_and_unknown() {
        assertTrue(upgradeTargets(Plan.LIBERTE).isEmpty())
        assertTrue(upgradeTargets(Plan.UNKNOWN).isEmpty())
    }
}
