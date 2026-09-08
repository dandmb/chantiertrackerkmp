package com.dmb.chantiertracker.presentation.projects.export

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.shareFile

actual suspend fun shareExportedPdf(path: String) {
    FileKit.shareFile(PlatformFile(path))
}
