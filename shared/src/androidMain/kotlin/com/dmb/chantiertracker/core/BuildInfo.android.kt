package com.dmb.chantiertracker.core

import android.content.Context
import android.content.pm.ApplicationInfo

actual val devApiBaseUrl: String = "http://10.0.2.2:8080/api/v1"

class AndroidBuildInfo(context: Context) : BuildInfo {
    override val isDebug: Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
