package com.dmb.chantiertracker.presentation.admin

import com.dmb.chantiertracker.domain.model.Plan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlanChangeDescriptionTest {

    @Test
    fun free_ignores_any_expiration_date() {
        val description = describePlanChange("dan@chantier.dev", Plan.FREE, "2026-12-31")
        assertEquals(PlanChangeDescription.ToFree("dan@chantier.dev"), description)
    }

    @Test
    fun a_paid_plan_with_a_date_is_described_with_expiration() {
        val description = describePlanChange("dan@chantier.dev", Plan.LIBERTE, "2026-12-31")
        assertEquals(PlanChangeDescription.WithExpiration("dan@chantier.dev", Plan.LIBERTE, "2026-12-31"), description)
    }

    @Test
    fun a_paid_plan_without_a_date_is_described_as_indefinite() {
        assertEquals(
            PlanChangeDescription.Indefinite("dan@chantier.dev", Plan.SEMI_FLEX),
            describePlanChange("dan@chantier.dev", Plan.SEMI_FLEX, ""),
        )
        assertEquals(
            PlanChangeDescription.Indefinite("dan@chantier.dev", Plan.SEMI_FLEX),
            describePlanChange("dan@chantier.dev", Plan.SEMI_FLEX, null),
        )
    }

    @Test
    fun resolving_the_expires_at_argument_ignores_free_and_blank_dates() {
        assertNull(resolvePlanExpiresAtArg(Plan.FREE, "2026-12-31"))
        assertNull(resolvePlanExpiresAtArg(Plan.LIBERTE, ""))
        assertEquals("2026-12-31T23:59:59", resolvePlanExpiresAtArg(Plan.LIBERTE, "2026-12-31"))
    }
}
