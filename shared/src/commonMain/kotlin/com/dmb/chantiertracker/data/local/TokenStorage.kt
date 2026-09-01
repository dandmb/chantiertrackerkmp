package com.dmb.chantiertracker.data.local

data class AuthTokens(val accessToken: String, val refreshToken: String)

interface TokenStorage {
    suspend fun get(): AuthTokens?
    suspend fun save(tokens: AuthTokens)
    suspend fun clear()
}
