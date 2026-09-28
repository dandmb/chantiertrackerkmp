package com.dmb.chantiertracker.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore

class AndroidTokenStorage(context: Context) : TokenStorage {

    private val appContext = context.applicationContext

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    private fun prefs(): SharedPreferences =
        cachedPrefs ?: synchronized(this) {
            cachedPrefs ?: openWithKeystoreRecovery(
                open = ::createEncryptedPrefs,
                onCorruptedState = ::discardCorruptedKeystoreState,
            ).also { cachedPrefs = it }
        }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun discardCorruptedKeystoreState() {
        appContext.deleteSharedPreferences(PREFS_FILE)
        runCatching {
            KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
                .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        }
    }

    override suspend fun get(): AuthTokens? = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = prefs()
            val access = prefs.getString(KEY_ACCESS, null)
            val refresh = prefs.getString(KEY_REFRESH, null)
            if (access != null && refresh != null) AuthTokens(access, refresh) else null
        }.getOrNull()
    }

    override suspend fun save(tokens: AuthTokens): Unit = withContext(Dispatchers.IO) {
        prefs().edit()
            .putString(KEY_ACCESS, tokens.accessToken)
            .putString(KEY_REFRESH, tokens.refreshToken)
            .apply()
    }

    override suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        val prefs = runCatching { prefs() }.getOrNull() ?: return@withContext
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_FILE = "chantiertracker_auth"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
    }
}
