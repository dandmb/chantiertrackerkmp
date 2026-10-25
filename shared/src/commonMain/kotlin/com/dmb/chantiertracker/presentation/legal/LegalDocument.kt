package com.dmb.chantiertracker.presentation.legal

import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.legal_doc_account_deletion
import com.dmb.chantiertracker.resources.legal_doc_cookies
import com.dmb.chantiertracker.resources.legal_doc_notice
import com.dmb.chantiertracker.resources.legal_doc_privacy
import com.dmb.chantiertracker.resources.legal_doc_terms_of_sale
import com.dmb.chantiertracker.resources.legal_doc_terms_of_use
import com.dmb.chantiertracker.resources.legal_link_account_deletion
import com.dmb.chantiertracker.resources.legal_link_cookies
import com.dmb.chantiertracker.resources.legal_link_notice
import com.dmb.chantiertracker.resources.legal_link_privacy
import com.dmb.chantiertracker.resources.legal_link_terms_of_sale
import com.dmb.chantiertracker.resources.legal_link_terms_of_use
import org.jetbrains.compose.resources.StringResource

// Same documents and order as LegalDocumentType on the backend (GET /legal-documents).
enum class LegalDocument(val label: StringResource, val link: StringResource, val slug: String) {
    LegalNotice(Res.string.legal_doc_notice, Res.string.legal_link_notice, "legal-notice"),
    TermsOfUse(Res.string.legal_doc_terms_of_use, Res.string.legal_link_terms_of_use, "terms-of-use"),
    TermsOfSale(Res.string.legal_doc_terms_of_sale, Res.string.legal_link_terms_of_sale, "terms-of-sale"),
    PrivacyPolicy(Res.string.legal_doc_privacy, Res.string.legal_link_privacy, "privacy-policy"),
    CookiePolicy(Res.string.legal_doc_cookies, Res.string.legal_link_cookies, "cookie-policy"),
    AccountDeletion(Res.string.legal_doc_account_deletion, Res.string.legal_link_account_deletion, "account-deletion");

    companion object {
        fun fromSlug(slug: String): LegalDocument? = entries.firstOrNull { it.slug == slug }
    }
}
