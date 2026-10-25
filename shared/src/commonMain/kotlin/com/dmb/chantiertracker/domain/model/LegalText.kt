package com.dmb.chantiertracker.domain.model

enum class LegalLanguage(val code: String) {
    French("fr"),
    English("en");

    companion object {
        fun forLocale(localeTag: String): LegalLanguage =
            if (localeTag.substringBefore('_').substringBefore('-').equals("en", ignoreCase = true)) English else French

        fun fromCode(code: String): LegalLanguage = entries.firstOrNull { it.code == code } ?: French
    }
}

data class LegalText(
    val document: String,
    val language: LegalLanguage,
    val fallback: Boolean,
    val title: String,
    val markdown: String,
    val version: Int,
    val publishedOn: String,
)
