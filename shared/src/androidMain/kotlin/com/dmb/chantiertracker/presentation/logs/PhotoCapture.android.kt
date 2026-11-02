package com.dmb.chantiertracker.presentation.logs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.compose.rememberCameraPickerLauncher

actual val systemPhotoCapture: PhotoCaptureFactory = object : PhotoCaptureFactory {
    @Composable
    override fun rememberCapture(onCaptured: (PlatformFile?) -> Unit): PhotoCapture? {
        val launcher = rememberCameraPickerLauncher(onResult = onCaptured)
        return remember(launcher) { PhotoCapture { launcher.launch() } }
    }
}
