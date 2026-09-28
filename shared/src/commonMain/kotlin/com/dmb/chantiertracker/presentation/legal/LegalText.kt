package com.dmb.chantiertracker.presentation.legal

sealed interface LegalBlock {
    data class Paragraph(val text: String) : LegalBlock
    data class Bullet(val text: String) : LegalBlock
}

private const val BULLET_PREFIX = "- "

fun parseLegalBlocks(body: String): List<LegalBlock> =
    body.lines().filter { it.isNotEmpty() }.map { line ->
        if (line.startsWith(BULLET_PREFIX)) LegalBlock.Bullet(line.removePrefix(BULLET_PREFIX)) else LegalBlock.Paragraph(line)
    }

fun String.withLegalPlaceholders(valuesByToken: Map<String, String>): String =
    valuesByToken.entries.fold(this) { text, (token, value) -> text.replace(token, value) }

fun LegalDocument.toArg(): String = name

fun legalDocumentFromArg(arg: String): LegalDocument? = LegalDocument.entries.firstOrNull { it.name == arg }
