package com.dmb.chantiertracker

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.dmb.chantiertracker.di.initKoin
import com.dmb.chantiertracker.presentation.App
import com.dmb.chantiertracker.presentation.branding.appIconPainter
import io.github.vinceglb.filekit.FileKit

fun main() {
    FileKit.init(appId = "ChantierTracker")
    initKoin()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "ChantierTracker",
            icon = appIconPainter(),
        ) {
            App()
        }
    }
}
