package com.dmb.chantiertracker.presentation.stages

import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.validation_amount_invalid
import com.dmb.chantiertracker.resources.validation_date_format
import org.jetbrains.compose.resources.StringResource

private val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")

fun validateIsoDateOrBlank(value: String): StringResource? {
    val trimmed = value.trim()
    return if (trimmed.isEmpty() || ISO_DATE.matches(trimmed)) null else Res.string.validation_date_format
}

fun validateAmountOrBlank(value: String): StringResource? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    val amount = trimmed.replace(',', '.').toDoubleOrNull()
    return if (amount != null && amount >= 0.0) null else Res.string.validation_amount_invalid
}

fun parseAmountOrNull(value: String): Double? =
    value.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0.0 }
