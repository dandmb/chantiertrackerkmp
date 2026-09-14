package com.dmb.chantiertracker.presentation.projects.export

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.openFileWithDefaultApplication

// Desktop has no share sheet — both actions open the PDF in the system's
// default viewer, same fallback shape as VideoPlayer's Desktop actual (ADR-36).
actual suspend fun openExportedPdf(path: String) {
    FileKit.openFileWithDefaultApplication(PlatformFile(path))
}

actual suspend fun shareExportedPdf(path: String) {
    openExportedPdf(path)
}
