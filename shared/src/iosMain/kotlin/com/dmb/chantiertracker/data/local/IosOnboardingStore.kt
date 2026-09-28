package com.dmb.chantiertracker.data.local

import platform.Foundation.NSUserDefaults

class IosOnboardingStore : OnboardingStore {

    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun hasCompletedFirstLogin(): Boolean = defaults.boolForKey(KEY)

    override suspend fun markFirstLoginCompleted() {
        defaults.setBool(true, forKey = KEY)
    }

    override suspend fun hasSeenOnboarding(): Boolean = defaults.boolForKey(KEY_ONBOARDING)

    override suspend fun markOnboardingSeen() {
        defaults.setBool(true, forKey = KEY_ONBOARDING)
    }

    private companion object {
        const val KEY = "chantiertracker.first_login_completed"
        const val KEY_ONBOARDING = "chantiertracker.onboarding_seen"
    }
}
