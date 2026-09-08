package com.dmb.chantiertracker.presentation.projects.export

/**
 * Hands the freshly-generated project PDF at [path] to the platform's own
 * "open / share" mechanism (ADR-48) — the same idea as `VideoPlayer`'s Desktop
 * fallback (ADR-36):
 * - Android / iOS → the native share sheet (`FileKit.shareFile`), which itself
 *   offers both "Open with…" and "Share to…" — covers both asks in one step.
 * - Desktop → open the file in the default PDF viewer
 *   (`FileKit.openFileWithDefaultApplication`).
 *
 * FileKit ships its own Android `FileProvider` (authority
 * `${applicationId}.FileKitFileProvider`, covering the cache path), so no
 * manifest change is needed on `androidApp`.
 */
expect suspend fun shareExportedPdf(path: String)

/**
 * Injectable seam over [shareExportedPdf] — the real binding just delegates to
 * it (see `presentationModule`); `ProjectExportViewModelTest` passes a fake so
 * it can assert the export flow without presenting a real share sheet.
 */
fun interface PdfSharer {
    suspend fun share(path: String)
}
