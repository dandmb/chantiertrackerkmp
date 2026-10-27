package com.dmb.chantiertracker.presentation.logs

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.PlatformFile

actual val systemPhotoCapture: PhotoCaptureFactory = object : PhotoCaptureFactory {
    @Composable
    override fun rememberCapture(onCaptured: (PlatformFile?) -> Unit): PhotoCapture? = null
}
