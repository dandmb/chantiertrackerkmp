package com.dmb.chantiertracker

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.dmb.chantiertracker.di.initKoin
import com.dmb.chantiertracker.presentation.App
import com.dmb.chantiertracker.presentation.branding.appIconPainter

fun main() {
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
