package com.dmb.chantiertracker.presentation.auth

import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.validation_code_format
import com.dmb.chantiertracker.resources.validation_email_invalid
import com.dmb.chantiertracker.resources.validation_email_required
import com.dmb.chantiertracker.resources.validation_password_digit
import com.dmb.chantiertracker.resources.validation_password_special
import com.dmb.chantiertracker.resources.validation_password_too_short
import com.dmb.chantiertracker.resources.validation_password_uppercase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthValidationTest {

    @Test
    fun email_validation() {
        assertNull(validateEmail("user@example.com"))
        assertEquals(Res.string.validation_email_required, validateEmail(""))
        assertEquals(Res.string.validation_email_invalid, validateEmail("not-an-email"))
        assertEquals(Res.string.validation_email_invalid, validateEmail("a@b"))
    }

    @Test
    fun strong_password_rules_match_backend() {
        assertNull(validateStrongPassword("Abcdefgh1234!"))
        assertEquals(Res.string.validation_password_too_short, validateStrongPassword("Ab1!xxxxx"))
        assertEquals(Res.string.validation_password_uppercase, validateStrongPassword("abcdefgh1234!"))
        assertEquals(Res.string.validation_password_digit, validateStrongPassword("Abcdefghijk!"))
        assertEquals(Res.string.validation_password_special, validateStrongPassword("Abcdefghij123"))
    }

    @Test
    fun verification_code_must_be_six_digits() {
        assertNull(validateCode("123456"))
        assertEquals(Res.string.validation_code_format, validateCode("12345"))
        assertEquals(Res.string.validation_code_format, validateCode("12345a"))
    }
}
