package com.dmb.chantiertracker.presentation.billing

import com.dmb.chantiertracker.domain.model.BillingCycle
import com.dmb.chantiertracker.domain.model.Plan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CheckoutIntentTest {

    @Test
    fun resolves_a_valid_paid_plan_and_cycle() {
        assertEquals(Plan.SEMI_FLEX to BillingCycle.MONTHLY, resolveCheckoutIntent("SEMI_FLEX", "MONTHLY"))
        assertEquals(Plan.LIBERTE to BillingCycle.YEARLY, resolveCheckoutIntent("LIBERTE", "YEARLY"))
    }

    @Test
    fun a_null_plan_or_cycle_resolves_to_nothing() {
        assertNull(resolveCheckoutIntent(null, "MONTHLY"))
        assertNull(resolveCheckoutIntent("SEMI_FLEX", null))
        assertNull(resolveCheckoutIntent(null, null))
    }

    @Test
    fun an_unrecognized_value_resolves_to_nothing_rather_than_throwing() {
        assertNull(resolveCheckoutIntent("NOT_A_PLAN", "MONTHLY"))
        assertNull(resolveCheckoutIntent("SEMI_FLEX", "NOT_A_CYCLE"))
    }

    @Test
    fun a_plan_never_offered_for_purchase_resolves_to_nothing() {
        // FREE/UNKNOWN are real Plan.name values but never a purchase target.
        assertNull(resolveCheckoutIntent("FREE", "MONTHLY"))
        assertNull(resolveCheckoutIntent("UNKNOWN", "MONTHLY"))
    }
}
