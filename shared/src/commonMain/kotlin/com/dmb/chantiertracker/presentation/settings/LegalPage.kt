package com.dmb.chantiertracker.presentation.settings

import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.settings_legal_cookies
import com.dmb.chantiertracker.resources.settings_legal_notice
import com.dmb.chantiertracker.resources.settings_legal_privacy
import com.dmb.chantiertracker.resources.settings_legal_terms_of_sale
import com.dmb.chantiertracker.resources.settings_legal_terms_of_use
import org.jetbrains.compose.resources.StringResource

private const val WEB_BASE_URL = "https://chantiertracker.com"

enum class LegalPage(val path: String, val label: StringResource) {
    LegalNotice("/mentions-legales", Res.string.settings_legal_notice),
    TermsOfUse("/cgu", Res.string.settings_legal_terms_of_use),
    TermsOfSale("/cgv", Res.string.settings_legal_terms_of_sale),
    PrivacyPolicy("/confidentialite", Res.string.settings_legal_privacy),
    CookiePolicy("/cookies", Res.string.settings_legal_cookies);

    val url: String get() = WEB_BASE_URL + path
}
