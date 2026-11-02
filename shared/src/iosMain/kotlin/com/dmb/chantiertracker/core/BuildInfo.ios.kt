package com.dmb.chantiertracker.core

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import platform.Foundation.NSBundle

actual val platformDevApiBaseUrl: String = LOCAL_API_BASE_URL

class IosBuildInfo : BuildInfo {
    @OptIn(ExperimentalNativeApi::class)
    override val isDebug: Boolean = Platform.isDebugBinary

    override val appVersion: String =
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "?"

    override val isStaging: Boolean = false
}
