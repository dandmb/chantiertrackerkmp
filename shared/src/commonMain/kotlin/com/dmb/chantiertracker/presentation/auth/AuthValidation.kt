package com.dmb.chantiertracker.presentation.auth

import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.validation_code_format
import com.dmb.chantiertracker.resources.validation_code_required
import com.dmb.chantiertracker.resources.validation_email_invalid
import com.dmb.chantiertracker.resources.validation_email_required
import com.dmb.chantiertracker.resources.validation_name_required
import com.dmb.chantiertracker.resources.validation_password_digit
import com.dmb.chantiertracker.resources.validation_password_lowercase
import com.dmb.chantiertracker.resources.validation_password_required
import com.dmb.chantiertracker.resources.validation_password_special
import com.dmb.chantiertracker.resources.validation_password_too_short
import com.dmb.chantiertracker.resources.validation_password_uppercase
import org.jetbrains.compose.resources.StringResource

private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
private val CODE_REGEX = Regex("\\d{6}")

fun validateEmail(email: String): StringResource? = when {
    email.isBlank() -> Res.string.validation_email_required
    !EMAIL_REGEX.matches(email.trim()) -> Res.string.validation_email_invalid
    else -> null
}

fun validateStrongPassword(password: String): StringResource? = when {
    password.isBlank() -> Res.string.validation_password_required
    password.length < 12 -> Res.string.validation_password_too_short
    password.none { it.isUpperCase() } -> Res.string.validation_password_uppercase
    password.none { it.isLowerCase() } -> Res.string.validation_password_lowercase
    password.none { it.isDigit() } -> Res.string.validation_password_digit
    password.none { !it.isLetterOrDigit() } -> Res.string.validation_password_special
    else -> null
}

fun validateRequiredPassword(password: String): StringResource? =
    if (password.isBlank()) Res.string.validation_password_required else null

fun validateCode(code: String): StringResource? = when {
    code.isBlank() -> Res.string.validation_code_required
    !CODE_REGEX.matches(code) -> Res.string.validation_code_format
    else -> null
}

fun validateName(name: String): StringResource? =
    if (name.isBlank()) Res.string.validation_name_required else null
