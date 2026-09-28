package com.dmb.chantiertracker.presentation.projects.export

/**
 * Opens the freshly-generated project PDF at [path] directly in the
 * platform's default PDF viewer (ADR-48) — Android → `Intent.ACTION_VIEW`
 * (`FileKit.openFileWithDefaultApplication`), iOS → `UIApplication.openURL`
 * falling back to the system "Open in…" menu, Desktop → `Desktop.open`
 * (`FileKit.openFileWithDefaultApplication` on all three — same call, three
 * actuals).
 *
 * FileKit ships its own Android `FileProvider` (authority
 * `${applicationId}.FileKitFileProvider`, covering the cache path), so no
 * manifest change is needed on `androidApp`.
 */
expect suspend fun openExportedPdf(path: String)

/**
 * Hands the freshly-generated project PDF at [path] to the platform's own
 * share sheet (ADR-48) — Android/iOS → `FileKit.shareFile` (a *send* sheet:
 * Gmail, Drive, Messages… — deliberately distinct from [openExportedPdf],
 * which opens the file directly). Desktop has no share sheet, so it behaves
 * the same as [openExportedPdf].
 */
expect suspend fun shareExportedPdf(path: String)

/**
 * Injectable seam over [openExportedPdf] — the real binding just delegates to
 * it (see `presentationModule`); `ProjectExportViewModelTest` passes a fake so
 * it can assert the export flow without presenting native UI.
 */
fun interface PdfOpener {
    suspend fun open(path: String)
}

/**
 * Injectable seam over [shareExportedPdf] — same idea as [PdfOpener].
 */
fun interface PdfSharer {
    suspend fun share(path: String)
}
