package com.dmb.chantiertracker.core

import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrashReportingSessionTest {

    private class RecordingCrashReporter : CrashReporter {
        val userIds = mutableListOf<String>()
        val keys = mutableMapOf<String, String>()
        override fun setUserId(id: String) { userIds += id }
        override fun setCustomKey(key: String, value: String) { keys[key] = value }
        fun everythingSent(): String = (userIds + keys.keys + keys.values).joinToString(" ")
    }

    private val sam = User(42, "sam@chantier.dev", "Sam Durand", true, GlobalRole.USER)

    @Test
    fun a_signed_in_account_is_known_by_its_id_only() = runTest {
        val reporter = RecordingCrashReporter()
        val state = MutableStateFlow<AuthState>(AuthState.Authenticated(sam))
        val job = launch { CrashReportingSession(reporter, "1.0.0 (17)").follow(state) }
        runCurrent()

        assertEquals("42", reporter.userIds.last())
        assertTrue("sam@chantier.dev" !in reporter.everythingSent(), "never the e-mail")
        assertTrue("Sam Durand" !in reporter.everythingSent(), "never the name")
        job.cancel()
    }

    @Test
    fun the_app_version_goes_with_every_report() = runTest {
        val reporter = RecordingCrashReporter()
        val job = launch { CrashReportingSession(reporter, "1.0.0 (17)").follow(MutableStateFlow(AuthState.Unknown)) }
        runCurrent()

        assertEquals("1.0.0 (17)", reporter.keys["app_version"])
        job.cancel()
    }

    @Test
    fun signing_out_or_a_forced_password_change_leaves_no_account_on_the_reports() = runTest {
        val reporter = RecordingCrashReporter()
        val state = MutableStateFlow<AuthState>(AuthState.Authenticated(sam))
        val job = launch { CrashReportingSession(reporter, "1.0.0 (17)").follow(state) }
        runCurrent()

        state.value = AuthState.Unauthenticated
        runCurrent()
        assertEquals("", reporter.userIds.last())

        state.value = AuthState.MustChangePassword("sam@chantier.dev")
        runCurrent()
        assertEquals("", reporter.userIds.last())
        assertTrue("sam@chantier.dev" !in reporter.everythingSent(), "never the e-mail")
        job.cancel()
    }
}
