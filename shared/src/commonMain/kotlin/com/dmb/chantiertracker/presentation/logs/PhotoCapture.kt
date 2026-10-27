package com.dmb.chantiertracker.presentation.logs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.vinceglb.filekit.PlatformFile

fun interface PhotoCapture {
    fun launch()
}

interface PhotoCaptureFactory {
    @Composable
    fun rememberCapture(onCaptured: (PlatformFile?) -> Unit): PhotoCapture?
}

expect val systemPhotoCapture: PhotoCaptureFactory

val LocalPhotoCapture = staticCompositionLocalOf { systemPhotoCapture }
