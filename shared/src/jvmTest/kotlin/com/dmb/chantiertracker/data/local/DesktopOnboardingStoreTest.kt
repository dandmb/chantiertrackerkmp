package com.dmb.chantiertracker.data.local

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopOnboardingStoreTest {

    private val dir = Files.createTempDirectory("ct-onboarding")
    private val file = dir.resolve("onboarding.flag")

    @AfterTest
    fun cleanUp() {
        dir.toFile().deleteRecursively()
    }

    @Test
    fun flag_defaults_to_false_and_persists_once_set() = runTest {
        assertFalse(DesktopOnboardingStore(file).hasCompletedFirstLogin())

        DesktopOnboardingStore(file).markFirstLoginCompleted()

        // A fresh instance (= app relaunch) still sees the flag.
        assertTrue(DesktopOnboardingStore(file).hasCompletedFirstLogin())
    }

    @Test
    fun marking_twice_is_idempotent() = runTest {
        val store = DesktopOnboardingStore(file)
        store.markFirstLoginCompleted()
        store.markFirstLoginCompleted()
        assertTrue(store.hasCompletedFirstLogin())
    }

    @Test
    fun onboarding_seen_flag_is_independent_and_persists() = runTest {
        val store = DesktopOnboardingStore(file)
        assertFalse(store.hasSeenOnboarding())

        store.markOnboardingSeen()

        assertTrue(DesktopOnboardingStore(file).hasSeenOnboarding())
        // seeing the onboarding does not imply a first login
        assertFalse(DesktopOnboardingStore(file).hasCompletedFirstLogin())
    }
}
