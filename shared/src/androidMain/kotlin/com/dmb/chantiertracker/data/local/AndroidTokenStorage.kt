package com.dmb.chantiertracker.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.google.crypto.tink.Aead
import com.google.crypto.tink.integration.android.AndroidKeystore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

class AndroidTokenStorage internal constructor(private val vault: DataStore<TokenVault>) : TokenStorage {

    constructor(context: Context) : this(openTokenVault(context.applicationContext))

    override suspend fun get(): AuthTokens? = withContext(Dispatchers.IO) {
        try {
            vault.data.first().tokens
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun save(tokens: AuthTokens): Unit = withContext(Dispatchers.IO) {
        vault.updateData { TokenVault(tokens) }
    }

    override suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        try {
            vault.updateData { TokenVault() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Unit
        }
    }
}

internal fun openTokenVault(
    file: File,
    legacy: LegacyTokens,
    aead: () -> Aead,
): DataStore<TokenVault> = DataStoreFactory.create(
    serializer = TokenVaultSerializer(aead),
    corruptionHandler = ReplaceFileCorruptionHandler { TokenVault() },
    migrations = listOf(LegacyTokensMigration(legacy)),
    produceFile = { file },
)

private fun openTokenVault(context: Context): DataStore<TokenVault> {
    val aead by lazy(::keystoreAead)
    return openTokenVault(File(context.filesDir, TOKEN_FILE), EncryptedPrefsLegacyTokens(context)) { aead }
}

private fun keystoreAead(): Aead {
    if (!AndroidKeystore.hasKey(KEYSTORE_ALIAS)) AndroidKeystore.generateNewAes256GcmKey(KEYSTORE_ALIAS)
    return AndroidKeystore.getAead(KEYSTORE_ALIAS)
}

private const val KEYSTORE_ALIAS = "chantiertracker_tokens"
private const val TOKEN_FILE = "datastore/chantiertracker_tokens.enc"
