package com.dmb.chantiertracker.presentation.billing

/**
 * Opens [url] in the platform's own external browser (ADR-49) — Stripe
 * Checkout/the billing portal are hosted web flows, never rebuilt natively.
 *
 * Unlike `PdfOpener`/`PdfSharer` (ADR-48, which wrap a third-party `expect
 * fun` from FileKit), there's no library call to delegate to here — Android
 * needs a `Context` to build the `Intent`, which Koin already provides via
 * `androidContext()`, so this is one interface + one class per platform,
 * bound by constructor injection, the same pattern as `TokenStorage`/
 * `AppPreferences`/`ConnectivityObserver`.
 */
fun interface UrlOpener {
    suspend fun open(url: String)
}
