package com.dmb.chantiertracker.core

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

actual val devApiBaseUrl: String = "http://localhost:8080/api/v1"

class IosBuildInfo : BuildInfo {
    @OptIn(ExperimentalNativeApi::class)
    override val isDebug: Boolean = Platform.isDebugBinary
}
