package com.dmb.chantiertracker.presentation.legal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import platform.Foundation.NSURL
import platform.SafariServices.SFSafariViewController

@Composable
actual fun platformLegalPageOpener(): LegalPageOpener {
    val controller = LocalUIViewController.current
    return remember(controller) {
        LegalPageOpener { url ->
            val nsUrl = NSURL(string = url) ?: throw IllegalArgumentException("Malformed URL: $url")
            controller.presentViewController(SFSafariViewController(uRL = nsUrl), animated = true, completion = null)
        }
    }
}
