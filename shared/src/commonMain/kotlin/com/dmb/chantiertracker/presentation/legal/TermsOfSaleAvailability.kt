package com.dmb.chantiertracker.presentation.legal

import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.legal_terms_of_sale_provisional_s1_body
import com.dmb.chantiertracker.resources.legal_terms_of_sale_provisional_s1_title
import com.dmb.chantiertracker.resources.legal_terms_of_sale_provisional_s2_body
import com.dmb.chantiertracker.resources.legal_terms_of_sale_provisional_s2_title

// Kept out of LegalContent.kt on purpose: that file and values-fr/legal_strings.xml
// are regenerated from the web's rendered pages (docs/walkthrough/tools/legal), which
// would wipe anything added there by hand. Same text as the web's CgvProvisionalPage.
val TERMS_OF_SALE_PROVISIONAL_SECTIONS = listOf(
    LegalSectionContent(Res.string.legal_terms_of_sale_provisional_s1_title, Res.string.legal_terms_of_sale_provisional_s1_body),
    LegalSectionContent(Res.string.legal_terms_of_sale_provisional_s2_title, Res.string.legal_terms_of_sale_provisional_s2_body),
)

/**
 * ADR-67 — the terms of sale only exist once something can actually be bought:
 * until paid plans are confirmed offered (closed **or unknown**), the CGV screen
 * shows [TERMS_OF_SALE_PROVISIONAL_SECTIONS] **instead of** its real sections.
 * Every other document never depends on billing.
 */
fun LegalDocument.sectionsToShow(paidPlansOffered: Boolean): List<LegalSectionContent> =
    if (this == LegalDocument.TermsOfSale && !paidPlansOffered) TERMS_OF_SALE_PROVISIONAL_SECTIONS else sections
