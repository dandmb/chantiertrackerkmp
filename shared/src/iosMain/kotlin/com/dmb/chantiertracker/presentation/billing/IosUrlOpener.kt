package com.dmb.chantiertracker.presentation.billing

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

// A plain https:// URL — unlike the file:// case documented in PdfShare.ios.kt
// (ADR-48 follow-up), openURL reliably hands this to Safari; no fallback needed.
class IosUrlOpener : UrlOpener {
    override suspend fun open(url: String) {
        val nsUrl = NSURL(string = url) ?: throw IllegalArgumentException("Malformed URL: $url")
        UIApplication.sharedApplication.openURL(nsUrl)
    }
}
