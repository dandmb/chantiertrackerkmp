package com.dmb.chantiertracker.presentation.projects.export

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.openFileWithDefaultApplication
import io.github.vinceglb.filekit.dialogs.shareFile

actual suspend fun openExportedPdf(path: String) {
    FileKit.openFileWithDefaultApplication(PlatformFile(path))
}

actual suspend fun shareExportedPdf(path: String) {
    FileKit.shareFile(PlatformFile(path))
}
