package com.dmb.chantiertracker.presentation.billing

import java.awt.Desktop
import java.net.URI

class DesktopUrlOpener : UrlOpener {
    override suspend fun open(url: String) {
        Desktop.getDesktop().browse(URI(url))
    }
}
