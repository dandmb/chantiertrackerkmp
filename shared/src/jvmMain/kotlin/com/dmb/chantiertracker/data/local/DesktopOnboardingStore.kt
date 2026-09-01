package com.dmb.chantiertracker.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText

class DesktopOnboardingStore(
    private val file: Path = DesktopTokenStorage.defaultConfigDir().resolve("onboarding.flag"),
    private val onboardingSeenFile: Path = file.resolveSibling("onboarding_seen.flag"),
) : OnboardingStore {

    override suspend fun hasCompletedFirstLogin(): Boolean = withContext(Dispatchers.IO) {
        file.exists()
    }

    override suspend fun markFirstLoginCompleted(): Unit = withContext(Dispatchers.IO) {
        Files.createDirectories(file.parent)
        if (!file.exists()) file.writeText("1")
    }

    override suspend fun hasSeenOnboarding(): Boolean = withContext(Dispatchers.IO) {
        onboardingSeenFile.exists()
    }

    override suspend fun markOnboardingSeen(): Unit = withContext(Dispatchers.IO) {
        Files.createDirectories(onboardingSeenFile.parent)
        if (!onboardingSeenFile.exists()) onboardingSeenFile.writeText("1")
    }
}
