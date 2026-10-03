package com.dmb.chantiertracker.presentation.legal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TermsOfSaleAvailabilityTest {

    @Test
    fun the_terms_of_sale_are_replaced_by_the_provisional_notice_while_paid_plans_are_not_offered() {
        assertEquals(TERMS_OF_SALE_PROVISIONAL_SECTIONS, LegalDocument.TermsOfSale.sectionsToShow(paidPlansOffered = false))
    }

    @Test
    fun the_real_terms_of_sale_come_back_unchanged_once_paid_plans_are_offered() {
        assertEquals(LegalDocument.TermsOfSale.sections, LegalDocument.TermsOfSale.sectionsToShow(paidPlansOffered = true))
    }

    @Test
    fun no_other_document_ever_depends_on_billing() {
        LegalDocument.entries.filter { it != LegalDocument.TermsOfSale }.forEach { document ->
            assertEquals(document.sections, document.sectionsToShow(paidPlansOffered = false), "$document while closed")
            assertEquals(document.sections, document.sectionsToShow(paidPlansOffered = true), "$document while open")
        }
    }

    @Test
    fun the_provisional_notice_shares_nothing_with_the_real_terms() {
        val real = LegalDocument.TermsOfSale.sections.flatMap { listOf(it.title, it.body) }.toSet()
        assertTrue(TERMS_OF_SALE_PROVISIONAL_SECTIONS.none { it.title in real || it.body in real })
        assertEquals(2, TERMS_OF_SALE_PROVISIONAL_SECTIONS.size)
    }
}
