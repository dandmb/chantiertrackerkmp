package com.dmb.chantiertracker.core

actual val devApiBaseUrl: String = "http://localhost:8080/api/v1"

class DesktopBuildInfo : BuildInfo {
    override val isDebug: Boolean =
        System.getProperty("chantiertracker.debug")?.toBooleanStrictOrNull() ?: false

    override val appVersion: String =
        System.getProperty("chantiertracker.appVersion")
            ?: DesktopBuildInfo::class.java.`package`?.implementationVersion
            ?: "dev"

    override val isStaging: Boolean = false
}
