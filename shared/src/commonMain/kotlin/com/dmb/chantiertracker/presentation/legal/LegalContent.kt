package com.dmb.chantiertracker.presentation.legal

import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.legal_cookies_s1_body
import com.dmb.chantiertracker.resources.legal_cookies_s1_title
import com.dmb.chantiertracker.resources.legal_cookies_s2_body
import com.dmb.chantiertracker.resources.legal_cookies_s2_title
import com.dmb.chantiertracker.resources.legal_cookies_s3_body
import com.dmb.chantiertracker.resources.legal_cookies_s3_title
import com.dmb.chantiertracker.resources.legal_cookies_s4_body
import com.dmb.chantiertracker.resources.legal_cookies_s4_title
import com.dmb.chantiertracker.resources.legal_doc_cookies
import com.dmb.chantiertracker.resources.legal_doc_notice
import com.dmb.chantiertracker.resources.legal_doc_privacy
import com.dmb.chantiertracker.resources.legal_doc_terms_of_sale
import com.dmb.chantiertracker.resources.legal_doc_terms_of_use
import com.dmb.chantiertracker.resources.legal_link_cookies
import com.dmb.chantiertracker.resources.legal_link_notice
import com.dmb.chantiertracker.resources.legal_link_privacy
import com.dmb.chantiertracker.resources.legal_link_terms_of_sale
import com.dmb.chantiertracker.resources.legal_link_terms_of_use
import com.dmb.chantiertracker.resources.legal_notice_s1_body
import com.dmb.chantiertracker.resources.legal_notice_s1_title
import com.dmb.chantiertracker.resources.legal_notice_s2_body
import com.dmb.chantiertracker.resources.legal_notice_s2_title
import com.dmb.chantiertracker.resources.legal_notice_s3_body
import com.dmb.chantiertracker.resources.legal_notice_s3_title
import com.dmb.chantiertracker.resources.legal_notice_s4_body
import com.dmb.chantiertracker.resources.legal_notice_s4_title
import com.dmb.chantiertracker.resources.legal_notice_s5_body
import com.dmb.chantiertracker.resources.legal_notice_s5_title
import com.dmb.chantiertracker.resources.legal_notice_s6_body
import com.dmb.chantiertracker.resources.legal_notice_s6_title
import com.dmb.chantiertracker.resources.legal_notice_s7_body
import com.dmb.chantiertracker.resources.legal_notice_s7_title
import com.dmb.chantiertracker.resources.legal_ph_account_data_retention_delay
import com.dmb.chantiertracker.resources.legal_ph_backend_host_details
import com.dmb.chantiertracker.resources.legal_ph_consumer_mediator_details
import com.dmb.chantiertracker.resources.legal_ph_data_protection_contact_email
import com.dmb.chantiertracker.resources.legal_ph_editor_address
import com.dmb.chantiertracker.resources.legal_ph_editor_contact_email
import com.dmb.chantiertracker.resources.legal_ph_editor_legal_status
import com.dmb.chantiertracker.resources.legal_ph_editor_name
import com.dmb.chantiertracker.resources.legal_ph_editor_siret
import com.dmb.chantiertracker.resources.legal_ph_editor_vat_number
import com.dmb.chantiertracker.resources.legal_ph_last_updated_date
import com.dmb.chantiertracker.resources.legal_ph_liberte_price_cgv
import com.dmb.chantiertracker.resources.legal_ph_publication_director_name
import com.dmb.chantiertracker.resources.legal_ph_semi_flex_price_cgv
import com.dmb.chantiertracker.resources.legal_ph_support_contact_email
import com.dmb.chantiertracker.resources.legal_privacy_s10_body
import com.dmb.chantiertracker.resources.legal_privacy_s10_title
import com.dmb.chantiertracker.resources.legal_privacy_s1_body
import com.dmb.chantiertracker.resources.legal_privacy_s1_title
import com.dmb.chantiertracker.resources.legal_privacy_s2_body
import com.dmb.chantiertracker.resources.legal_privacy_s2_title
import com.dmb.chantiertracker.resources.legal_privacy_s3_body
import com.dmb.chantiertracker.resources.legal_privacy_s3_title
import com.dmb.chantiertracker.resources.legal_privacy_s4_body
import com.dmb.chantiertracker.resources.legal_privacy_s4_title
import com.dmb.chantiertracker.resources.legal_privacy_s5_body
import com.dmb.chantiertracker.resources.legal_privacy_s5_title
import com.dmb.chantiertracker.resources.legal_privacy_s6_body
import com.dmb.chantiertracker.resources.legal_privacy_s6_title
import com.dmb.chantiertracker.resources.legal_privacy_s7_body
import com.dmb.chantiertracker.resources.legal_privacy_s7_title
import com.dmb.chantiertracker.resources.legal_privacy_s8_body
import com.dmb.chantiertracker.resources.legal_privacy_s8_title
import com.dmb.chantiertracker.resources.legal_privacy_s9_body
import com.dmb.chantiertracker.resources.legal_privacy_s9_title
import com.dmb.chantiertracker.resources.legal_terms_of_sale_s10_body
import com.dmb.chantiertracker.resources.legal_terms_of_sale_s10_title
import com.dmb.chantiertracker.resources.legal_terms_of_sale_s1_body
import com.dmb.chantiertracker.resources.legal_terms_of_sale_s1_title
import com.dmb.chantiertracker.resources.legal_terms_of_sale_s2_body
import com.dmb.chantiertracker.resources.legal_terms_of_sale_s2_title
import com.dmb.chantiertracker.resources.legal_terms_of_sale_s3_body
import com.dmb.chantiertracker.resources.legal_terms_of_sale_s3_title
import com.dmb.chantiertracker.resources.legal_terms_of_sale_s4_body
import com.dmb.chantiertracker.resources.legal_terms_of_sale_s4_title
import com.dmb.chantiertracker.resources.legal_terms_of_sale_s5_body
import com.dmb.chantiertracker.resources.legal_terms_of_sale_s5_title
import com.dmb.chantiertracker.resources.legal_terms_of_sale_s6_body
import com.dmb.chantiertracker.resources.legal_terms_of_sale_s6_title
import com.dmb.chantiertracker.resources.legal_terms_of_sale_s7_body
import com.dmb.chantiertracker.resources.legal_terms_of_sale_s7_title
import com.dmb.chantiertracker.resources.legal_terms_of_sale_s8_body
import com.dmb.chantiertracker.resources.legal_terms_of_sale_s8_title
import com.dmb.chantiertracker.resources.legal_terms_of_sale_s9_body
import com.dmb.chantiertracker.resources.legal_terms_of_sale_s9_title
import com.dmb.chantiertracker.resources.legal_terms_of_use_s10_body
import com.dmb.chantiertracker.resources.legal_terms_of_use_s10_title
import com.dmb.chantiertracker.resources.legal_terms_of_use_s1_body
import com.dmb.chantiertracker.resources.legal_terms_of_use_s1_title
import com.dmb.chantiertracker.resources.legal_terms_of_use_s2_body
import com.dmb.chantiertracker.resources.legal_terms_of_use_s2_title
import com.dmb.chantiertracker.resources.legal_terms_of_use_s3_body
import com.dmb.chantiertracker.resources.legal_terms_of_use_s3_title
import com.dmb.chantiertracker.resources.legal_terms_of_use_s4_body
import com.dmb.chantiertracker.resources.legal_terms_of_use_s4_title
import com.dmb.chantiertracker.resources.legal_terms_of_use_s5_body
import com.dmb.chantiertracker.resources.legal_terms_of_use_s5_title
import com.dmb.chantiertracker.resources.legal_terms_of_use_s6_body
import com.dmb.chantiertracker.resources.legal_terms_of_use_s6_title
import com.dmb.chantiertracker.resources.legal_terms_of_use_s7_body
import com.dmb.chantiertracker.resources.legal_terms_of_use_s7_title
import com.dmb.chantiertracker.resources.legal_terms_of_use_s8_body
import com.dmb.chantiertracker.resources.legal_terms_of_use_s8_title
import com.dmb.chantiertracker.resources.legal_terms_of_use_s9_body
import com.dmb.chantiertracker.resources.legal_terms_of_use_s9_title
import org.jetbrains.compose.resources.StringResource

