package com.dmb.chantiertracker.core

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

data class NonFatalReport(
    val screen: String,
    val errorType: String,
    val httpStatus: Int?,
    val errorCode: String?,
    val cause: String?,
) {
    fun message(): String = buildString {
        append(screen).append(": ").append(errorType)
        httpStatus?.let { append(" http=").append(it) }
        errorCode?.let { append(" code=").append(it) }
        cause?.let { append(" cause=").append(it) }
    }
}

class ErrorReportingScreen private constructor(
    val name: String,
    val reportsNetworkFailures: Boolean,
) : AbstractCoroutineContextElement(ErrorReportingScreen) {

    companion object Key : CoroutineContext.Key<ErrorReportingScreen> {
        val Login = ErrorReportingScreen("login", reportsNetworkFailures = true)
        val Sync = ErrorReportingScreen("sync", reportsNetworkFailures = false)
    }
}

object NonFatalErrors {
    var sink: (NonFatalReport) -> Unit = {}

    fun report(
        screen: ErrorReportingScreen?,
        errorType: String,
        httpStatus: Int? = null,
        errorCode: String? = null,
        cause: String? = null,
        networkFailure: Boolean = false,
    ) {
        if (screen == null || (networkFailure && !screen.reportsNetworkFailures)) return
        val report = NonFatalReport(screen.name, errorType, httpStatus, errorCode?.takeIf(::isAnErrorCode), cause)
        runCatching { sink(report) }
    }

    private fun isAnErrorCode(code: String): Boolean = code.length <= 64 && code.all { it.isUpperCase() || it.isDigit() || it == '_' }
}
