package com.dmb.chantiertracker.domain.model

/**
 * The publisher's legal identity as filled in by a SUPER_ADMIN on the backend
 * (ADR-68). Every field `null` = not filled in yet: the legal pages then keep
 * their "[À COMPLÉTER]" text for it. Blank values are treated as missing.
 */
data class EditorIdentity(
    val firstName: String? = null,
    val lastName: String? = null,
    val companyName: String? = null,
    val legalStatus: String? = null,
    val siret: String? = null,
    val address: String? = null,
    val contactEmail: String? = null,
    val vatNumber: String? = null,
    val hostingProviderName: String? = null,
    val hostingProviderAddress: String? = null,
) {
    /** "Prénom Nom", or whichever of the two is known. */
    fun personName(): String? = joinKnown(" ", firstName, lastName)

    /** The company once it exists, otherwise the person behind the site. */
    fun editorName(): String? = companyName.known() ?: personName()

    /** For a natural-person publisher, the publication director is that person. */
    fun publicationDirectorName(): String? = personName()

    /** "Name, address", or whichever of the two is known. */
    fun hostingProviderDetails(): String? = joinKnown(", ", hostingProviderName, hostingProviderAddress)

    private fun String?.known(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun joinKnown(separator: String, vararg parts: String?): String? =
        parts.mapNotNull { it.known() }.joinToString(separator).takeIf { it.isNotEmpty() }
}
