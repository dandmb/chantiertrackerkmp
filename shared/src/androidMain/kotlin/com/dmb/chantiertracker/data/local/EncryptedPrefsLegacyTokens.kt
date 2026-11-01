package com.dmb.chantiertracker.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.KeyStore

// Only reads what older versions stored, so it can move to the Tink file once and be erased.
internal class EncryptedPrefsLegacyTokens(context: Context) : LegacyTokens {

    private val appContext = context.applicationContext

    override fun exist(): Boolean = File(appContext.applicationInfo.dataDir, "shared_prefs/$PREFS_FILE.xml").exists()

    override fun read(): AuthTokens? {
        val masterKey = MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val prefs = EncryptedSharedPreferences.create(
            appContext,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        val access = prefs.getString(KEY_ACCESS, null)
        val refresh = prefs.getString(KEY_REFRESH, null)
        return if (access != null && refresh != null) AuthTokens(access, refresh) else null
    }

    override fun erase() {
        appContext.deleteSharedPreferences(PREFS_FILE)
        runCatching { KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS) }
    }

    private companion object {
        const val PREFS_FILE = "chantiertracker_auth"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
    }
}
