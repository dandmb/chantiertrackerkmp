package com.dmb.chantiertracker.presentation.legal

import com.dmb.chantiertracker.domain.model.EditorIdentity

/**
 * ADR-68 — the server's value for a placeholder, or `null` to keep the generated
 * "[À COMPLÉTER]" resource. Kept out of LegalContent.kt on purpose: that file (and
 * the `legal_ph_*` keys, which now serve as the waiting text) is regenerated from
 * the web by docs/walkthrough/tools/legal. The other tokens (date, prices, support
 * and data-protection e-mails, mediator, retention delay) have no backend field.
 */
fun EditorIdentity?.valueFor(placeholder: LegalPlaceholder): String? {
    val identity = this ?: return null
    return when (placeholder) {
        LegalPlaceholder.EditorName -> identity.editorName()
        LegalPlaceholder.EditorLegalStatus -> identity.legalStatus.known()
        LegalPlaceholder.EditorSiret -> identity.siret.known()
        LegalPlaceholder.EditorAddress -> identity.address.known()
        LegalPlaceholder.EditorContactEmail -> identity.contactEmail.known()
        LegalPlaceholder.EditorVatNumber -> identity.vatNumber.known()
        LegalPlaceholder.PublicationDirectorName -> identity.publicationDirectorName()
        LegalPlaceholder.BackendHostDetails -> identity.hostingProviderDetails()
        else -> null
    }
}

private fun String?.known(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
