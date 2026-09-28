package com.dmb.chantiertracker.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JwtEmailTest {

    // header {"alg":"HS256","typ":"JWT"}, payload {"sub":"user@chantier.dev","exp":9999999999}
    // — a real backend-shaped token (JwtService.subject(user.getEmail())), signature irrelevant here.
    private val validToken =
        "eyJhbGciOiAiSFMyNTYiLCAidHlwIjogIkpXVCJ9." +
            "eyJzdWIiOiAidXNlckBjaGFudGllci5kZXYiLCAiZXhwIjogOTk5OTk5OTk5OX0." +
            "fakesignature"

    @Test
    fun reads_the_sub_claim_as_the_email() {
        assertEquals("user@chantier.dev", decodeJwtEmail(validToken))
    }

    @Test
    fun a_token_with_the_wrong_number_of_segments_returns_null() {
        assertNull(decodeJwtEmail("not.a.jwt.at.all"))
        assertNull(decodeJwtEmail("onlyonesegment"))
    }

    @Test
    fun unparseable_base64_in_the_payload_returns_null_rather_than_throwing() {
        assertNull(decodeJwtEmail("header.%%%not-base64%%%.signature"))
    }

    @Test
    fun a_payload_without_a_sub_claim_returns_null() {
        // payload = {"exp":9999999999} — valid base64url JSON, no "sub".
        val token = "header.eyJleHAiOiA5OTk5OTk5OTk5fQ.signature"
        assertNull(decodeJwtEmail(token))
    }

    @Test
    fun an_empty_string_returns_null() {
        assertNull(decodeJwtEmail(""))
    }
}
