package com.dmb.chantiertracker.data.local

interface OnboardingStore {
    suspend fun hasCompletedFirstLogin(): Boolean
    suspend fun markFirstLoginCompleted()
    suspend fun hasSeenOnboarding(): Boolean
    suspend fun markOnboardingSeen()
}
