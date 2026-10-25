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

private const val WEBSITE = "https://chantiertracker.com"

// Same pages and order as LEGAL_LINKS in the web's src/legal/legalLinks.ts.
enum class LegalDocument(val label: StringResource, val link: StringResource, private val path: String) {
    LegalNotice(Res.string.legal_doc_notice, Res.string.legal_link_notice, "/mentions-legales"),
    TermsOfUse(Res.string.legal_doc_terms_of_use, Res.string.legal_link_terms_of_use, "/cgu"),
    TermsOfSale(Res.string.legal_doc_terms_of_sale, Res.string.legal_link_terms_of_sale, "/cgv"),
    PrivacyPolicy(Res.string.legal_doc_privacy, Res.string.legal_link_privacy, "/confidentialite"),
    CookiePolicy(Res.string.legal_doc_cookies, Res.string.legal_link_cookies, "/cookies"),
    AccountDeletion(Res.string.legal_doc_account_deletion, Res.string.legal_link_account_deletion, "/suppression-compte");

    val url: String get() = WEBSITE + path
}
