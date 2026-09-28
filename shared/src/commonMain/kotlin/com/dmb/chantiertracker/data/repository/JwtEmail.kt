package com.dmb.chantiertracker.data.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// Reads the JWT payload's `sub` claim (the user's email — verified against
// JwtService.subject(user.getEmail())) without verifying the signature —
// purely to recover the email for a fresh login after a mustChangePassword
// password change, never for an auth decision (the server is still the only
// one that ever validates the token itself). Same technique as the web's
// decodeAccessTokenEmail (client.ts), needed here for AuthRepositoryImpl
// .bootstrap(): a cold start only has the stored token, never the plaintext
// email a live login() call already has as a parameter.
@OptIn(ExperimentalEncodingApi::class)
internal fun decodeJwtEmail(accessToken: String): String? {
    val parts = accessToken.split(".")
    if (parts.size != 3) return null
    return runCatching {
        // JWT segments are base64url, unpadded — convert to standard base64
        // before decoding (mirrors the web's manual replace('-','+')/replace('_','/')).
        val standard = parts[1].replace('-', '+').replace('_', '/')
        val padded = standard + "=".repeat((4 - standard.length % 4) % 4)
        val json = Base64.decode(padded).decodeToString()
        Json.parseToJsonElement(json).jsonObject["sub"]?.jsonPrimitive?.content
    }.getOrNull()
}
