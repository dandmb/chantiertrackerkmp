package com.dmb.chantiertracker.data.local

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataMigration
import androidx.datastore.core.Serializer
import com.google.crypto.tink.Aead
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException

private val TOKEN_FILE_CONTEXT = "chantiertracker-tokens-v1".encodeToByteArray()

internal data class TokenVault(val tokens: AuthTokens? = null)

@Serializable
private class StoredTokens(val access: String, val refresh: String)

internal class TokenVaultSerializer(private val aead: () -> Aead) : Serializer<TokenVault> {

    override val defaultValue = TokenVault()

    override suspend fun readFrom(input: InputStream): TokenVault {
        val sealed = input.readBytes()
        if (sealed.isEmpty()) return defaultValue
        val stored = try {
            Json.decodeFromString<StoredTokens>(aead().decrypt(sealed, TOKEN_FILE_CONTEXT).decodeToString())
        } catch (e: GeneralSecurityException) {
            throw CorruptionException("the token file cannot be opened", e)
        } catch (e: SerializationException) {
            throw CorruptionException("the token file cannot be read", e)
        }
        return TokenVault(AuthTokens(stored.access, stored.refresh))
    }

    override suspend fun writeTo(t: TokenVault, output: OutputStream) {
        val tokens = t.tokens ?: return
        val plain = Json.encodeToString(StoredTokens(tokens.accessToken, tokens.refreshToken)).encodeToByteArray()
        output.write(aead().encrypt(plain, TOKEN_FILE_CONTEXT))
    }
}

internal interface LegacyTokens {
    fun exist(): Boolean
    fun read(): AuthTokens?
    fun erase()
}

internal class LegacyTokensMigration(private val legacy: LegacyTokens) : DataMigration<TokenVault> {

    override suspend fun shouldMigrate(currentData: TokenVault): Boolean = legacy.exist()

    override suspend fun migrate(currentData: TokenVault): TokenVault =
        if (currentData.tokens != null) currentData else TokenVault(runCatching { legacy.read() }.getOrNull())

    override suspend fun cleanUp() = legacy.erase()
}
