package com.dmb.chantiertracker.core

import com.dmb.chantiertracker.domain.model.AuthState
import kotlinx.coroutines.flow.Flow

interface CrashReporter {
    fun setUserId(id: String)
    fun setCustomKey(key: String, value: String)
}

class CrashReportingSession(private val reporter: CrashReporter, private val appVersion: String) {

    suspend fun follow(authState: Flow<AuthState>) {
        reporter.setCustomKey(APP_VERSION_KEY, appVersion)
        authState.collect { reporter.setUserId(it.accountIdForCrashReports()) }
    }
}

private const val APP_VERSION_KEY = "app_version"

private fun AuthState.accountIdForCrashReports(): String =
    if (this is AuthState.Authenticated) user.id.toString() else ""

