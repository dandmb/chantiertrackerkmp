package com.dmb.chantiertracker.data.remote

// Impl réelle (stockage sécurisé + refresh) livrée avec la feature auth ; ici seul NoAuthTokenProvider existe.
interface AuthTokenProvider {
    suspend fun currentAccessToken(): String?
    suspend fun currentRefreshToken(): String?
    suspend fun refresh(): String?
}

class NoAuthTokenProvider : AuthTokenProvider {
    override suspend fun currentAccessToken(): String? = null
    override suspend fun currentRefreshToken(): String? = null
    override suspend fun refresh(): String? = null
}
