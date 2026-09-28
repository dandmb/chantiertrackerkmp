package com.dmb.chantiertracker.core

import android.content.Context
import android.content.pm.ApplicationInfo

actual val devApiBaseUrl: String = "http://10.0.2.2:8080/api/v1"

class AndroidBuildInfo(context: Context) : BuildInfo {
    override val isDebug: Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    override val appVersion: String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "?"

    // The staging build type sets applicationIdSuffix ".staging" (androidApp/build.gradle.kts) —
    // no BuildConfig flag needed, same "detect per platform without a plugin" approach as isDebug.
    override val isStaging: Boolean = context.packageName.endsWith(".staging")
}
