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
enum class LegalDocument(val label: StringResource, val link: StringResource, val slug: String, val frenchSitePath: String) {
    LegalNotice(Res.string.legal_doc_notice, Res.string.legal_link_notice, "legal-notice", "/mentions-legales"),
    TermsOfUse(Res.string.legal_doc_terms_of_use, Res.string.legal_link_terms_of_use, "terms-of-use", "/cgu"),
    TermsOfSale(Res.string.legal_doc_terms_of_sale, Res.string.legal_link_terms_of_sale, "terms-of-sale", "/cgv"),
    PrivacyPolicy(Res.string.legal_doc_privacy, Res.string.legal_link_privacy, "privacy-policy", "/confidentialite"),
    CookiePolicy(Res.string.legal_doc_cookies, Res.string.legal_link_cookies, "cookie-policy", "/cookies"),
    AccountDeletion(Res.string.legal_doc_account_deletion, Res.string.legal_link_account_deletion, "account-deletion", "/suppression-compte");

    val englishSitePath: String get() = "/en/$slug"

    companion object {
        private val SITE_ORIGIN = Regex("^https?://(www\\.)?(chantiertracker\\.com|localhost(:\\d+)?)(?=/|$)", RegexOption.IGNORE_CASE)

        fun fromSlug(slug: String): LegalDocument? = entries.firstOrNull { it.slug == slug }

        fun fromLink(target: String): LegalDocument? {
            val trimmed = target.trim()
            val isAbsolute = trimmed.contains("://")
            val origin = SITE_ORIGIN.find(trimmed)
            if (isAbsolute && origin == null) return null
            val path = trimmed.removePrefix(origin?.value.orEmpty()).substringBefore('#').substringBefore('?').trimEnd('/').lowercase()
            return entries.firstOrNull { path == it.slug || path == "/${it.slug}" || path == it.frenchSitePath || path == it.englishSitePath }
        }
    }
}
