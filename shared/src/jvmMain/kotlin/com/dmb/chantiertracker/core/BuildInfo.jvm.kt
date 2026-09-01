package com.dmb.chantiertracker.core

actual val devApiBaseUrl: String = "http://localhost:8080/api/v1"

class DesktopBuildInfo : BuildInfo {
    override val isDebug: Boolean =
        System.getProperty("chantiertracker.debug")?.toBooleanStrictOrNull() ?: false
}