class LegalSectionContent(val title: StringResource, val body: StringResource)

enum class LegalPlaceholder(val token: String, val value: StringResource) {
    AccountDataRetentionDelay("{ACCOUNT_DATA_RETENTION_DELAY}", Res.string.legal_ph_account_data_retention_delay),
    BackendHostDetails("{BACKEND_HOST_DETAILS}", Res.string.legal_ph_backend_host_details),
    ConsumerMediatorDetails("{CONSUMER_MEDIATOR_DETAILS}", Res.string.legal_ph_consumer_mediator_details),
    DataProtectionContactEmail("{DATA_PROTECTION_CONTACT_EMAIL}", Res.string.legal_ph_data_protection_contact_email),
    EditorAddress("{EDITOR_ADDRESS}", Res.string.legal_ph_editor_address),
    EditorContactEmail("{EDITOR_CONTACT_EMAIL}", Res.string.legal_ph_editor_contact_email),
    EditorLegalStatus("{EDITOR_LEGAL_STATUS}", Res.string.legal_ph_editor_legal_status),
    EditorName("{EDITOR_NAME}", Res.string.legal_ph_editor_name),
    EditorSiret("{EDITOR_SIRET}", Res.string.legal_ph_editor_siret),
    EditorVatNumber("{EDITOR_VAT_NUMBER}", Res.string.legal_ph_editor_vat_number),
    LastUpdatedDate("{LAST_UPDATED_DATE}", Res.string.legal_ph_last_updated_date),
    LibertePriceCgv("{LIBERTE_PRICE_CGV}", Res.string.legal_ph_liberte_price_cgv),
    PublicationDirectorName("{PUBLICATION_DIRECTOR_NAME}", Res.string.legal_ph_publication_director_name),
    SemiFlexPriceCgv("{SEMI_FLEX_PRICE_CGV}", Res.string.legal_ph_semi_flex_price_cgv),
    SupportContactEmail("{SUPPORT_CONTACT_EMAIL}", Res.string.legal_ph_support_contact_email);
}

