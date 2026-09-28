package com.dmb.chantiertracker.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidOnboardingStore(context: Context) : OnboardingStore {

    private val prefs =
        context.applicationContext.getSharedPreferences("chantiertracker_prefs", Context.MODE_PRIVATE)

    override suspend fun hasCompletedFirstLogin(): Boolean = withContext(Dispatchers.IO) {
        prefs.getBoolean(KEY, false)
    }

    override suspend fun markFirstLoginCompleted(): Unit = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY, true).apply()
    }

    override suspend fun hasSeenOnboarding(): Boolean = withContext(Dispatchers.IO) {
        prefs.getBoolean(KEY_ONBOARDING, false)
    }

    override suspend fun markOnboardingSeen(): Unit = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY_ONBOARDING, true).apply()
    }

    private companion object {
        const val KEY = "first_login_completed"
        const val KEY_ONBOARDING = "onboarding_seen"
    }
}