enum class LegalDocument(val label: StringResource, val link: StringResource, val sections: List<LegalSectionContent>) {
    LegalNotice(
        Res.string.legal_doc_notice,
        Res.string.legal_link_notice,
        listOf(
            LegalSectionContent(Res.string.legal_notice_s1_title, Res.string.legal_notice_s1_body),
            LegalSectionContent(Res.string.legal_notice_s2_title, Res.string.legal_notice_s2_body),
            LegalSectionContent(Res.string.legal_notice_s3_title, Res.string.legal_notice_s3_body),
            LegalSectionContent(Res.string.legal_notice_s4_title, Res.string.legal_notice_s4_body),
            LegalSectionContent(Res.string.legal_notice_s5_title, Res.string.legal_notice_s5_body),
            LegalSectionContent(Res.string.legal_notice_s6_title, Res.string.legal_notice_s6_body),
            LegalSectionContent(Res.string.legal_notice_s7_title, Res.string.legal_notice_s7_body),
        ),
    ),
    TermsOfUse(
        Res.string.legal_doc_terms_of_use,
        Res.string.legal_link_terms_of_use,
        listOf(
            LegalSectionContent(Res.string.legal_terms_of_use_s1_title, Res.string.legal_terms_of_use_s1_body),
            LegalSectionContent(Res.string.legal_terms_of_use_s2_title, Res.string.legal_terms_of_use_s2_body),
            LegalSectionContent(Res.string.legal_terms_of_use_s3_title, Res.string.legal_terms_of_use_s3_body),
            LegalSectionContent(Res.string.legal_terms_of_use_s4_title, Res.string.legal_terms_of_use_s4_body),
            LegalSectionContent(Res.string.legal_terms_of_use_s5_title, Res.string.legal_terms_of_use_s5_body),
            LegalSectionContent(Res.string.legal_terms_of_use_s6_title, Res.string.legal_terms_of_use_s6_body),
            LegalSectionContent(Res.string.legal_terms_of_use_s7_title, Res.string.legal_terms_of_use_s7_body),
            LegalSectionContent(Res.string.legal_terms_of_use_s8_title, Res.string.legal_terms_of_use_s8_body),
            LegalSectionContent(Res.string.legal_terms_of_use_s9_title, Res.string.legal_terms_of_use_s9_body),
            LegalSectionContent(Res.string.legal_terms_of_use_s10_title, Res.string.legal_terms_of_use_s10_body),
        ),
    ),
    TermsOfSale(
        Res.string.legal_doc_terms_of_sale,
        Res.string.legal_link_terms_of_sale,
        listOf(
            LegalSectionContent(Res.string.legal_terms_of_sale_s1_title, Res.string.legal_terms_of_sale_s1_body),
            LegalSectionContent(Res.string.legal_terms_of_sale_s2_title, Res.string.legal_terms_of_sale_s2_body),
            LegalSectionContent(Res.string.legal_terms_of_sale_s3_title, Res.string.legal_terms_of_sale_s3_body),
            LegalSectionContent(Res.string.legal_terms_of_sale_s4_title, Res.string.legal_terms_of_sale_s4_body),
            LegalSectionContent(Res.string.legal_terms_of_sale_s5_title, Res.string.legal_terms_of_sale_s5_body),
            LegalSectionContent(Res.string.legal_terms_of_sale_s6_title, Res.string.legal_terms_of_sale_s6_body),
            LegalSectionContent(Res.string.legal_terms_of_sale_s7_title, Res.string.legal_terms_of_sale_s7_body),
            LegalSectionContent(Res.string.legal_terms_of_sale_s8_title, Res.string.legal_terms_of_sale_s8_body),
            LegalSectionContent(Res.string.legal_terms_of_sale_s9_title, Res.string.legal_terms_of_sale_s9_body),
            LegalSectionContent(Res.string.legal_terms_of_sale_s10_title, Res.string.legal_terms_of_sale_s10_body),
        ),
    ),
    PrivacyPolicy(
        Res.string.legal_doc_privacy,
        Res.string.legal_link_privacy,
        listOf(
            LegalSectionContent(Res.string.legal_privacy_s1_title, Res.string.legal_privacy_s1_body),
            LegalSectionContent(Res.string.legal_privacy_s2_title, Res.string.legal_privacy_s2_body),
            LegalSectionContent(Res.string.legal_privacy_s3_title, Res.string.legal_privacy_s3_body),
            LegalSectionContent(Res.string.legal_privacy_s4_title, Res.string.legal_privacy_s4_body),
            LegalSectionContent(Res.string.legal_privacy_s5_title, Res.string.legal_privacy_s5_body),
            LegalSectionContent(Res.string.legal_privacy_s6_title, Res.string.legal_privacy_s6_body),
            LegalSectionContent(Res.string.legal_privacy_s7_title, Res.string.legal_privacy_s7_body),
            LegalSectionContent(Res.string.legal_privacy_s8_title, Res.string.legal_privacy_s8_body),
            LegalSectionContent(Res.string.legal_privacy_s9_title, Res.string.legal_privacy_s9_body),
            LegalSectionContent(Res.string.legal_privacy_s10_title, Res.string.legal_privacy_s10_body),
        ),
    ),
    CookiePolicy(
        Res.string.legal_doc_cookies,
        Res.string.legal_link_cookies,
        listOf(
            LegalSectionContent(Res.string.legal_cookies_s1_title, Res.string.legal_cookies_s1_body),
            LegalSectionContent(Res.string.legal_cookies_s2_title, Res.string.legal_cookies_s2_body),
            LegalSectionContent(Res.string.legal_cookies_s3_title, Res.string.legal_cookies_s3_body),
            LegalSectionContent(Res.string.legal_cookies_s4_title, Res.string.legal_cookies_s4_body),
        ),
    );
}
